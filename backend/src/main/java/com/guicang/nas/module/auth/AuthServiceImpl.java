package com.guicang.nas.module.auth;

import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.module.auth.dto.CurrentUserInfo;
import com.guicang.nas.module.auth.dto.LoginRequest;
import com.guicang.nas.module.auth.dto.LoginResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 认证服务实现：PAM 校验（经 PAMVerifier 抽象）→ 签发 JWT → 审计。 */
@Service
public class AuthServiceImpl implements AuthService {

  private final PAMVerifier pamVerifier;
  private final JwtService jwtService;

  public AuthServiceImpl(PAMVerifier pamVerifier, JwtService jwtService) {
    this.pamVerifier = pamVerifier;
    this.jwtService = jwtService;
  }

  @Override
  @Audit(action = "login", resource = "#request.username()")
  public LoginResponse login(LoginRequest request) {
    PAMVerifyResult result = pamVerifier.verify(request.username(), request.password());
    if (!result.ok()) {
      throw new BizException(ResultCodes.UNAUTHORIZED, "用户名或密码错误");
    }
    String token = jwtService.issue(request.username(), result.uid());
    return new LoginResponse(
        token,
        new CurrentUserInfo(request.username(), result.uid(), result.home(), result.shell()));
  }

  @Override
  public CurrentUserInfo me() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
      throw new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期");
    }
    return new CurrentUserInfo(user.username(), user.uid(), null, null);
  }

  @Override
  public void logout() {
    // 客户端丢弃令牌即可；服务端黑名单待 Redis 接入后实现（Step 6/8）
  }
}
