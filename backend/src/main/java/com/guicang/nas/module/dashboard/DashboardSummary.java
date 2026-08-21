package com.guicang.nas.module.dashboard;

import java.util.List;

/** 大屏聚合数据。 */
public record DashboardSummary(
    long diskTotalKb,
    long diskAvailKb,
    long diskUsedPercent,
    long fileTotal,
    long fileDirs,
    long fileImages,
    long fileVideos,
    long fileNotes,
    long userTotal,
    long userEnabled,
    List<RecentOperation> recentOperations,
    List<UserStorageUsage> userStorage) {

  /** 最近操作条目。 */
  public record RecentOperation(
      long id, String username, String action, String resource, String result, Long createdAt) {}

  /** 用户存储占用（按 personal/<user>/ 从 file_index 聚合）。 */
  public record UserStorageUsage(String username, long bytes, long fileCount) {}
}
