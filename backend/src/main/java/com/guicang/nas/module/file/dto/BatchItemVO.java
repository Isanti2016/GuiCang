package com.guicang.nas.module.file.dto;

/** 批量转码任务项状态。 */
public record BatchItemVO(
    String path, String status, int progress, String message, String outputPath) {}
