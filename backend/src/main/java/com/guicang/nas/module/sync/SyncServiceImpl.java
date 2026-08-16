package com.guicang.nas.module.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.module.file.FileIndexService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 同步任务服务实现：CRUD + 执行（扫描 → 应用增量 → 写历史）。 */
@Service
public class SyncServiceImpl implements SyncService {

  private final SyncTaskMapper syncTaskMapper;
  private final SyncHistoryMapper syncHistoryMapper;
  private final SyncSource syncSource;
  private final FileIndexService fileIndexService;
  private final SyncScheduler syncScheduler;

  public SyncServiceImpl(
      SyncTaskMapper syncTaskMapper,
      SyncHistoryMapper syncHistoryMapper,
      SyncSource syncSource,
      FileIndexService fileIndexService,
      SyncScheduler syncScheduler) {
    this.syncTaskMapper = syncTaskMapper;
    this.syncHistoryMapper = syncHistoryMapper;
    this.syncSource = syncSource;
    this.fileIndexService = fileIndexService;
    this.syncScheduler = syncScheduler;
  }

  @Override
  public List<SyncTask> listTasks() {
    return syncTaskMapper.selectList(
        new LambdaQueryWrapper<SyncTask>().orderByDesc(SyncTask::getId));
  }

  @Override
  @Transactional
  @Audit(action = "sync.create", resource = "#name")
  public SyncTask createTask(String name, String sourceConfig, String cron) {
    validateCron(cron);
    SyncTask task = new SyncTask();
    task.setName(name);
    task.setSourceType("directory");
    task.setSourceConfig(sourceConfig);
    task.setCron(cron);
    task.setEnabled(1);
    task.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    syncTaskMapper.insert(task);
    syncScheduler.registerTask(task);
    return task;
  }

  @Override
  @Transactional
  @Audit(action = "sync.update", resource = "#id")
  public SyncTask updateTask(
      Long id, String name, String sourceConfig, String cron, boolean enabled) {
    SyncTask task = requireTask(id);
    validateCron(cron);
    task.setName(name);
    task.setSourceConfig(sourceConfig);
    task.setCron(cron);
    task.setEnabled(enabled ? 1 : 0);
    syncTaskMapper.updateById(task);
    syncScheduler.rescheduleTask(task);
    return task;
  }

  @Override
  @Transactional
  @Audit(action = "sync.delete", resource = "#id")
  public void deleteTask(Long id) {
    SyncTask task = requireTask(id);
    syncScheduler.unregisterTask(task.getId());
    syncTaskMapper.deleteById(id);
  }

  @Override
  public SyncHistory runNow(Long taskId) {
    SyncTask task = requireTask(taskId);
    SyncHistory history = execute(task);
    task.setLastRunAt(history.getStartedAt());
    task.setLastStatus(history.getStatus());
    syncTaskMapper.updateById(task);
    return history;
  }

  @Override
  public List<SyncHistory> listHistory(Long taskId, long limit) {
    return syncHistoryMapper.selectList(
        new LambdaQueryWrapper<SyncHistory>()
            .eq(taskId != null, SyncHistory::getTaskId, taskId)
            .orderByDesc(SyncHistory::getId)
            .last("LIMIT " + Math.min(limit, 200)));
  }

  @Override
  @Transactional
  public SyncHistory execute(SyncTask task) {
    SyncHistory history = new SyncHistory();
    history.setTaskId(task.getId());
    history.setStatus("running");
    history.setStartedAt(System.currentTimeMillis());
    history.setAdded(0);
    history.setUpdated(0);
    history.setDeleted(0);
    syncHistoryMapper.insert(history);

    try {
      ChangeSet changeSet = syncSource.scan(task.getSourceConfig());
      changeSet.added().forEach(idx -> applyAdded(idx));
      changeSet.updated().forEach(idx -> applyAdded(idx));
      changeSet.deleted().forEach(fileIndexService::remove);

      history.setAdded(changeSet.added().size());
      history.setUpdated(changeSet.updated().size());
      history.setDeleted(changeSet.deleted().size());
      history.setStatus("success");
      history.setFinishedAt(System.currentTimeMillis());
      syncHistoryMapper.updateById(history);
      return history;
    } catch (Exception e) {
      history.setStatus("failed");
      history.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
      history.setFinishedAt(System.currentTimeMillis());
      syncHistoryMapper.updateById(history);
      return history;
    }
  }

  private void applyAdded(com.guicang.nas.module.file.FileIndex idx) {
    fileIndexService.upsert(
        idx.getPath(), idx.getName(), idx.getKind(), idx.getSize(), idx.getMtime(), idx.getOwner());
  }

  private SyncTask requireTask(Long id) {
    SyncTask task = syncTaskMapper.selectById(id);
    if (task == null) {
      throw new BizException("任务不存在: id=" + id);
    }
    return task;
  }

  private void validateCron(String cron) {
    if (cron == null || !CronExpression.isValidExpression(cron)) {
      throw new BizException("cron 表达式不合法（Quartz 6 段格式）");
    }
  }
}
