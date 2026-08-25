package com.guicang.nas.module.setting;

import com.guicang.nas.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统设置接口（需 admin 权限）。 */
@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class SysSettingController {

  private final SysSettingService sysSettingService;

  public SysSettingController(SysSettingService sysSettingService) {
    this.sysSettingService = sysSettingService;
  }

  /**
   * 全部设置项（含默认值）。
   *
   * @return key → 当前值
   */
  @GetMapping
  public Result<Map<String, String>> all() {
    return Result.ok(sysSettingService.all());
  }

  /**
   * 更新设置项。
   *
   * @param request 待更新 {key: value}
   * @return 空结果
   */
  @PutMapping
  public Result<Void> update(@Valid @RequestBody UpdateRequest request) {
    sysSettingService.update(request.values());
    return Result.ok();
  }

  /**
   * 设置项定义列表（前端动态渲染表单）。
   *
   * @return 设置项定义
   */
  @GetMapping("/definitions")
  public Result<List<SysSetting>> definitions() {
    return Result.ok(sysSettingService.definitions());
  }

  /** 更新设置请求体。 */
  public record UpdateRequest(@NotNull Map<String, String> values) {}
}
