package com.guicang.nas.common;

/** 业务异常，携带统一错误码，由全局异常处理器转为 {@link Result}。 */
public class BizException extends RuntimeException {

  private final int code;

  public BizException(int code, String message) {
    super(message);
    this.code = code;
  }

  public BizException(String message) {
    this(ResultCodes.BIZ_ERROR, message);
  }

  public int getCode() {
    return code;
  }
}
