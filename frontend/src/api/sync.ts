import { del, get, post, put } from "@/utils/http";

/** 同步任务（与后端 SyncTask 对应）。 */
export interface SyncTask {
  id: number;
  name: string;
  sourceType: string;
  /** index_scan（索引扫描）/ organize（自动整理）。 */
  taskType: string;
  sourceConfig: string;
  /** 目标目录（自动整理用）。 */
  targetConfig: string | null;
  /** date_year / date_month / date_day / kind。 */
  ruleType: string;
  ruleConfig: string;
  /** move / copy。 */
  action: string;
  /** skip / overwrite / rename。 */
  conflict: string;
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
  taskType: string;
  startedAt: number;
  finishedAt: number | null;
  /** running / success / partial / failed。 */
  status: string;
  processed: number;
  succeeded: number;
  failed: number;
  skipped: number;
  added: number;
  updated: number;
  deleted: number;
  error: string | null;
  details: string | null;
}

/** 新建/编辑任务请求体。 */
export interface SyncTaskPayload {
  name: string;
  sourceConfig: string;
  cron: string;
  taskType?: string;
  targetConfig?: string;
  ruleType?: string;
  ruleConfig?: string;
  action?: string;
  conflict?: string;
}

/** 查询全部同步任务。 */
export function fetchTasks(): Promise<SyncTask[]> {
  return get<SyncTask[]>("/sync/tasks");
}

/** 新建同步任务。 */
export function createTask(data: SyncTaskPayload): Promise<SyncTask> {
  return post<SyncTask>("/sync/tasks", data);
}

/** 更新同步任务（含启用状态）。 */
export function updateTask(
  id: number,
  enabled: boolean,
  data: SyncTaskPayload,
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
