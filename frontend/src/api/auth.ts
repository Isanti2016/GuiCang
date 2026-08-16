import { get, post } from '@/utils/http'

/** 当前用户信息。 */
export interface CurrentUserInfo {
  username: string
  uid: number
  home: string | null
  shell: string | null
  roles: string[]
}

/** 登录响应。 */
export interface LoginResponse {
  token: string
  user: CurrentUserInfo
}

export interface LoginRequest {
  username: string
  password: string
}

export function login(data: LoginRequest): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/login', data)
}

export function fetchMe(): Promise<CurrentUserInfo> {
  return get<CurrentUserInfo>('/auth/me')
}

export function logout(): Promise<void> {
  return post<void>('/auth/logout')
}
