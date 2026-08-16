package com.guicang.nas.infra.account;

/**
 * 特权账号供给接口：创建/管理 Linux 系统账号（配合 sudo 白名单 helper，后端不跑 root）。
 *
 * <p>Step 1.4 仅接入延迟实现；Step 2.1 编写 guicang-helper 后替换为 helper 实现。
 */
public interface SystemAccountProvisioner {

  /** 为首次初始化创建管理员系统账号。 */
  ProvisionResult provisionAdmin(String username);
}
