package com.guicang.nas.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.infra.account.ProvisionResult;
import com.guicang.nas.infra.account.ProvisionStatus;
import com.guicang.nas.infra.account.SystemAccountProvisioner;
import com.guicang.nas.module.user.dto.UserCreateRequest;
import com.guicang.nas.module.user.dto.UserPage;
import com.guicang.nas.module.user.dto.UserPasswordRequest;
import com.guicang.nas.module.user.dto.UserStatusRequest;
import com.guicang.nas.module.user.dto.UserUpdateRequest;
import com.guicang.nas.module.user.dto.UserVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户管理服务实现：系统账号（helper）与 Web 元数据（sys_user）同步管理。 */
@Service
public class UserServiceImpl implements UserService {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private final SysUserMapper sysUserMapper;
  private final SysRoleMapper sysRoleMapper;
  private final SysRolePermissionMapper sysRolePermissionMapper;
  private final SysPermissionMapper sysPermissionMapper;
  private final SystemAccountProvisioner systemAccountProvisioner;

  public UserServiceImpl(
      SysUserMapper sysUserMapper,
      SysRoleMapper sysRoleMapper,
      SysRolePermissionMapper sysRolePermissionMapper,
      SysPermissionMapper sysPermissionMapper,
      SystemAccountProvisioner systemAccountProvisioner) {
    this.sysUserMapper = sysUserMapper;
    this.sysRoleMapper = sysRoleMapper;
    this.sysRolePermissionMapper = sysRolePermissionMapper;
    this.sysPermissionMapper = sysPermissionMapper;
    this.systemAccountProvisioner = systemAccountProvisioner;
  }

  @Override
  @Transactional
  @Audit(action = "user.create", resource = "#request.username()")
  public UserVO createUser(UserCreateRequest request) {
    if (sysUserMapper.selectCount(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.username()))
        > 0) {
      throw new BizException("用户已存在: " + request.username());
    }
    SysRole role = requireRole(request.roleId());

    ProvisionResult provision =
        systemAccountProvisioner.createUser(request.username(), request.password());
    if (provision.status() != ProvisionStatus.CREATED) {
      throw new BizException("系统账号服务未就绪（guicang-helper 未部署），用户创建失败");
    }

    SysUser user = new SysUser();
    user.setUsername(request.username());
    user.setUid(provision.uid());
    user.setDisplayName(request.displayName());
    user.setEmail(request.email());
    user.setEnabled(1);
    user.setRoleId(role.getId());
    user.setHomePath("/home/" + request.username());
    user.setQuotaBytes(request.quotaBytes());
    String now = now();
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    sysUserMapper.insert(user);
    return toVO(user, role);
  }

  @Override
  @Transactional
  @Audit(action = "user.update", resource = "#username")
  public UserVO updateUser(String username, UserUpdateRequest request) {
    SysUser user = requireUser(username);
    SysRole role = requireRole(request.roleId());
    user.setDisplayName(request.displayName());
    user.setEmail(request.email());
    user.setRoleId(role.getId());
    user.setQuotaBytes(request.quotaBytes());
    user.setUpdatedAt(now());
    sysUserMapper.updateById(user);
    return toVO(user, role);
  }

  @Override
  @Transactional
  @Audit(action = "user.status", resource = "#username")
  public UserVO setStatus(String username, UserStatusRequest request) {
    SysUser user = requireUser(username);
    if ("admin".equals(user.getUsername())) {
      throw new BizException("内置 admin 账号不可停用");
    }
    ProvisionResult provision = systemAccountProvisioner.setUserStatus(username, request.enabled());
    if (provision.status() == ProvisionStatus.FAILED) {
      throw new BizException("系统账号状态变更失败: " + provision.message());
    }
    user.setEnabled(request.enabled() ? 1 : 0);
    user.setUpdatedAt(now());
    sysUserMapper.updateById(user);
    return toVO(user, requireRole(user.getRoleId()));
  }

  @Override
  @Transactional
  @Audit(action = "user.password", resource = "#username")
  public void resetPassword(String username, UserPasswordRequest request) {
    requireUser(username);
    ProvisionResult provision =
        systemAccountProvisioner.setUserPassword(username, request.password());
    if (provision.status() != ProvisionStatus.CREATED) {
      throw new BizException("密码重置失败（guicang-helper 未部署）");
    }
  }

  @Override
  @Transactional
  @Audit(action = "user.delete", resource = "#username")
  public void deleteUser(String username, boolean removeHome) {
    requireUser(username);
    if ("admin".equals(username)) {
      throw new BizException("内置 admin 账号不可删除");
    }
    ProvisionResult provision = systemAccountProvisioner.deleteUser(username, removeHome);
    if (provision.status() == ProvisionStatus.FAILED) {
      throw new BizException("系统账号删除失败: " + provision.message());
    }
    sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
  }

  @Override
  public UserPage listUsers(long page, long size, String keyword) {
    LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
    if (keyword != null && !keyword.isBlank()) {
      wrapper.like(SysUser::getUsername, keyword).or().like(SysUser::getDisplayName, keyword);
    }
    long total = sysUserMapper.selectCount(wrapper);
    wrapper.orderByDesc(SysUser::getId);
    List<SysUser> users = sysUserMapper.selectList(wrapper);
    long skip = (page - 1) * size;
    List<UserVO> records =
        users.stream()
            .skip(skip)
            .limit(size)
            .map(u -> toVO(u, requireRole(u.getRoleId())))
            .toList();
    return new UserPage(records, total);
  }

  @Override
  public UserVO getUser(String username) {
    SysUser user = requireUser(username);
    return toVO(user, requireRole(user.getRoleId()));
  }

  @Override
  public Optional<SysUser> findByUsername(String username) {
    return Optional.ofNullable(
        sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)));
  }

  @Override
  public List<String> loadAuthorities(String username) {
    SysUser user =
        sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    if (user == null || user.getEnabled() == null || user.getEnabled() == 0) {
      return List.of();
    }
    SysRole role = sysRoleMapper.selectById(user.getRoleId());
    if (role == null) {
      return List.of();
    }
    List<String> authorities = new ArrayList<>();
    authorities.add("ROLE_" + role.getCode().toUpperCase());
    sysRolePermissionMapper
        .selectList(
            new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, role.getId()))
        .forEach(
            rp -> {
              SysPermission permission = sysPermissionMapper.selectById(rp.getPermissionId());
              if (permission != null) {
                authorities.add(permission.getCode());
              }
            });
    return authorities;
  }

  private SysUser requireUser(String username) {
    SysUser user =
        sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    if (user == null) {
      throw new BizException("用户不存在: " + username);
    }
    return user;
  }

  private SysRole requireRole(Long roleId) {
    SysRole role = sysRoleMapper.selectById(roleId);
    if (role == null) {
      throw new BizException("角色不存在: id=" + roleId);
    }
    return role;
  }

  private UserVO toVO(SysUser user, SysRole role) {
    return new UserVO(
        user.getId(),
        user.getUsername(),
        user.getUid(),
        user.getDisplayName(),
        user.getEmail(),
        user.getEnabled() != null && user.getEnabled() == 1,
        user.getRoleId(),
        role.getCode(),
        role.getName(),
        user.getHomePath(),
        user.getQuotaBytes(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }

  private String now() {
    return LocalDateTime.now().format(TIME_FORMAT);
  }
}
