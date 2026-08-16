package com.guicang.nas.module.setup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 初始化请求。
 *
 * @param username 管理员登录名（同时是待创建的系统账号名）
 * @param displayName 显示名
 */
public record SetupInitRequest(
    @NotBlank(message = "管理员用户名不能为空")
        @Pattern(
            regexp = "^[a-z_][a-z0-9_-]{0,31}$",
            message = "用户名须为小写字母/数字/下划线/连字符，且以字母或下划线开头，最长 32 位")
        String username,
    @NotBlank(message = "显示名不能为空") @Size(max = 50, message = "显示名最长 50 字符") String displayName) {}
