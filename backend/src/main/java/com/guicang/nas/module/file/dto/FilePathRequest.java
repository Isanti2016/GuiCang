package com.guicang.nas.module.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 目录操作请求（路径为存储根下相对路径；路径安全性由 PathUtils 统一校验）。 */
public record FilePathRequest(
    @NotBlank(message = "路径不能为空") @Size(max = 1000, message = "路径过长") String path) {}
