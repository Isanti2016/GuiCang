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
import com.guicang.nas.module.auth.dto.TotpEnableResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.TotpUtil;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
import com.guicang.nas.module.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
  private final SysUserMapper sysUserMapper;
  private final RecoveryCodeMapper recoveryCodeMapper;

  public AuthServiceImpl(
      PAMVerifier pamVerifier,
      JwtService jwtService,
      UserService userService,
      SystemAccountProvisioner systemAccountProvisioner,
      SessionService sessionService,
      SysUserMapper sysUserMapper,
      RecoveryCodeMapper recoveryCodeMapper) {
    this.pamVerifier = pamVerifier;
    this.jwtService = jwtService;
    this.userService = userService;
    this.systemAccountProvisioner = systemAccountProvisioner;
    this.sessionService = sessionService;
    this.sysUserMapper = sysUserMapper;
    this.recoveryCodeMapper = recoveryCodeMapper;
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
    String totpSecret = sysUser.get().getTotpSecret();
    if (totpSecret != null && !totpSecret.isBlank()) {
      boolean totpOk = TotpUtil.verify(totpSecret, request.totp());
      boolean recoveryOk = consumeRecoveryCode(sysUser.get().getId(), request.totp());
      if (!totpOk && !recoveryOk) {
        throw new BizException(ResultCodes.UNAUTHORIZED, "两步验证码错误");
      }
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

  @Override
  @Audit(action = "totp.enable")
  public TotpEnableResult enableTotp() {
    AuthenticatedUser user = requireUser();
    String secret = TotpUtil.generateSecret();
    SysUser sysUser = requireSysUser(user.username());
    sysUser.setTotpSecret(secret);
    sysUserMapper.updateById(sysUser);
    // 重新开启时清掉旧恢复码，重新生成 10 个
    recoveryCodeMapper.delete(
        new LambdaQueryWrapper<RecoveryCode>().eq(RecoveryCode::getUserId, sysUser.getId()));
    List<String> codes = new ArrayList<>(10);
    for (int i = 0; i < 10; i++) {
      String code = generateRecoveryCode();
      codes.add(code);
      RecoveryCode rc = new RecoveryCode();
      rc.setUserId(sysUser.getId());
      rc.setCodeHash(sha256Hex(code));
      rc.setUsed(0);
      rc.setCreatedAt(System.currentTimeMillis());
      recoveryCodeMapper.insert(rc);
    }
    return new TotpEnableResult(secret, codes);
  }

  @Override
  @Audit(action = "totp.disable")
  public void disableTotp() {
    AuthenticatedUser user = requireUser();
    SysUser sysUser = requireSysUser(user.username());
    sysUser.setTotpSecret(null);
    sysUserMapper.updateById(sysUser);
    recoveryCodeMapper.delete(
        new LambdaQueryWrapper<RecoveryCode>().eq(RecoveryCode::getUserId, sysUser.getId()));
  }

  /** 校验并消费一次性恢复码；命中则标记已用返回 true。 */
  private boolean consumeRecoveryCode(Long userId, String code) {
    if (code == null || code.isBlank()) {
      return false;
    }
    RecoveryCode rc = recoveryCodeMapper.selectOne(
        new LambdaQueryWrapper<RecoveryCode>()
            .eq(RecoveryCode::getUserId, userId)
            .eq(RecoveryCode::getCodeHash, sha256Hex(code.trim()))
            .eq(RecoveryCode::getUsed, 0)
            .last("LIMIT 1"));
    if (rc == null) {
      return false;
    }
    rc.setUsed(1);
    rc.setUsedAt(System.currentTimeMillis());
    recoveryCodeMapper.updateById(rc);
    return true;
  }

  /** 生成 6 位数字恢复码（与 TOTP 动态码同格式，登录框无需改动）。 */
  private String generateRecoveryCode() {
    return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 不可用", e);
    }
  }

  @Override
  public boolean isTotpEnabled() {
    AuthenticatedUser user = requireUser();
    SysUser sysUser = sysUserMapper.selectOne(
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, user.username()));
    return sysUser != null && sysUser.getTotpSecret() != null && !sysUser.getTotpSecret().isBlank();
  }

  private SysUser requireSysUser(String username) {
    SysUser sysUser = sysUserMapper.selectOne(
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    if (sysUser == null) {
      throw new BizException("用户不存在");
    }
    return sysUser;
  }

  private AuthenticatedUser requireUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
      throw new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期");
    }
    return user;
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
