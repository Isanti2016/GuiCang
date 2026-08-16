package com.guicang.nas.module.file;

import java.util.List;

/** 目录级权限判定：角色规则 + sys_user_dir 附加授权。 */
public interface DirPermissionService {

  /**
   * 校验用户对路径的目录级权限，不满足抛 {@link com.guicang.nas.common.BizException}。
   *
   * @param username 用户名
   * @param authorities 角色/权限（ROLE_xxx + 权限码）
   * @param relativePath 存储根下相对路径
   * @param required 所需权限
   */
  void check(String username, List<String> authorities, String relativePath, DirPerm required);

  /** 是否具备对路径的目录级权限（不抛异常，用于搜索过滤等场景）。 */
  boolean has(String username, List<String> authorities, String relativePath, DirPerm required);
}
