package com.guicang.nas.infra.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/** 存储访问服务：目录/文件系统操作（全部经路径规范化与越权校验）。 */
public interface StorageService {

  /** 目录列表（按名称排序，目录在前）。 */
  List<FileEntry> list(String relativePath);

  /** 新建目录（含多级）。 */
  void mkdir(String relativePath);

  /** 重命名/移动目录项。 */
  void rename(String relativePath, String newName);

  /** 移动到目标路径。 */
  void move(String relativePath, String targetPath);

  /** 删除目录项（目录须为空或递归删除）。 */
  void delete(String relativePath, boolean recursive);

  /**
   * 流式上传：先写同卷临时文件 {@code <root>/.guicang-tmp/<uuid>.part}，完成后原子改名到目标。
   *
   * @param targetRelativePath 目标文件相对路径（含文件名）
   * @param inputStream 内容流
   * @param size 内容字节数（用于校验与进度）
   */
  void upload(String targetRelativePath, InputStream inputStream, long size);

  /** 解析并校验文件相对路径，返回绝对路径（用于下载/预览流式读取）。 */
  Path resolveFile(String relativePath);

  /** 存储根绝对路径。 */
  Path root();
}
