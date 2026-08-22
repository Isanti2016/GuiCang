import { get } from "@/utils/http";

/** 主机指标快照（与后端 HostMetrics 对应）。 */
export interface HostMetrics {
  cpu: number;
  load1: number;
  load5: number;
  load15: number;
  memTotalKb: number;
  memAvailKb: number;
  swapTotalKb: number;
  swapFreeKb: number;
  diskTotalKb: number;
  diskAvailKb: number;
  netRxBytes: number;
  netTxBytes: number;
  uptimeSec: number;
  timestamp: number;
}

/** 指标序列点（与后端 SeriesPoint 对应）。 */
export interface SeriesPoint {
  timestamp: number;
  value: number;
}

/** 主机实时指标快照。 */
export function fetchOverview(): Promise<HostMetrics> {
  return get<HostMetrics>("/monitor/overview");
}

/** 按指标名查询时间序列（granularity: fine=30s 采样）。 */
export function fetchSeries(
  metric: string,
  granularity = "fine",
): Promise<SeriesPoint[]> {
  return get<SeriesPoint[]>("/monitor/series", { metric, granularity });
}
