package com.guicang.nas.module.sync;

import com.guicang.nas.module.notification.NotificationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Quartz 同步执行任务：从 JobDataMap 取 taskId 执行同步并更新任务状态。 */
@Component
@DisallowConcurrentExecution
public class SyncRunJob implements Job {

  public static final String JOB_DATA_TASK_ID = "taskId";

  private final SyncService syncService;
  private final SyncTaskMapper syncTaskMapper;
  private final NotificationService notificationService;

  public SyncRunJob(
      SyncService syncService,
      SyncTaskMapper syncTaskMapper,
      NotificationService notificationService) {
    this.syncService = syncService;
    this.syncTaskMapper = syncTaskMapper;
    this.notificationService = notificationService;
  }

  @Override
  public void execute(JobExecutionContext context) {
    JobDataMap data = context.getMergedJobDataMap();
    Long taskId = data.getLong(JOB_DATA_TASK_ID);
    SyncTask task = syncTaskMapper.selectById(taskId);
    if (task == null) {
      return;
    }
    SyncHistory history = syncService.execute(task);
    task.setLastRunAt(history.getStartedAt());
    task.setLastStatus(history.getStatus());
    syncTaskMapper.updateById(task);
    if ("failed".equals(history.getStatus()) || "partial".equals(history.getStatus())) {
      int failed = history.getFailed() == null ? 0 : history.getFailed();
      String result = "failed".equals(history.getStatus()) ? "失败" : "部分失败";
      notificationService.create(
          "sync", "同步任务异常",
          "任务「" + task.getName() + "」执行" + result + "（失败 " + failed + " 项）。", null);
    }
  }
}
