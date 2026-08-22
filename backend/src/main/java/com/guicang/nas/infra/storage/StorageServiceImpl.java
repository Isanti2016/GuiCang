package com.guicang.nas.infra.storage;

import com.guicang.nas.common.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 存储访问服务实现（基于 java.nio.file，全部路径经 {@link PathUtils} 校验）。 */
@Service
public class StorageServiceImpl implements StorageService {

  private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);
  private static final String TMP_DIR = ".guicang-tmp";

  private final Path storageRoot;

  public StorageServiceImpl(@Value("${guicang.storage.root}") String storageRoot) {
    this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
  }

  /**
   * 目录列表（按名称排序，目录在前）。
   *
   * @param relativePath 存储根下相对路径
   * @return 目录下的文件条目列表
   */
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

  /**
   * 新建目录（含多级）。
   *
   * @param relativePath 要创建的目录相对路径
   */
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

  /**
   * 重命名/移动目录项。
   *
   * @param relativePath 原相对路径
   * @param newName 新名称
   */
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

  /**
   * 移动到目标路径。
   *
   * @param relativePath 源相对路径
   * @param targetPath 目标目录相对路径
   */
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

  /**
   * 移动到指定完整目标路径（同卷原子改名；用于回收站迁移/恢复）。
   *
   * @param sourceRelativePath 源相对路径
   * @param targetRelativePath 目标完整相对路径
   */
  @Override
  public void moveTo(String sourceRelativePath, String targetRelativePath) {
    Path source = PathUtils.resolve(storageRoot, sourceRelativePath);
    PathUtils.checkRealPath(storageRoot, source);
    Path target = PathUtils.resolve(storageRoot, targetRelativePath);
    PathUtils.checkRealPath(storageRoot, target);
    if (!Files.exists(source)) {
      throw new BizException("不存在: " + sourceRelativePath);
    }
    if (Files.exists(target)) {
      throw new BizException("目标已存在: " + targetRelativePath);
    }
    Path parent = target.getParent();
    if (parent != null && !Files.isDirectory(parent)) {
      try {
        Files.createDirectories(parent);
      } catch (IOException e) {
        throw new BizException("目标目录不可用: " + parent);
      }
    }
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new BizException("移动失败: " + sourceRelativePath);
    }
  }

  /**
   * 删除目录项（目录须为空或递归删除）。
   *
   * @param relativePath 相对路径
   * @param recursive 是否递归删除
   */
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

  /**
   * 流式上传：先写同卷临时文件 {@code <root>/.guicang-tmp/<uuid>.part}，完成后原子改名到目标。
   *
   * @param targetRelativePath 目标文件相对路径（含文件名）
   * @param inputStream 内容流
   * @param size 内容字节数（用于校验与进度）
   */
  @Override
  public void upload(String targetRelativePath, InputStream inputStream, long size) {
    Path target = PathUtils.resolve(storageRoot, targetRelativePath);
    PathUtils.checkRealPath(storageRoot, target);
    validateName(target.getFileName().toString());
    if (Files.exists(target)) {
      throw new BizException("目标已存在: " + targetRelativePath);
    }
    Path tmpDir = storageRoot.resolve(TMP_DIR);
    try {
      Files.createDirectories(tmpDir);
      // 同卷临时文件，保证最终 rename 原子且不跨设备
      Path tmp = tmpDir.resolve(UUID.randomUUID() + ".part");
      try {
        copyStream(inputStream, tmp, size);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
      } finally {
        Files.deleteIfExists(tmp);
      }
    } catch (IOException e) {
      throw new BizException("上传写入失败: " + targetRelativePath);
    }
  }

  /**
   * 文本写入（md/txt 保存）：同卷临时文件 + 原子改名，允许覆盖已存在文件。
   *
   * @param relativePath 目标相对路径
   * @param content 文本内容
   */
  @Override
  public void writeText(String relativePath, String content) {
    Path target = PathUtils.resolve(storageRoot, relativePath);
    PathUtils.checkRealPath(storageRoot, target);
    validateName(target.getFileName().toString());
    Path tmpDir = storageRoot.resolve(TMP_DIR);
    try {
      Files.createDirectories(tmpDir);
      Path tmp = tmpDir.resolve(UUID.randomUUID() + ".part");
      try {
        Files.writeString(tmp, content);
        Files.move(
            tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(tmp);
      }
    } catch (IOException e) {
      throw new BizException("文本保存失败: " + relativePath);
    }
  }

  /**
   * 读取文本文件内容（超过 maxBytes 拒绝，防内存放大）。
   *
   * @param relativePath 文件相对路径
   * @param maxBytes 最大允许字节数
   * @return 文本内容
   */
  @Override
  public String readText(String relativePath, long maxBytes) {
    Path file = resolveFile(relativePath);
    try {
      long size = Files.size(file);
      if (size > maxBytes) {
        throw new BizException("文本文件过大（超过 " + (maxBytes / 1024 / 1024) + "MB），请用下载查看");
      }
      return Files.readString(file);
    } catch (IOException e) {
      throw new BizException("读取文本失败: " + relativePath);
    }
  }

  /**
   * 解析并校验文件相对路径，返回绝对路径（用于下载/预览流式读取）。
   *
   * @param relativePath 文件相对路径
   * @return 文件绝对路径
   */
  @Override
  public Path resolveFile(String relativePath) {
    Path file = PathUtils.resolve(storageRoot, relativePath);
    PathUtils.checkRealPath(storageRoot, file);
    if (!Files.isRegularFile(file)) {
      throw new BizException("文件不存在: " + relativePath);
    }
    return file;
  }

  /**
   * 存储根绝对路径。
   *
   * @return 存储根路径
   */
  @Override
  public Path root() {
    return storageRoot;
  }

  private void copyStream(InputStream inputStream, Path tmp, long expectedSize) throws IOException {
    try (OutputStream out = Files.newOutputStream(tmp)) {
      byte[] buffer = new byte[8192];
      long written = 0;
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        out.write(buffer, 0, read);
        written += read;
      }
      if (expectedSize >= 0 && written != expectedSize) {
        throw new IOException("文件大小与声明不符: expected=" + expectedSize + " actual=" + written);
      }
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
