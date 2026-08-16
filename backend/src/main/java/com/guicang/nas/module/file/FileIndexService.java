package com.guicang.nas.module.file;

import java.util.List;

/** 文件索引维护与搜索服务。 */
public interface FileIndexService {

  /** 新增/更新索引（path 唯一，存在则更新）。 */
  void upsert(String relativePath, String name, String kind, Long size, Long mtime, String owner);

  /** 删除索引。 */
  void remove(String relativePath);

  /** 重命名/移动后更新路径。 */
  void rename(String oldPath, String newPath);

  /** 按名称关键字搜索（LIKE 匹配 name）。 */
  List<FileIndex> search(String keyword);
}
