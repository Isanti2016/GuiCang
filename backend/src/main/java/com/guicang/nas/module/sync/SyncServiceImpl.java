package com.guicang.nas.module.sync;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.module.file.FileIndexService;
import com.guicang.nas.module.sync.dto.SyncTaskRequest;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同步任务服务实现：CRUD + 执行（索引扫描 / 自动整理 → 写历史）。
 *
 * <p>执行按 {@code taskType} 分发：index_scan 走目录扫描增量更新 file_index；organize 走自动整理
 * 引擎移动/复制真实文件并同步索引。同一任务同一时刻仅允许一个执行实例（任务级锁）。
 */
@Service
public class SyncServiceImpl implements SyncService {

  private final SyncTaskMapper syncTaskMapper;
  private final SyncHistoryMapper syncHistoryMapper;
  private final SyncSource syncSource;
  private final FileIndexService fileIndexService;
  private final SyncScheduler syncScheduler;
  private final OrganizeExecutor organizeExecutor;
  private final ConcurrentHashMap<Long, AtomicBoolean> runningLocks = new ConcurrentHashMap<>();

  public SyncServiceImpl(
      SyncTaskMapper syncTaskMapper,
      SyncHistoryMapper syncHistoryMapper,
      SyncSource syncSource,
      FileIndexService fileIndexService,
      SyncScheduler syncScheduler,
      OrganizeExecutor organizeExecutor) {
    this.syncTaskMapper = syncTaskMapper;
    this.syncHistoryMapper = syncHistoryMapper;
    this.syncSource = syncSource;
    this.fileIndexService = fileIndexService;
    this.syncScheduler = syncScheduler;
    this.organizeExecutor = organizeExecutor;
  }

  /**
   * 任务列表。
   *
   * @return 同步任务列表
   */
  @Override
  public List<SyncTask> listTasks() {
    return syncTaskMapper.selectList(
        new LambdaQueryWrapper<SyncTask>().orderByDesc(SyncTask::getId));
  }

  /**
   * 新建任务（注册定时调度）。
   *
   * @param request 任务请求（名称 + 源/目标 + 规则 + cron）
   * @return 创建后的任务
   */
  @Override
  @Transactional
  @Audit(action = "sync.create", resource = "#request.name()")
  public SyncTask createTask(SyncTaskRequest request) {
    validateCron(request.cron());
    SyncTask task = new SyncTask();
    task.setName(request.name());
    task.setSourceType("directory");
    task.setTaskType(defaultIfBlank(request.taskType(), "index_scan"));
    task.setSourceConfig(request.sourceConfig());
    task.setTargetConfig(request.targetConfig());
    task.setRuleType(defaultIfBlank(request.ruleType(), "date_month"));
    task.setRuleConfig(defaultIfBlank(request.ruleConfig(), "{}"));
    task.setAction(defaultIfBlank(request.action(), "move"));
    task.setConflict(defaultIfBlank(request.conflict(), "rename"));
    task.setCron(request.cron());
    task.setEnabled(1);
    task.setCreatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    validateTask(task);
    syncTaskMapper.insert(task);
    syncScheduler.registerTask(task);
    return task;
  }

  /**
   * 编辑任务（重新调度）。
   *
   * @param id 任务 ID
   * @param enabled 是否启用
   * @param request 任务请求（名称 + 源/目标 + 规则 + cron）
   * @return 更新后的任务
   */
  @Override
  @Transactional
  @Audit(action = "sync.update", resource = "#id")
  public SyncTask updateTask(Long id, boolean enabled, SyncTaskRequest request) {
    SyncTask task = requireTask(id);
    validateCron(request.cron());
    task.setName(request.name());
    task.setTaskType(defaultIfBlank(request.taskType(), task.getTaskType()));
    task.setSourceConfig(request.sourceConfig());
    task.setTargetConfig(defaultIfBlank(request.targetConfig(), task.getTargetConfig()));
    task.setRuleType(defaultIfBlank(request.ruleType(), task.getRuleType()));
    task.setRuleConfig(defaultIfBlank(request.ruleConfig(), task.getRuleConfig()));
    task.setAction(defaultIfBlank(request.action(), task.getAction()));
    task.setConflict(defaultIfBlank(request.conflict(), task.getConflict()));
    task.setCron(request.cron());
    task.setEnabled(enabled ? 1 : 0);
    validateTask(task);
    syncTaskMapper.updateById(task);
    syncScheduler.rescheduleTask(task);
    return task;
  }

  /**
   * 删除任务（取消调度）。
   *
   * @param id 任务 ID
   */
  @Override
  @Transactional
  @Audit(action = "sync.delete", resource = "#id")
  public void deleteTask(Long id) {
    SyncTask task = requireTask(id);
    syncScheduler.unregisterTask(task.getId());
    syncTaskMapper.deleteById(id);
    runningLocks.remove(id);
  }

  /**
   * 立即执行（同步 + 应用索引 + 写历史，并刷新任务最近执行信息）。
   *
   * @param taskId 任务 ID
   * @return 本次执行历史
   */
  @Override
  @Audit(action = "sync.run", resource = "#taskId")
  public SyncHistory runNow(Long taskId) {
    SyncTask task = requireTask(taskId);
    SyncHistory history = execute(task);
    task.setLastRunAt(history.getStartedAt());
    task.setLastStatus(history.getStatus());
    syncTaskMapper.updateById(task);
    return history;
  }

  /**
   * 执行历史（按任务筛选，倒序）。
   *
   * @param taskId 任务 ID（可空，空则查询全部任务）
   * @param limit 返回条数上限（不超过 200）
   * @return 执行历史列表
   */
  @Override
  public List<SyncHistory> listHistory(Long taskId, long limit) {
    return syncHistoryMapper.selectList(
        new LambdaQueryWrapper<SyncHistory>()
            .eq(taskId != null, SyncHistory::getTaskId, taskId)
            .orderByDesc(SyncHistory::getId)
            .last("LIMIT " + Math.min(limit, 200)));
  }

  /**
   * 按任务类型分发执行：index_scan → 目录扫描增量；organize → 自动整理引擎。 异常记录为 failed 历史（供 Quartz Job 与 runNow
   * 复用，任务级锁防并发）。
   *
   * <p>注意：本方法不做数据库事务 —— 文件 IO 无法回滚，且长事务会长时间占用 SQLite 连接； history 的 insert/update 各自原子提交即可。
   *
   * @param task 同步任务
   * @return 执行历史（成功/部分成功/失败均落库）
   */
  @Override
  @Audit(action = "sync.run", resource = "#task.id")
  public SyncHistory execute(SyncTask task) {
    AtomicBoolean lock = runningLocks.computeIfAbsent(task.getId(), k -> new AtomicBoolean(false));
    if (!lock.compareAndSet(false, true)) {
      throw new BizException("任务正在执行中，请稍候");
    }
    SyncHistory history = new SyncHistory();
    try {
      history.setTaskId(task.getId());
      history.setTaskType(task.getTaskType());
      history.setStatus("running");
      history.setStartedAt(System.currentTimeMillis());
      history.setProcessed(0);
      history.setSucceeded(0);
      history.setFailed(0);
      history.setSkipped(0);
      history.setAdded(0);
      history.setUpdated(0);
      history.setDeleted(0);
      syncHistoryMapper.insert(history);

      if ("organize".equals(task.getTaskType())) {
        fillOrganize(history, task);
      } else {
        fillIndexScan(history, task);
      }
      history.setStatus(history.getFailed() > 0 ? "partial" : "success");
      history.setFinishedAt(System.currentTimeMillis());
      syncHistoryMapper.updateById(history);
      return history;
    } catch (Exception e) {
      history.setStatus("failed");
      history.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
      history.setFinishedAt(System.currentTimeMillis());
      try {
        syncHistoryMapper.updateById(history);
      } catch (Exception ignored) {
        // 历史写入失败不影响返回，仅兜底
      }
      return history;
    } finally {
      lock.set(false);
    }
  }

  /** 索引扫描执行：扫描目录 → 与 file_index 对比 → 应用增量。 */
  private void fillIndexScan(SyncHistory history, SyncTask task) {
    ChangeSet changeSet = syncSource.scan(task.getSourceConfig());
    changeSet.added().forEach(idx -> applyAdded(idx));
    changeSet.updated().forEach(idx -> applyAdded(idx));
    changeSet.deleted().forEach(fileIndexService::remove);
    history.setAdded(changeSet.added().size());
    history.setUpdated(changeSet.updated().size());
    history.setDeleted(changeSet.deleted().size());
    history.setProcessed(changeSet.total());
    history.setSucceeded(changeSet.total());
  }

  /** 自动整理执行：规则引擎 → 真实文件移动/复制 → 索引同步。 */
  private void fillOrganize(SyncHistory history, SyncTask task) {
    OrganizeRule rule = resolveRule(task);
    OrganizeResult result = organizeExecutor.execute(task, rule);
    history.setProcessed(result.processed());
    history.setSucceeded(result.succeeded());
    history.setFailed(result.failed());
    history.setSkipped(result.skipped());
    if (!result.errors().isEmpty()) {
      history.setDetails(String.join("\n", result.errors()));
    }
  }

  private OrganizeRule resolveRule(SyncTask task) {
    String ruleType = task.getRuleType() == null ? "date_month" : task.getRuleType();
    if ("kind".equals(ruleType)) {
      return new KindOrganizeRule();
    }
    return new DateOrganizeRule(ruleType);
  }

  private void applyAdded(com.guicang.nas.module.file.FileIndex idx) {
    fileIndexService.upsert(
        idx.getPath(), idx.getName(), idx.getKind(), idx.getSize(), idx.getMtime(), idx.getOwner());
  }

  /** 任务合法性校验：organize 类型必须有目标目录；目标不能是源目录或其子目录（含 .. 规范化后）。 */
  private void validateTask(SyncTask task) {
    if ("organize".equals(task.getTaskType())) {
      if (task.getTargetConfig() == null || task.getTargetConfig().isBlank()) {
        throw new BizException("自动整理任务必须填写目标目录");
      }
      Path source = normalizePath(task.getSourceConfig());
      Path target = normalizePath(task.getTargetConfig());
      if (target.equals(source) || target.startsWith(source)) {
        throw new BizException("目标目录不能是源目录或其子目录（防循环整理）");
      }
    }
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

  private String defaultIfBlank(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private String normalize(String relative) {
    if (relative == null) {
      return "";
    }
    String trimmed = relative.trim().replace('\\', '/');
    while (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  /** 规范化相对路径为 Path（解析 .. 防绕过循环校验）。 */
  private Path normalizePath(String relative) {
    String base = normalize(relative);
    return base.isEmpty() ? Path.of("") : Path.of(base).normalize();
  }
}
