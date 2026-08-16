package com.guicang.nas.module.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** JWT 签发与解析（HS256）。密钥长度须 ≥ 32 字节，生产经 GUICANG_JWT_SECRET 注入。 */
@Service
public class JwtService {

  public static final String CLAIM_UID = "uid";
  public static final String CLAIM_ROLES = "roles";

  private final SecretKey key;
  private final long expireMillis;

  public JwtService(
      @Value("${guicang.jwt.secret}") String secret,
      @Value("${guicang.jwt.expire-hours:2}") long expireHours) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expireMillis = ChronoUnit.HOURS.getDuration().toMillis() * expireHours;
  }

  /** 签发令牌：sub=用户名，uid=系统 uid，roles=角色/权限 authorities。 */
  public String issue(String username, long uid, List<String> authorities) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(username)
        .claim(CLAIM_UID, uid)
        .claim(CLAIM_ROLES, authorities)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(expireMillis)))
        .signWith(key)
        .compact();
  }

  /** 解析令牌；非法/过期抛 {@link io.jsonwebtoken.JwtException}。 */
  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
