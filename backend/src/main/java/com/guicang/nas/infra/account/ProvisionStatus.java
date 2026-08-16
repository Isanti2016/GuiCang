package com.guicang.nas.infra.account;

/** 账号供给结果状态。 */
public enum ProvisionStatus {
  /** 系统账号已创建。 */
  CREATED,
  /** 特权服务未就绪，账号待后续创建。 */
  PENDING
}
