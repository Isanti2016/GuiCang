package com.guicang.nas.module.user;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.user.dto.RoleUpsertRequest;
import com.guicang.nas.module.user.dto.RoleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 角色管理接口（仅管理员）。 */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class RoleController {

  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  /** 角色列表。 */
  @GetMapping
  public Result<List<RoleVO>> list() {
    return Result.ok(roleService.listRoles());
  }

  /** 新建角色。 */
  @PostMapping
  public Result<RoleVO> create(@Valid @RequestBody RoleUpsertRequest request) {
    return Result.ok(roleService.createRole(request));
  }

  /** 编辑角色。 */
  @PutMapping("/{id}")
  public Result<RoleVO> update(
      @PathVariable @NotNull Long id, @Valid @RequestBody RoleUpsertRequest request) {
    return Result.ok(roleService.updateRole(id, request));
  }

  /** 删除角色。 */
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable @NotNull Long id) {
    roleService.deleteRole(id);
    return Result.ok();
  }
}
