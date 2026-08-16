package com.guicang.nas.infra.monitor;

/** 主机指标快照（字段与 guicang-helper metrics 输出一致）。 */
public record HostMetrics(
    double cpu,
    double load1,
    double load5,
    double load15,
    long memTotalKb,
    long memAvailKb,
    long swapTotalKb,
    long swapFreeKb,
    long diskTotalKb,
    long diskAvailKb,
    long netRxBytes,
    long netTxBytes,
    long uptimeSec,
    long timestamp) {}
