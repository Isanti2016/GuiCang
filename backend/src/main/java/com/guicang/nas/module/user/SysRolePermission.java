package com.guicang.nas.module.user;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 角色-权限关联实体，对应 sys_role_permission 表。 */
@Getter
@Setter
@TableName("sys_role_permission")
public class SysRolePermission {

  private Long roleId;

  private Long permissionId;
}
