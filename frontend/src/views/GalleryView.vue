<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { Download, FullScreen } from "@element-plus/icons-vue";
import {
  batchDelete,
  fetchMedia,
  downloadFileAsBlob,
  mediaInspect,
  startTranscode,
  streamUrl,
  thumbnailUrl,
  transcodeStatus,
  type FileEntry,
  type MediaMetadata,
  type TranscodeStatus,
} from "@/api/file";

const entries = ref<FileEntry[]>([]);
const loading = ref(false);
const filter = ref<"all" | "image" | "video">("all");
const viewMode = ref<"grid" | "timeline">("grid");

/** 选择模式：开启后点击条目为勾选，关闭后点击打开灯箱。 */
const selectionMode = ref(false);
const selected = ref<Set<string>>(new Set());

const filtered = computed(() =>
  filter.value === "all"
    ? entries.value
    : entries.value.filter((e) => e.kind === filter.value),
);

/** 时间线分组：按修改日期（mtime）分组，倒序。 */
const timelineGroups = computed(() => {
  const map = new Map<string, FileEntry[]>();
  for (const e of filtered.value) {
    const d = new Date(e.mtime);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(e);
  }
  return [...map.entries()].sort((a, b) => b[0].localeCompare(a[0]));
});

const imageCount = computed(
  () => entries.value.filter((e) => e.kind === "image").length,
);
const videoCount = computed(
  () => entries.value.filter((e) => e.kind === "video").length,
);

/** 当前可见列表中选中的条目。 */
const selectedEntries = computed(() =>
  filtered.value.filter((e) => selected.value.has(e.path)),
);
const allSelected = computed(
  () =>
    filtered.value.length > 0 &&
    selectedEntries.value.length === filtered.value.length,
);

/** 切换选择模式（关闭时清空勾选）。 */
function toggleSelectionMode(): void {
  selectionMode.value = !selectionMode.value;
  if (!selectionMode.value) {
    selected.value = new Set();
  }
}

/** 点击条目：选择模式下切换勾选，否则打开灯箱。 */
function handleEntryClick(index: number, entry: FileEntry): void {
  if (selectionMode.value) {
    if (selected.value.has(entry.path)) selected.value.delete(entry.path);
    else selected.value.add(entry.path);
  } else {
    openLightbox(index);
  }
}

/** 全选/取消全选当前可见列表。 */
function toggleSelectAll(): void {
  if (allSelected.value) {
    selected.value = new Set();
  } else {
    selected.value = new Set(filtered.value.map((e) => e.path));
  }
}

/** 批量删除选中项（软删除进回收站，确认后执行）。 */
async function handleBatchDelete(): Promise<void> {
  if (selectedEntries.value.length === 0) return;
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${selectedEntries.value.length} 个文件？删除后可在回收站恢复。`,
      "批量删除",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await batchDelete(selectedEntries.value.map((e) => e.path));
    ElMessage.success("已删除");
    selected.value = new Set();
    await load();
  } catch {
    // 错误提示由拦截器处理
  }
}

const lightboxOpen = ref(false);
const lightboxIndex = ref(0);
const lightboxList = computed(() =>
  filter.value === "all"
    ? entries.value
    : entries.value.filter((e) => e.kind === filter.value),
);

/** 打开指定索引的灯箱。 */
function openLightbox(index: number): void {
  lightboxIndex.value = index;
  lightboxOpen.value = true;
}

/** 根据条目打开灯箱（时间线视图用）。 */
function openLightboxFor(entry: FileEntry): void {
  const index = lightboxList.value.findIndex((e) => e.path === entry.path);
  if (index >= 0) openLightbox(index);
}

/** 灯箱上一张（循环）。 */
function prev(): void {
  lightboxIndex.value =
    (lightboxIndex.value - 1 + lightboxList.value.length) %
    lightboxList.value.length;
}

/** 灯箱下一张（循环）。 */
function next(): void {
  lightboxIndex.value = (lightboxIndex.value + 1) % lightboxList.value.length;
}

const videoEl = ref<HTMLVideoElement | null>(null);
const lightboxContainer = ref<HTMLElement | null>(null);
const videoRate = ref(1);
const VIDEO_RATES = [0.5, 1, 1.5, 2];
/** 媒体元数据（首次打开视频时探测，命中缓存秒返）。 */
const mediaMeta = ref<MediaMetadata | null>(null);
/** 切换灯箱项时计数器，避免慢请求回写覆盖新状态。 */
let mediaInspectSeq = 0;
/** 转码任务状态（null=未开始）。 */
const transcodeJob = ref<TranscodeStatus | null>(null);
/** 转码轮询定时器。 */
let transcodeTimer: number | null = null;
/** 兼容版播放路径（转码完成后切到 compat 文件）。 */
const activePlayPath = ref<string | null>(null);
/** 是否正在转码（按钮 loading + 进度条）。 */
const transcoding = computed(() => transcodeJob.value?.status === "RUNNING");

function cycleVideoRate(): void {
  const i = VIDEO_RATES.indexOf(videoRate.value);
  videoRate.value = VIDEO_RATES[(i + 1) % VIDEO_RATES.length];
  if (videoEl.value) videoEl.value.playbackRate = videoRate.value;
}

function seekVideo(ds: number): void {
  if (!videoEl.value) return;
  videoEl.value.currentTime = Math.max(0, videoEl.value.currentTime + ds);
}

function toggleVideoPlay(): void {
  if (!videoEl.value) return;
  if (videoEl.value.paused) void videoEl.value.play();
  else videoEl.value.pause();
}

function onVideoError(): void {
  ElMessage.warning("该视频编码浏览器可能不支持，可点击「下载」后用本地播放器观看");
}

/** 探测媒体编码（ffprobe，结果缓存）。同 path 重复点击只跑一次。 */
async function inspectMedia(): Promise<void> {
  const c = current.value;
  if (!c || c.kind !== "video") return;
  const seq = ++mediaInspectSeq;
  const probePath = activePlayPath.value ?? c.path;
  try {
    const m = await mediaInspect(probePath);
    if (seq !== mediaInspectSeq) return;
    mediaMeta.value = m;
  } catch {
    if (seq === mediaInspectSeq) mediaMeta.value = null;
  }
}

/** 停止转码轮询。 */
function stopTranscodePolling(): void {
  if (transcodeTimer) {
    window.clearInterval(transcodeTimer);
    transcodeTimer = null;
  }
}

/** 启动「转码兼容版」：调用后端 ffmpeg 任务并轮询进度。 */
async function startCompatTranscode(): Promise<void> {
  const c = current.value;
  if (!c || c.kind !== "video") return;
  stopTranscodePolling();
  try {
    const job = await startTranscode(c.path);
    transcodeJob.value = job;
    if (job.status === "DONE") {
      onCompatReady(job.outputPath);
      return;
    }
    if (job.status === "FAILED") {
      ElMessage.error(job.message || "转码失败，请查看服务器日志");
      return;
    }
    transcodeTimer = window.setInterval(async () => {
      try {
        const s = await transcodeStatus(c.path);
        transcodeJob.value = s;
        if (s.status === "DONE") {
          stopTranscodePolling();
          onCompatReady(s.outputPath);
        } else if (s.status === "FAILED") {
          stopTranscodePolling();
          ElMessage.error(s.message || "转码失败，请查看服务器日志");
        }
      } catch {
        stopTranscodePolling();
        ElMessage.error("转码状态查询失败");
      }
    }, 2000);
  } catch {
    /* http 层已提示 */
  }
}

/** 转码完成：切到 compat 文件播放 + 重新探测编码（aac 应支持）。 */
function onCompatReady(outputPath: string): void {
  activePlayPath.value = outputPath;
  transcodeJob.value = { status: "DONE", progress: 100, message: "", outputPath };
  ElMessage.success("兼容版已生成，正在播放");
  void inspectMedia();
}

/** 灯箱关闭：彻底销毁视频元素，避免后台音频残留 / 继续下载。 */
function disposeVideo(): void {
  if (videoEl.value) {
    videoEl.value.pause();
    videoEl.value.removeAttribute("src");
    videoEl.value.load();
  }
  if (document.fullscreenElement) void document.exitFullscreen().catch(() => {});
  stopTranscodePolling();
  mediaMeta.value = null;
  transcodeJob.value = null;
  activePlayPath.value = null;
}

/** 全屏切换：容器元素 requestFullscreen，否则提示。 */
function toggleFullscreen(): void {
  if (document.fullscreenElement) {
    void document.exitFullscreen().catch(() => {});
    return;
  }
  const el = lightboxContainer.value;
  if (el && el.requestFullscreen) {
    void el.requestFullscreen().catch((e) => ElMessage.warning("全屏失败：" + (e?.message || "未知")));
  } else {
    ElMessage.warning("当前浏览器不支持 Fullscreen API");
  }
}

async function downloadCurrent(): Promise<void> {
  if (!current.value) return;
  try {
    await downloadFileAsBlob(current.value.path);
  } catch {
    ElMessage.error("下载失败");
  }
}

/** 灯箱键盘导航：视频态 ←/→ 快进快退、空格播放暂停；图片态翻页。 */
function onKeydown(e: KeyboardEvent): void {
  if (!lightboxOpen.value) return;
  if (current.value?.kind === "video") {
    if (e.key === "ArrowLeft") {
      e.preventDefault();
      seekVideo(-5);
      return;
    }
    if (e.key === "ArrowRight") {
      e.preventDefault();
      seekVideo(5);
      return;
    }
    if (e.key === " ") {
      e.preventDefault();
      toggleVideoPlay();
      return;
    }
  }
  if (e.key === "ArrowLeft") prev();
  else if (e.key === "ArrowRight") next();
  else if (e.key === "Escape") lightboxOpen.value = false;
}

const current = computed<FileEntry | null>(
  () => lightboxList.value[lightboxIndex.value] ?? null,
);
const currentName = computed(() => current.value?.name ?? "");

/** 灯箱项变更：视频即探测编码，其他清空。 */
watch(current, (v) => {
  stopTranscodePolling();
  transcodeJob.value = null;
  activePlayPath.value = null;
  if (v?.kind === "video") void inspectMedia();
  else mediaMeta.value = null;
});

/** 加载全部图片/视频（递归收集）。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    entries.value = await fetchMedia("");
  } catch {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
  window.addEventListener("keydown", onKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener("keydown", onKeydown);
});
</script>

<template>
  <div class="gallery">
    <el-card shadow="never" class="gallery__panel">
      <template #header>
        <div class="gallery__header">
          <div class="gallery__title-wrap">
            <span class="gallery__title">相册</span>
            <span class="gallery__count">
              {{ imageCount }} 张图片 · {{ videoCount }} 个视频
            </span>
          </div>
          <div class="gallery__header-actions">
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button value="grid">网格</el-radio-button>
              <el-radio-button value="timeline">时间线</el-radio-button>
            </el-radio-group>
            <el-segmented
              v-model="filter"
              :options="[
                { label: `全部（${entries.length}）`, value: 'all' },
                { label: `图片（${imageCount}）`, value: 'image' },
                { label: `视频（${videoCount}）`, value: 'video' },
              ]"
            />
            <el-button
              :type="selectionMode ? 'primary' : 'default'"
              plain
              @click="toggleSelectionMode"
            >
              {{ selectionMode ? "取消选择" : "选择" }}
            </el-button>
          </div>
        </div>
      </template>

      <div v-if="selectionMode" class="gallery__select-bar">
        <el-checkbox :model-value="allSelected" @change="toggleSelectAll">
          全选当前
        </el-checkbox>
        <span class="gallery__select-count">
          已选 {{ selectedEntries.length }} 项
        </span>
        <div class="gallery__select-actions">
          <el-button
            size="small"
            type="danger"
            :disabled="selectedEntries.length === 0"
            @click="handleBatchDelete"
          >
            删除所选
          </el-button>
          <el-button size="small" @click="toggleSelectionMode">取消</el-button>
        </div>
      </div>

      <div v-loading="loading" class="gallery__body">
        <div v-if="filtered.length === 0 && !loading" class="gallery__empty">
          还没有媒体文件，去「文件管理」上传图片或视频吧
        </div>
        <template v-if="viewMode === 'timeline' && filtered.length > 0">
          <div v-for="g in timelineGroups" :key="g[0]" style="margin-bottom: 20px">
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px">
              <span style="font-weight: 500; font-size: 14px">{{ g[0] }}</span>
              <span style="color: #999; font-size: 12px">{{ g[1].length }} 项</span>
            </div>
            <div style="display: flex; flex-wrap: wrap; gap: 6px">
              <div
                v-for="item in g[1]"
                :key="item.path"
                @click="openLightboxFor(item)"
                style="width: 90px; height: 90px; border-radius: 6px; overflow: hidden; cursor: pointer; background: #f0f0f0"
              >
                <img
                  :src="thumbnailUrl(item.path)"
                  :alt="item.name"
                  loading="lazy"
                  style="width: 100%; height: 100%; object-fit: cover"
                />
              </div>
            </div>
          </div>
        </template>
        <div v-else class="gallery__waterfall">
          <div
            v-for="(entry, index) in filtered"
            :key="entry.path"
            class="gallery__item"
            :class="{ 'gallery__item--selected': selected.has(entry.path) }"
            @click="handleEntryClick(index, entry)"
          >
            <span
              v-if="selectionMode"
              class="gallery__item-check"
              :class="{ 'gallery__item-check--on': selected.has(entry.path) }"
            >
              <el-icon v-if="selected.has(entry.path)"><Check /></el-icon>
            </span>
            <img
              v-if="entry.kind === 'image'"
              :src="thumbnailUrl(entry.path)"
              :alt="entry.name"
              loading="lazy"
            />
            <div v-else class="gallery__video">
              <img
                :src="thumbnailUrl(entry.path)"
                :alt="entry.name"
                loading="lazy"
              />
              <span class="gallery__video-badge">
                <el-icon><VideoPlay /></el-icon>
              </span>
            </div>
            <div class="gallery__item-mask">
              <span class="gallery__item-name">{{ entry.name }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="lightboxOpen"
      class="gallery__lightbox"
      width="min(1100px, 92vw)"
      :show-close="true"
      append-to-body
      align-center
      @close="disposeVideo"
    >
      <template #header>
        <span class="gallery__lightbox-title">{{ currentName }}</span>
      </template>
      <div ref="lightboxContainer" class="gallery__lightbox-body">
        <div
          v-if="current?.kind === 'video' && mediaMeta"
          class="gallery__codec-hint"
          :class="{ 'gallery__codec-hint--warn': mediaMeta.browserAudioSupported === false || mediaMeta.browserVideoSupported === false }"
        >
          <template v-if="mediaMeta.browserAudioSupported === false || mediaMeta.browserVideoSupported === false">
            <strong>⚠️ 当前浏览器可能无法播放该视频</strong>
            <div class="gallery__codec-hint-detail">
              <span v-if="mediaMeta.browserAudioSupported === false">
                音轨编码 <code>{{ mediaMeta.audioCodec }}</code>（如 AC-3/EAC3/DTS/TrueHD）Chrome/Edge 不解码，画面会有但无声音
              </span>
              <span v-if="mediaMeta.browserVideoSupported === false">
                视频编码 <code>{{ mediaMeta.videoCodec }}</code> 浏览器不支持
              </span>
              <span> · 容器 {{ mediaMeta.container }} · {{ Math.floor(mediaMeta.durationSec / 60) }} 分 {{ Math.floor(mediaMeta.durationSec % 60) }} 秒</span>
            </div>
            <div class="gallery__codec-hint-tip">
              可点击「转码兼容版」生成浏览器可播的副本（视频不重编码，仅音轨转 aac）
            </div>
            <div class="gallery__codec-hint-actions">
              <el-button
                size="small"
                type="primary"
                :loading="transcoding"
                :disabled="transcodeJob?.status === 'DONE'"
                @click="startCompatTranscode"
              >
                {{ transcodeJob?.status === 'DONE' ? '兼容版已生成' : transcodeJob?.status === 'FAILED' ? '重试转码' : '转码兼容版' }}
              </el-button>
              <el-progress
                v-if="transcoding"
                class="gallery__codec-hint-progress"
                :percentage="transcodeJob?.progress ?? 0"
                :stroke-width="6"
              />
            </div>
          </template>
          <template v-else>
            编码：<code>{{ mediaMeta.videoCodec || '?' }}</code> + <code>{{ mediaMeta.audioCodec || '无音轨' }}</code> · 容器 {{ mediaMeta.container }}
          </template>
        </div>
        <button
          class="gallery__nav gallery__nav--prev"
          aria-label="上一张"
          @click="prev"
        >
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <img
          v-if="current?.kind === 'image'"
          :src="current ? streamUrl(current.path) : ''"
          :alt="currentName"
          class="gallery__lightbox-media"
        />
        <video
          v-else-if="current?.kind === 'video'"
          :src="current ? streamUrl(activePlayPath ?? current.path) : ''"
          controls
          autoplay
          ref="videoEl"
          @error="onVideoError"
          class="gallery__lightbox-media"
        />
        <div v-if="current?.kind === 'video'" class="gallery__video-tools">
          <el-button size="small" @click="seekVideo(-10)">-10s</el-button>
          <el-button size="small" @click="seekVideo(-5)">-5s</el-button>
          <el-button size="small" @click="cycleVideoRate">{{ videoRate }}x</el-button>
          <el-button size="small" @click="seekVideo(5)">+5s</el-button>
          <el-button size="small" @click="seekVideo(10)">+10s</el-button>
          <el-button
            size="small"
            type="primary"
            :icon="Download"
            @click="downloadCurrent"
          >
            下载
          </el-button>
          <el-button size="small" :icon="FullScreen" @click="toggleFullscreen">
            全屏
          </el-button>
        </div>
        <button
          class="gallery__nav gallery__nav--next"
          aria-label="下一张"
          @click="next"
        >
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>
      <template #footer>
        <div class="gallery__lightbox-footer">
          <span>{{ lightboxIndex + 1 }} / {{ lightboxList.length }}</span>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.gallery {
  position: relative;
  z-index: 1;
}

.gallery__panel {
  border: 1px solid rgba(126, 210, 255, 0.18);
  background: linear-gradient(
    160deg,
    rgba(8, 26, 54, 0.72),
    rgba(4, 16, 38, 0.78)
  );
  backdrop-filter: blur(10px);
  border-radius: 14px;
  box-shadow: 0 10px 40px rgba(2, 10, 26, 0.55);
}

.gallery__panel :deep(.el-card__header) {
  border-bottom: 1px solid rgba(212, 175, 55, 0.22);
}

.gallery__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.gallery__title {
  font-size: 17px;
  font-weight: 600;
  color: #eaf6ff;
  letter-spacing: 1px;
}

.gallery__count {
  margin-left: 12px;
  font-size: 12px;
  color: rgba(159, 198, 234, 0.75);
}

.gallery__body {
  min-height: 260px;
}

.gallery__header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.gallery__select-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 12px;
  margin-bottom: 12px;
  border: 1px solid rgba(212, 175, 55, 0.35);
  border-radius: 10px;
  background: rgba(212, 175, 55, 0.08);
}

.gallery__select-count {
  font-size: 13px;
  color: #d4af37;
}

.gallery__select-actions {
  margin-left: auto;
}

.gallery__item {
  cursor: pointer;
}

.gallery__item--selected {
  border-color: rgba(212, 175, 55, 0.85);
  box-shadow: 0 0 0 2px rgba(212, 175, 55, 0.35), 0 8px 26px rgba(2, 10, 26, 0.6);
}

.gallery__item-check {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.7);
  background: rgba(3, 12, 28, 0.55);
  color: transparent;
  font-size: 14px;
}

.gallery__item-check--on {
  background: #d4af37;
  border-color: #d4af37;
  color: #061228;
}

.gallery__empty {
  padding: 80px 0;
  text-align: center;
  color: rgba(159, 198, 234, 0.6);
}

.gallery__waterfall {
  columns: 5 220px;
  column-gap: 14px;
}

.gallery__item {
  position: relative;
  display: inline-block;
  width: 100%;
  margin-bottom: 14px;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid rgba(126, 210, 255, 0.16);
  background: rgba(10, 30, 60, 0.5);
  transition:
    transform 0.25s ease,
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}

.gallery__item:hover {
  transform: translateY(-3px);
  border-color: rgba(212, 175, 55, 0.55);
  box-shadow: 0 8px 26px rgba(2, 10, 26, 0.6);
}

.gallery__item img {
  display: block;
  width: 100%;
  height: auto;
}

.gallery__video {
  position: relative;
}

.gallery__video-badge {
  position: absolute;
  right: 8px;
  bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(3, 12, 28, 0.72);
  color: #d4af37;
  font-size: 15px;
  border: 1px solid rgba(212, 175, 55, 0.5);
}

.gallery__item-mask {
  position: absolute;
  inset: auto 0 0 0;
  padding: 8px 10px;
  background: linear-gradient(180deg, transparent, rgba(3, 12, 28, 0.85));
  opacity: 0;
  transition: opacity 0.25s ease;
}

.gallery__item:hover .gallery__item-mask {
  opacity: 1;
}

.gallery__item-name {
  font-size: 12px;
  color: #eaf6ff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.gallery__lightbox-title {
  color: #eaf6ff;
  font-size: 15px;
}

.gallery__lightbox-body {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.gallery__lightbox-media {
  max-width: 100%;
  max-height: 68vh;
  border-radius: 8px;
  box-shadow: 0 14px 50px rgba(2, 10, 26, 0.7);
}

.gallery__video-tools {
  position: absolute;
  bottom: 14px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 6px;
  z-index: 5;
  background: rgba(4, 14, 32, 0.72);
  border: 1px solid rgba(126, 210, 255, 0.22);
  border-radius: 10px;
  padding: 4px 8px;
  backdrop-filter: blur(6px);
}

.gallery__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid rgba(212, 175, 55, 0.45);
  background: rgba(3, 12, 28, 0.6);
  color: #d4af37;
  cursor: pointer;
  font-size: 18px;
  transition: background 0.2s ease;
}

.gallery__nav:hover {
  background: rgba(212, 175, 55, 0.2);
}

.gallery__nav--prev {
  left: -22px;
}

.gallery__nav--next {
  right: -22px;
}

.gallery__lightbox-footer {
  display: flex;
  justify-content: center;
  color: rgba(159, 198, 234, 0.8);
  font-size: 13px;
}

.gallery__panel :deep(.el-segmented) {
  --el-segmented-bg-color: rgba(3, 12, 28, 0.55);
  --el-segmented-item-selected-bg-color: rgba(110, 200, 255, 0.18);
  --el-segmented-item-selected-color: #bfe9ff;
  --el-segmented-item-hover-bg-color: rgba(110, 200, 255, 0.1);
  --el-segmented-item-hover-color: #bfe9ff;
  --el-segmented-item-active-bg-color: rgba(110, 200, 255, 0.16);
}

.gallery__panel :deep(.el-dialog) {
  --el-dialog-bg-color: rgba(6, 20, 44, 0.96);
}

/* ---------- 移动端适配 ---------- */
@media (max-width: 768px) {
  .gallery__waterfall {
    columns: 2;
    column-gap: 8px;
  }

  .gallery__item {
    margin-bottom: 8px;
  }

  .gallery__header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .gallery__header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .gallery__lightbox-body {
    min-height: 40vh;
  }

  .gallery__nav--prev {
    left: 4px;
  }

  .gallery__nav--next {
    right: 4px;
  }
}

.gallery__codec-hint {
  margin: 0 12px 12px;
  padding: 10px 14px;
  border-radius: 6px;
  background: rgba(99, 99, 99, 0.12);
  border-left: 3px solid #909399;
  font-size: 13px;
  line-height: 1.5;
  color: #c0c4cc;
}

.gallery__codec-hint code {
  background: rgba(0, 0, 0, 0.25);
  padding: 1px 6px;
  border-radius: 3px;
  font-family: "JetBrains Mono", Consolas, monospace;
  color: #f89898;
}

.gallery__codec-hint--warn {
  background: rgba(245, 108, 108, 0.15);
  border-left-color: #f56c6c;
  color: #fef0f0;
}

.gallery__codec-hint--warn strong {
  color: #f56c6c;
  display: block;
  margin-bottom: 4px;
}

.gallery__codec-hint-detail {
  margin-bottom: 4px;
}

.gallery__codec-hint-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.gallery__codec-hint-progress {
  flex: 1;
  max-width: 220px;
}
.gallery__codec-hint-tip {
  font-style: italic;
  opacity: 0.85;
}

</style>
