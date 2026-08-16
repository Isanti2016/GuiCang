package com.guicang.nas.module.file;

import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.common.security.CurrentUserContext;
import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.infra.storage.StorageService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 文件管理服务实现：目录操作，每次操作校验登录与目录级权限，并审计留痕。 */
@Service
public class FileServiceImpl implements FileService {

  private final StorageService storageService;
  private final DirPermissionService dirPermissionService;

  public FileServiceImpl(StorageService storageService, DirPermissionService dirPermissionService) {
    this.storageService = storageService;
    this.dirPermissionService = dirPermissionService;
  }

  @Override
  public List<FileEntry> list(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    return storageService.list(path);
  }

  @Override
  @Audit(action = "file.mkdir", resource = "#path")
  public void mkdir(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    storageService.mkdir(path);
  }

  @Override
  @Audit(action = "file.rename", resource = "#path")
  public void rename(String path, String newName) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    storageService.rename(path, newName);
  }

  @Override
  @Audit(action = "file.move", resource = "#path")
  public void move(String path, String target) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    dirPermissionService.check(user.username(), authorities(), target, DirPerm.WRITE);
    storageService.move(path, target);
  }

  @Override
  @Audit(action = "file.delete", resource = "#path")
  public void delete(String path, boolean recursive) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    storageService.delete(path, recursive);
  }

  private AuthenticatedUser requireUser() {
    return CurrentUserContext.currentUser()
        .orElseThrow(() -> new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期"));
  }

  private List<String> authorities() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return List.of();
    }
    return authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
  }
}
