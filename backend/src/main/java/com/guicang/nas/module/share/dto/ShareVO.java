package com.guicang.nas.module.share.dto;

/** 分享链接信息（返回给前端）。 */
public record ShareVO(String token, String path, boolean hasPassword, Long expiresAt) {}
