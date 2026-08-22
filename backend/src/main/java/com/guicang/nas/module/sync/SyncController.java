package com.guicang.nas.module.sync;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.sync.dto.SyncTaskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 同步任务接口（需 sync.manage 权限，默认仅管理员）。 */
@RestController
@RequestMapping("/api/v1/sync")
@PreAuthorize("hasAuthority('sync.manage')")
@Validated
public class SyncController {

  private final SyncService syncService;

  public SyncController(SyncService syncService) {
    this.syncService = syncService;
  }

  /**
   * 任务列表。
   *
   * @return 同步任务列表
   */
  @GetMapping("/tasks")
  public Result<List<SyncTask>> listTasks() {
    return Result.ok(syncService.listTasks());
  }

  /**
   * 新建任务。
   *
   * @param request 新建任务请求体（名称 + 源配置 + cron 表达式）
   * @return 新建的同步任务
   */
  @PostMapping("/tasks")
  public Result<SyncTask> create(@Valid @RequestBody SyncTaskRequest request) {
    return Result.ok(
        syncService.createTask(request.name(), request.sourceConfig(), request.cron()));
  }

  /**
   * 编辑任务。
   *
   * @param id 任务 ID
   * @param enabled 是否启用
   * @param request 任务编辑请求体（名称 + 源配置 + cron 表达式）
   * @return 更新后的同步任务
   */
  @PutMapping("/tasks/{id}")
  public Result<SyncTask> update(
      @PathVariable @NotNull Long id,
      @RequestParam(defaultValue = "true") boolean enabled,
      @Valid @RequestBody SyncTaskRequest request) {
    return Result.ok(
        syncService.updateTask(
            id, request.name(), request.sourceConfig(), request.cron(), enabled));
  }

  /**
   * 删除任务。
   *
   * @param id 任务 ID
   * @return 空结果
   */
  @DeleteMapping("/tasks/{id}")
  public Result<Void> delete(@PathVariable @NotNull Long id) {
    syncService.deleteTask(id);
    return Result.ok();
  }

  /**
   * 立即执行。
   *
   * @param id 任务 ID
   * @return 本次执行历史记录
   */
  @PostMapping("/tasks/{id}/run")
  public Result<SyncHistory> run(@PathVariable @NotNull Long id) {
    return Result.ok(syncService.runNow(id));
  }

  /**
   * 执行历史。
   *
   * @param taskId 任务 ID（可选，空为全部任务）
   * @param limit 返回条数上限
   * @return 执行历史列表
   */
  @GetMapping("/history")
  public Result<List<SyncHistory>> history(
      @RequestParam(required = false) Long taskId, @RequestParam(defaultValue = "50") long limit) {
    return Result.ok(syncService.listHistory(taskId, limit));
  }
}
