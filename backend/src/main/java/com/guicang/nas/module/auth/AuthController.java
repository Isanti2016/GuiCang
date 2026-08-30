package com.guicang.nas.module.auth;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.auth.dto.ChangePasswordRequest;
import com.guicang.nas.module.auth.dto.CurrentUserInfo;
import com.guicang.nas.module.auth.dto.LoginRequest;
import com.guicang.nas.module.auth.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口：登录 / 当前用户 / 登出。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final SessionService sessionService;

  public AuthController(AuthService authService, SessionService sessionService) {
    this.authService = authService;
    this.sessionService = sessionService;
  }

  /**
   * 登录：PAM 校验通过后返回 JWT 与用户信息。
   *
   * @param request 登录请求体（用户名 + 密码）
   * @return JWT 与当前用户信息
   */
  @PostMapping("/login")
  public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return Result.ok(authService.login(request));
  }

  /**
   * 当前登录用户。
   *
   * @return 当前登录用户信息
   */
  @GetMapping("/me")
  public Result<CurrentUserInfo> me() {
    return Result.ok(authService.me());
  }

  /**
   * 登出（客户端丢弃令牌）。
   *
   * @return 空结果
   */
  @PostMapping("/logout")
  public Result<Void> logout(HttpServletRequest request) {
    String token = resolveToken(request);
    if (token != null) {
      sessionService.revokeByToken(token);
    }
    authService.logout();
    return Result.ok();
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring(7);
    }
    return request.getParameter("token");
  }

  /**
   * 修改本人密码（校验旧密码，同步系统账号）。
   *
   * @param request 改密请求体（旧密码 + 新密码）
   * @return 空结果
   */
  @PutMapping("/password")
  public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(request.oldPassword(), request.newPassword());
    return Result.ok();
  }
}
