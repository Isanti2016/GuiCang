<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import {
  fetchSettingDefinitions,
  fetchSettings,
  updateSettings,
  type SysSetting,
} from "@/api/setting";

const definitions = ref<SysSetting[]>([]);
const values = reactive<Record<string, string>>({});
const loading = ref(false);
const saving = ref(false);

/** 加载设置定义与当前值（前端按定义动态渲染表单，新增设置项自动出现）。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const [defs, cur] = await Promise.all([fetchSettingDefinitions(), fetchSettings()]);
    definitions.value = defs;
    Object.keys(values).forEach((k) => delete values[k]);
    for (const def of defs) {
      values[def.key] = cur[def.key] ?? def.defaultValue;
    }
  } catch {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false;
  }
}

/** 保存设置。 */
async function handleSave(): Promise<void> {
  saving.value = true;
  try {
    await updateSettings({ ...values });
    ElMessage.success("设置已保存");
  } catch {
    // 错误提示由拦截器处理
  } finally {
    saving.value = false;
  }
}

/** 设置项说明（当前仅有回收站自动清空）。 */
function hintOf(key: string): string {
  const hints: Record<string, string> = {
    "trash.auto-purge-days": "回收站中超过该天数的条目将被每天 02:00 自动彻底删除；0 表示不自动清空",
    "camera.receive-dir": "监控录像接收目录：摄像头录像放入该目录（子目录名=摄像头名），系统每 5 分钟自动按摄像头/日期归档；留空=存储根/cameras/incoming",
    "disk.alert-threshold": "磁盘使用率超过该百分比时生成站内告警通知；0 表示不告警",
  };
  return hints[key] ?? "";
}

onMounted(load);
</script>

<template>
  <div class="settings">
    <el-card v-loading="loading" shadow="never" class="settings__card">
      <template #header>
        <div class="settings__header">
          <span class="settings__title">系统设置</span>
          <span class="settings__desc">可扩展的设置中心，新增设置项会自动出现在这里</span>
        </div>
      </template>

      <el-form label-width="220px" label-position="left">
        <el-form-item
          v-for="def in definitions"
          :key="def.key"
          :label="def.label"
        >
          <div class="settings__field">
            <el-input
              v-if="def.type === 'int'"
              v-model="values[def.key]"
              placeholder="请输入整数"
              style="width: 240px"
            />
            <el-switch
              v-else-if="def.type === 'bool'"
              v-model="values[def.key]"
              :active-value="'true'"
              :inactive-value="'false'"
            />
            <el-input
              v-else
              v-model="values[def.key]"
              placeholder="请输入"
              style="width: 320px"
            />
            <p v-if="hintOf(def.key)" class="settings__hint">{{ hintOf(def.key) }}</p>
          </div>
        </el-form-item>
      </el-form>

      <div class="settings__actions">
        <el-button type="primary" :loading="saving" @click="handleSave">保存设置</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.settings {
  position: relative;
  z-index: 1;
}

.settings__card {
  max-width: 760px;
  margin: 0 auto;
  border: 1px solid rgba(126, 210, 255, 0.18);
  background: linear-gradient(160deg, rgba(8, 26, 54, 0.72), rgba(4, 16, 38, 0.78));
  backdrop-filter: blur(10px);
  border-radius: 14px;
  box-shadow: 0 10px 40px rgba(2, 10, 26, 0.55);
}

.settings__card :deep(.el-card__header) {
  border-bottom: 1px solid rgba(212, 175, 55, 0.22);
}

.settings__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.settings__title {
  font-size: 17px;
  font-weight: 600;
  color: #eaf6ff;
  letter-spacing: 1px;
}

.settings__desc {
  font-size: 12px;
  color: rgba(159, 198, 234, 0.65);
}

.settings__field {
  width: 100%;
}

.settings__hint {
  margin: 4px 0 0;
  font-size: 12px;
  color: rgba(159, 198, 234, 0.6);
  line-height: 1.5;
}

.settings__actions {
  margin-top: 8px;
  text-align: right;
}
</style>
