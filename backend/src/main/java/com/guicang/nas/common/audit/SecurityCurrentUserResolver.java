package com.guicang.nas.common.audit;

import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Security 版当前用户解析器：从 SecurityContext 读取用户名；未登录返回空。 取代默认解析器（认证链路接入后）。 */
@Component
public class SecurityCurrentUserResolver implements CurrentUserResolver {

  @Override
  public Optional<String> currentUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return Optional.empty();
    }
    return Optional.ofNullable(authentication.getName());
  }
}
