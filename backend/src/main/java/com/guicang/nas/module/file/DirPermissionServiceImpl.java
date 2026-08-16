package com.guicang.nas.module.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.module.user.SysUserDir;
import com.guicang.nas.module.user.SysUserDirMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 目录级权限判定实现（手册 3.4）：
 *
 * <ul>
 *   <li>admin：全路径 MANAGE；
 *   <li>personal/&lt;本人&gt;：本人 WRITE；其他用户需附加授权；
 *   <li>shared：member WRITE / guest READ；
 *   <li>media：所有登录用户 READ；
 *   <li>sys_user_dir 附加授权：按路径前缀授予 read/write/manage，与基础规则取更高者。
 * </ul>
 */
@Service
public class DirPermissionServiceImpl implements DirPermissionService {

  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final String ROLE_MEMBER = "ROLE_MEMBER";
  private static final String ROLE_GUEST = "ROLE_GUEST";

  private final SysUserDirMapper sysUserDirMapper;

  public DirPermissionServiceImpl(SysUserDirMapper sysUserDirMapper) {
    this.sysUserDirMapper = sysUserDirMapper;
  }

  @Override
  public void check(
      String username, List<String> authorities, String relativePath, DirPerm required) {
    String path = normalize(relativePath);
    if (hasRole(authorities, ROLE_ADMIN)) {
      return;
    }
    DirPerm granted = resolveGranted(username, authorities, path);
    if (granted == null || granted.ordinal() < required.ordinal()) {
      throw new BizException("无权限执行该操作");
    }
  }

  /** 用户对路径的最高权限 = max(基础规则, sys_user_dir 附加授权)；均无返回 null。 */
  private DirPerm resolveGranted(String username, List<String> authorities, String path) {
    return max(baseGrant(username, authorities, path), resolveUserDirGrant(username, path));
  }

  private DirPerm baseGrant(String username, List<String> authorities, String path) {
    if (path.equals("personal/" + username) || path.startsWith("personal/" + username + "/")) {
      return DirPerm.WRITE;
    }
    if (path.equals("shared") || path.startsWith("shared/")) {
      if (hasRole(authorities, ROLE_MEMBER)) {
        return DirPerm.WRITE;
      }
      if (hasRole(authorities, ROLE_GUEST)) {
        return DirPerm.READ;
      }
    }
    if (path.equals("media") || path.startsWith("media/")) {
      return DirPerm.READ;
    }
    return null;
  }

  private DirPerm resolveUserDirGrant(String username, String path) {
    List<SysUserDir> grants =
        sysUserDirMapper.selectList(
            new LambdaQueryWrapper<SysUserDir>()
                .eq(SysUserDir::getUsername, username)
                .orderByDesc(SysUserDir::getPath));
    DirPerm best = null;
    for (SysUserDir grant : grants) {
      if (isUnder(path, normalize(grant.getPath()))) {
        DirPerm perm = parsePerm(grant.getPerm());
        best = max(best, perm);
      }
    }
    return best;
  }

  private static DirPerm max(DirPerm a, DirPerm b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.ordinal() >= b.ordinal() ? a : b;
  }

  private static boolean isUnder(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

  private static DirPerm parsePerm(String perm) {
    return switch (perm == null ? "" : perm.toLowerCase()) {
      case "manage" -> DirPerm.MANAGE;
      case "write" -> DirPerm.WRITE;
      case "read" -> DirPerm.READ;
      default -> null;
    };
  }

  private static boolean hasRole(List<String> authorities, String role) {
    return authorities != null && authorities.contains(role);
  }

  /** 去首尾斜杠，禁止越界。 */
  private static String normalize(String path) {
    String trimmed = path == null ? "" : path.replace('\\', '/');
    while (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1);
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    if (trimmed.contains("..")) {
      throw new BizException("非法路径（禁止 ..）");
    }
    return trimmed;
  }
}
