<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref, watch } from "vue";
import { Download, FullScreen } from "@element-plus/icons-vue";
import {
  deleteCamera,
  fetchCameraMeta,
  fetchCameraRecords,
  fetchCameras,
  saveCamera,
  type Camera,
  type CameraMeta,
  type CameraRecord,
} from "@/api/camera";
import { downloadFileAsBlob, mediaInspect, type MediaMetadata } from "@/api/file";
import { useAuthStore } from "@/stores/auth";

const cameras = ref<Camera[]>([]);
const meta = ref<CameraMeta | null>(null);
const selected = ref<Camera | null>(null);
const records = ref<CameraRecord[]>([]);
const loadingCameras = ref(false);
const loadingRecords = ref(false);
const day = ref(new Date().toISOString().slice(0, 10));

const saveDialog = ref(false);
const saving = ref(false);
const editing = ref<{ id?: number; name: string; location: string }>({
  name: "",
  location: "",
});

const playDialog = ref(false);
const playing = ref<CameraRecord | null>(null);

/** 切换录像时探测编码；清空时复位。 */
watch(playing, (r) => {
  if (r) void inspectMediaForRecord(r.path);
  else mediaMeta.value = null;
});

const hasCameras = computed(() => cameras.value.length > 0);

const formatBytes = (size: number): string => {
  if (!size || size <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = size;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value >= 100 || unit === 0 ? 0 : 1)} ${units[unit]}`;
};

const formatTime = (ms: number): string => {
  if (!ms) return "--";
  return new Date(ms).toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};

async function loadCameras(): Promise<void> {
  loadingCameras.value = true;
  try {
    cameras.value = await fetchCameras();
    if (
      !selected.value ||
      !cameras.value.some((c) => c.id === selected.value?.id)
    ) {
      selected.value = cameras.value[0] ?? null;
    }
    if (selected.value) {
      void loadRecords();
    } else {
      records.value = [];
    }
  } catch {
    // 错误提示由拦截器处理
  } finally {
    loadingCameras.value = false;
  }
}

async function loadRecords(): Promise<void> {
  const camera = selected.value;
  if (!camera) {
    records.value = [];
    return;
  }
  loadingRecords.value = true;
  try {
    records.value = await fetchCameraRecords(camera.name, day.value);
  } catch {
    // 错误提示由拦截器处理
  } finally {
    loadingRecords.value = false;
  }
}

function selectCamera(camera: Camera): void {
  selected.value = camera;
  void loadRecords();
}

function openAdd(): void {
  editing.value = { name: "", location: "" };
  saveDialog.value = true;
}

function openEdit(camera: Camera): void {
  editing.value = {
    id: camera.id,
    name: camera.name,
    location: camera.location ?? "",
  };
  saveDialog.value = true;
}

async function handleSave(): Promise<void> {
  if (!editing.value.name.trim()) {
    ElMessage.warning("请输入摄像头名称");
    return;
  }
  saving.value = true;
  try {
    await saveCamera(editing.value);
    ElMessage.success("已保存");
    saveDialog.value = false;
    await loadCameras();
  } catch {
    // 错误提示由拦截器处理
  } finally {
    saving.value = false;
  }
}

async function handleDelete(camera: Camera): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除摄像头「${camera.name}」？仅移除注册信息，录像文件不受影响。`,
      "删除确认",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await deleteCamera(camera.id as number);
    ElMessage.success("已删除");
    if (selected.value?.id === camera.id) {
      selected.value = null;
    }
    await loadCameras();
  } catch {
    // 错误提示由拦截器处理
  }
}

function play(record: CameraRecord): void {
  playing.value = record;
  playDialog.value = true;
}

function streamUrl(record: CameraRecord): string {
  // 通过 query 参数携带 JWT 给 <video> 标签，避开 video 标签不能设 Authorization 头的限制
  const token = useAuthStore().token || "";
  return `/api/v1/files/stream?path=${encodeURIComponent(record.path)}&token=${encodeURIComponent(token)}`;
}

const videoEl = ref<HTMLVideoElement | null>(null);
const playDialogContainer = ref<HTMLElement | null>(null);
const videoRate = ref(1);
const VIDEO_RATES = [0.5, 1, 1.5, 2];
const mediaMeta = ref<MediaMetadata | null>(null);
let mediaInspectSeq = 0;

function cycleVideoRate(): void {
  const i = VIDEO_RATES.indexOf(videoRate.value);
  videoRate.value = VIDEO_RATES[(i + 1) % VIDEO_RATES.length];
  if (videoEl.value) videoEl.value.playbackRate = videoRate.value;
}

function seekVideo(ds: number): void {
  if (!videoEl.value) return;
  videoEl.value.currentTime = Math.max(0, videoEl.value.currentTime + ds);
}

function onVideoError(): void {
  ElMessage.warning("该视频编码浏览器可能不支持，可点击「下载」后用本地播放器观看");
}

async function inspectMediaForRecord(path: string): Promise<void> {
  const seq = ++mediaInspectSeq;
  try {
    const m = await mediaInspect(path);
    if (seq === mediaInspectSeq) mediaMeta.value = m;
  } catch {
    if (seq === mediaInspectSeq) mediaMeta.value = null;
  }
}

/** 监控视频弹窗关闭时销毁 video，避免后台继续下载/播声音。 */
function disposePlaying(): void {
  if (videoEl.value) {
    videoEl.value.pause();
    videoEl.value.removeAttribute("src");
    videoEl.value.load();
  }
  if (document.fullscreenElement) void document.exitFullscreen().catch(() => {});
  mediaMeta.value = null;
}

/** 全屏切换。 */
function toggleFullscreen(): void {
  if (document.fullscreenElement) {
    void document.exitFullscreen().catch(() => {});
    return;
  }
  const el = playDialogContainer.value;
  if (el && el.requestFullscreen) {
    void el.requestFullscreen().catch((e) => ElMessage.warning("全屏失败：" + (e?.message || "未知")));
  } else {
    ElMessage.warning("当前浏览器不支持 Fullscreen API");
  }
}

async function downloadPlaying(): Promise<void> {
  if (!playing.value) return;
  try {
    await downloadFileAsBlob(playing.value.path);
  } catch {
    ElMessage.error("下载失败");
  }
}

onMounted(async () => {
  await Promise.all([
    loadCameras(),
    fetchCameraMeta()
      .then((m) => (meta.value = m))
      .catch(() => {
        meta.value = null;
      }),
  ]);
});
</script>

<template>
  <div class="cameras">
    <el-card v-loading="loadingCameras" shadow="never" class="cameras__card">
      <template #header>
        <div class="cameras__header">
          <div>
            <span class="cameras__title">监控录像</span>
            <span class="cameras__desc">录像自动按「摄像头 / 日期」归档，点击即播</span>
          </div>
          <el-button type="primary" @click="openAdd">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>添加摄像头
          </el-button>
        </div>
      </template>

      <!-- 摄像头卡片 -->
      <div v-if="hasCameras" class="cameras__grid">
        <div
          v-for="camera in cameras"
          :key="camera.id"
          class="cameras__item"
          :class="{ 'cameras__item--active': selected?.id === camera.id }"
          @click="selectCamera(camera)"
        >
          <div class="cameras__item-head">
            <el-icon class="cameras__item-icon"><VideoCamera /></el-icon>
            <span class="cameras__item-name">{{ camera.name }}</span>
            <el-icon
              class="cameras__item-edit"
              @click.stop="openEdit(camera)"
              title="编辑"
            >
              <Edit />
            </el-icon>
            <el-icon
              class="cameras__item-del"
              @click.stop="handleDelete(camera)"
              title="删除"
            >
              <Delete />
            </el-icon>
          </div>
          <div class="cameras__item-loc">
            {{ camera.location || "未设置位置" }}
          </div>
          <div class="cameras__item-stat">
            <span>共 {{ camera.totalRecords ?? 0 }} 段录像</span>
            <span>最近 {{ formatTime(camera.lastRecordAt ?? 0) }}</span>
          </div>
        </div>
      </div>
      <el-empty
        v-else
        description="暂无摄像头，添加后可接入监控录像"
        :image-size="80"
      />

      <!-- 录像列表 -->
      <template v-if="selected">
        <div class="cameras__records-head">
          <div class="cameras__records-title">
            {{ selected.name }} 录像
            <span class="cameras__records-sub">
              {{ day }} · {{ records.length }} 段
            </span>
          </div>
          <el-date-picker
            v-model="day"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 150px"
            @change="loadRecords"
          />
        </div>

        <div v-loading="loadingRecords" class="cameras__records">
          <div
            v-for="record in records"
            :key="record.id"
            class="cameras__record"
            @click="play(record)"
          >
            <el-icon class="cameras__record-icon"><VideoPlay /></el-icon>
            <div class="cameras__record-info">
              <div class="cameras__record-name" :title="record.name">
                {{ record.name }}
              </div>
              <div class="cameras__record-meta">
                {{ formatBytes(record.size) }} · {{ formatTime(record.mtime) }}
              </div>
            </div>
            <el-button size="small" type="primary" plain>播放</el-button>
          </div>
          <el-empty
            v-if="!loadingRecords && records.length === 0"
            description="当天暂无录像"
            :image-size="70"
          />
        </div>
      </template>

      <!-- 接入说明 -->
      <el-alert
        class="cameras__guide"
        type="info"
        :closable="false"
        show-icon
      >
        <template #title>
          <span>如何接入：</span>
          将摄像头录像（FTP/SD 卡拷出）放入接收目录
          <code class="cameras__code">{{ meta?.receiveDir ?? "存储根/cameras/incoming" }}</code>
          ，子目录名即摄像头名（如 CAM01/、客厅/），系统每 5 分钟自动按「摄像头/日期」归档；
          也可在系统设置中修改接收目录。
        </template>
      </el-alert>
    </el-card>

    <!-- 添加/编辑摄像头 -->
    <el-dialog
      v-model="saveDialog"
      :title="editing.id ? '编辑摄像头' : '添加摄像头'"
      width="420px"
      append-to-body
    >
      <el-form label-width="80px" label-position="left">
        <el-form-item label="名称" required>
          <el-input
            v-model="editing.name"
            placeholder="与接收目录子目录名一致，如 CAM01 / 客厅"
            maxlength="32"
          />
        </el-form-item>
        <el-form-item label="位置">
          <el-input
            v-model="editing.location"
            placeholder="如 门口 / 客厅 / 车库（可选）"
            maxlength="64"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 录像播放 -->
    <el-dialog
      v-model="playDialog"
      :title="playing?.name ?? '播放'"
      width="720px"
      append-to-body
      destroy-on-close
      @close="disposePlaying"
    >
      <video
        v-if="playing"
        :src="streamUrl(playing)"
        controls
        autoplay
        ref="videoEl"
        @error="onVideoError"
        class="cameras__video"
      />
      <div
        v-if="playing && mediaMeta"
        class="cameras__codec-hint"
        :class="{ 'cameras__codec-hint--warn': mediaMeta.browserAudioSupported === false || mediaMeta.browserVideoSupported === false }"
      >
        <template v-if="mediaMeta.browserAudioSupported === false || mediaMeta.browserVideoSupported === false">
          <strong>⚠️ 该录像可能无法播放</strong>
          <span v-if="mediaMeta.browserAudioSupported === false">音轨编码 <code>{{ mediaMeta.audioCodec }}</code>（AC-3/EAC3 等）浏览器不解码，画面有但无声音</span>
          <span v-else>视频编码 <code>{{ mediaMeta.videoCodec }}</code> 浏览器不支持</span>
          <span class="cameras__codec-hint-tip">建议「下载」后用 VLC 等本地播放器</span>
        </template>
        <template v-else>
          编码：<code>{{ mediaMeta.videoCodec || '?' }}</code> + <code>{{ mediaMeta.audioCodec || '无音轨' }}</code> · 容器 {{ mediaMeta.container }}
        </template>
      </div>
      <div ref="playDialogContainer" v-if="playing" class="cameras__video-tools">
        <el-button size="small" @click="seekVideo(-10)">-10s</el-button>
        <el-button size="small" @click="seekVideo(-5)">-5s</el-button>
        <el-button size="small" @click="cycleVideoRate">{{ videoRate }}x</el-button>
        <el-button size="small" @click="seekVideo(5)">+5s</el-button>
        <el-button size="small" @click="seekVideo(10)">+10s</el-button>
        <el-button
          size="small"
          type="primary"
          :icon="Download"
          @click="downloadPlaying"
        >
          下载
        </el-button>
        <el-button size="small" :icon="FullScreen" @click="toggleFullscreen">
          全屏
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.cameras {
  position: relative;
  z-index: 1;
}

.cameras__video-tools {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.cameras__card {
  border: 1px solid rgba(126, 210, 255, 0.18);
  background: linear-gradient(160deg, rgba(8, 26, 54, 0.72), rgba(4, 16, 38, 0.78));
  backdrop-filter: blur(10px);
  border-radius: 14px;
  box-shadow: 0 10px 40px rgba(2, 10, 26, 0.55);
}

.cameras__card :deep(.el-card__header) {
  border-bottom: 1px solid rgba(212, 175, 55, 0.22);
}

.cameras__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.cameras__title {
  font-size: 17px;
  font-weight: 600;
  color: #eaf6ff;
  letter-spacing: 1px;
}

.cameras__desc {
  margin-left: 10px;
  font-size: 12px;
  color: rgba(159, 198, 234, 0.65);
}

.cameras__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.cameras__item {
  padding: 12px 14px;
  border: 1px solid rgba(126, 210, 255, 0.16);
  border-radius: 10px;
  background: rgba(10, 32, 62, 0.5);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;
}

.cameras__item:hover {
  border-color: rgba(126, 210, 255, 0.45);
  transform: translateY(-1px);
}

.cameras__item--active {
  border-color: #409eff;
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.45),
    inset 0 0 18px rgba(64, 158, 255, 0.08);
}

.cameras__item-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cameras__item-icon {
  color: #5cb8ff;
  font-size: 16px;
}

.cameras__item-name {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: #eaf6ff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cameras__item-edit,
.cameras__item-del {
  color: rgba(159, 198, 234, 0.55);
  font-size: 14px;
  cursor: pointer;
}

.cameras__item-edit:hover {
  color: #409eff;
}

.cameras__item-del:hover {
  color: #f56c6c;
}

.cameras__item-loc {
  margin: 6px 0;
  font-size: 12px;
  color: rgba(159, 198, 234, 0.7);
}

.cameras__item-stat {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: rgba(159, 198, 234, 0.5);
}

.cameras__records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.cameras__records-title {
  font-size: 14px;
  font-weight: 600;
  color: #eaf6ff;
}

.cameras__records-sub {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: rgba(159, 198, 234, 0.6);
}

.cameras__records {
  min-height: 120px;
  border-top: 1px solid rgba(126, 210, 255, 0.1);
}

.cameras__record {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(126, 210, 255, 0.08);
  cursor: pointer;
  transition: background 0.15s;
}

.cameras__record:hover {
  background: rgba(64, 158, 255, 0.08);
}

.cameras__record-icon {
  color: #5cb8ff;
  font-size: 20px;
}

.cameras__record-info {
  flex: 1;
  min-width: 0;
}

.cameras__record-name {
  font-size: 13px;
  color: #dceeff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cameras__record-meta {
  margin-top: 2px;
  font-size: 11px;
  color: rgba(159, 198, 234, 0.55);
}

.cameras__guide {
  margin-top: 18px;
  border-color: rgba(64, 158, 255, 0.25);
  background: rgba(64, 158, 255, 0.06);
}

.cameras__guide :deep(.el-alert__title) {
  font-size: 12px;
  line-height: 1.8;
}

.cameras__code {
  margin: 0 4px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(64, 158, 255, 0.14);
  color: #7ec9ff;
  font-family: "JetBrains Mono", Consolas, monospace;
  word-break: break-all;
}

.cameras__video {
  width: 100%;
  max-height: 70vh;
  border-radius: 8px;
  background: #000;
}

.cameras__codec-hint {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 6px;
  background: rgba(99, 99, 99, 0.12);
  border-left: 3px solid #909399;
  font-size: 13px;
  line-height: 1.5;
  color: #c0c4cc;
}

.cameras__codec-hint code {
  background: rgba(0, 0, 0, 0.25);
  padding: 1px 6px;
  border-radius: 3px;
  font-family: "JetBrains Mono", Consolas, monospace;
  color: #f89898;
}

.cameras__codec-hint--warn {
  background: rgba(245, 108, 108, 0.15);
  border-left-color: #f56c6c;
  color: #fef0f0;
}

.cameras__codec-hint--warn strong {
  color: #f56c6c;
  display: block;
  margin-bottom: 4px;
}

.cameras__codec-hint-tip {
  display: block;
  font-style: italic;
  opacity: 0.85;
  margin-top: 4px;
}

</style>
