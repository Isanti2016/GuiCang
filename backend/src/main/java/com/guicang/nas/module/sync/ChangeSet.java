package com.guicang.nas.module.sync;

import com.guicang.nas.module.file.FileIndex;
import java.util.List;

/**
 * 扫描增量结果。
 *
 * @param added 新增条目
 * @param updated 已变更条目（path+mtime+size 与磁盘不一致）
 * @param deleted 索引存在但磁盘已不存在的路径
 */
public record ChangeSet(List<FileIndex> added, List<FileIndex> updated, List<String> deleted) {

  public static ChangeSet empty() {
    return new ChangeSet(List.of(), List.of(), List.of());
  }

  public int total() {
    return added.size() + updated.size() + deleted.size();
  }
}
