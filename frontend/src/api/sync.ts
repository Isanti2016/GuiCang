import { del, get, post, put } from "@/utils/http";

export interface SyncTask {
  id: number;
  name: string;
  sourceType: string;
  sourceConfig: string;
  cron: string;
  enabled: number;
  lastRunAt: number | null;
  lastStatus: string | null;
  createdAt: string;
}

export interface SyncHistory {
  id: number;
  taskId: number;
  startedAt: number;
  finishedAt: number | null;
  status: string;
  added: number;
  updated: number;
  deleted: number;
  error: string | null;
}

export function fetchTasks(): Promise<SyncTask[]> {
  return get<SyncTask[]>("/sync/tasks");
}

export function createTask(data: {
  name: string;
  sourceConfig: string;
  cron: string;
}): Promise<SyncTask> {
  return post<SyncTask>("/sync/tasks", data);
}

export function updateTask(
  id: number,
  enabled: boolean,
  data: { name: string; sourceConfig: string; cron: string },
): Promise<SyncTask> {
  return put<SyncTask>(`/sync/tasks/${id}`, data, { enabled });
}

export function deleteTask(id: number): Promise<void> {
  return del<void>(`/sync/tasks/${id}`);
}

export function runTask(id: number): Promise<SyncHistory> {
  return post<SyncHistory>(`/sync/tasks/${id}/run`);
}

export function fetchHistory(
  taskId?: number,
  limit = 50,
): Promise<SyncHistory[]> {
  return get<SyncHistory[]>("/sync/history", { taskId, limit });
}
