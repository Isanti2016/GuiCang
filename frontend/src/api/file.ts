import {
  del,
  downloadBlob,
  downloadBlobPost,
  get,
  getToken,
  post,
  put,
  saveBlob,
  uploadFile,
} from "@/utils/http";

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

/** 全文检索（匹配 md/txt 内容）。 */
export function searchContent(q: string): Promise<FileEntry[]> {
  return get<FileEntry[]>("/files/search-content", { q });
}

/** 重复文件分组（与后端 DuplicateGroup 对应）。 */
export interface DuplicateGroup {
  size: number;
  hash: string;
  files: FileEntry[];
}

/** 查找重复文件（相同大小 + 相同 SHA-256）。 */
export function findDuplicates(path: string): Promise<DuplicateGroup[]> {
  return get<DuplicateGroup[]>("/files/duplicates", { path });
}

/** 文件历史版本（与后端 FileVersion 对应）。 */
export interface FileVersion {
  id: number;
  path: string;
  content: string;
  size: number | null;
  createdBy: string;
  createdAt: number;
}

/** 某文件的历史版本列表。 */
export function listVersions(path: string): Promise<FileVersion[]> {
  return get<FileVersion[]>("/files/versions", { path });
}

/** 回滚到指定历史版本。 */
export function restoreVersion(id: number): Promise<void> {
  return post<void>(`/files/versions/${id}/restore`);
}

/** 上传单个分片（大文件分片上传）。 */
export function uploadChunk(
  path: string,
  filename: string,
  uploadId: string,
  chunkIndex: number,
  totalChunks: number,
  file: Blob,
): Promise<void> {
  return uploadFile<void>("/files/chunk", file, {
    path,
    filename,
    uploadId,
    chunkIndex,
    totalChunks,
  });
}

/** 合并分片为完整文件。 */
export function completeChunkUpload(
  path: string,
  filename: string,
  uploadId: string,
  totalChunks: number,
): Promise<FileEntry> {
  return post<FileEntry>("/files/chunk/complete", {
    path,
    filename,
    uploadId,
    totalChunks,
  });
}

/** 递归收集图片/视频（相册数据源）。 */
export function fetchMedia(path = ""): Promise<FileEntry[]> {
  return get<FileEntry[]>("/files/media", { path });
}

/** 浏览器原生支持的音轨编码（与后端 MediaInspectService.AUDIO_SUPPORTED 对齐）。 */
export const BROWSER_AUDIO_SUPPORTED = new Set<string>([
  "aac", "mp3", "opus", "vorbis", "flac",
  "pcm_mulaw", "pcm_alaw", "pcm_s8", "pcm_u8",
  "pcm_s16le", "pcm_s16be", "pcm_s24le", "pcm_s24be",
  "pcm_s32le", "pcm_s32be", "pcm_f32le", "pcm_f64le",
]);

/** 媒体元数据（与后端 MediaMetadataVO 对齐，浏览器原生兼容性前端二次校验）。 */
export interface MediaMetadata {
  container: string;
  videoCodec: string;
  audioCodec: string;
  audioCodecs: string[];
  videoCodecs: string[];
  durationSec: number;
  width: number;
  height: number;
  hasSubtitle: boolean;
  browserAudioSupported: boolean | null;
  browserVideoSupported: boolean | null;
}

/** 获取媒体元数据（编码 + 浏览器兼容性）。首次会触发后端 ffprobe 并回写索引缓存。 */
export function mediaInspect(path: string): Promise<MediaMetadata> {
  return get<MediaMetadata>("/files/media/inspect", { path });
}

/** 浏览器原生支持的音轨编码（与后端 MediaInspectService.AUDIO_SUPPORTED 对齐）。 */

/** 浏览器原生支持的音轨编码（与后端 MediaInspectService.AUDIO_SUPPORTED 对齐）。 */

/** 带 token 的流媒体 URL（img/video 标签用）。 */
export function streamUrl(path: string): string {
  return `/api/v1/files/stream?path=${encodeURIComponent(path)}&token=${getToken() ?? ""}`;
}

/** 转码任务状态（与后端 TranscodeStatusVO 对应）。 */
export interface TranscodeStatus {
  status: "IDLE" | "RUNNING" | "DONE" | "FAILED";
  progress: number;
  message: string;
  outputPath: string;
}

/** 启动视频转码为浏览器兼容版（音轨转 aac，输出 原名.compat.mp4）。 */
export function startTranscode(path: string): Promise<TranscodeStatus> {
  return post<TranscodeStatus>(
    `/files/media/transcode?path=${encodeURIComponent(path)}`,
  );
}

/** 查询视频转码状态（IDLE/RUNNING/DONE/FAILED + 进度）。 */
export function transcodeStatus(path: string): Promise<TranscodeStatus> {
  return get<TranscodeStatus>("/files/media/transcode/status", { path });
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
  const name = path.includes("/")
    ? path.substring(path.lastIndexOf("/") + 1)
    : path;
  saveBlob(blob, name);
}

/** 批量删除（软删除进回收站）。 */
export function batchDelete(paths: string[], recursive = false): Promise<void> {
  return post<void>("/files/batch-delete", { paths, recursive });
}

/** 批量打包下载（zip）。 */
export async function downloadZip(paths: string[]): Promise<void> {
  const blob = await downloadBlobPost("/files/zip", paths);
  saveBlob(blob, "guicang-download.zip");
}

/** 分片上传状态（断点续传：已传分片序号与字节数）。 */
export interface ChunkStatus {
  uploadId: string;
  uploadedChunks: number[];
  uploadedBytes: number;
}

/** 查询已上传分片（断点续传）。 */
export function chunkStatus(uploadId: string): Promise<ChunkStatus> {
  return get<ChunkStatus>("/files/chunk/status", { uploadId });
}
