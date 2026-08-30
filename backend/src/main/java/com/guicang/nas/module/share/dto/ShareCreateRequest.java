package com.guicang.nas.module.share.dto;

import jakarta.validation.constraints.NotBlank;

/** 创建分享请求。 */
public record ShareCreateRequest(
    @NotBlank String path, String password, Integer expireDays) {}
