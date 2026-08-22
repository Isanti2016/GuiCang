import { get, post, put } from "@/utils/http";

/** 当前用户信息。 */
export interface CurrentUserInfo {
  username: string;
  uid: number;
  home: string | null;
  shell: string | null;
  roles: string[];
}

/** 登录响应。 */
export interface LoginResponse {
  token: string;
  user: CurrentUserInfo;
}

/** 登录请求体。 */
export interface LoginRequest {
  username: string;
  password: string;
}

/** 登录（返回 JWT 与用户信息）。 */
export function login(data: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>("/auth/login", data);
}

/** 获取当前登录用户信息。 */
export function fetchMe(): Promise<CurrentUserInfo> {
  return get<CurrentUserInfo>("/auth/me");
}

/** 注销当前会话。 */
export function logout(): Promise<void> {
  return post<void>("/auth/logout");
}

/** 修改本人密码（校验旧密码后由后端经系统账号改密）。 */
export function changePassword(
  oldPassword: string,
  newPassword: string,
): Promise<void> {
  return put<void>("/auth/password", { oldPassword, newPassword });
}
