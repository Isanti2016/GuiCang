package com.guicang.nas.module.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 登录会话实体，对应 login_session 表。 */
@Getter
@Setter
@TableName("login_session")
public class LoginSession {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** token 的 SHA-256 哈希（唯一标识）。 */
  private String tokenHash;

  private String username;

  private String ip;

  private String userAgent;

  private Long createdAt;

  private Long lastActiveAt;

  /** 0 有效 / 1 已踢下线。 */
  private Integer revoked;
}
