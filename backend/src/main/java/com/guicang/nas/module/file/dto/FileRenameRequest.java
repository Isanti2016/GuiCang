package com.guicang.nas.module.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重命名请求。 */
public record FileRenameRequest(
    @NotBlank(message = "路径不能为空") String path,
    @NotBlank(message = "新名称不能为空") @Size(max = 255, message = "名称过长") String newName) {}
