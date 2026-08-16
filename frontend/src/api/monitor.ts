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

export interface SeriesPoint {
  timestamp: number;
  value: number;
}

export function fetchOverview(): Promise<HostMetrics> {
  return get<HostMetrics>("/monitor/overview");
}

export function fetchSeries(
  metric: string,
  granularity = "fine",
): Promise<SeriesPoint[]> {
  return get<SeriesPoint[]>("/monitor/series", { metric, granularity });
}
