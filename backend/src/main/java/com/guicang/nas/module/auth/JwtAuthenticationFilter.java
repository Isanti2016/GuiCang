package com.guicang.nas.module.auth;

import com.guicang.nas.common.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器：解析 Authorization: Bearer &lt;token&gt;，成功则写入 SecurityContext
 * （principal=AuthenticatedUser，authorities=角色/权限）。令牌非法/过期时不设置认证， 由 Security 的入口点统一返回 401 JSON。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;
  private final SessionService sessionService;

  public JwtAuthenticationFilter(JwtService jwtService, SessionService sessionService) {
    this.jwtService = jwtService;
    this.sessionService = sessionService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null) {
      try {
        Claims claims = jwtService.parse(token);
        if (sessionService.isRevoked(token)) {
          log.debug("令牌已被踢下线，按未登录处理");
          filterChain.doFilter(request, response);
          return;
        }
        long uid = claims.get(JwtService.CLAIM_UID, Long.class);
        List<String> roles = claims.get(JwtService.CLAIM_ROLES, List.class);
        var principal = new AuthenticatedUser(claims.getSubject(), uid);
        var authorities =
            roles == null
                ? List.<SimpleGrantedAuthority>of()
                : roles.stream().map(SimpleGrantedAuthority::new).toList();
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (Exception e) {
        log.debug("JWT 解析失败，按未登录处理: {}", e.getClass().getSimpleName());
      }
    }
    filterChain.doFilter(request, response);
  }

  /** 优先 Authorization 头，其次 query token（供 img/video 标签带认证访问）。 */
  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length());
    }
    String queryToken = request.getParameter("token");
    return queryToken == null || queryToken.isBlank() ? null : queryToken;
  }
}
