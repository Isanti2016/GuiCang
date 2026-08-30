package com.guicang.nas.module.sync;

import com.guicang.nas.common.BizException;
import com.guicang.nas.infra.storage.FileTypeUtils;
import com.guicang.nas.module.file.FileIndexService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 自动整理执行器：遍历源目录下文件，按规则计算目标子目录，执行移动/复制并同步 file_index。
 *
 * <p>约束：
 *
 * <ul>
 *   <li>仅处理普通文件（目录不整理，仅 move 后清理空目录）；
 *   <li>单文件失败跳过并继续，最终汇总失败明细；
 *   <li>目标目录自动创建；
 *   <li>路径均做规范化并校验在存储根内（防目录穿越）；
 *   <li>保存任务时已校验目标目录不是源目录的子目录（防循环整理）；
 *   <li>冲突策略：skip 计为 skipped；overwrite/rename 会先清理目标旧索引再同步（防 UNIQUE 冲突）。
 * </ul>
 */
@Component
public class OrganizeExecutor {

  private static final Logger log = LoggerFactory.getLogger(OrganizeExecutor.class);

  private final FileIndexService fileIndexService;
  private final Path storageRoot;

  public OrganizeExecutor(
      FileIndexService fileIndexService, @Value("${guicang.storage.root}") String storageRoot) {
    this.fileIndexService = fileIndexService;
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  /**
   * 执行自动整理。
   *
   * @param task 自动整理任务（taskType=organize）
   * @param rule 整理规则
   * @return 执行结果（处理数/成功数/失败数/跳过数/失败明细）
   */
  public OrganizeResult execute(SyncTask task, OrganizeRule rule) {
    Path source = resolve(task.getSourceConfig());
    Path targetBase = resolve(task.getTargetConfig());
    if (!Files.isDirectory(source)) {
      return OrganizeResult.empty();
    }
    String action = task.getAction() == null ? "move" : task.getAction();
    String conflict = task.getConflict() == null ? "rename" : task.getConflict();

    int processed = 0;
    int succeeded = 0;
    int skipped = 0;
    List<String> errors = new ArrayList<>();

    try (var walk = Files.walk(source)) {
      List<Path> files =
          walk.filter(p -> Files.isRegularFile(p) && !p.equals(source)).sorted().toList();
      for (Path file : files) {
        processed++;
        try {
          Path target = targetBase.resolve(rule.subPath(file, file.getFileName().toString()));
          Files.createDirectories(target);
          Path dest = resolveConflict(target.resolve(file.getFileName()), conflict);
          if (dest == null) {
            skipped++; // 冲突策略 skip 且已存在同名文件
            continue;
          }
          moveOrCopy(file, dest, action);
          syncIndex(file, dest, action);
          succeeded++;
        } catch (Exception e) {
          errors.add(relativize(file) + " -> " + e.getMessage());
          log.warn("整理文件失败 {}: {}", file, e.getMessage());
        }
      }
    } catch (IOException e) {
      throw new BizException("遍历源目录失败: " + e.getMessage());
    }

    if ("move".equals(action)) {
      cleanupEmptyDirs(source);
    }
    return new OrganizeResult(processed, succeeded, errors.size(), skipped, errors);
  }

  private Path resolve(String relative) {
    String base = normalize(relative);
    Path p = storageRoot;
    if (!base.isEmpty()) {
      p = p.resolve(base).normalize();
    }
    if (!p.startsWith(storageRoot)) {
      throw new BizException("路径越界: " + relative);
    }
    return p;
  }

  private void moveOrCopy(Path src, Path dest, String action) throws IOException {
    if ("copy".equals(action)) {
      Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
    } else {
      Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** 冲突处理：skip 返回 null（跳过）；overwrite/rename 返回最终目标路径。 */
  private Path resolveConflict(Path dest, String conflict) {
    if (!Files.exists(dest)) {
      return dest;
    }
    if ("skip".equals(conflict)) {
      return null;
    }
    if ("overwrite".equals(conflict)) {
      return dest;
    }
    // rename：加序号 (1)、(2)...
    Path parent = dest.getParent();
    String name = dest.getFileName().toString();
    int dot = name.lastIndexOf('.');
    String base = dot >= 0 ? name.substring(0, dot) : name;
    String ext = dot >= 0 ? name.substring(dot) : "";
    for (int i = 1; ; i++) {
      Path candidate = parent.resolve(base + " (" + i + ")" + ext);
      if (!Files.exists(candidate)) {
        return candidate;
      }
    }
  }

  /**
   * 同步 file_index：move 优先 rename（无旧索引则 upsert）；copy → upsert 新路径。 目标已有同名索引时先清理 （file_index.path 有
   * UNIQUE 约束，直接 rename 会冲突）。
   */
  private void syncIndex(Path src, Path dest, String action) {
    String oldPath = relativize(src);
    String newPath = relativize(dest);
    String name = dest.getFileName().toString();
    if ("copy".equals(action)) {
      // upsert 本身是「存在则更新」，不会产生 UNIQUE 冲突
      fileIndexService.upsert(
          newPath, name, FileTypeUtils.kind(name), sizeOf(dest), mtimeOf(dest), null);
      return;
    }
    if (fileIndexService.exists(newPath)) {
      // overwrite 场景：目标旧索引先删，避免 rename 撞 UNIQUE
      fileIndexService.remove(newPath);
    }
    if (fileIndexService.exists(oldPath)) {
      fileIndexService.rename(oldPath, newPath);
    } else {
      fileIndexService.upsert(
          newPath, name, FileTypeUtils.kind(name), sizeOf(dest), mtimeOf(dest), null);
    }
  }

  /**
   * move 后清理源目录下产生的空目录（保留源根目录），并同步删除对应目录索引。
   *
   * @param source 源目录
   */
  private void cleanupEmptyDirs(Path source) {
    try (var walk = Files.walk(source)) {
      walk.filter(p -> !p.equals(source) && Files.isDirectory(p))
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  if (isEmpty(p)) {
                    Files.delete(p);
                    // 目录索引一并清理，保持 file_index 与磁盘一致
                    fileIndexService.remove(relativize(p));
                  }
                } catch (IOException e) {
                  log.debug("清理空目录失败 {}: {}", p, e.getMessage());
                }
              });
    } catch (IOException e) {
      log.warn("清理空目录遍历失败: {}", e.getMessage());
    }
  }

  private boolean isEmpty(Path dir) throws IOException {
    try (var s = Files.list(dir)) {
      return s.findAny().isEmpty();
    }
  }

  private String relativize(Path path) {
    return storageRoot.relativize(path).toString().replace('\\', '/');
  }

  private long sizeOf(Path p) {
    try {
      return Files.size(p);
    } catch (IOException e) {
      return 0L;
    }
  }

  private long mtimeOf(Path p) {
    try {
      return Files.getLastModifiedTime(p).toMillis();
    } catch (IOException e) {
      return 0L;
    }
  }

  private String normalize(String relative) {
    if (relative == null) {
      return "";
    }
    String trimmed = relative.trim().replace('\\', '/');
    while (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
