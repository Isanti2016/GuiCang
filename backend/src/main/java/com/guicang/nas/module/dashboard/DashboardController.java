package com.guicang.nas.module.dashboard;

import com.guicang.nas.common.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 大屏接口（需 dashboard.view 权限）。 */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAuthority('dashboard.view')")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  /**
   * 大屏聚合数据。
   *
   * @return 大屏聚合数据
   */
  @GetMapping("/summary")
  public Result<DashboardSummary> summary() {
    return Result.ok(dashboardService.summary());
  }
}
