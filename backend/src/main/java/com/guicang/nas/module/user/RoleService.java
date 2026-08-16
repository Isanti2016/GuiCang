package com.guicang.nas.module.user;

import com.guicang.nas.module.user.dto.RoleUpsertRequest;
import com.guicang.nas.module.user.dto.RoleVO;
import java.util.List;

/** 角色管理服务。 */
public interface RoleService {

  /** 角色列表（含授权权限编码）。 */
  List<RoleVO> listRoles();

  /** 新建角色。 */
  RoleVO createRole(RoleUpsertRequest request);

  /** 编辑角色（内置角色仅可改授权与描述，不可改编码）。 */
  RoleVO updateRole(Long id, RoleUpsertRequest request);

  /** 删除角色（内置角色与已分配角色不可删）。 */
  void deleteRole(Long id);
}
