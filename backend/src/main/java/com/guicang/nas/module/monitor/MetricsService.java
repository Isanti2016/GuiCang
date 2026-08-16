package com.guicang.nas.module.monitor;

import com.guicang.nas.infra.monitor.HostMetrics;
import java.util.List;

/** 监控指标服务：定时采集 + 内存序列缓存。 */
public interface MetricsService {

  /** 最近一次指标快照（可能为 null，首次采集前）。 */
  HostMetrics latest();

  /**
   * 指标序列。
   *
   * @param metric cpu / mem / disk / net
   * @param granularity fine（2h 30s 粒度）或 coarse（24h 5min 粒度），其他值默认 coarse
   */
  List<SeriesPoint> series(String metric, String granularity);
}
