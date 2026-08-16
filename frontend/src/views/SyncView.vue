<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createTask,
  deleteTask,
  fetchHistory,
  fetchTasks,
  runTask,
  updateTask,
  type SyncHistory,
  type SyncTask,
} from '@/api/sync'

const tasks = ref<SyncTask[]>([])
const loading = ref(false)

const dialogOpen = ref(false)
const editing = ref<SyncTask | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', sourceConfig: '', cron: '' })

const rules: FormRules = {
  name: [{ required: true, message: '请输入任务名', trigger: 'blur' }],
  sourceConfig: [{ required: true, message: '请输入源目录', trigger: 'blur' }],
  cron: [{ required: true, message: '请输入 cron 表达式', trigger: 'blur' }],
}

// 历史
const historyOpen = ref(false)
const history = ref<SyncHistory[]>([])
const historyLoading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    tasks.value = await fetchTasks()
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  form.name = ''
  form.sourceConfig = ''
  form.cron = '0 0 3 * * ?'
  dialogOpen.value = true
}

function openEdit(task: SyncTask): void {
  editing.value = task
  form.name = task.name
  form.sourceConfig = task.sourceConfig
  form.cron = task.cron
  dialogOpen.value = true
}

async function handleSave(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (editing.value) {
    await updateTask(editing.value.id, true, { ...form })
    ElMessage.success('已更新')
  } else {
    await createTask({ ...form })
    ElMessage.success('已创建')
  }
  dialogOpen.value = false
  await load()
}

async function handleToggle(task: SyncTask): Promise<void> {
  await updateTask(task.id, task.enabled === 0, { name: task.name, sourceConfig: task.sourceConfig, cron: task.cron })
  ElMessage.success(task.enabled === 0 ? '已启用' : '已停用')
  await load()
}

async function handleDelete(task: SyncTask): Promise<void> {
  await ElMessageBox.confirm(`确认删除任务「${task.name}」？`, '删除确认', { type: 'warning' })
  await deleteTask(task.id)
  ElMessage.success('已删除')
  await load()
}

async function handleRun(task: SyncTask): Promise<void> {
  const result = await runTask(task.id)
  ElMessage.success(`执行完成：新增 ${result.added} / 更新 ${result.updated} / 删除 ${result.deleted}`)
  await load()
}

async function openHistory(task: SyncTask): Promise<void> {
  historyOpen.value = true
  historyLoading.value = true
  try {
    history.value = await fetchHistory(task.id, 50)
  } finally {
    historyLoading.value = false
  }
}

const formatTime = (ts: number | null): string =>
  ts ? new Date(ts).toLocaleString('zh-CN', { hour12: false }) : '--'

const statusLabel = (status: string): string =>
  ({ running: '执行中', success: '成功', failed: '失败' })[status] ?? status

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="sync-view">
    <el-card shadow="never">
      <template #header>
        <div class="sync-view__header">
          <span>同步任务</span>
          <el-button type="primary" size="small" @click="openCreate">新建任务</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tasks" size="small">
        <el-table-column prop="name" label="任务名" min-width="140" />
        <el-table-column prop="sourceConfig" label="源目录" min-width="160" />
        <el-table-column prop="cron" label="Cron" width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上次执行" width="160">
          <template #default="{ row }">
            <span>{{ formatTime(row.lastRunAt) }}</span>
            <el-tag v-if="row.lastStatus" size="small" :type="row.lastStatus === 'success' ? 'success' : 'danger'" style="margin-left: 4px">
              {{ statusLabel(row.lastStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleRun(row)">立即执行</el-button>
            <el-button link type="primary" size="small" @click="openHistory(row)">历史</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="handleToggle(row)">
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogOpen" :title="editing ? '编辑任务' : '新建任务'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="任务名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="源目录" prop="sourceConfig">
          <el-input v-model="form.sourceConfig" placeholder="如 media/photos（存储根下相对路径）" />
        </el-form-item>
        <el-form-item label="Cron" prop="cron">
          <el-input v-model="form.cron" placeholder="Quartz 6 段，如 0 0 3 * * ?" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyOpen" title="执行历史" width="640px">
      <el-table v-loading="historyLoading" :data="history" size="small">
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : row.status === 'failed' ? 'danger' : 'warning'" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="added" label="新增" width="70" />
        <el-table-column prop="updated" label="更新" width="70" />
        <el-table-column prop="deleted" label="删除" width="70" />
        <el-table-column prop="error" label="错误" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.sync-view__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
