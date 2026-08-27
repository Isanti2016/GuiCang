<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { fetchAuditLogs, type AuditLog } from "@/api/audit";

const records = ref<AuditLog[]>([]);
const total = ref(0);
const loading = ref(false);
const page = ref(1);
const size = ref(20);

const filters = reactive({
  username: "",
  action: "",
  result: "",
});

const actionLabels: Record<string, string> = {
  login: "登录",
  "setup.init": "系统初始化",
  "user.create": "新建用户",
  "user.delete": "删除用户",
  "user.status": "用户状态",
  "user.password": "重置密码",
  "role.create": "新建角色",
  "role.update": "编辑角色",
  "role.delete": "删除角色",
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
  "sync.create": "新建同步任务",
  "sync.update": "编辑同步任务",
  "sync.delete": "删除同步任务",
};

/** 动作码转中文标签。 */
const actionLabel = (action: string): string => actionLabels[action] ?? action;

/** 对象列友好显示：纯数字（回收站条目 id）转可读文本。 */
function resourceLabel(resource: string | null): string {
  if (!resource) return "--";
  if (/^\d+$/.test(resource)) {
    return `回收站条目 #${resource}`;
  }
  return resource;
}

/** 按当前筛选条件分页加载审计日志。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await fetchAuditLogs(page.value, size.value, {
      username: filters.username || undefined,
      action: filters.action || undefined,
      result: filters.result || undefined,
    });
    records.value = data.records;
    total.value = data.total;
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
  filters.username = "";
  filters.action = "";
  filters.result = "";
  handleSearch();
}

/** 切换页码并重新加载。 */
function handlePageChange(value: number): void {
  page.value = value;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="audit-view">
    <el-card shadow="never" class="audit-view__filter">
      <el-form inline>
        <el-form-item label="用户名">
          <el-input
            v-model="filters.username"
            placeholder="操作者"
            clearable
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="动作">
          <el-select
            v-model="filters.action"
            clearable
            filterable
            placeholder="全部动作"
            style="width: 200px"
          >
            <el-option
              v-for="(label, code) in actionLabels"
              :key="code"
              :label="`${label}（${code}）`"
              :value="code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select
            v-model="filters.result"
            clearable
            placeholder="全部"
            style="width: 110px"
          >
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="records" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column label="动作" width="130">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ resourceLabel(row.resource) }}</template>
        </el-table-column>
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
        <el-table-column prop="ip" label="来源 IP" width="140" />
        <el-table-column label="时间" width="180">
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

      <el-pagination
        class="audit-view__pagination"
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
.audit-view__filter {
  margin-bottom: 12px;
}

.audit-view__pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
/* ---------- 移动端适配 ---------- */
@media (max-width: 768px) {
  .el-form--inline .el-form-item {
    margin-right: 0;
    display: flex;
    flex: 1 1 auto;
  }

  .el-pagination {
    flex-wrap: wrap;
    justify-content: center;
  }

  .el-card__body {
    padding: 12px;
  }
}

</style>
