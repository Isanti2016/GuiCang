package com.guicang.nas.module.auth.dto;

import java.util.List;

/**
 * 当前用户信息。
 *
 * @param username 用户名
 * @param uid 系统 uid
 * @param home 家目录（登录响应中返回，/me 暂为空，Step 2.3 后由 sys_user 补全）
 * @param shell 登录 shell
 * @param roles 角色与权限（ROLE_xxx + 权限码）
 */
public record CurrentUserInfo(
    String username, long uid, String home, String shell, List<String> roles) {}
