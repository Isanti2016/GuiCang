package com.guicang.nas.module.user.dto;

import jakarta.validation.constraints.NotNull;

/** 启用/禁用用户请求。 */
public record UserStatusRequest(@NotNull(message = "启用状态不能为空") Boolean enabled) {}
