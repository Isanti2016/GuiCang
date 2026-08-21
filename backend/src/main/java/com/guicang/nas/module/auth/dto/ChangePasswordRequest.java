package com.guicang.nas.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改本人密码请求。
 *
 * @param oldPassword 旧密码（校验用）
 * @param newPassword 新密码（同步 Linux + Samba）
 */
public record ChangePasswordRequest(
    @NotBlank(message = "旧密码不能为空") String oldPassword,
    @NotBlank(message = "新密码不能为空") @Size(min = 8, max = 128, message = "新密码长度须为 8-128 位")
        String newPassword) {}
