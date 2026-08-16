package com.guicang.nas.infra.storage;

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
}
