package com.guicang.nas.module.dashboard;

/** 大屏聚合服务。 */
public interface DashboardService {

  /** 大屏聚合：存储/文件统计/用户数/最近操作。 */
  DashboardSummary summary();
}
