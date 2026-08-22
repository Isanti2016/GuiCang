import { get } from "@/utils/http";

/** 操作审计日志条目（与后端 AuditLog 对应）。 */
export interface AuditLog {
  id: number;
  username: string | null;
  action: string;
  resource: string | null;
  ip: string | null;
  userAgent: string | null;
  result: string;
  detail: string | null;
  createdAt: number | null;
}

/** 审计日志分页结果。 */
export interface AuditPage {
  records: AuditLog[];
  total: number;
}

/** 分页查询审计日志（可按用户/动作/结果过滤）。 */
export function fetchAuditLogs(
  page: number,
  size: number,
  filters?: { username?: string; action?: string; result?: string },
): Promise<AuditPage> {
  return get<AuditPage>("/audit/logs", {
    page,
    size,
    username: filters?.username || undefined,
    action: filters?.action || undefined,
    result: filters?.result || undefined,
  });
}
