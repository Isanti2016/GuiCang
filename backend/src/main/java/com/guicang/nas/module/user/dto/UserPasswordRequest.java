package com.guicang.nas.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 重置用户密码请求（admin 操作，经 helper 同步 Linux + Samba）。 */
public record UserPasswordRequest(
    @NotBlank(message = "密码不能为空") @Size(min = 8, max = 128, message = "密码长度须为 8-128 位")
        String password) {}
