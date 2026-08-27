<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import type { EChartsOption } from "echarts";
import { fetchOverview, fetchSeries, type HostMetrics, type SeriesPoint } from "@/api/monitor";
import { useECharts } from "@/composables/useECharts";

const overview = ref<HostMetrics | null>(null);
const granularity = ref<"fine" | "coarse">("fine");
const loading = ref(false);
const lastUpdated = ref("");

const cpuRef = ref<HTMLElement | null>(null);
const memRef = ref<HTMLElement | null>(null);
const diskRef = ref<HTMLElement | null>(null);
const netRef = ref<HTMLElement | null>(null);

const { setOption: setCpuOption } = useECharts(cpuRef);
const { setOption: setMemOption } = useECharts(memRef);
const { setOption: setDiskOption } = useECharts(diskRef);
const { setOption: setNetOption } = useECharts(netRef);

let pollTimer: ReturnType<typeof setInterval> | undefined;

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

const formatPercent = (value: number): string => `${value.toFixed(1)}%`;

const cpuPercent = computed(() => overview.value?.cpu ?? 0);
const memPercent = computed(() => {
  const m = overview.value;
  if (!m || m.memTotalKb <= 0) return 0;
  return ((m.memTotalKb - m.memAvailKb) * 100) / m.memTotalKb;
});
const diskPercent = computed(() => {
  const m = overview.value;
  if (!m || m.diskTotalKb <= 0) return 0;
  return ((m.diskTotalKb - m.diskAvailKb) * 100) / m.diskTotalKb;
});
const loadAvg = computed(() =>
  overview.value ? `${overview.value.load1} / ${overview.value.load5} / ${overview.value.load15}` : "--",
);

const granularityLabel = computed(() =>
  granularity.value === "fine" ? "近 2 小时（30s 粒度）" : "近 24 小时（5min 粒度）",
);

/** 渲染单指标趋势图。 */
function renderChart(
  setter: (o: EChartsOption) => void,
  points: SeriesPoint[],
  name: string,
  unit: string,
  color: string,
): void {
  setter({
    tooltip: { trigger: "axis" },
    grid: { left: 44, right: 16, top: 20, bottom: 28 },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: points.map((p) => new Date(p.timestamp).toLocaleTimeString("zh-CN", { hour12: false })),
    },
    yAxis: { type: "value", axisLabel: { formatter: `{value}${unit}` } },
    series: [
      {
        name,
        type: "line",
        smooth: true,
        showSymbol: false,
        data: points.map((p) => p.value),
        lineStyle: { color, width: 2 },
        areaStyle: { color, opacity: 0.12 },
      },
    ],
  });
}

async function loadData(): Promise<void> {
  try {
    overview.value = await fetchOverview();
    lastUpdated.value = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  } catch {
    /* 拦截器已提示 */
  }
  loading.value = true;
  try {
    const g = granularity.value;
    const [cpu, mem, disk, net] = await Promise.all([
      fetchSeries("cpu", g),
      fetchSeries("mem", g),
      fetchSeries("disk", g),
      fetchSeries("net", g),
    ]);
    renderChart(setCpuOption, cpu, "CPU", "%", "#6ec8ff");
    renderChart(setMemOption, mem, "内存", "%", "#67e8a0");
    renderChart(setDiskOption, disk, "磁盘", "%", "#e8d9a8");
    renderChart(setNetOption, net, "网络吞吐", "B", "#f5a3a3");
  } finally {
    loading.value = false;
  }
}

function switchGranularity(): void {
  void loadData();
}

onMounted(() => {
  void loadData();
  pollTimer = setInterval(loadData, 30000);
});

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer);
});
</script>

<template>
  <div class="monitor-view">
    <!-- 实时指标卡 -->
    <el-row :gutter="12">
      <el-col :xs="12" :span="6">
        <div class="monitor-view__stat">
          <div class="monitor-view__stat-label">CPU 使用率</div>
          <div class="monitor-view__stat-value" style="color: #6ec8ff">{{ formatPercent(cpuPercent) }}</div>
          <div class="monitor-view__stat-line" style="background: #6ec8ff" />
        </div>
      </el-col>
      <el-col :xs="12" :span="6">
        <div class="monitor-view__stat">
          <div class="monitor-view__stat-label">内存使用率</div>
          <div class="monitor-view__stat-value" style="color: #67e8a0">{{ formatPercent(memPercent) }}</div>
          <div class="monitor-view__stat-line" style="background: #67e8a0" />
          <div class="monitor-view__stat-extra">可用 {{ formatBytes(overview?.memAvailKb ?? 0) }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :span="6">
        <div class="monitor-view__stat">
          <div class="monitor-view__stat-label">磁盘使用率</div>
          <div class="monitor-view__stat-value" style="color: #e8d9a8">{{ formatPercent(diskPercent) }}</div>
          <div class="monitor-view__stat-line" style="background: #d4af37" />
          <div class="monitor-view__stat-extra">可用 {{ formatBytes(overview?.diskAvailKb ?? 0) }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :span="6">
        <div class="monitor-view__stat">
          <div class="monitor-view__stat-label">系统负载（1/5/15）</div>
          <div class="monitor-view__stat-value" style="color: #f5a3a3; font-size: 22px">{{ loadAvg }}</div>
          <div class="monitor-view__stat-line" style="background: #f5a3a3" />
          <div class="monitor-view__stat-extra">运行 {{ Math.floor((overview?.uptimeSec ?? 0) / 86400) }} 天</div>
        </div>
      </el-col>
    </el-row>

    <!-- 工具栏 -->
    <div class="monitor-view__toolbar">
      <span class="monitor-view__toolbar-title">指标趋势</span>
      <div class="monitor-view__toolbar-right">
        <span class="monitor-view__updated">更新于 {{ lastUpdated || "--" }}</span>
        <el-radio-group v-model="granularity" size="small" @change="switchGranularity">
          <el-radio-button value="fine">近 2 小时</el-radio-button>
          <el-radio-button value="coarse">近 24 小时</el-radio-button>
        </el-radio-group>
        <el-button size="small" @click="loadData"><el-icon><Refresh /></el-icon>刷新</el-button>
      </div>
    </div>

    <!-- 趋势图 -->
    <div v-loading="loading" class="monitor-view__charts">
      <div class="monitor-view__chart-card">
        <div class="monitor-view__chart-title">CPU 使用率（{{ granularityLabel }}）</div>
        <div ref="cpuRef" class="monitor-view__chart" />
      </div>
      <div class="monitor-view__chart-card">
        <div class="monitor-view__chart-title">内存使用率（{{ granularityLabel }}）</div>
        <div ref="memRef" class="monitor-view__chart" />
      </div>
      <div class="monitor-view__chart-card">
        <div class="monitor-view__chart-title">磁盘使用率（{{ granularityLabel }}）</div>
        <div ref="diskRef" class="monitor-view__chart" />
      </div>
      <div class="monitor-view__chart-card">
        <div class="monitor-view__chart-title">网络吞吐（{{ granularityLabel }}）</div>
        <div ref="netRef" class="monitor-view__chart" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.monitor-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-family: var(--gc-font-sans, inherit);
}

.monitor-view__stat {
  background: rgba(7, 22, 46, 0.7);
  border: 1px solid rgba(140, 220, 255, 0.2);
  border-radius: 12px;
  padding: 14px 16px;
  backdrop-filter: blur(8px);
}

.monitor-view__stat-label {
  font-size: 12px;
  letter-spacing: 2px;
  color: #8fb6dd;
}

.monitor-view__stat-value {
  font-size: 26px;
  font-weight: 600;
  color: #eaf6ff;
  font-variant-numeric: tabular-nums;
  margin: 4px 0 8px;
}

.monitor-view__stat-line {
  height: 2px;
  width: 60px;
  background: linear-gradient(90deg, #6ec8ff, transparent);
  border-radius: 1px;
}

.monitor-view__stat-extra {
  margin-top: 6px;
  font-size: 11px;
  color: #8fb6dd;
}

.monitor-view__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(7, 22, 46, 0.6);
  border: 1px solid rgba(140, 220, 255, 0.18);
  border-radius: 12px;
  padding: 10px 14px;
  backdrop-filter: blur(8px);
}

.monitor-view__toolbar-title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  color: #eaf6ff;
}

.monitor-view__toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.monitor-view__updated {
  font-size: 12px;
  color: #8fb6dd;
}

.monitor-view__charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.monitor-view__chart-card {
  background: rgba(7, 22, 46, 0.6);
  border: 1px solid rgba(140, 220, 255, 0.16);
  border-radius: 12px;
  padding: 12px 14px;
  backdrop-filter: blur(8px);
}

.monitor-view__chart-title {
  font-size: 13px;
  font-weight: 500;
  color: #bfdcf8;
  margin-bottom: 6px;
}

.monitor-view__chart {
  height: 220px;
}

/* ---------- 移动端适配 ---------- */
@media (max-width: 768px) {
  .monitor-view__charts {
    grid-template-columns: 1fr;
  }

  .monitor-view__stat-value {
    font-size: 18px;
  }
}
</style>
