package com.guicang.nas.common.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前登录用户读取工具（从 SecurityContext 提取）。 */
public final class CurrentUserContext {

  private CurrentUserContext() {}

  /** 当前已认证用户；未登录返回空。 */
  public static Optional<AuthenticatedUser> currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
      return Optional.of(user);
    }
    return Optional.empty();
  }

  /** 当前用户名；未登录返回 null。 */
  public static String currentUsername() {
    return currentUser().map(AuthenticatedUser::username).orElse(null);
  }
}
