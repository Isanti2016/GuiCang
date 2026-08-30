package com.guicang.nas.module.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 系统用户实体，对应 sys_user 表（密码不在此表，由系统/PAM 管理）。 */
@Getter
@Setter
@TableName("sys_user")
public class SysUser {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 用户名（对应系统账号）。 */
  private String username;

  /** 系统 uid。 */
  private Long uid;

  private String displayName;

  private String email;

  /** 是否启用（1 启用 / 0 停用）。 */
  private Integer enabled;

  /** 主角色 id。 */
  private Long roleId;

  /** 个人目录。 */
  private String homePath;

  /** 可选配额（字节）。 */
  private Long quotaBytes;

  private String createdAt;

  /** 两步验证 TOTP 密钥（Base32，null/空表示未开启）。 */
  private String totpSecret;

  private String updatedAt;
}
