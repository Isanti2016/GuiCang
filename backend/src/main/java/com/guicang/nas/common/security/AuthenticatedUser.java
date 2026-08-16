package com.guicang.nas.common.security;

/** 已认证用户身份（置于 SecurityContext principal）。 */
public record AuthenticatedUser(String username, long uid) {}
