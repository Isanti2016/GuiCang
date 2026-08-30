package com.guicang.nas.module.auth;

import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.common.security.CurrentUserContext;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.infra.account.ProvisionResult;
import com.guicang.nas.infra.account.ProvisionStatus;
import com.guicang.nas.infra.account.SystemAccountProvisioner;
import com.guicang.nas.module.auth.dto.CurrentUserInfo;
import com.guicang.nas.module.auth.dto.LoginRequest;
import com.guicang.nas.module.auth.dto.LoginResponse;
import com.guicang.nas.module.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 认证服务实现：PAM 校验 → 查 sys_user（启用/角色）→ 签发 JWT（含权限）→ 审计。 */
@Service
public class AuthServiceImpl implements AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

  private final PAMVerifier pamVerifier;
  private final JwtService jwtService;
  private final UserService userService;
  private final SystemAccountProvisioner systemAccountProvisioner;
  private final SessionService sessionService;

  public AuthServiceImpl(
      PAMVerifier pamVerifier,
      JwtService jwtService,
      UserService userService,
      SystemAccountProvisioner systemAccountProvisioner,
      SessionService sessionService) {
    this.pamVerifier = pamVerifier;
    this.jwtService = jwtService;
    this.userService = userService;
    this.systemAccountProvisioner = systemAccountProvisioner;
    this.sessionService = sessionService;
  }

  /**
   * PAM 校验并签发 JWT；失败抛业务异常（code=401）。
   *
   * @param request 登录请求（用户名/密码）
   * @return 登录响应（含 JWT 与当前用户信息）
   */
  @Override
  @Audit(action = "login", resource = "#request.username()")
  public LoginResponse login(LoginRequest request) {
    PAMVerifyResult result = pamVerifier.verify(request.username(), request.password());
    if (!result.ok()) {
      throw new BizException(ResultCodes.UNAUTHORIZED, "用户名或密码错误");
    }
    var sysUser = userService.findByUsername(request.username());
    if (sysUser.isEmpty()
        || sysUser.get().getEnabled() == null
        || sysUser.get().getEnabled() == 0) {
      throw new BizException(ResultCodes.UNAUTHORIZED, "账号未开通或已停用");
    }
    List<String> authorities = userService.loadAuthorities(request.username());
    String token = jwtService.issue(request.username(), result.uid(), authorities);
    recordSession(token, request.username());
    return new LoginResponse(
        token,
        new CurrentUserInfo(
            request.username(), result.uid(), result.home(), result.shell(), authorities));
  }

  private void recordSession(String token, String username) {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs == null) {
        return;
      }
      HttpServletRequest req = attrs.getRequest();
      String ip = req.getHeader("X-Forwarded-For");
      if (ip == null || ip.isBlank()) {
        ip = req.getRemoteAddr();
      }
      sessionService.record(token, username, ip, req.getHeader("User-Agent"));
    } catch (Exception e) {
      log.debug("记录登录会话失败: {}", e.getMessage());
    }
  }

  /**
   * 当前登录用户信息（从 SecurityContext 读取）。
   *
   * @return 当前用户信息（用户名/UID/角色权限）
   */
  @Override
  public CurrentUserInfo me() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
      throw new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期");
    }
    List<String> roles =
        authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
    return new CurrentUserInfo(user.username(), user.uid(), null, null, roles);
  }

  /** 登出（客户端丢弃令牌；服务端黑名单待 Redis 接入后实现）。 */
  @Override
  public void logout() {
    // 客户端丢弃令牌即可；服务端黑名单待 Redis 接入后实现（Step 6/8）
  }

  /**
   * 修改本人密码（校验旧密码，同步 Linux + Samba）。
   *
   * @param oldPassword 旧密码
   * @param newPassword 新密码
   */
  @Override
  @Audit(action = "auth.password")
  public void changePassword(String oldPassword, String newPassword) {
    String username =
        CurrentUserContext.currentUser()
            .orElseThrow(() -> new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期"))
            .username();
    // 校验旧密码（PAM）
    PAMVerifyResult verify = pamVerifier.verify(username, oldPassword);
    if (!verify.ok()) {
      throw new BizException(ResultCodes.UNAUTHORIZED, "旧密码错误");
    }
    // 同步 Linux + Samba（helper）
    ProvisionResult provision = systemAccountProvisioner.setUserPassword(username, newPassword);
    if (provision.status() != ProvisionStatus.CREATED) {
      throw new BizException("密码修改失败（guicang-helper 未部署）");
    }
  }
}
