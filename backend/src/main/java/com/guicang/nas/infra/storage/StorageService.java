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

  /** 移动到指定完整目标路径（同卷原子改名；用于回收站迁移/恢复）。 */
  void moveTo(String sourceRelativePath, String targetRelativePath);

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

  /**
   * 文本写入（md/txt 保存）：同卷临时文件 + 原子改名，允许覆盖已存在文件。
   *
   * @param relativePath 目标相对路径
   * @param content 文本内容
   */
  void writeText(String relativePath, String content);

  /** 读取文本文件内容（超过 maxBytes 拒绝，防内存放大）。 */
  String readText(String relativePath, long maxBytes);

  /** 解析并校验文件相对路径，返回绝对路径（用于下载/预览流式读取）。 */
  Path resolveFile(String relativePath);

  /** 存储根绝对路径。 */
  Path root();
}
