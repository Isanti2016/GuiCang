package com.guicang.nas.module.auth;

import com.guicang.nas.module.auth.dto.SessionVO;
import java.util.List;

/** 登录会话服务：记录登录、黑名单检查、踢下线。 */
public interface SessionService {

  /** 登录成功后记录会话。 */
  void record(String token, String username, String ip, String userAgent);

  /** token 是否已被踢下线（黑名单）。 */
  boolean isRevoked(String token);

  /** 当前用户的会话列表（有效会话）。 */
  List<SessionVO> listForCurrentUser();

  /** 踢下线指定会话。 */
  void revoke(Long id);

  /** 将指定 token 标记为已踢下线（登出用）。 */
  void revokeByToken(String token);
}
