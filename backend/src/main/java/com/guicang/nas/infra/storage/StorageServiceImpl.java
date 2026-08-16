package com.guicang.nas.infra.storage;

import com.guicang.nas.common.BizException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 存储访问服务实现（基于 java.nio.file，全部路径经 {@link PathUtils} 校验）。 */
@Service
public class StorageServiceImpl implements StorageService {

  private final Path storageRoot;

  public StorageServiceImpl(@Value("${guicang.storage.root}") String storageRoot) {
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  @Override
  public List<FileEntry> list(String relativePath) {
    Path dir = PathUtils.resolve(storageRoot, relativePath);
    PathUtils.checkRealPath(storageRoot, dir);
    if (!Files.isDirectory(dir)) {
      throw new BizException("目录不存在: " + relativePath);
    }
    try (var stream = Files.list(dir)) {
      return stream
          .map(this::toEntry)
          .sorted(Comparator.comparing(FileEntry::dir).reversed().thenComparing(FileEntry::name))
          .toList();
    } catch (IOException e) {
      throw new BizException("读取目录失败: " + relativePath);
    }
  }

  @Override
  public void mkdir(String relativePath) {
    Path target = PathUtils.resolve(storageRoot, relativePath);
    PathUtils.checkRealPath(storageRoot, target);
    if (Files.exists(target)) {
      throw new BizException("已存在同名目录/文件: " + relativePath);
    }
    try {
      Files.createDirectories(target);
    } catch (IOException e) {
      throw new BizException("创建目录失败: " + relativePath);
    }
  }

  @Override
  public void rename(String relativePath, String newName) {
    Path source = PathUtils.resolve(storageRoot, relativePath);
    PathUtils.checkRealPath(storageRoot, source);
    validateName(newName);
    Path target = source.resolveSibling(newName);
    if (Files.exists(target)) {
      throw new BizException("目标已存在: " + newName);
    }
    try {
      Files.move(source, target);
    } catch (IOException e) {
      throw new BizException("重命名失败: " + relativePath);
    }
  }

  @Override
  public void move(String relativePath, String targetPath) {
    Path source = PathUtils.resolve(storageRoot, relativePath);
    PathUtils.checkRealPath(storageRoot, source);
    Path target = PathUtils.resolve(storageRoot, targetPath);
    PathUtils.checkRealPath(storageRoot, target);
    if (!Files.isDirectory(target)) {
      throw new BizException("目标必须是目录: " + targetPath);
    }
    Path dest = target.resolve(source.getFileName());
    if (Files.exists(dest)) {
      throw new BizException("目标位置已存在同名项");
    }
    try {
      Files.move(source, dest, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new BizException("移动失败: " + relativePath);
    }
  }

  @Override
  public void delete(String relativePath, boolean recursive) {
    Path target = PathUtils.resolve(storageRoot, relativePath);
    PathUtils.checkRealPath(storageRoot, target);
    if (!Files.exists(target)) {
      throw new BizException("不存在: " + relativePath);
    }
    try {
      if (recursive) {
        try (var walk = Files.walk(target)) {
          walk.sorted(Comparator.reverseOrder()).forEach(p -> deleteQuietly(p));
        }
      } else {
        Files.delete(target);
      }
    } catch (IOException e) {
      throw new BizException("删除失败: " + relativePath);
    }
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      // 单文件删除失败不中断整体递归（后续抛错由调用方感知）
    }
  }

  private void validateName(String name) {
    if (name == null || name.isBlank() || name.contains("/") || name.contains("\\")) {
      throw new BizException("名称不合法");
    }
  }

  private FileEntry toEntry(Path path) {
    try {
      boolean isDir = Files.isDirectory(path);
      long size = isDir ? 0 : Files.size(path);
      long mtime = Files.getLastModifiedTime(path).toMillis();
      String name = path.getFileName().toString();
      return new FileEntry(name, relativePathOf(path), isDir, size, mtime, classify(name, isDir));
    } catch (IOException e) {
      return new FileEntry(
          path.getFileName().toString(), relativePathOf(path), false, 0, 0, "other");
    }
  }

  private String relativePathOf(Path path) {
    return storageRoot.relativize(path).toString().replace('\\', '/');
  }

  /** 按扩展名归类：dir/image/video/note/other。 */
  static String classify(String name, boolean isDir) {
    if (isDir) {
      return "dir";
    }
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      return "other";
    }
    String ext = name.substring(dot + 1).toLowerCase();
    return switch (ext) {
      case "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> "image";
      case "mp4", "mkv", "webm", "avi", "mov", "m4v" -> "video";
      case "md", "txt", "markdown" -> "note";
      default -> "other";
    };
  }
}
