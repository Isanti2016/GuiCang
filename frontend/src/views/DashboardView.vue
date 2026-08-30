<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import { fetchDashboardSummary, type DashboardSummary } from "@/api/dashboard";
import {
  fetchOverview,
  fetchSeries,
  type HostMetrics,
  type SeriesPoint,
} from "@/api/monitor";
import { useECharts } from "@/composables/useECharts";

const summary = ref<DashboardSummary | null>(null);
const overview = ref<HostMetrics | null>(null);

const trendRef = ref<HTMLElement | null>(null);
const diskRef = ref<HTMLElement | null>(null);
const fileTypeRef = ref<HTMLElement | null>(null);
const userStorageRef = ref<HTMLElement | null>(null);
const { setOption: setTrendOption } = useECharts(trendRef);
const { setOption: setDiskOption } = useECharts(diskRef);
const { setOption: setFileTypeOption, clear: clearFileType } =
  useECharts(fileTypeRef);
const { setOption: setUserStorageOption, clear: clearUserStorage } =
  useECharts(userStorageRef);

/** 格式化 KB 为人类可读大小。 */
const formatBytes = (kb: number): string => {
  if (kb <= 0) return "0 B";
  const units = ["KB", "MB", "GB", "TB"];
  let value = kb;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(1)} ${units[unit]}`;
};

/** 保留一位小数的百分比。 */
const formatPercent = (value: number): string => `${value.toFixed(1)}%`;

const diskTotal = computed(() => formatBytes(summary.value?.diskTotalKb ?? 0));
const diskAvail = computed(() => formatBytes(summary.value?.diskAvailKb ?? 0));
const diskUsedPercent = computed(() => summary.value?.diskUsedPercent ?? 0);
const memPercent = computed(() => {
  const m = overview.value;
  if (!m || m.memTotalKb <= 0) return 0;
  return ((m.memTotalKb - m.memAvailKb) * 100) / m.memTotalKb;
});
const cpuPercent = computed(() => overview.value?.cpu ?? 0);
const loadAvg = computed(() =>
  overview.value
    ? `${overview.value.load1} / ${overview.value.load5} / ${overview.value.load15}`
    : "--",
);

/** 渲染 CPU 使用率趋势折线图。 */
function renderTrend(points: SeriesPoint[]): void {
  setTrendOption({
    tooltip: { trigger: "axis" },
    grid: { left: 40, right: 16, top: 16, bottom: 28 },
    xAxis: {
      type: "category",
      data: points.map((p) =>
        new Date(p.timestamp).toLocaleTimeString("zh-CN", { hour12: false }),
      ),
    },
    yAxis: { type: "value", axisLabel: { formatter: "{value}%" } },
    series: [
      {
        name: "CPU 使用率",
        type: "line",
        smooth: true,
        data: points.map((p) => p.value),
        areaStyle: { opacity: 0.15 },
      },
    ],
  });
}

/** 渲染磁盘占用环形图。 */
function renderDisk(): void {
  const s = summary.value;
  if (!s) return;
  setDiskOption({
    tooltip: { trigger: "item", formatter: "{b}: {c}%" },
    series: [
      {
        type: "pie",
        radius: ["55%", "75%"],
        label: { formatter: "{b}: {c}%" },
        data: [
          { name: "已用", value: s.diskUsedPercent },
          { name: "可用", value: 100 - s.diskUsedPercent },
        ],
        color: ["#409eff", "#67c23a"],
      },
    ],
  });
}

/** 渲染文件类型分布饼图。 */
function renderFileType(): void {
  const s = summary.value;
  if (!s) return;
  const data = [
    { name: "图片", value: s.fileImages },
    { name: "视频", value: s.fileVideos },
    { name: "文档", value: s.fileNotes },
    {
      name: "其他",
      value: Math.max(
        0,
        s.fileTotal - s.fileImages - s.fileVideos - s.fileNotes,
      ),
    },
  ].filter((d) => d.value > 0);
  if (data.length === 0) {
    clearFileType();
    return;
  }
  setFileTypeOption({
    tooltip: { trigger: "item", formatter: "{b}: {c} 个 ({d}%)" },
    legend: { bottom: 0, textStyle: { color: "#9fc6ea" } },
    series: [
      {
        type: "pie",
        radius: ["42%", "68%"],
        center: ["50%", "44%"],
        itemStyle: { borderColor: "rgba(6,18,40,0.9)", borderWidth: 2 },
        label: { color: "#bfe9ff" },
        data,
        color: ["#409eff", "#d4af37", "#67c23a", "#9fc6ea"],
      },
    ],
  });
}

/** 渲染用户存储占用条图（取占用前 10）。 */
function renderUserStorage(): void {
  const list = summary.value?.userStorage ?? [];
  if (list.length === 0) {
    clearUserStorage();
    return;
  }
  const sorted = [...list].sort((a, b) => b.bytes - a.bytes).slice(0, 10);
  setUserStorageOption({
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: unknown) => {
        const arr = Array.isArray(params) ? params : [params];
        const p = arr[0] as { name?: string; value?: number };
        return `${p.name ?? ""}<br/>占用 ${formatBytes((p.value ?? 0) / 1024)} · ${list.find((u) => u.username === p.name)?.fileCount ?? 0} 个文件`;
      },
    },
    grid: { left: 50, right: 16, top: 16, bottom: 32 },
    xAxis: {
      type: "category",
      data: sorted.map((u) => u.username),
      axisLabel: { color: "#9fc6ea" },
      axisLine: { lineStyle: { color: "rgba(126,210,255,0.3)" } },
    },
    yAxis: {
      type: "value",
      axisLabel: {
        color: "#9fc6ea",
        formatter: (v: number) => formatBytes(v),
      },
      splitLine: { lineStyle: { color: "rgba(126,210,255,0.12)" } },
    },
    series: [
      {
        type: "bar",
        barWidth: "46%",
        data: sorted.map((u) => u.bytes / 1024),
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: "linear",
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: "#6ec8ff" },
              { offset: 1, color: "#2b6fd4" },
            ],
          },
        },
      },
    ],
  });
}

/** 拉取大屏聚合数据并渲染全部图表（30s 轮询）。 */
async function loadData(): Promise<void> {
  try {
    summary.value = await fetchDashboardSummary();
    renderDisk();
    renderFileType();
    renderUserStorage();
  } catch {
    // 错误提示由拦截器处理
  }
  try {
    overview.value = await fetchOverview();
  } catch {
    // 忽略
  }
  try {
    const points = await fetchSeries("cpu", "fine");
    renderTrend(points);
  } catch {
    // 忽略
  }
}

void loadData();
const pollTimer = setInterval(loadData, 30000);

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer);
});

const recentOperations = computed(() => summary.value?.recentOperations ?? []);
/** 动作码转中文标签。 */
const actionLabel = (action: string): string => {
  const labels: Record<string, string> = {
    login: "登录",
    "setup.init": "系统初始化",
    "user.create": "新建用户",
    "user.delete": "删除用户",
    "user.status": "用户状态",
    "user.password": "重置密码",
    "file.upload": "上传",
    "file.write": "编辑文件",
    "file.mkdir": "新建目录",
    "file.rename": "重命名",
    "file.move": "移动",
    "file.delete": "删除",
    "file.restore": "恢复",
    "file.purge": "彻底删除",
    "file.trash.empty": "清空回收站",
    "file.trash.auto": "自动清理回收站",
  };
  return labels[action] ?? action;
};
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="12">
      <el-col :xs="12" :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__icon stat-card__icon--disk">
            <el-icon><Coin /></el-icon>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__label">磁盘已用</div>
            <div class="stat-card__value">{{ formatPercent(diskUsedPercent) }}</div>
            <div class="stat-card__extra">
              已用
              {{
                formatBytes(
                  summary?.diskTotalKb
                    ? summary.diskTotalKb - summary.diskAvailKb
                    : 0,
                )
              }}
              / 共 {{ diskTotal }} · 可用 {{ diskAvail }}
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__icon stat-card__icon--cpu">
            <el-icon><Cpu /></el-icon>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__label">CPU 使用率</div>
            <div class="stat-card__value">{{ formatPercent(cpuPercent) }}</div>
            <div class="stat-card__extra">负载 {{ loadAvg }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__icon stat-card__icon--mem">
            <el-icon><Odometer /></el-icon>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__label">内存使用率</div>
            <div class="stat-card__value">{{ formatPercent(memPercent) }}</div>
            <div class="stat-card__extra">
              可用 {{ formatBytes(overview?.memAvailKb ?? 0) }}
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-card__icon stat-card__icon--file">
            <el-icon><Folder /></el-icon>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__label">文件 / 用户</div>
            <div class="stat-card__value">
              {{ summary?.fileTotal ?? 0 }} / {{ summary?.userTotal ?? 0 }}
            </div>
            <div class="stat-card__extra">
              图片 {{ summary?.fileImages ?? 0 }} · 视频
              {{ summary?.fileVideos ?? 0 }} · 文档
              {{ summary?.fileNotes ?? 0 }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="dashboard__charts">
      <el-col :xs="24" :span="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="chart-card__title">CPU 使用率趋势</span>
            <span class="chart-card__sub">30s 采样 · 近 2 小时</span>
          </template>
          <div ref="trendRef" class="dashboard__chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :span="8">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="chart-card__title">磁盘占用</span>
            <span class="chart-card__sub">已用 / 可用</span>
          </template>
          <div ref="diskRef" class="dashboard__chart" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="dashboard__charts">
      <el-col :xs="24" :span="8">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="chart-card__title">文件类型分布</span>
            <span class="chart-card__sub">图片 / 视频 / 文档</span>
          </template>
          <div ref="fileTypeRef" class="dashboard__chart" />
        </el-card>
      </el-col>
      <el-col :xs="24" :span="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="chart-card__title">用户存储占用</span>
            <span class="chart-card__sub">按用户聚合（前 10）</span>
          </template>
          <div ref="userStorageRef" class="dashboard__chart" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="chart-card dashboard__ops">
      <template #header>
        <span class="chart-card__title">最近操作</span>
        <span class="chart-card__sub">实时审计动态</span>
      </template>
      <el-table :data="recentOperations" size="small" max-height="100%">
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column prop="resource" label="对象" show-overflow-tooltip />
        <el-table-column prop="result" label="结果" width="80">
          <template #default="{ row }">
            <el-tag
              :type="row.result === 'success' ? 'success' : 'danger'"
              size="small"
            >
              {{ row.result === "success" ? "成功" : "失败" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            {{
              row.createdAt
                ? new Date(row.createdAt).toLocaleString("zh-CN", {
                    hour12: false,
                  })
                : "--"
            }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 92px);
  overflow: hidden;
}

.stat-card {
  border: 1px solid rgba(126, 210, 255, 0.18);
  background: linear-gradient(160deg, rgba(8, 26, 54, 0.72), rgba(4, 16, 38, 0.78));
  backdrop-filter: blur(10px);
  border-radius: 12px;
  overflow: hidden;
  transition:
    transform 0.25s ease,
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: rgba(212, 175, 55, 0.5);
  box-shadow: 0 8px 24px rgba(2, 10, 26, 0.6);
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}

.stat-card__icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  font-size: 22px;
  color: #bfe9ff;
  border: 1px solid rgba(126, 210, 255, 0.25);
  background: rgba(110, 200, 255, 0.08);
}

.stat-card__icon--disk {
  color: #6ec8ff;
  background: linear-gradient(135deg, rgba(110, 200, 255, 0.16), rgba(43, 111, 212, 0.08));
}

.stat-card__icon--cpu {
  color: #d4af37;
  background: linear-gradient(135deg, rgba(212, 175, 55, 0.18), rgba(212, 175, 55, 0.06));
}

.stat-card__icon--mem {
  color: #67e0a3;
  background: linear-gradient(135deg, rgba(103, 224, 163, 0.16), rgba(103, 224, 163, 0.05));
}

.stat-card__icon--file {
  color: #c9a8ff;
  background: linear-gradient(135deg, rgba(201, 168, 255, 0.16), rgba(201, 168, 255, 0.05));
}

.stat-card__body {
  min-width: 0;
}

.stat-card__label {
  color: rgba(159, 198, 234, 0.8);
  font-size: 11px;
  letter-spacing: 1px;
}

.stat-card__value {
  font-size: 21px;
  font-weight: 600;
  margin: 2px 0 1px;
  color: #eaf6ff;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.stat-card__extra {
  color: rgba(159, 198, 234, 0.65);
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dashboard__charts {
  margin: 10px 0 0;
  flex-shrink: 0;
}

.chart-card {
  border: 1px solid rgba(126, 210, 255, 0.18);
  background: linear-gradient(160deg, rgba(8, 26, 54, 0.72), rgba(4, 16, 38, 0.78));
  backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(2, 10, 26, 0.55);
}

.chart-card :deep(.el-card__header) {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  border-bottom: 1px solid rgba(212, 175, 55, 0.22);
  padding: 8px 16px;
}

.chart-card__title {
  font-size: 14px;
  font-weight: 600;
  color: #eaf6ff;
  letter-spacing: 1px;
}

.chart-card__sub {
  font-size: 11px;
  color: rgba(159, 198, 234, 0.6);
}

.dashboard__chart {
  height: 172px;
}

.dashboard__ops {
  margin-top: 10px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.dashboard__ops :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 8px 12px;
}

.dashboard__ops :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(110, 200, 255, 0.08);
  --el-table-header-text-color: #bfe9ff;
  --el-table-border-color: rgba(126, 210, 255, 0.12);
  --el-table-row-hover-bg-color: rgba(110, 200, 255, 0.08);
  --el-table-text-color: #cfe7f8;
  --el-table-expanded-cell-bg-color: transparent;
  background: transparent;
}

.dashboard__ops :deep(.el-table__inner-wrapper::before) {
  display: none;
}
</style>
