package com.guicang.nas.module.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 权限点实体，对应 sys_permission 表。 */
@Getter
@Setter
@TableName("sys_permission")
public class SysPermission {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 权限编码，如 file.read / user.manage / audit.view。 */
  private String code;

  private String name;

  /** menu / api / dir。 */
  private String type;

  /** 目录路径（type=dir 时）。 */
  private String resource;
}
