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

  /** 路径是否存在索引。 */
  boolean exists(String relativePath);

  /** 按名称关键字搜索（LIKE 匹配 name）。 */
  List<FileIndex> search(String keyword);

  /** 按路径前缀查询（含前缀自身，用于目录扫描对比）。 */
  List<FileIndex> listByPrefix(String prefix);

  /** 更新文件全文内容（md/txt）。 */
  void updateContent(String path, String content);

  /** 全文检索（LIKE 匹配 content）。 */
  List<FileIndex> searchContent(String keyword);

  /** 统计某路径前缀下所有文件的大小总和（字节）。 */
  long sumSizeByPrefix(String prefix);
}
