package com.guicang.nas.module.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 用户目录授权实体，对应 sys_user_dir 表。 */
@Getter
@Setter
@TableName("sys_user_dir")
public class SysUserDir {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String username;

  /** 目录路径（存储根相对或绝对）。 */
  private String path;

  /** read / write / manage。 */
  private String perm;
}
