import { del, downloadBlob, get, getToken, post, put, saveBlob, uploadFile } from "@/utils/http";

/** 文件/目录条目（与后端 FileEntry 对应）。 */
export interface FileEntry {
  name: string;
  path: string;
  dir: boolean;
  size: number;
  mtime: number;
  kind: string;
}

export function listFiles(path: string): Promise<FileEntry[]> {
  return get<FileEntry[]>("/files/list", { path });
}

export function mkdir(path: string): Promise<void> {
  return post<void>("/files/mkdir", { path });
}

export function renameFile(path: string, newName: string): Promise<void> {
  return put<void>("/files/rename", { path, newName });
}

export function moveFile(path: string, target: string): Promise<void> {
  return post<void>("/files/move", { path, target });
}

export function deleteFile(path: string, recursive = false): Promise<void> {
  return del<void>("/files", { path, recursive });
}

export function upload(path: string, file: File): Promise<FileEntry> {
  return uploadFile<FileEntry>("/files/upload", file, { path });
}

export function readText(path: string): Promise<string> {
  return get<string>("/files/text", { path });
}

export function writeText(path: string, content: string): Promise<void> {
  return put<void>("/files/write", { path, content });
}

export function searchFiles(q: string): Promise<FileEntry[]> {
  return get<FileEntry[]>("/files/search", { q });
}

/** 递归收集图片/视频（相册数据源）。 */
export function fetchMedia(path = ""): Promise<FileEntry[]> {
  return get<FileEntry[]>("/files/media", { path });
}

/** 带 token 的流媒体 URL（img/video 标签用）。 */
export function streamUrl(path: string): string {
  return `/api/v1/files/stream?path=${encodeURIComponent(path)}&token=${getToken() ?? ""}`;
}

/** 带 token 的缩略图 URL。 */
export function thumbnailUrl(path: string): string {
  return `/api/v1/files/thumbnail?path=${encodeURIComponent(path)}&token=${getToken() ?? ""}`;
}

/** 上传（带进度回调）。 */
export function uploadWithProgress(
  path: string,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<FileEntry> {
  return uploadFile<FileEntry>("/files/upload", file, { path }, onProgress);
}

/** 回收站条目（与后端 TrashItem 对应）。 */
export interface TrashItem {
  id: number;
  originalPath: string;
  trashPath: string;
  username: string;
  kind: string;
  size: number | null;
  deletedAt: number;
}

export function fetchTrash(): Promise<TrashItem[]> {
  return get<TrashItem[]>("/files/trash");
}

export function restoreTrash(id: number): Promise<void> {
  return post<void>(`/files/trash/${id}/restore`);
}

export function purgeTrash(id: number): Promise<void> {
  return del<void>(`/files/trash/${id}`);
}

export function emptyTrash(): Promise<void> {
  return del<void>("/files/trash");
}

/** 下载为文件（Blob，保留文件名）。 */
export async function downloadFileAsBlob(path: string): Promise<void> {
  const blob = await downloadBlob("/files/download", { path });
  const name = path.includes("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
  saveBlob(blob, name);
}
