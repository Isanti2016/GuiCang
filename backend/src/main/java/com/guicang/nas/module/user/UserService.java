package com.guicang.nas.module.user;

import com.guicang.nas.module.user.dto.UserCreateRequest;
import com.guicang.nas.module.user.dto.UserPage;
import com.guicang.nas.module.user.dto.UserPasswordRequest;
import com.guicang.nas.module.user.dto.UserStatusRequest;
import com.guicang.nas.module.user.dto.UserUpdateRequest;
import com.guicang.nas.module.user.dto.UserVO;
import java.util.List;
import java.util.Optional;

/** 用户管理服务：CRUD + 系统账号同步 + 登录时权限装载。 */
public interface UserService {

  /** 新建用户（创建系统账号 + Samba 同步 + 写 sys_user）。 */
  UserVO createUser(UserCreateRequest request);

  /** 编辑用户元数据。 */
  UserVO updateUser(String username, UserUpdateRequest request);

  /** 启用/禁用（同步系统账号锁定状态）。 */
  UserVO setStatus(String username, UserStatusRequest request);

  /** 重置密码（同步 Linux + Samba）。 */
  void resetPassword(String username, UserPasswordRequest request);

  /** 删除用户（默认保留个人目录，可选 --remove-home）。 */
  void deleteUser(String username, boolean removeHome);

  /** 用户列表（分页 + 关键字）。 */
  UserPage listUsers(long page, long size, String keyword);

  /** 用户详情。 */
  UserVO getUser(String username);

  /** 按用户名查 sys_user（登录用）。 */
  Optional<SysUser> findByUsername(String username);

  /** 装载用户角色与权限（登录用）：返回 [ROLE_xxx, permission.code, ...]。 */
  List<String> loadAuthorities(String username);
}
