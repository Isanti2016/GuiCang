import { get, post } from "@/utils/http";

/** 登录会话信息（与后端 SessionVO 对应）。 */
export interface SessionVO {
  id: number;
  username: string;
  ip: string | null;
  userAgent: string | null;
  createdAt: number;
  lastActiveAt: number;
}

export function listSessions(): Promise<SessionVO[]> {
  return get<SessionVO[]>("/sessions");
}

export function revokeSession(id: number): Promise<void> {
  return post<void>(`/sessions/${id}/revoke`);
}
