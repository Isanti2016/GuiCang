<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { fetchDashboardSummary, type DashboardSummary } from '@/api/dashboard'
import { fetchOverview, fetchSeries, type HostMetrics, type SeriesPoint } from '@/api/monitor'
import { useECharts } from '@/composables/useECharts'

const summary = ref<DashboardSummary | null>(null)
const overview = ref<HostMetrics | null>(null)

const trendRef = ref<HTMLElement | null>(null)
const diskRef = ref<HTMLElement | null>(null)
const { setOption: setTrendOption } = useECharts(trendRef)
const { setOption: setDiskOption } = useECharts(diskRef)


const formatBytes = (kb: number): string => {
  if (kb <= 0) return '0 B'
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = kb
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return `${value.toFixed(1)} ${units[unit]}`
}

const formatPercent = (value: number): string => `${value.toFixed(1)}%`

const diskTotal = computed(() => formatBytes(summary.value?.diskTotalKb ?? 0))
const diskAvail = computed(() => formatBytes(summary.value?.diskAvailKb ?? 0))
const diskUsedPercent = computed(() => summary.value?.diskUsedPercent ?? 0)
const memPercent = computed(() => {
  const m = overview.value
  if (!m || m.memTotalKb <= 0) return 0
  return ((m.memTotalKb - m.memAvailKb) * 100) / m.memTotalKb
})
const cpuPercent = computed(() => overview.value?.cpu ?? 0)
const loadAvg = computed(() => (overview.value ? `${overview.value.load1} / ${overview.value.load5} / ${overview.value.load15}` : '--'))

function renderTrend(points: SeriesPoint[]): void {
  setTrendOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 16, bottom: 28 },
    xAxis: {
      type: 'category',
      data: points.map((p) => new Date(p.timestamp).toLocaleTimeString('zh-CN', { hour12: false })),
    },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
    series: [
      {
        name: 'CPU 使用率',
        type: 'line',
        smooth: true,
        data: points.map((p) => p.value),
        areaStyle: { opacity: 0.15 },
      },
    ],
  })
}

function renderDisk(): void {
  const s = summary.value
  if (!s) return
  setDiskOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}%' },
    series: [
      {
        type: 'pie',
        radius: ['55%', '75%'],
        label: { formatter: '{b}: {c}%' },
        data: [
          { name: '已用', value: s.diskUsedPercent },
          { name: '可用', value: 100 - s.diskUsedPercent },
        ],
        color: ['#409eff', '#67c23a'],
      },
    ],
  })
}

async function loadData(): Promise<void> {
  try {
    summary.value = await fetchDashboardSummary()
    renderDisk()
  } catch {
    // 错误提示由拦截器处理
  }
  try {
    overview.value = await fetchOverview()
  } catch {
    // 忽略
  }
  try {
    const points = await fetchSeries('cpu', 'fine')
    renderTrend(points)
  } catch {
    // 忽略
  }
}

void loadData()
const pollTimer = setInterval(loadData, 30000)

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})

const recentOperations = computed(() => summary.value?.recentOperations ?? [])
const actionLabel = (action: string): string => {
  const labels: Record<string, string> = {
    login: '登录',
    'setup.init': '系统初始化',
    'user.create': '新建用户',
    'user.delete': '删除用户',
    'user.status': '用户状态',
    'user.password': '重置密码',
    'file.upload': '上传',
    'file.write': '编辑文件',
    'file.mkdir': '新建目录',
    'file.rename': '重命名',
    'file.move': '移动',
    'file.delete': '删除',
  }
  return labels[action] ?? action
}
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-card__label">磁盘已用</div>
            <div class="stat-card__value">{{ formatPercent(diskUsedPercent) }}</div>
            <div class="stat-card__extra">
              已用 {{ formatBytes(summary?.diskTotalKb ? summary.diskTotalKb - summary.diskAvailKb : 0) }}
              / 共 {{ diskTotal }} · 可用 {{ diskAvail }}
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-card__label">CPU 使用率</div>
            <div class="stat-card__value">{{ formatPercent(cpuPercent) }}</div>
            <div class="stat-card__extra">负载 {{ loadAvg }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-card__label">内存使用率</div>
            <div class="stat-card__value">{{ formatPercent(memPercent) }}</div>
            <div class="stat-card__extra">可用 {{ formatBytes(overview?.memAvailKb ?? 0) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-card__label">文件 / 用户</div>
            <div class="stat-card__value">
              {{ summary?.fileTotal ?? 0 }} / {{ summary?.userTotal ?? 0 }}
            </div>
            <div class="stat-card__extra">
              图片 {{ summary?.fileImages ?? 0 }} · 视频 {{ summary?.fileVideos ?? 0 }} · 文档 {{ summary?.fileNotes ?? 0 }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="dashboard__charts">
      <el-col :span="16">
        <el-card shadow="hover" header="CPU 使用率趋势（30s）">
          <div ref="trendRef" class="dashboard__chart" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" header="磁盘占用">
          <div ref="diskRef" class="dashboard__chart" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" header="最近操作">
      <el-table :data="recentOperations" size="small">
        <el-table-column prop="username" label="用户" width="140" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column prop="resource" label="对象" show-overflow-tooltip />
        <el-table-column prop="result" label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'success' ? 'success' : 'danger'" size="small">
              {{ row.result === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString('zh-CN', { hour12: false }) : '--' }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.stat-card__label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.stat-card__value {
  font-size: 26px;
  font-weight: 600;
  margin: 8px 0 4px;
}

.stat-card__extra {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.dashboard__charts {
  margin: 16px 0;
}

.dashboard__chart {
  height: 300px;
}
</style>
