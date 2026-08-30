package com.guicang.nas.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * @param username 系统用户名
 * @param password 密码（仅本次请求内存中使用，不落库不落日志）
 */
public record LoginRequest(
    @NotBlank(message = "用户名不能为空") @Size(max = 32, message = "用户名过长") String username,
    @NotBlank(message = "密码不能为空") @Size(max = 128, message = "密码过长") String password,
    String totp) {

  /** 兼容两参数构造（未开启两步验证）。 */
  public LoginRequest(String username, String password) {
    this(username, password, null);
  }
}
