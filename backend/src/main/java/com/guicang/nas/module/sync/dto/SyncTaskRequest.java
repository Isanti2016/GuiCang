package com.guicang.nas.module.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 同步任务创建/更新请求。 */
public record SyncTaskRequest(
    @NotBlank(message = "任务名不能为空") @Size(max = 50, message = "任务名最长 50 字符") String name,
    @NotBlank(message = "源目录不能为空") String sourceConfig,
    @NotBlank(message = "cron 不能为空") String cron) {}
