package com.guicang.nas.module.user;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.user.dto.UserCreateRequest;
import com.guicang.nas.module.user.dto.UserPage;
import com.guicang.nas.module.user.dto.UserPasswordRequest;
import com.guicang.nas.module.user.dto.UserStatusRequest;
import com.guicang.nas.module.user.dto.UserUpdateRequest;
import com.guicang.nas.module.user.dto.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理接口（仅管理员）。 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  /**
   * 用户列表（分页 + 关键字）。
   *
   * @param page 页码（从 1 开始）
   * @param size 每页条数
   * @param keyword 关键字筛选（可选）
   * @return 用户分页结果
   */
  @GetMapping
  public Result<UserPage> list(
      @RequestParam(defaultValue = "1") @Min(1) long page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
      @RequestParam(required = false) String keyword) {
    return Result.ok(userService.listUsers(page, size, keyword));
  }

  /**
   * 新建用户（同步创建系统账号与 Samba 账号）。
   *
   * @param request 用户创建请求体
   * @return 新建的用户信息
   */
  @PostMapping
  public Result<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
    return Result.ok(userService.createUser(request));
  }

  /**
   * 用户详情。
   *
   * @param name 用户名
   * @return 用户详情
   */
  @GetMapping("/{name}")
  public Result<UserVO> get(@PathVariable @NotBlank String name) {
    return Result.ok(userService.getUser(name));
  }

  /**
   * 编辑用户。
   *
   * @param name 用户名
   * @param request 用户编辑请求体
   * @return 更新后的用户信息
   */
  @PutMapping("/{name}")
  public Result<UserVO> update(
      @PathVariable @NotBlank String name, @Valid @RequestBody UserUpdateRequest request) {
    return Result.ok(userService.updateUser(name, request));
  }

  /**
   * 启用/禁用。
   *
   * @param name 用户名
   * @param request 状态请求体（启用/禁用）
   * @return 更新后的用户信息
   */
  @PutMapping("/{name}/status")
  public Result<UserVO> setStatus(
      @PathVariable @NotBlank String name, @Valid @RequestBody UserStatusRequest request) {
    return Result.ok(userService.setStatus(name, request));
  }

  /**
   * 重置密码（admin 操作）。
   *
   * @param name 用户名
   * @param request 重置密码请求体（新密码）
   * @return 空结果
   */
  @PutMapping("/{name}/password")
  public Result<Void> resetPassword(
      @PathVariable @NotBlank String name, @Valid @RequestBody UserPasswordRequest request) {
    userService.resetPassword(name, request);
    return Result.ok();
  }

  /**
   * 删除用户（默认保留个人目录）。
   *
   * @param name 用户名
   * @param removeHome 是否同时删除个人目录
   * @return 空结果
   */
  @DeleteMapping("/{name}")
  public Result<Void> delete(
      @PathVariable @NotBlank String name,
      @RequestParam(defaultValue = "false") boolean removeHome) {
    userService.deleteUser(name, removeHome);
    return Result.ok();
  }
}
