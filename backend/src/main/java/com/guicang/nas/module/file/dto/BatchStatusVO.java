package com.guicang.nas.module.file.dto;

import java.util.List;

/**
 * 批量转码任务整体状态。
 *
 * @param batchId 批次 ID（空字符串表示无任务）
 * @param state RUNNING / DONE / GONE（批次不存在）
 * @param total 总项数
 * @param running 转码中项数
 * @param done 已完成项数
 * @param failed 失败项数
 * @param items 各项状态
 */
public record BatchStatusVO(
    String batchId,
    String state,
    int total,
    int running,
    int done,
    int failed,
    List<BatchItemVO> items) {}
