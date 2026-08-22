package com.guicang.nas.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.module.user.dto.RoleUpsertRequest;
import com.guicang.nas.module.user.dto.RoleVO;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色管理服务实现：内置角色受保护，删除前校验占用。 */
@Service
public class RoleServiceImpl implements RoleService {

  private static final Set<String> BUILTIN_CODES = Set.of("admin", "member", "guest");

  private final SysRoleMapper sysRoleMapper;
  private final SysRolePermissionMapper sysRolePermissionMapper;
  private final SysPermissionMapper sysPermissionMapper;
  private final SysUserMapper sysUserMapper;

  public RoleServiceImpl(
      SysRoleMapper sysRoleMapper,
      SysRolePermissionMapper sysRolePermissionMapper,
      SysPermissionMapper sysPermissionMapper,
      SysUserMapper sysUserMapper) {
    this.sysRoleMapper = sysRoleMapper;
    this.sysRolePermissionMapper = sysRolePermissionMapper;
    this.sysPermissionMapper = sysPermissionMapper;
    this.sysUserMapper = sysUserMapper;
  }

  /**
   * 角色列表（含授权权限编码）。
   *
   * @return 角色列表
   */
  @Override
  public List<RoleVO> listRoles() {
    return sysRoleMapper.selectList(null).stream().map(this::toVO).toList();
  }

  /**
   * 新建角色。
   *
   * @param request 创建请求（编码/名称/描述/授权权限）
   * @return 创建后的角色
   */
  @Override
  @Transactional
  @Audit(action = "role.create", resource = "#request.code()")
  public RoleVO createRole(RoleUpsertRequest request) {
    if (sysRoleMapper.selectCount(
            new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, request.code()))
        > 0) {
      throw new BizException("角色编码已存在: " + request.code());
    }
    SysRole role = new SysRole();
    role.setCode(request.code());
    role.setName(request.name());
    role.setDescription(request.description());
    sysRoleMapper.insert(role);
    replacePermissions(role.getId(), request.permissionIds());
    return toVO(role);
  }

  /**
   * 编辑角色（内置角色仅可改授权与描述，不可改编码）。
   *
   * @param id 角色 ID
   * @param request 更新请求（编码/名称/描述/授权权限）
   * @return 更新后的角色
   */
  @Override
  @Transactional
  @Audit(action = "role.update", resource = "#request.code()")
  public RoleVO updateRole(Long id, RoleUpsertRequest request) {
    SysRole role = sysRoleMapper.selectById(id);
    if (role == null) {
      throw new BizException("角色不存在: id=" + id);
    }
    if (BUILTIN_CODES.contains(role.getCode()) && !role.getCode().equals(request.code())) {
      throw new BizException("内置角色编码不可修改");
    }
    role.setCode(request.code());
    role.setName(request.name());
    role.setDescription(request.description());
    sysRoleMapper.updateById(role);
    replacePermissions(id, request.permissionIds());
    return toVO(role);
  }

  /**
   * 删除角色（内置角色与已分配用户角色不可删）。
   *
   * @param id 角色 ID
   */
  @Override
  @Transactional
  @Audit(action = "role.delete", resource = "#id")
  public void deleteRole(Long id) {
    SysRole role = sysRoleMapper.selectById(id);
    if (role == null) {
      throw new BizException("角色不存在: id=" + id);
    }
    if (BUILTIN_CODES.contains(role.getCode())) {
      throw new BizException("内置角色不可删除");
    }
    if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleId, id))
        > 0) {
      throw new BizException("角色已分配给用户，不可删除");
    }
    sysRolePermissionMapper.delete(
        new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
    sysRoleMapper.deleteById(id);
  }

  private void replacePermissions(Long roleId, List<Long> permissionIds) {
    sysRolePermissionMapper.delete(
        new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
    if (permissionIds == null) {
      return;
    }
    permissionIds.stream()
        .distinct()
        .forEach(
            pid -> {
              if (sysPermissionMapper.selectById(pid) == null) {
                throw new BizException("权限点不存在: id=" + pid);
              }
              SysRolePermission rp = new SysRolePermission();
              rp.setRoleId(roleId);
              rp.setPermissionId(pid);
              sysRolePermissionMapper.insert(rp);
            });
  }

  private RoleVO toVO(SysRole role) {
    List<String> permissionCodes =
        sysRolePermissionMapper
            .selectList(
                new LambdaQueryWrapper<SysRolePermission>()
                    .eq(SysRolePermission::getRoleId, role.getId()))
            .stream()
            .map(rp -> sysPermissionMapper.selectById(rp.getPermissionId()))
            .filter(p -> p != null)
            .map(SysPermission::getCode)
            .collect(Collectors.toList());
    return new RoleVO(
        role.getId(), role.getCode(), role.getName(), role.getDescription(), permissionCodes);
  }
}
