import { get } from "@/utils/http";

/** 日志条目（与后端 LogEntry 对应）。 */
export interface LogEntry {
  timestamp: number;
  level: string;
  logger: string;
  thread: string;
  message: string;
}

/** 日志分页结果。 */
export interface LogPage {
  records: LogEntry[];
  total: number;
}

/** 分页查询系统日志（可按级别/关键字过滤）。 */
export function fetchLogs(
  page: number,
  size: number,
  filters?: { level?: string; keyword?: string },
): Promise<LogPage> {
  return get<LogPage>("/logs", {
    page,
    size,
    level: filters?.level || undefined,
    keyword: filters?.keyword || undefined,
  });
}

/** 支持的日志级别。 */
export function fetchLogLevels(): Promise<string[]> {
  return get<string[]>("/logs/levels");
}
