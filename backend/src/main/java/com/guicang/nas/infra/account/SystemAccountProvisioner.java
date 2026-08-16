package com.guicang.nas.infra.account;

/**
 * 特权账号供给接口：创建/管理 Linux 系统账号（配合 sudo 白名单 guicang-helper，后端不跑 root）。
 *
 * <p>密码仅经 stdin 传给 helper；本接口的入参密码只在方法调用期存在，不落库不落日志。
 */
public interface SystemAccountProvisioner {

  /** 为首次初始化创建管理员系统账号。 */
  ProvisionResult provisionAdmin(String username);

  /** 创建系统账号（useradd + 设密码 + Samba），返回 uid。 */
  ProvisionResult createUser(String username, String password);

  /** 删除系统账号（默认保留个人目录）。 */
  ProvisionResult deleteUser(String username, boolean removeHome);

  /** 启用/禁用系统账号（锁定/解锁 + Samba 禁用）。 */
  ProvisionResult setUserStatus(String username, boolean enabled);

  /** 重置系统账号密码（Linux + Samba 同步）。 */
  ProvisionResult setUserPassword(String username, String password);
}
