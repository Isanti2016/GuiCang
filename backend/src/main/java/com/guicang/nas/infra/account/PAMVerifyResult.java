package com.guicang.nas.infra.account;

/**
 * PAM 密码校验接口。生产实现经宿主 guicang-helper（sudo 白名单）；开发可切 local 直连 PAM 脚本。
 *
 * @param ok 是否认证通过
 * @param uid 系统 uid
 * @param gid 系统 gid
 * @param home 家目录
 * @param shell 登录 shell
 * @param error 失败原因（仅失败时非空）
 */
public record PAMVerifyResult(
    boolean ok, long uid, long gid, String home, String shell, String error) {

  public static PAMVerifyResult success(long uid, long gid, String home, String shell) {
    return new PAMVerifyResult(true, uid, gid, home, shell, null);
  }

  public static PAMVerifyResult failure(String error) {
    return new PAMVerifyResult(false, 0, 0, null, null, error);
  }
}
