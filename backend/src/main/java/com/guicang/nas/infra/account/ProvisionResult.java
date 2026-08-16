package com.guicang.nas.infra.account;

/**
 * 账号供给结果。
 *
 * @param status 结果状态
 * @param message 说明（未就绪时给出原因）
 * @param uid 系统 uid（创建成功时返回）
 */
public record ProvisionResult(ProvisionStatus status, String message, Long uid) {

  public static ProvisionResult created(long uid) {
    return new ProvisionResult(ProvisionStatus.CREATED, "ok", uid);
  }

  public static ProvisionResult pending(String message) {
    return new ProvisionResult(ProvisionStatus.PENDING, message, null);
  }

  public static ProvisionResult failed(String message) {
    return new ProvisionResult(ProvisionStatus.FAILED, message, null);
  }
}
