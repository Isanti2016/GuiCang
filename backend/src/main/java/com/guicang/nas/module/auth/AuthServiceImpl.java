package com.guicang.nas.module.auth;

import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
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

  public AuthServiceImpl(PAMVerifier pamVerifier, JwtService jwtService, UserService userService) {
    this.pamVerifier = pamVerifier;
    this.jwtService = jwtService;
    this.userService = userService;
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
}
