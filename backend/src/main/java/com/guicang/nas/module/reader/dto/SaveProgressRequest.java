package com.guicang.nas.module.reader.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 保存阅读进度请求。 */
public record SaveProgressRequest(
    @NotBlank String path,
    @NotNull @Min(0) Integer chapterIndex,
    @NotNull @Min(0) @Max(100) Integer percent) {}
