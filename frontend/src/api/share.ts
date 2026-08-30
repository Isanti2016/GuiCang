import { del, get, post } from "@/utils/http";

/** 分享链接信息（与后端 ShareVO 对应）。 */
export interface ShareVO {
  token: string;
  path: string;
  hasPassword: boolean;
  expiresAt: number | null;
}

/** 创建分享链接。 */
export function createShare(
  path: string,
  password?: string,
  expireDays?: number,
): Promise<ShareVO> {
  return post<ShareVO>("/shares", { path, password, expireDays });
}

/** 列出我的分享。 */
export function listShares(): Promise<ShareVO[]> {
  return get<ShareVO[]>("/shares");
}

/** 撤销分享。 */
export function revokeShare(token: string): Promise<void> {
  return del<void>(`/shares/${token}`);
}
