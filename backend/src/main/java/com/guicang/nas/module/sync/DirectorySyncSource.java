package com.guicang.nas.module.sync;

import com.guicang.nas.infra.storage.FileTypeUtils;
import com.guicang.nas.module.file.FileIndex;
import com.guicang.nas.module.file.FileIndexService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 目录扫描同步源（一期）：遍历目录树，与 file_index 按 path+mtime+size 对比， 生成新增/变更/删除增量；扫描为只读，不直接改索引（应用由 SyncService
 * 负责）。
 */
@Component
public class DirectorySyncSource implements SyncSource {

  private final FileIndexService fileIndexService;
  private final Path storageRoot;

  public DirectorySyncSource(
      FileIndexService fileIndexService, @Value("${guicang.storage.root}") String storageRoot) {
    this.fileIndexService = fileIndexService;
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public ChangeSet scan(String relativePath) {
    String base = normalize(relativePath);
    Path root = storageRoot;
    if (!base.isEmpty()) {
      root = root.resolve(base).normalize();
    }
    if (!Files.isDirectory(root)) {
      return ChangeSet.empty();
    }

    // 1. 遍历磁盘，建立 相对路径 → 条目 映射
    final Path walkRoot = root;
    Map<String, FileIndex> diskEntries = new HashMap<>();
    try (var walk = Files.walk(walkRoot)) {
      walk.filter(p -> !p.equals(walkRoot))
          .forEach(p -> diskEntries.put(relativize(p), toIndex(p)));
    } catch (IOException e) {
      return ChangeSet.empty();
    }

    // 2. 现有索引（扫描前缀下）
    List<FileIndex> existing = fileIndexService.listByPrefix(base);
    Map<String, FileIndex> existingMap = new HashMap<>();
    for (FileIndex idx : existing) {
      existingMap.put(idx.getPath(), idx);
    }

    // 3. 对比
    List<FileIndex> added = new ArrayList<>();
    List<FileIndex> updated = new ArrayList<>();
    for (Map.Entry<String, FileIndex> entry : diskEntries.entrySet()) {
      FileIndex disk = entry.getValue();
      FileIndex old = existingMap.get(entry.getKey());
      if (old == null) {
        added.add(disk);
      } else if (changed(old, disk)) {
        updated.add(disk);
      }
    }

    Set<String> diskPaths = diskEntries.keySet();
    List<String> deleted =
        existing.stream().map(FileIndex::getPath).filter(p -> !diskPaths.contains(p)).toList();

    return new ChangeSet(added, updated, deleted);
  }

  private boolean changed(FileIndex old, FileIndex disk) {
    return !java.util.Objects.equals(old.getSize(), disk.getSize())
        || !java.util.Objects.equals(old.getMtime(), disk.getMtime())
        || !java.util.Objects.equals(old.getKind(), disk.getKind());
  }

  private String relativize(Path path) {
    return storageRoot.relativize(path).toString().replace('\\', '/');
  }

  private FileIndex toIndex(Path path) {
    String rel = relativize(path);
    String name = path.getFileName().toString();
    FileIndex index = new FileIndex();
    index.setPath(rel);
    index.setName(name);
    int dot = name.lastIndexOf('.');
    index.setExt(dot >= 0 ? name.substring(dot + 1).toLowerCase() : null);
    index.setKind(FileTypeUtils.kind(name));
    try {
      if (Files.isDirectory(path)) {
        index.setKind("dir");
        index.setSize(0L);
      } else {
        index.setSize(Files.size(path));
      }
      index.setMtime(Files.getLastModifiedTime(path).toMillis());
    } catch (IOException e) {
      index.setSize(0L);
      index.setMtime(0L);
    }
    return index;
  }

  private String normalize(String relativePath) {
    if (relativePath == null) {
      return "";
    }
    String trimmed = relativePath.trim().replace('\\', '/');
    while (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
