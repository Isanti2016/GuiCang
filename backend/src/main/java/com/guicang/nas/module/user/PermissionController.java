package com.guicang.nas.module.user;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.user.dto.PermissionVO;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 权限点查询接口（仅管理员）。 */
@RestController
@RequestMapping("/api/v1/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController {

  private final PermissionService permissionService;

  public PermissionController(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  /** 全部权限点列表。 */
  @GetMapping
  public Result<List<PermissionVO>> list() {
    return Result.ok(permissionService.listAll());
  }
}
