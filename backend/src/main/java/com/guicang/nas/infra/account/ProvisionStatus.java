package com.guicang.nas.infra.account;

/** 账号供给结果状态。 */
public enum ProvisionStatus {
  /** 系统账号已创建/操作完成。 */
  CREATED,
  /** 特权服务未就绪，操作待后续执行。 */
  PENDING,
  /** 操作失败。 */
  FAILED
}
