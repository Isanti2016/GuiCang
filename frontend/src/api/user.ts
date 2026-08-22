import { del, get, post, put } from "@/utils/http";

/** 用户视图（与后端 UserVO 对应）。 */
export interface UserVO {
  id: number;
  username: string;
  uid: number | null;
  displayName: string;
  email: string | null;
  enabled: boolean;
  roleId: number;
  roleCode: string;
  roleName: string;
  homePath: string | null;
  quotaBytes: number | null;
  createdAt: string | null;
  updatedAt: string | null;
}

/** 角色视图（与后端 RoleVO 对应）。 */
export interface RoleVO {
  id: number;
  code: string;
  name: string;
  description: string | null;
  permissionCodes: string[];
  userCount: number;
}

/** 权限视图（与后端 PermissionVO 对应）。 */
export interface PermissionVO {
  id: number;
  code: string;
  name: string;
  type: string;
  resource: string | null;
}

/** 新建用户请求体。 */
export interface UserCreateRequest {
  username: string;
  displayName: string;
  email?: string;
  password: string;
  roleId: number;
  quotaBytes?: number | null;
}

/** 更新用户请求体。 */
export interface UserUpdateRequest {
  displayName: string;
  email?: string;
  roleId: number;
  quotaBytes?: number | null;
}

/** 用户分页结果。 */
export interface UserPage {
  records: UserVO[];
  total: number;
}

/** 分页查询用户（可按关键字/启用状态过滤）。 */
export function fetchUsers(
  page: number,
  size: number,
  keyword?: string,
  enabled?: boolean,
): Promise<UserPage> {
  return get<UserPage>("/users", {
    page,
    size,
    keyword: keyword || undefined,
    enabled: enabled === undefined ? undefined : enabled ? 1 : 0,
  });
}

/** 新建用户。 */
export function createUser(data: UserCreateRequest): Promise<UserVO> {
  return post<UserVO>("/users", data);
}

/** 更新用户资料。 */
export function updateUser(
  username: string,
  data: UserUpdateRequest,
): Promise<UserVO> {
  return put<UserVO>(`/users/${username}`, data);
}

/** 启用/禁用用户。 */
export function setUserStatus(
  username: string,
  enabled: boolean,
): Promise<UserVO> {
  return put<UserVO>(`/users/${username}/status`, { enabled });
}

/** 重置用户密码。 */
export function resetUserPassword(
  username: string,
  password: string,
): Promise<void> {
  return put<void>(`/users/${username}/password`, { password });
}

/** 删除用户（可同时移除家目录）。 */
export function deleteUser(
  username: string,
  removeHome = false,
): Promise<void> {
  return del<void>(`/users/${username}`, { removeHome });
}

/** 查询全部角色。 */
export function fetchRoles(): Promise<RoleVO[]> {
  return get<RoleVO[]>("/roles");
}

/** 新建角色。 */
export function createRole(data: {
  code: string;
  name: string;
  description?: string;
  permissionIds: number[];
}): Promise<RoleVO> {
  return post<RoleVO>("/roles", data);
}

/** 更新角色（含权限点）。 */
export function updateRole(
  id: number,
  data: {
    code: string;
    name: string;
    description?: string;
    permissionIds: number[];
  },
): Promise<RoleVO> {
  return put<RoleVO>(`/roles/${id}`, data);
}

/** 删除角色。 */
export function deleteRole(id: number): Promise<void> {
  return del<void>(`/roles/${id}`);
}

/** 查询全部权限点。 */
export function fetchPermissions(): Promise<PermissionVO[]> {
  return get<PermissionVO[]>("/permissions");
}
