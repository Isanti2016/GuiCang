<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import cronstrue from "cronstrue";
import {
  createTask,
  deleteTask,
  fetchHistory,
  fetchTasks,
  runTask,
  updateTask,
  type SyncHistory,
  type SyncTask,
  type SyncTaskPayload,
} from "@/api/sync";

const tasks = ref<SyncTask[]>([]);
const loading = ref(false);

const dialogOpen = ref(false);
const editing = ref<SyncTask | null>(null);
const formRef = ref<FormInstance>();
const form = reactive({
  name: "",
  taskType: "organize",
  sourceConfig: "",
  targetConfig: "",
  ruleType: "date_month",
  action: "move",
  conflict: "rename",
  cron: "0 0 3 * * ?",
});

const rules: FormRules = {
  name: [{ required: true, message: "请输入任务名", trigger: "blur" }],
  sourceConfig: [{ required: true, message: "请输入源目录", trigger: "blur" }],
  targetConfig: [
    {
      validator: (_rule, _value, callback) => {
        if (form.taskType === "organize" && !form.targetConfig.trim()) {
          callback(new Error("自动整理必须填写目标目录"));
        } else {
          callback();
        }
      },
      trigger: "blur",
    },
  ],
  cron: [{ required: true, message: "请输入 cron 表达式", trigger: "blur" }],
};

const cronPresets = [
  { label: "每天 03:00", value: "0 0 3 * * ?" },
  { label: "每小时整点", value: "0 0 * * * ?" },
  { label: "每天 00:30", value: "0 30 0 * * ?" },
  { label: "每周日 02:00", value: "0 0 2 ? * SUN" },
  { label: "每 10 分钟", value: "0 0/10 * * * ?" },
];

const ruleOptions = [
  { label: "按日期（年/月）", value: "date_month" },
  { label: "按日期（年）", value: "date_year" },
  { label: "按日期（年/月/日）", value: "date_day" },
  { label: "按文件类型", value: "kind" },
];

const actionOptions = [
  { label: "移动", value: "move" },
  { label: "复制", value: "copy" },
];

const conflictOptions = [
  { label: "重命名（保留两者）", value: "rename" },
  { label: "跳过", value: "skip" },
  { label: "覆盖", value: "overwrite" },
];

const historyOpen = ref(false);
const history = ref<SyncHistory[]>([]);
const historyLoading = ref(false);
const historyTask = ref<SyncTask | null>(null);

// 统计卡
const stat = computed(() => ({
  total: tasks.value.length,
  enabled: tasks.value.filter((t) => t.enabled === 1).length,
  success: tasks.value.filter((t) => t.lastStatus === "success").length,
  failed: tasks.value.filter(
    (t) => t.lastStatus === "failed" || t.lastStatus === "partial",
  ).length,
}));

/** 加载任务列表。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    tasks.value = await fetchTasks();
  } finally {
    loading.value = false;
  }
}

/** cron 表达式转中文描述（解析失败回退原文）。 */
function cronText(cron: string): string {
  try {
    return cronstrue.toString(cron, { locale: "zh_CN" });
  } catch {
    return cron;
  }
}

/** 任务类型中文标签。 */
function taskTypeLabel(taskType: string | null): string {
  return taskType === "organize" ? "自动整理" : "索引扫描";
}

/** 规则中文标签。 */
function ruleLabel(ruleType: string | null): string {
  const map: Record<string, string> = {
    date_year: "按年归档",
    date_month: "按年月归档",
    date_day: "按年月日归档",
    kind: "按类型归档",
  };
  return (ruleType && map[ruleType]) || "按年月归档";
}

/** 操作方式中文标签。 */
function actionLabel(action: string | null): string {
  return action === "copy" ? "复制" : "移动";
}

/** 冲突策略中文标签。 */
function conflictLabel(conflict: string | null): string {
  const map: Record<string, string> = {
    skip: "跳过",
    overwrite: "覆盖",
    rename: "重命名",
  };
  return (conflict && map[conflict]) || "重命名";
}

/** 打开新建任务对话框。 */
function openCreate(): void {
  editing.value = null;
  Object.assign(form, {
    name: "",
    taskType: "organize",
    sourceConfig: "",
    targetConfig: "",
    ruleType: "date_month",
    action: "move",
    conflict: "rename",
    cron: cronPresets[0].value,
  });
  dialogOpen.value = true;
}

/** 打开编辑任务对话框。 */
function openEdit(task: SyncTask): void {
  editing.value = task;
  Object.assign(form, {
    name: task.name,
    taskType: task.taskType || "index_scan",
    sourceConfig: task.sourceConfig,
    targetConfig: task.targetConfig || "",
    ruleType: task.ruleType || "date_month",
    action: task.action || "move",
    conflict: task.conflict || "rename",
    cron: task.cron,
  });
  dialogOpen.value = true;
}

/** 组装提交负载。 */
function buildPayload(): SyncTaskPayload {
  return {
    name: form.name,
    sourceConfig: form.sourceConfig,
    cron: form.cron,
    taskType: form.taskType,
    targetConfig: form.taskType === "organize" ? form.targetConfig : "",
    ruleType: form.taskType === "organize" ? form.ruleType : "date_month",
    action: form.taskType === "organize" ? form.action : "move",
    conflict: form.taskType === "organize" ? form.conflict : "rename",
  };
}

/** 提交新建/编辑任务。 */
async function handleSave(): Promise<void> {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  try {
    if (editing.value) {
      await updateTask(editing.value.id, true, buildPayload());
      ElMessage.success("已更新");
    } else {
      await createTask(buildPayload());
      ElMessage.success("已创建");
    }
    dialogOpen.value = false;
    await load();
  } catch {
    // 后端错误信息已由 http 层提示
  }
}

/** 启用/停用任务。 */
async function handleToggle(task: SyncTask): Promise<void> {
  await updateTask(task.id, task.enabled === 0, {
    name: task.name,
    sourceConfig: task.sourceConfig,
    cron: task.cron,
    taskType: task.taskType,
    targetConfig: task.targetConfig || "",
    ruleType: task.ruleType,
    action: task.action,
    conflict: task.conflict,
  });
  ElMessage.success(task.enabled === 0 ? "已启用" : "已停用");
  await load();
}

/** 删除任务（确认后调用后端）。 */
async function handleDelete(task: SyncTask): Promise<void> {
  await ElMessageBox.confirm(`确认删除任务「${task.name}」？`, "删除确认", {
    type: "warning",
    confirmButtonText: "删除",
  });
  await deleteTask(task.id);
  ElMessage.success("已删除");
  await load();
}

/** 立即执行任务并提示结果。 */
async function handleRun(task: SyncTask): Promise<void> {
  // 自动整理 + 移动 = 破坏性操作，先二次确认（需求审查高优先级）
  if (task.taskType === "organize" && task.action === "move") {
    await ElMessageBox.confirm(
      `将把「${task.sourceConfig}」下的文件移动到归档目录，确认执行？`,
      "确认整理",
      { type: "warning", confirmButtonText: "执行", cancelButtonText: "取消" },
    );
  }
  const result = await runTask(task.id);
  if (task.taskType === "organize") {
    const msg =
      `整理完成：成功 ${result.succeeded} / 失败 ${result.failed} / 跳过 ${result.skipped} / 共 ${result.processed}`;
    if (result.status === "failed") {
      ElMessage.error(msg + (result.error ? `（${result.error}）` : ""));
    } else if (result.status === "partial") {
      ElMessage.warning(msg);
    } else {
      ElMessage.success(msg);
    }
  } else {
    ElMessage.success(
      `执行完成：新增 ${result.added} / 更新 ${result.updated} / 删除 ${result.deleted}`,
    );
  }
  await load();
}

/** 打开任务执行历史抽屉。 */
async function openHistory(task: SyncTask): Promise<void> {
  historyTask.value = task;
  historyOpen.value = true;
  historyLoading.value = true;
  try {
    history.value = await fetchHistory(task.id, 50);
  } finally {
    historyLoading.value = false;
  }
}

/** 格式化时间戳为本地字符串。 */
const formatTime = (ts: number | null): string =>
  ts ? new Date(ts).toLocaleString("zh-CN", { hour12: false }) : "--";

/** 执行状态码转中文标签。 */
const statusLabel = (status: string): string =>
  ({ running: "执行中", success: "成功", partial: "部分成功", failed: "失败" })[
    status
  ] ?? status;

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="sync-view">
    <!-- 页面头部 -->
    <div class="sync-view__header">
      <div class="sync-view__header-title">
        <h2 class="sync-view__heading">自动整理</h2>
        <span class="sync-view__sub">定时整理收件箱 · 按规则自动归档文件</span>
      </div>
      <el-button type="primary" @click="openCreate"
        ><el-icon><Plus /></el-icon>新建任务</el-button
      >
    </div>

    <!-- 统计卡 -->
    <el-row :gutter="12" class="sync-view__stats">
      <el-col :xs="12" :span="6">
        <div class="sync-view__stat">
          <div class="sync-view__stat-label">任务总数</div>
          <div class="sync-view__stat-value">{{ stat.total }}</div>
          <div class="sync-view__stat-line" />
        </div>
      </el-col>
      <el-col :xs="12" :span="6">
        <div class="sync-view__stat">
          <div class="sync-view__stat-label">已启用</div>
          <div class="sync-view__stat-value" style="color: #67e8a0">
            {{ stat.enabled }}
          </div>
          <div class="sync-view__stat-line" style="background: #67e8a0" />
        </div>
      </el-col>
      <el-col :xs="12" :span="6">
        <div class="sync-view__stat">
          <div class="sync-view__stat-label">上次成功</div>
          <div class="sync-view__stat-value" style="color: #6ec8ff">
            {{ stat.success }}
          </div>
          <div class="sync-view__stat-line" style="background: #6ec8ff" />
        </div>
      </el-col>
      <el-col :xs="12" :span="6">
        <div class="sync-view__stat">
          <div class="sync-view__stat-label">上次失败</div>
          <div class="sync-view__stat-value" style="color: #f5a3a3">
            {{ stat.failed }}
          </div>
          <div class="sync-view__stat-line" style="background: #f5a3a3" />
        </div>
      </el-col>
    </el-row>

    <!-- 任务卡片 -->
    <div v-loading="loading" class="sync-view__tasks">
      <el-empty
        v-if="!loading && tasks.length === 0"
        description="暂无任务，点击右上角新建"
      />

      <div v-for="task in tasks" :key="task.id" class="sync-view__card">
        <div class="sync-view__card-head">
          <div class="sync-view__card-title">
            <span
              class="sync-view__status-dot"
              :class="task.enabled === 1 ? 'is-on' : 'is-off'"
            />
            {{ task.name }}
            <el-tag size="small" class="sync-view__type-tag">{{
              taskTypeLabel(task.taskType)
            }}</el-tag>
          </div>
          <div class="sync-view__card-actions">
            <el-button
              link
              type="primary"
              size="small"
              @click="handleRun(task)"
            >
              <el-icon><VideoPlay /></el-icon>立即执行
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              @click="openHistory(task)"
              >历史</el-button
            >
            <el-button link type="primary" size="small" @click="openEdit(task)"
              >编辑</el-button
            >
            <el-button
              link
              type="warning"
              size="small"
              @click="handleToggle(task)"
            >
              {{ task.enabled === 1 ? "停用" : "启用" }}
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              @click="handleDelete(task)"
              >删除</el-button
            >
          </div>
        </div>

        <div class="sync-view__card-body">
          <div class="sync-view__field">
            <span class="sync-view__field-label">路径</span>
            <span class="sync-view__field-value">
              <template v-if="task.taskType === 'organize'">
                {{ task.sourceConfig || "（全根）" }}
                <span class="sync-view__arrow">→</span>
                {{ task.targetConfig }}
              </template>
              <template v-else>{{ task.sourceConfig || "（全根）" }}</template>
            </span>
          </div>
          <template v-if="task.taskType === 'organize'">
            <div class="sync-view__field">
              <span class="sync-view__field-label">规则</span>
              <span class="sync-view__field-value">
                {{ ruleLabel(task.ruleType) }}
                <span class="sync-view__rule-tag">{{
                  actionLabel(task.action)
                }}</span>
                <span class="sync-view__rule-tag">{{
                  conflictLabel(task.conflict)
                }}</span>
              </span>
            </div>
          </template>
          <div class="sync-view__field">
            <span class="sync-view__field-label">调度</span>
            <span class="sync-view__field-value">
              {{ task.cron }}
              <span class="sync-view__cron-text">{{
                cronText(task.cron)
              }}</span>
            </span>
          </div>
          <div class="sync-view__field">
            <span class="sync-view__field-label">上次执行</span>
            <span class="sync-view__field-value">
              {{ formatTime(task.lastRunAt) }}
              <el-tag
                v-if="task.lastStatus"
                size="small"
                :type="
                  task.lastStatus === 'success'
                    ? 'success'
                    : task.lastStatus === 'partial'
                      ? 'warning'
                      : 'danger'
                "
                class="sync-view__status-tag"
              >
                {{ statusLabel(task.lastStatus) }}
              </el-tag>
              <span v-else class="sync-view__never">尚未执行</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="dialogOpen"
      :title="editing ? '编辑任务' : '新建任务'"
      width="520px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="任务名" prop="name">
          <el-input v-model="form.name" placeholder="如：照片收件箱整理" />
        </el-form-item>
        <el-form-item label="任务类型" prop="taskType">
          <el-radio-group v-model="form.taskType">
            <el-radio value="organize">自动整理</el-radio>
            <el-radio value="index_scan">索引扫描</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.taskType === 'organize'">
          <el-form-item label="源目录" prop="sourceConfig">
            <el-input
              v-model="form.sourceConfig"
              placeholder="收件箱目录，如 media/photos/inbox"
            />
          </el-form-item>
          <el-form-item label="目标目录" prop="targetConfig">
            <el-input
              v-model="form.targetConfig"
              placeholder="归档根目录，如 media/photos"
            />
          </el-form-item>
          <el-form-item label="整理规则" prop="ruleType">
            <el-select v-model="form.ruleType" style="width: 100%">
              <el-option
                v-for="opt in ruleOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="操作方式" prop="action">
            <el-radio-group v-model="form.action">
              <el-radio-button v-for="opt in actionOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="冲突处理" prop="conflict">
            <el-select v-model="form.conflict" style="width: 100%">
              <el-option
                v-for="opt in conflictOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item v-else label="源目录" prop="sourceConfig">
          <el-input
            v-model="form.sourceConfig"
            placeholder="存储根下相对路径，如 media/photos；空表示全根"
          />
        </el-form-item>
        <el-form-item label="Cron" prop="cron">
          <el-input
            v-model="form.cron"
            placeholder="Quartz 6 段，如 0 0 3 * * ?"
          />
          <div class="sync-view__presets">
            <el-tag
              v-for="preset in cronPresets"
              :key="preset.value"
              size="small"
              class="sync-view__preset"
              @click="form.cron = preset.value"
            >
              {{ preset.label }}
            </el-tag>
          </div>
          <div class="sync-view__cron-preview">
            {{ cronText(form.cron) }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 执行历史 -->
    <el-dialog
      v-model="historyOpen"
      :title="`执行历史 · ${historyTask?.name ?? ''}`"
      width="720px"
    >
      <el-table v-loading="historyLoading" :data="history" size="small">
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{
            formatTime(row.startedAt)
          }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="
                row.status === 'success'
                  ? 'success'
                  : row.status === 'partial'
                    ? 'warning'
                    : row.status === 'failed'
                      ? 'danger'
                      : 'info'
              "
              size="small"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="historyTask?.taskType === 'organize'"
          label="成功/失败/跳过"
          width="130"
        >
          <template #default="{ row }">
            <span style="color: #67e8a0">{{ row.succeeded }}</span>
            <span style="color: #8a97a8"> / </span>
            <span style="color: #f5a3a3">{{ row.failed }}</span>
            <span style="color: #8a97a8"> / </span>
            <span style="color: #e8d9a8">{{ row.skipped }}</span>
          </template>
        </el-table-column>
        <template v-else>
          <el-table-column label="新增" width="70">
            <template #default="{ row }"
              ><span style="color: #67e8a0">{{ row.added }}</span></template
            >
          </el-table-column>
          <el-table-column label="更新" width="70">
            <template #default="{ row }"
              ><span style="color: #6ec8ff">{{ row.updated }}</span></template
            >
          </el-table-column>
          <el-table-column label="删除" width="70">
            <template #default="{ row }"
              ><span style="color: #f5a3a3">{{ row.deleted }}</span></template
            >
          </el-table-column>
        </template>
        <el-table-column label="错误/明细" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.error || row.details || "--" }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!historyLoading && history.length === 0"
        description="暂无执行记录"
        :image-size="60"
      />
    </el-dialog>
  </div>
</template>

<style scoped>
.sync-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-family: var(--gc-font-sans, inherit);
}

.sync-view__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(7, 22, 46, 0.6);
  border: 1px solid rgba(140, 220, 255, 0.18);
  border-radius: 12px;
  padding: 12px 16px;
  backdrop-filter: blur(8px);
}

.sync-view__heading {
  margin: 0;
  font-size: 18px;
  letter-spacing: 2px;
  color: #eaf6ff;
}

.sync-view__sub {
  font-size: 12px;
  color: #8fb6dd;
  margin-left: 10px;
}

/* 统计卡 */
.sync-view__stat {
  background: rgba(7, 22, 46, 0.7);
  border: 1px solid rgba(140, 220, 255, 0.2);
  border-radius: 12px;
  padding: 14px 16px;
  backdrop-filter: blur(8px);
}

.sync-view__stat-label {
  font-size: 12px;
  letter-spacing: 2px;
  color: #8fb6dd;
}

.sync-view__stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #eaf6ff;
  font-variant-numeric: tabular-nums;
  margin: 4px 0 8px;
}

.sync-view__stat-line {
  height: 2px;
  width: 60px;
  background: linear-gradient(90deg, #6ec8ff, transparent);
  border-radius: 1px;
}

/* 任务卡片 */
.sync-view__tasks {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sync-view__card {
  background: rgba(7, 22, 46, 0.7);
  border: 1px solid rgba(140, 220, 255, 0.2);
  border-radius: 12px;
  padding: 14px 16px;
  backdrop-filter: blur(8px);
  transition: border-color 0.15s;
}

.sync-view__card:hover {
  border-color: rgba(110, 200, 255, 0.45);
}

.sync-view__card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(212, 175, 55, 0.25);
  margin-bottom: 10px;
}

.sync-view__card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: #eaf6ff;
  letter-spacing: 1px;
}

.sync-view__type-tag {
  background: rgba(110, 200, 255, 0.15);
  border-color: rgba(110, 200, 255, 0.35);
  color: #6ec8ff;
}

.sync-view__status-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  display: inline-block;
}

.sync-view__status-dot.is-on {
  background: #67e8a0;
  box-shadow: 0 0 7px rgba(103, 232, 160, 0.9);
}

.sync-view__status-dot.is-off {
  background: #8a97a8;
}

.sync-view__card-actions {
  display: flex;
  align-items: center;
}

.sync-view__card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sync-view__field {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 13px;
}

.sync-view__field-label {
  width: 64px;
  flex-shrink: 0;
  color: #8fb6dd;
  letter-spacing: 1px;
  font-size: 12px;
}

.sync-view__field-value {
  color: #bfdcf8;
  font-variant-numeric: tabular-nums;
}

.sync-view__arrow {
  color: #e8d9a8;
  margin: 0 4px;
}

.sync-view__rule-tag {
  margin-left: 8px;
  font-size: 12px;
  color: #8fb6dd;
  border: 1px solid rgba(140, 220, 255, 0.25);
  border-radius: 4px;
  padding: 1px 6px;
}

.sync-view__cron-text {
  margin-left: 8px;
  color: #e8d9a8;
  font-size: 12px;
}

.sync-view__status-tag {
  margin-left: 8px;
}

.sync-view__never {
  color: #8a97a8;
  font-size: 12px;
  margin-left: 8px;
}

/* 表单 cron */
.sync-view__presets {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.sync-view__preset {
  cursor: pointer;
}

.sync-view__cron-preview {
  margin-top: 8px;
  font-size: 12px;
  color: #e8d9a8;
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
