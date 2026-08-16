package com.guicang.nas.common;

/** 统一错误码。 */
public final class ResultCodes {

  /** 成功。 */
  public static final int SUCCESS = 0;

  /** 请求参数错误。 */
  public static final int BAD_REQUEST = 400;

  /** 未认证。 */
  public static final int UNAUTHORIZED = 401;

  /** 无权限。 */
  public static final int FORBIDDEN = 403;

  /** 资源不存在。 */
  public static final int NOT_FOUND = 404;

  /** 请求方法不支持。 */
  public static final int METHOD_NOT_ALLOWED = 405;

  /** 服务器内部错误。 */
  public static final int INTERNAL_ERROR = 500;

  /** 业务错误起始码（1000 起）。 */
  public static final int BIZ_ERROR = 1000;

  private ResultCodes() {}
}
