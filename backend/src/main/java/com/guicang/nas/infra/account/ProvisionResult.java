package com.guicang.nas.infra.account;

/**
 * 账号供给结果。
 *
 * @param status 结果状态
 * @param message 说明（未就绪时给出原因）
 */
public record ProvisionResult(ProvisionStatus status, String message) {}
