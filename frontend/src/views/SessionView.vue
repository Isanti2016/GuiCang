<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { listSessions, revokeSession, type SessionVO } from "@/api/session";

const sessions = ref<SessionVO[]>([]);
const loading = ref(false);

async function load(): Promise<void> {
  loading.value = true;
  try {
    sessions.value = await listSessions();
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false;
  }
}

function formatTime(ts: number): string {
  const d = new Date(ts);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

async function handleRevoke(s: SessionVO): Promise<void> {
  await ElMessageBox.confirm("确认踢下线该会话？该设备将立即退出登录。", "踢下线", {
    type: "warning",
  });
  await revokeSession(s.id);
  ElMessage.success("已踢下线");
  await load();
}

onMounted(load);
</script>

<template>
  <div class="session-page">
    <el-card shadow="never">
      <template #header>
        <div class="session-page__header">
          <span>登录会话</span>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="sessions" size="small">
        <el-table-column label="IP 地址" width="170">
          <template #default="{ row }">{{ row.ip || "-" }}</template>
        </el-table-column>
        <el-table-column
          label="设备 / 浏览器"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.userAgent || "-" }}</template>
        </el-table-column>
        <el-table-column label="登录时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              size="small"
              @click="handleRevoke(row)"
              >踢下线</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.session-page {
  padding: 16px;
}
.session-page__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
