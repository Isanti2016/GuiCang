package com.guicang.nas.module.monitor;

import com.guicang.nas.common.Result;
import com.guicang.nas.infra.monitor.HostMetrics;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 监控接口（需 monitor.view 权限，默认仅管理员）。 */
@RestController
@RequestMapping("/api/v1/monitor")
@PreAuthorize("hasAuthority('monitor.view')")
public class MonitorController {

  private final MetricsService metricsService;

  public MonitorController(MetricsService metricsService) {
    this.metricsService = metricsService;
  }

  /** 实时指标快照。 */
  @GetMapping("/overview")
  public Result<HostMetrics> overview() {
    return Result.ok(metricsService.latest());
  }

  /** 历史序列（granularity=fine 2h / coarse 24h）。 */
  @GetMapping("/series")
  public Result<List<SeriesPoint>> series(
      @RequestParam(defaultValue = "cpu") String metric,
      @RequestParam(defaultValue = "coarse") String granularity) {
    return Result.ok(metricsService.series(metric, granularity));
  }
}
