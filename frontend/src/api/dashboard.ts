import { get } from '@/utils/http'

/** 大屏聚合数据（与后端 DashboardSummary 对应）。 */
export interface RecentOperation {
  id: number
  username: string | null
  action: string
  resource: string | null
  result: string
  createdAt: number | null
}

export interface DashboardSummary {
  diskTotalKb: number
  diskAvailKb: number
  diskUsedPercent: number
  fileTotal: number
  fileDirs: number
  fileImages: number
  fileVideos: number
  fileNotes: number
  userTotal: number
  userEnabled: number
  recentOperations: RecentOperation[]
}

export function fetchDashboardSummary(): Promise<DashboardSummary> {
  return get<DashboardSummary>('/dashboard/summary')
}
