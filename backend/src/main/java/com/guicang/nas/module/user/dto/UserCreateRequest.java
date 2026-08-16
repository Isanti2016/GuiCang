package com.guicang.nas.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建用户请求。
 *
 * @param username 登录名（同时创建系统账号）
 * @param displayName 显示名
 * @param email 邮箱（可选）
 * @param password 初始密码（仅本次请求内存中使用，经 helper stdin 设置，不落库）
 * @param roleId 主角色
 * @param quotaBytes 可选配额
 */
public record UserCreateRequest(
    @NotBlank(message = "用户名不能为空")
        @Pattern(
            regexp = "^[a-z_][a-z0-9_-]{0,31}$",
            message = "用户名须为小写字母/数字/下划线/连字符，且以字母或下划线开头，最长 32 位")
        String username,
    @NotBlank(message = "显示名不能为空") @Size(max = 50, message = "显示名最长 50 字符") String displayName,
    @Email(message = "邮箱格式不正确") @Size(max = 100, message = "邮箱过长") String email,
    @NotBlank(message = "密码不能为空") @Size(min = 8, max = 128, message = "密码长度须为 8-128 位")
        String password,
    @NotNull(message = "角色不能为空") Long roleId,
    Long quotaBytes) {}
