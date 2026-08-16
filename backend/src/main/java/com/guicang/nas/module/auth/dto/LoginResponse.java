package com.guicang.nas.module.auth.dto;

/**
 * 登录响应。
 *
 * @param token JWT 访问令牌
 * @param user 当前用户信息
 */
public record LoginResponse(String token, CurrentUserInfo user) {}
