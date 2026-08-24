<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { fetchLogLevels, fetchLogs, type LogEntry } from "@/api/log";

const records = ref<LogEntry[]>([]);
const total = ref(0);
const loading = ref(false);
const page = ref(1);
const size = ref(50);
const levels = ref<string[]>([]);

const filters = reactive({
  level: "",
  keyword: "",
});

const levelTag = (level: string): "danger" | "warning" | "info" | "success" => {
  if (level === "ERROR") return "danger";
  if (level === "WARN") return "warning";
  if (level === "DEBUG") return "info";
  return "success";
};

/** 格式化时间戳（ISO 毫秒）为本地字符串。 */
function formatTime(ts: number): string {
  if (!ts) return "--";
  return new Date(ts).toLocaleString("zh-CN", { hour12: false });
}

/** 按当前筛选条件分页加载日志。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await fetchLogs(page.value, size.value, {
      level: filters.level || undefined,
      keyword: filters.keyword || undefined,
    });
    records.value = data.records;
    total.value = data.total;
  } catch {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false;
  }
}

/** 按筛选条件搜索（回到第一页）。 */
function handleSearch(): void {
  page.value = 1;
  void load();
}

/** 清空筛选条件并重新加载。 */
function handleReset(): void {
  filters.level = "";
  filters.keyword = "";
  handleSearch();
}

/** 切换页码并重新加载。 */
function handlePageChange(value: number): void {
  page.value = value;
  void load();
}

/** 自动刷新定时器（30s，与审计页一致）。 */
let timer: ReturnType<typeof setInterval> | undefined;

onMounted(() => {
  void load();
  void fetchLogLevels().then((data) => (levels.value = data));
  timer = setInterval(load, 30000);
});

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="log-view">
    <el-card shadow="never" class="log-view__filter">
      <el-form inline>
        <el-form-item label="级别">
          <el-select
            v-model="filters.level"
            clearable
            placeholder="全部级别"
            style="width: 130px"
          >
            <el-option v-for="lv in levels" :key="lv" :label="lv" :value="lv" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="filters.keyword"
            placeholder="消息 / 记录器"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="log-view__table">
      <el-table v-loading="loading" :data="records" size="small">
        <el-table-column label="时间" width="190">
          <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="thread" label="线程" width="110" show-overflow-tooltip />
        <el-table-column prop="logger" label="记录器" min-width="220" show-overflow-tooltip />
        <el-table-column prop="message" label="消息" min-width="300" show-overflow-tooltip />
      </el-table>

      <el-pagination
        class="log-view__pagination"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<style scoped>
.log-view {
  position: relative;
  z-index: 1;
}

.log-view__filter {
  margin-bottom: 12px;
  border: 1px solid rgba(126, 210, 255, 0.18);
  background: linear-gradient(160deg, rgba(8, 26, 54, 0.72), rgba(4, 16, 38, 0.78));
  backdrop-filter: blur(10px);
  border-radius: 12px;
}

.log-view__table {
  border: 1px solid rgba(126, 210, 255, 0.18);
  background: linear-gradient(160deg, rgba(8, 26, 54, 0.72), rgba(4, 16, 38, 0.78));
  backdrop-filter: blur(10px);
  border-radius: 12px;
}

.log-view__pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.log-view__table :deep(.el-table) {
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

.log-view__table :deep(.el-table__inner-wrapper::before) {
  display: none;
}
</style>
