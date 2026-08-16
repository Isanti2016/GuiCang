package com.guicang.nas.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 编辑用户请求（昵称/邮箱/角色/配额）。 */
public record UserUpdateRequest(
    @NotBlank(message = "显示名不能为空") @Size(max = 50, message = "显示名最长 50 字符") String displayName,
    @Email(message = "邮箱格式不正确") @Size(max = 100, message = "邮箱过长") String email,
    @NotNull(message = "角色不能为空") Long roleId,
    Long quotaBytes) {}
