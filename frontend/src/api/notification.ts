import { get, post } from "@/utils/http";

/** 站内通知（与后端 Notification 对应）。 */
export interface Notification {
  id: number;
  type: string;
  title: string;
  content: string | null;
  username: string | null;
  readFlag: number;
  createdAt: number;
}

export function listNotifications(): Promise<Notification[]> {
  return get<Notification[]>("/notifications");
}

export function unreadCount(): Promise<number> {
  return get<number>("/notifications/unread-count");
}

export function markRead(id: number): Promise<void> {
  return post<void>(`/notifications/${id}/read`);
}

export function markAllRead(): Promise<void> {
  return post<void>("/notifications/read-all");
}
