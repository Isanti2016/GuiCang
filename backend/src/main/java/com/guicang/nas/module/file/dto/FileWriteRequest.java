package com.guicang.nas.module.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 文本保存请求。 */
public record FileWriteRequest(
    @NotBlank(message = "路径不能为空") String path,
    @NotBlank(message = "内容不能为空") @Size(max = 2_000_000, message = "内容过大") String content) {}
