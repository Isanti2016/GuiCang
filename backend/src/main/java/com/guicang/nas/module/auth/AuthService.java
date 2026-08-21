package com.guicang.nas.module.auth;

import com.guicang.nas.module.auth.dto.CurrentUserInfo;
import com.guicang.nas.module.auth.dto.LoginRequest;
import com.guicang.nas.module.auth.dto.LoginResponse;

/** 认证服务：登录、当前用户、登出。 */
public interface AuthService {

  /** PAM 校验并签发 JWT；失败抛业务异常（code=401）。 */
  LoginResponse login(LoginRequest request);

  /** 当前登录用户信息（从 SecurityContext 读取）。 */
  CurrentUserInfo me();

  /** 登出（客户端丢弃令牌；服务端黑名单待 Redis 接入后实现）。 */
  void logout();

  /** 修改本人密码（校验旧密码，同步 Linux + Samba）。 */
  void changePassword(String oldPassword, String newPassword);
}
