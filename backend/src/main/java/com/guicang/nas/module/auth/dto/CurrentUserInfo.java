package com.guicang.nas.module.auth.dto;

/**
 * 当前用户信息。
 *
 * @param username 用户名
 * @param uid 系统 uid
 * @param home 家目录（登录响应中返回，/me 暂为空，Step 2.3 接 sys_user 后补全）
 * @param shell 登录 shell
 */
public record CurrentUserInfo(String username, long uid, String home, String shell) {}
