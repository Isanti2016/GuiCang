import { del, get, post } from "@/utils/http";

/** 摄像头（与后端 CameraVO 对应）。 */
export interface Camera {
  id?: number;
  name: string;
  location?: string;
  totalRecords?: number;
  lastRecordAt?: number;
}

/** 单条监控录像。 */
export interface CameraRecord {
  id: number;
  path: string;
  name: string;
  size: number;
  mtime: number;
}

/** 目录约定信息。 */
export interface CameraMeta {
  receiveDir: string;
  archiveDir: string;
}

export function fetchCameras(): Promise<Camera[]> {
  return get<Camera[]>("/cameras");
}

export function fetchCameraMeta(): Promise<CameraMeta> {
  return get<CameraMeta>("/cameras/meta");
}

export function saveCamera(data: {
  id?: number;
  name: string;
  location?: string;
}): Promise<Camera> {
  return post<Camera>("/cameras", data);
}

export function deleteCamera(id: number): Promise<void> {
  return del<void>(`/cameras/${id}`);
}

export function fetchCameraRecords(
  camera: string,
  date?: string,
): Promise<CameraRecord[]> {
  return get<CameraRecord[]>("/cameras/records", { camera, date });
}
