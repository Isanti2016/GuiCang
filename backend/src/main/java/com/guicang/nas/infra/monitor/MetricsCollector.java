package com.guicang.nas.infra.monitor;

/** 主机指标采集接口（local 读 /proc；helper 经 sudo 调宿主脚本）。 */
public interface MetricsCollector {

  /** 采集一次主机指标快照。 */
  HostMetrics collect();
}
