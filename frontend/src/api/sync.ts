import { del, get, post, put } from "@/utils/http";

/** 同步任务（与后端 SyncTask 对应）。 */
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

/** 同步执行历史（与后端 SyncHistory 对应）。 */
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

/** 查询全部同步任务。 */
export function fetchTasks(): Promise<SyncTask[]> {
  return get<SyncTask[]>("/sync/tasks");
}

/** 新建同步任务。 */
export function createTask(data: {
  name: string;
  sourceConfig: string;
  cron: string;
}): Promise<SyncTask> {
  return post<SyncTask>("/sync/tasks", data);
}

/** 更新同步任务（含启用状态）。 */
export function updateTask(
  id: number,
  enabled: boolean,
  data: { name: string; sourceConfig: string; cron: string },
): Promise<SyncTask> {
  return put<SyncTask>(`/sync/tasks/${id}`, data, { enabled });
}

/** 删除同步任务。 */
export function deleteTask(id: number): Promise<void> {
  return del<void>(`/sync/tasks/${id}`);
}

/** 立即执行同步任务，返回本次执行历史。 */
export function runTask(id: number): Promise<SyncHistory> {
  return post<SyncHistory>(`/sync/tasks/${id}/run`);
}

/** 查询执行历史（可按任务过滤，默认最近 50 条）。 */
export function fetchHistory(
  taskId?: number,
  limit = 50,
): Promise<SyncHistory[]> {
  return get<SyncHistory[]>("/sync/history", { taskId, limit });
}
