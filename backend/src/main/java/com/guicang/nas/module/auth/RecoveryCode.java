package com.guicang.nas.module.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 两步验证恢复码实体，对应 recovery_code 表（一次性，用后即焚）。 */
@Getter
@Setter
@TableName("recovery_code")
public class RecoveryCode {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属用户 id。 */
  private Long userId;

  /** 恢复码的 SHA-256 哈希（不存明文）。 */
  private String codeHash;

  /** 0 未使用 / 1 已使用。 */
  private Integer used;

  private Long createdAt;

  private Long usedAt;
}
