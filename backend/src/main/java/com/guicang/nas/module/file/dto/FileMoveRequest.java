package com.guicang.nas.module.file.dto;

import jakarta.validation.constraints.NotBlank;

/** 移动请求。 */
public record FileMoveRequest(
    @NotBlank(message = "路径不能为空") String path, @NotBlank(message = "目标目录不能为空") String target) {}
