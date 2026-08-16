package com.guicang.nas.module.sync;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Quartz 调度管理：启动时从 sync_task 注册启用任务，任务变更时增删改调度。
 *
 * <p>使用内存 JobStore；重启后由本组件重新注册（任务定义以 DB 为准）。
 */
@Component
public class SyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

  private final Scheduler scheduler;
  private final SyncTaskMapper syncTaskMapper;

  public SyncScheduler(Scheduler scheduler, SyncTaskMapper syncTaskMapper) {
    this.scheduler = scheduler;
    this.syncTaskMapper = syncTaskMapper;
  }

  @PostConstruct
  public void init() {
    List<SyncTask> tasks = syncTaskMapper.selectList(null);
    tasks.stream()
        .filter(t -> t.getEnabled() != null && t.getEnabled() == 1)
        .forEach(this::registerTask);
    log.info("Quartz 已注册 {} 个启用同步任务", tasks.size());
  }

  public void registerTask(SyncTask task) {
    try {
      if (scheduler.checkExists(jobKey(task.getId()))) {
        rescheduleTask(task);
        return;
      }
      JobDetail job = buildJob(task);
      scheduler.scheduleJob(job, buildTrigger(task));
      log.info("已调度同步任务 {} (id={})", task.getName(), task.getId());
    } catch (SchedulerException e) {
      log.error("调度任务失败 id={}", task.getId(), e);
    }
  }

  public void rescheduleTask(SyncTask task) {
    try {
      if (task.getEnabled() == null || task.getEnabled() == 0) {
        unregisterTask(task.getId());
        return;
      }
      scheduler.deleteJob(jobKey(task.getId()));
      scheduler.scheduleJob(buildJob(task), buildTrigger(task));
      log.info("已重新调度同步任务 {} (id={})", task.getName(), task.getId());
    } catch (SchedulerException e) {
      log.error("重新调度任务失败 id={}", task.getId(), e);
    }
  }

  public void unregisterTask(Long taskId) {
    try {
      scheduler.deleteJob(jobKey(taskId));
      log.info("已移除同步任务调度 id={}", taskId);
    } catch (SchedulerException e) {
      log.error("移除任务调度失败 id={}", taskId, e);
    }
  }

  private JobDetail buildJob(SyncTask task) {
    return JobBuilder.newJob(SyncRunJob.class)
        .withIdentity(jobKey(task.getId()))
        .usingJobData(SyncRunJob.JOB_DATA_TASK_ID, task.getId())
        .build();
  }

  private Trigger buildTrigger(SyncTask task) {
    return TriggerBuilder.newTrigger()
        .withIdentity(triggerKey(task.getId()))
        .withSchedule(CronScheduleBuilder.cronSchedule(task.getCron()))
        .forJob(jobKey(task.getId()))
        .build();
  }

  private JobKey jobKey(Long taskId) {
    return JobKey.jobKey("sync-task-" + taskId);
  }

  private TriggerKey triggerKey(Long taskId) {
    return TriggerKey.triggerKey("sync-trigger-" + taskId);
  }
}
