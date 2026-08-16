package com.guicang.nas.module.auth;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.auth.dto.CurrentUserInfo;
import com.guicang.nas.module.auth.dto.LoginRequest;
import com.guicang.nas.module.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口：登录 / 当前用户 / 登出。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /** 登录：PAM 校验通过后返回 JWT 与用户信息。 */
  @PostMapping("/login")
  public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return Result.ok(authService.login(request));
  }

  /** 当前登录用户。 */
  @GetMapping("/me")
  public Result<CurrentUserInfo> me() {
    return Result.ok(authService.me());
  }

  /** 登出（客户端丢弃令牌）。 */
  @PostMapping("/logout")
  public Result<Void> logout() {
    authService.logout();
    return Result.ok();
  }
}
