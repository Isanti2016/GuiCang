import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一返回体（与 Result<T> 对应）。 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

const TOKEN_KEY = 'guicang.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
})

// 请求拦截：自动带 Bearer token
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一错误提示；未登录跳登录页
http.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      if (body.code === 401) {
        clearToken()
        redirectToLogin()
      }
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      clearToken()
      redirectToLogin()
      ElMessage.error('未登录或登录已过期')
    } else if (status === 403) {
      ElMessage.error('无权限访问')
    } else if (status >= 500) {
      ElMessage.error('服务器繁忙，请稍后重试')
    }
    return Promise.reject(error)
  },
)

function redirectToLogin(): void {
  if (!window.location.pathname.startsWith('/login')) {
    window.location.href = '/login'
  }
}

/** GET 请求返回 data。 */
export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await http.get<ApiResult<T>>(url, { params })
  return response.data.data
}

/** POST 请求返回 data。 */
export async function post<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.post<ApiResult<T>>(url, body)
  return response.data.data
}

/** PUT 请求返回 data。 */
export async function put<T>(url: string, body?: unknown, params?: Record<string, unknown>): Promise<T> {
  const response = await http.put<ApiResult<T>>(url, body, { params })
  return response.data.data
}

/** DELETE 请求返回 data。 */
export async function del<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await http.delete<ApiResult<T>>(url, { params })
  return response.data.data
}

/** multipart 上传。 */
export async function uploadFile<T>(url: string, file: File, params?: Record<string, unknown>): Promise<T> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiResult<T>>(url, formData, {
    params,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 0,
  })
  return response.data.data
}

export default http
