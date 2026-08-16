package com.guicang.nas.common;

/**
 * 统一返回体。
 *
 * @param code 业务码，0 成功，非 0 见 {@link ResultCodes}
 * @param message 提示信息
 * @param data 数据负载
 */
public record Result<T>(int code, String message, T data) {

  public static <T> Result<T> ok(T data) {
    return new Result<>(ResultCodes.SUCCESS, "ok", data);
  }

  public static Result<Void> ok() {
    return ok(null);
  }

  public static <T> Result<T> fail(int code, String message) {
    return new Result<>(code, message, null);
  }
}
