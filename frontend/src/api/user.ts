import { del, get, post, put } from '@/utils/http'

/** 用户视图（与后端 UserVO 对应）。 */
export interface UserVO {
  id: number
  username: string
  uid: number | null
  displayName: string
  email: string | null
  enabled: boolean
  roleId: number
  roleCode: string
  roleName: string
  homePath: string | null
  quotaBytes: number | null
  createdAt: string | null
  updatedAt: string | null
}

export interface RoleVO {
  id: number
  code: string
  name: string
  description: string | null
  permissionCodes: string[]
}

export interface PermissionVO {
  id: number
  code: string
  name: string
  type: string
  resource: string | null
}

export interface UserCreateRequest {
  username: string
  displayName: string
  email?: string
  password: string
  roleId: number
  quotaBytes?: number | null
}

export interface UserUpdateRequest {
  displayName: string
  email?: string
  roleId: number
  quotaBytes?: number | null
}

export function fetchUsers(page: number, size: number, keyword?: string): Promise<UserVO[]> {
  return get<UserVO[]>('/users', { page, size, keyword: keyword || undefined })
}

export function createUser(data: UserCreateRequest): Promise<UserVO> {
  return post<UserVO>('/users', data)
}

export function updateUser(username: string, data: UserUpdateRequest): Promise<UserVO> {
  return put<UserVO>(`/users/${username}`, data)
}

export function setUserStatus(username: string, enabled: boolean): Promise<UserVO> {
  return put<UserVO>(`/users/${username}/status`, { enabled })
}

export function resetUserPassword(username: string, password: string): Promise<void> {
  return put<void>(`/users/${username}/password`, { password })
}

export function deleteUser(username: string, removeHome = false): Promise<void> {
  return del<void>(`/users/${username}`, { removeHome })
}

export function fetchRoles(): Promise<RoleVO[]> {
  return get<RoleVO[]>('/roles')
}

export function createRole(data: {
  code: string
  name: string
  description?: string
  permissionIds: number[]
}): Promise<RoleVO> {
  return post<RoleVO>('/roles', data)
}

export function updateRole(id: number, data: { code: string; name: string; description?: string; permissionIds: number[] }): Promise<RoleVO> {
  return put<RoleVO>(`/roles/${id}`, data)
}

export function deleteRole(id: number): Promise<void> {
  return del<void>(`/roles/${id}`)
}

export function fetchPermissions(): Promise<PermissionVO[]> {
  return get<PermissionVO[]>('/permissions')
}
