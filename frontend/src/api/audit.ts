import { get } from "@/utils/http";

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

export interface AuditPage {
  records: AuditLog[];
  total: number;
}

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
