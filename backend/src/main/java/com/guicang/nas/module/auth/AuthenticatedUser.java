package com.guicang.nas.module.auth;

/** 已认证用户身份（置于 SecurityContext principal）。 */
public record AuthenticatedUser(String username, long uid) {}
