package com.guicang.nas.module.sync;

import java.util.List;

/** 同步任务服务：任务 CRUD、立即执行、历史查询。 */
public interface SyncService {

  /** 任务列表。 */
  List<SyncTask> listTasks();

  /** 新建任务（注册定时调度）。 */
  SyncTask createTask(String name, String sourceConfig, String cron);

  /** 编辑任务（重新调度）。 */
  SyncTask updateTask(Long id, String name, String sourceConfig, String cron, boolean enabled);

  /** 删除任务（取消调度）。 */
  void deleteTask(Long id);

  /** 立即执行（同步 + 应用索引 + 写历史）。 */
  SyncHistory runNow(Long taskId);

  /** 执行历史（按任务筛选，倒序）。 */
  List<SyncHistory> listHistory(Long taskId, long limit);

  /** 扫描并应用增量到 file_index（供 Quartz Job 与 runNow 复用）。 */
  SyncHistory execute(SyncTask task);
}
