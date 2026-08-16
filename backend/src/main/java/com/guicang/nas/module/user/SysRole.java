package com.guicang.nas.module.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 角色实体，对应 sys_role 表。 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 角色编码：admin / member / guest / custom。 */
  private String code;

  private String name;

  private String description;
}
