package com.guicang.nas.module.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.common.security.CurrentUserContext;
import com.guicang.nas.module.auth.dto.SessionVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.stereotype.Service;

/** 登录会话服务实现。 */
@Service
public class SessionServiceImpl implements SessionService {

  private final LoginSessionMapper loginSessionMapper;

  public SessionServiceImpl(LoginSessionMapper loginSessionMapper) {
    this.loginSessionMapper = loginSessionMapper;
  }

  @Override
  public void record(String token, String username, String ip, String userAgent) {
    String tokenHash = hash(token);
    LoginSession existing = loginSessionMapper.selectOne(
        new LambdaQueryWrapper<LoginSession>().eq(LoginSession::getTokenHash, tokenHash));
    if (existing != null) {
      existing.setLastActiveAt(System.currentTimeMillis());
      existing.setRevoked(0);
      loginSessionMapper.updateById(existing);
      return;
    }
    LoginSession s = new LoginSession();
    s.setTokenHash(tokenHash);
    s.setUsername(username);
    s.setIp(ip);
    s.setUserAgent(userAgent);
    s.setCreatedAt(System.currentTimeMillis());
    s.setLastActiveAt(System.currentTimeMillis());
    s.setRevoked(0);
    loginSessionMapper.insert(s);
    trimSessions(username);
  }

  @Override
  public boolean isRevoked(String token) {
    LoginSession s = loginSessionMapper.selectOne(
        new LambdaQueryWrapper<LoginSession>().eq(LoginSession::getTokenHash, hash(token)));
    return s != null && s.getRevoked() != null && s.getRevoked() == 1;
  }

  @Override
  public List<SessionVO> listForCurrentUser() {
    String username = requireUser().username();
    return loginSessionMapper.selectList(
        new LambdaQueryWrapper<LoginSession>()
            .eq(LoginSession::getUsername, username)
            .eq(LoginSession::getRevoked, 0)
            .orderByDesc(LoginSession::getLastActiveAt))
        .stream()
        .map(s -> new SessionVO(s.getId(), s.getUsername(), s.getIp(), s.getUserAgent(),
            s.getCreatedAt(), s.getLastActiveAt()))
        .toList();
  }

  @Override
  public void revoke(Long id) {
    AuthenticatedUser user = requireUser();
    LoginSession s = loginSessionMapper.selectById(id);
    if (s == null) {
      return;
    }
    if (!user.username().equals(s.getUsername())) {
      throw new BizException("无权限操作他人会话");
    }
    s.setRevoked(1);
    loginSessionMapper.updateById(s);
  }

  @Override
  public void revokeByToken(String token) {
    LoginSession s = loginSessionMapper.selectOne(
        new LambdaQueryWrapper<LoginSession>().eq(LoginSession::getTokenHash, hash(token)));
    if (s != null) {
      s.setRevoked(1);
      loginSessionMapper.updateById(s);
    }
  }

  private void trimSessions(String username) {
    List<LoginSession> list = loginSessionMapper.selectList(
        new LambdaQueryWrapper<LoginSession>()
            .eq(LoginSession::getUsername, username)
            .orderByDesc(LoginSession::getCreatedAt));
    for (int i = 20; i < list.size(); i++) {
      loginSessionMapper.deleteById(list.get(i).getId());
    }
  }

  private String hash(String token) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64);
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new BizException("令牌处理失败");
    }
  }

  private AuthenticatedUser requireUser() {
    return CurrentUserContext.currentUser()
        .orElseThrow(() -> new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期"));
  }
}
