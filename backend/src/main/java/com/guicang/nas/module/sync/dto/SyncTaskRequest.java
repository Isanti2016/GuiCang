package com.guicang.nas.module.sync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 同步任务创建/更新请求。
 *
 * @param name 任务名称
 * @param sourceConfig 源目录相对路径
 * @param cron cron 表达式（Quartz 6 段）
 * @param taskType 任务类型：index_scan（索引扫描，默认）/ organize（自动整理）
 * @param targetConfig 目标目录相对路径（organize 必填）
 * @param ruleType 整理规则：date_year / date_month / date_day / kind
 * @param ruleConfig 规则附加配置（JSON，可选）
 * @param action 操作方式：move（默认）/ copy
 * @param conflict 冲突处理：skip / overwrite / rename（默认）
 */
public record SyncTaskRequest(
    @NotBlank(message = "任务名不能为空") @Size(max = 50, message = "任务名最长 50 字符") String name,
    @NotBlank(message = "源目录不能为空") String sourceConfig,
    @NotBlank(message = "cron 不能为空") String cron,
    @Pattern(regexp = "index_scan|organize", message = "任务类型仅支持 index_scan / organize")
        String taskType,
    String targetConfig,
    @Pattern(regexp = "date_year|date_month|date_day|kind", message = "整理规则不合法") String ruleType,
    String ruleConfig,
    @Pattern(regexp = "move|copy", message = "操作方式仅支持 move / copy") String action,
    @Pattern(regexp = "skip|overwrite|rename", message = "冲突策略仅支持 skip / overwrite / rename")
        String conflict) {}
