package com.guicang.nas.infra.account;

/** PAM 密码校验接口：登录时校验系统账号密码（密码不落库、不落日志）。 */
public interface PAMVerifier {

  /** 校验用户名/密码，返回系统账号信息或失败原因。 */
  PAMVerifyResult verify(String username, String password);
}
