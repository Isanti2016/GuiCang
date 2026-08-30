package com.guicang.nas.module.auth.dto;

/** 登录会话信息（不含 token 哈希）。 */
public record SessionVO(
    Long id, String username, String ip, String userAgent, Long createdAt, Long lastActiveAt) {}
