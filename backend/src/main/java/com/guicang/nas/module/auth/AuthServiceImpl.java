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
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 认证服务实现：PAM 校验 → 查 sys_user（启用/角色）→ 签发 JWT（含权限）→ 审计。 */
@Service
public class AuthServiceImpl implements AuthService {

  private final PAMVerifier pamVerifier;
  private final JwtService jwtService;
  private final UserService userService;
  private final SystemAccountProvisioner systemAccountProvisioner;

  public AuthServiceImpl(
      PAMVerifier pamVerifier,
      JwtService jwtService,
      UserService userService,
      SystemAccountProvisioner systemAccountProvisioner) {
    this.pamVerifier = pamVerifier;
    this.jwtService = jwtService;
    this.userService = userService;
    this.systemAccountProvisioner = systemAccountProvisioner;
  }

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
    return new LoginResponse(
        token,
        new CurrentUserInfo(
            request.username(), result.uid(), result.home(), result.shell(), authorities));
  }

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

  @Override
  public void logout() {
    // 客户端丢弃令牌即可；服务端黑名单待 Redis 接入后实现（Step 6/8）
  }

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
