<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowLeft, ArrowRight, List, Microphone, Setting } from "@element-plus/icons-vue";
import {
  fetchChapter,
  fetchProgress,
  openNovel,
  saveProgress,
  type ChapterContent,
  type NovelMeta,
  type ReaderProgress,
} from "@/api/reader";
import {
  loadSettings,
  saveSettings,
  scrollPercent,
  THEME_COLORS,
  type ReaderSettings,
} from "@/utils/reader";

const route = useRoute();
const router = useRouter();

const path = computed(() => (route.query.path as string) || "");

// ============ 状态 ============
const loading = ref(false);
const novel = ref<NovelMeta | null>(null);
const chapter = ref<ChapterContent | null>(null);
const chapterLoading = ref(false);
const settings = ref<ReaderSettings>(loadSettings());
const settingsOpen = ref(false);
const tocOpen = ref(true);
const progress = ref<ReaderProgress | null>(null);

const contentRef = ref<HTMLElement | null>(null);
const saveTimer = ref<number | null>(null);

// ============ 计算属性 ============
const title = computed(() => novel.value?.title || path.value.split("/").pop() || "阅读");
const author = computed(() => novel.value?.author || "");
const formatLabel = computed(() => (novel.value?.format === "EPUB" ? "EPUB" : "TXT"));
const theme = computed(() => THEME_COLORS[settings.value.theme]);

const fontSizeStyle = computed(() => ({
  fontSize: `${settings.value.fontSize}px`,
  lineHeight: settings.value.lineHeight,
}));

const contentStyle = computed(() => ({
  background: theme.value.bg,
  color: theme.value.color,
}));

const totalChapters = computed(() => novel.value?.chapterCount ?? 0);

// ============ 章节加载 ============
async function loadChapter(index: number): Promise<void> {
  if (!path.value) return;
  if (speaking.value && !ttsAuto) stopTts();
  chapterLoading.value = true;
  try {
    const data = await fetchChapter(path.value, index);
    chapter.value = data;
  } catch {
    ElMessage.error("章节加载失败");
  } finally {
    chapterLoading.value = false;
    await scrollToTop();
  }
}

async function scrollToTop(): Promise<void> {
  await new Promise((r) => setTimeout(r, 30));
  contentRef.value?.scrollTo({ top: 0 });
}

function nextChapter(): void {
  if (!chapter.value) return;
  const next = chapter.value.index + 1;
  if (next >= totalChapters.value) {
    ElMessage.info("已经是最后一章");
    return;
  }
  void loadChapter(next);
}

function prevChapter(): void {
  if (!chapter.value) return;
  const prev = chapter.value.index - 1;
  if (prev < 0) {
    ElMessage.info("已经是第一章");
    return;
  }
  void loadChapter(prev);
}

function jumpTo(index: number): void {
  tocOpen.value = false;
  void loadChapter(index);
}

// ============ 进度保存 ============
function currentPercent(): number {
  const el = contentRef.value;
  if (!el) return 0;
  return scrollPercent(el.scrollTop, el.scrollHeight, el.clientHeight);
}

/** 保存进度（供滚动防抖与离开页面时调用）。 */
async function persistProgress(): Promise<void> {
  if (!path.value || !chapter.value || !settings.value.autoSave) return;
  try {
    await saveProgress(path.value, chapter.value.index, currentPercent());
  } catch {
    /* 静默失败，不打断阅读 */
  }
}

function scheduleSave(): void {
  if (saveTimer.value !== null) {
    window.clearTimeout(saveTimer.value);
  }
  saveTimer.value = window.setTimeout(() => {
    void persistProgress();
  }, 800);
}

// ============ 朗读（TTS） ============
const speaking = ref(false);
const ttsPaused = ref(false);
const ttsRate = ref(1);
const ttsProgress = ref(0);
let ttsTimer: number | null = null;
let ttsCancel = false;
let ttsAuto = false;
let ttsParagraphs: string[] = [];
let ttsCursor = 0;
let zhVoice: SpeechSynthesisVoice | null = null;

function pickZhVoice(): SpeechSynthesisVoice | null {
  const voices = window.speechSynthesis.getVoices();
  return (
    voices.find((v) => v.lang.toLowerCase().startsWith("zh-cn") && v.localService) ||
    voices.find((v) => v.lang.toLowerCase().startsWith("zh")) ||
    null
  );
}

function stopTts(): void {
  ttsCancel = true;
  ttsAuto = false;
  if (ttsTimer !== null) {
    window.clearTimeout(ttsTimer);
    ttsTimer = null;
  }
  window.speechSynthesis.cancel();
  speaking.value = false;
  ttsPaused.value = false;
  ttsProgress.value = 0;
}

function speakParagraph(index: number): void {
  if (ttsCancel) return;
  if (index >= ttsParagraphs.length) {
    // 本章读完：自动连播下一章
    if (ttsAuto && chapter.value && chapter.value.index < totalChapters.value - 1) {
      const next = chapter.value.index + 1;
      void loadChapter(next).then(() => {
        if (!ttsCancel && speaking.value) {
          ttsCursor = 0;
          speakCurrentChapter();
        }
      });
    } else {
      stopTts();
    }
    return;
  }
  const text = (ttsParagraphs[index] || "").trim();
  ttsProgress.value = Math.round(((index + 1) / ttsParagraphs.length) * 100);
  if (!text) {
    ttsCursor = index + 1;
    ttsTimer = window.setTimeout(() => {
      ttsTimer = null;
      speakParagraph(index + 1);
    }, 120);
    return;
  }
  const u = new SpeechSynthesisUtterance(text);
  u.lang = "zh-CN";
  u.rate = ttsRate.value;
  if (zhVoice) u.voice = zhVoice;
  u.onend = () => {
    if (ttsCancel) return;
    ttsCursor = index + 1;
    ttsTimer = window.setTimeout(() => {
      ttsTimer = null;
      speakParagraph(index + 1);
    }, 400);
  };
  u.onerror = () => {
    if (ttsCancel) return;
    ttsCursor = index + 1;
    ttsTimer = window.setTimeout(() => {
      ttsTimer = null;
      speakParagraph(index + 1);
    }, 150);
  };
  window.speechSynthesis.speak(u);
}

function speakCurrentChapter(): void {
  if (!chapter.value) return;
  const raw = chapter.value.content || "";
  ttsParagraphs = raw
    .split(/\n+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
  if (ttsParagraphs.length === 0) {
    ElMessage.warning("本章没有可朗读的文本");
    return;
  }
  ttsCancel = false;
  ttsCursor = 0;
  zhVoice = pickZhVoice();
  speaking.value = true;
  ttsPaused.value = false;
  speakParagraph(0);
}

function toggleTts(): void {
  if (ttsPaused.value) {
    window.speechSynthesis.resume();
    ttsPaused.value = false;
    return;
  }
  if (speaking.value) {
    window.speechSynthesis.pause();
    ttsPaused.value = true;
    return;
  }
  ttsAuto = true;
  speakCurrentChapter();
}

function changeTtsRate(delta: number): void {
  const next = Math.max(0.5, Math.min(2, Math.round((ttsRate.value + delta) * 4) / 4));
  ttsRate.value = next;
  if (speaking.value && !ttsPaused.value && ttsCursor < ttsParagraphs.length) {
    // 重读当前段以应用新语速
    window.speechSynthesis.cancel();
    speakParagraph(ttsCursor);
  }
}

// ============ 设置 ============
function changeFontSize(delta: number): void {
  const next = Math.max(14, Math.min(32, settings.value.fontSize + delta));
  settings.value.fontSize = next;
  saveSettings(settings.value);
}

function changeLineHeight(delta: number): void {
  const next = Math.max(1.4, Math.min(2.4, settings.value.lineHeight + delta));
  settings.value.lineHeight = next;
  saveSettings(settings.value);
}

function changeTheme(t: ReaderSettings["theme"]): void {
  settings.value.theme = t;
  saveSettings(settings.value);
}

// ============ 初始化 ============
async function init(): Promise<void> {
  if (!path.value) {
    ElMessage.warning("缺少文件路径参数");
    void router.replace("/files");
    return;
  }
  loading.value = true;
  try {
    const [meta, prog] = await Promise.all([
      openNovel(path.value),
      fetchProgress(path.value).catch(() => null),
    ]);
    novel.value = meta;
    progress.value = prog;
    const startIndex =
      prog && prog.chapterIndex >= 0 && prog.chapterIndex < meta.chapterCount
        ? prog.chapterIndex
        : 0;
    await loadChapter(startIndex);
  } catch {
    ElMessage.error("无法打开该文件，仅支持 TXT / EPUB 格式");
    void router.replace("/files");
  } finally {
    loading.value = false;
  }
}

function handleScroll(): void {
  scheduleSave();
}

function goBack(): void {
  void router.back();
}

// ============ 生命周期 ============
onMounted(() => {
  void init();
});

onBeforeUnmount(() => {
  if (saveTimer.value !== null) {
    window.clearTimeout(saveTimer.value);
  }
  stopTts();
  void persistProgress();
});
</script>

<template>
  <div class="reader">
    <!-- 顶部工具栏 -->
    <header class="reader__bar" :style="{ borderColor: theme.border }">
      <div class="reader__bar-left">
        <el-button text :icon="ArrowLeft" @click="goBack">返回</el-button>
        <div class="reader__title">
          <span class="reader__title-name">{{ title }}</span>
          <span v-if="author" class="reader__title-author">{{ author }}</span>
          <el-tag size="small" type="info" effect="plain">{{ formatLabel }}</el-tag>
        </div>
      </div>
      <div class="reader__bar-right">
        <el-button
          text
          :icon="List"
          :type="tocOpen ? 'primary' : ''"
          @click="tocOpen = !tocOpen"
        >
          目录
        </el-button>
        <el-button
          text
          :icon="Setting"
          :type="settingsOpen ? 'primary' : ''"
          @click="settingsOpen = !settingsOpen"
        >
          设置
        </el-button>
        <el-button
          text
          :icon="Microphone"
          :type="speaking ? 'primary' : ''"
          @click="toggleTts"
        >
          {{ speaking ? (ttsPaused ? "继续" : "暂停") : "朗读" }}
        </el-button>
      </div>
    </header>

    <div class="reader__body" :style="{ background: theme.bg }">
      <!-- 目录侧栏 -->
      <aside
        v-if="tocOpen && novel"
        class="reader__toc"
        :style="{ background: theme.bg, borderColor: theme.border, color: theme.color }"
      >
        <div class="reader__toc-title">目录（{{ novel.chapterCount }}）</div>
        <div class="reader__toc-list">
          <div
            v-for="c in novel.chapters"
            :key="c.index"
            class="reader__toc-item"
            :class="{ 'is-active': chapter && c.index === chapter.index }"
            @click="jumpTo(c.index)"
          >
            <span class="reader__toc-idx">{{ c.index + 1 }}</span>
            <span class="reader__toc-text">{{ c.title }}</span>
          </div>
        </div>
      </aside>

      <!-- 阅读设置面板 -->
      <aside
        v-if="settingsOpen"
        class="reader__settings"
        :style="{ background: theme.bg, borderColor: theme.border, color: theme.color }"
      >
        <div class="reader__settings-item">
          <span class="reader__settings-label">字号</span>
          <el-button size="small" @click="changeFontSize(-2)">A-</el-button>
          <span class="reader__settings-value">{{ settings.fontSize }}px</span>
          <el-button size="small" @click="changeFontSize(2)">A+</el-button>
        </div>
        <div class="reader__settings-item">
          <span class="reader__settings-label">行距</span>
          <el-button size="small" @click="changeLineHeight(-0.1)">-</el-button>
          <span class="reader__settings-value">{{ settings.lineHeight.toFixed(1) }}</span>
          <el-button size="small" @click="changeLineHeight(0.1)">+</el-button>
        </div>
        <div class="reader__settings-item">
          <span class="reader__settings-label">主题</span>
          <el-radio-group :model-value="settings.theme" size="small" @change="changeTheme">
            <el-radio-button value="paper">纸质</el-radio-button>
            <el-radio-button value="sepia">羊皮纸</el-radio-button>
            <el-radio-button value="dark">夜间</el-radio-button>
          </el-radio-group>
        </div>
        <el-switch
          v-model="settings.autoSave"
          active-text="自动保存进度"
          @change="saveSettings(settings)"
        />
      </aside>

      <!-- 正文区 -->
      <main
        ref="contentRef"
        class="reader__content"
        :style="contentStyle"
        v-loading="chapterLoading"
        @scroll="handleScroll"
      >
        <div v-if="chapter" class="reader__article" :style="fontSizeStyle">
          <h1 class="reader__chapter-title">{{ chapter.title }}</h1>
          <div class="reader__chapter-meta">
            第 {{ chapter.index + 1 }} / {{ chapter.total }} 章
            <el-progress
              :percentage="progress ? progress.percent : 0"
              :stroke-width="4"
              :show-text="false"
              style="width: 160px; margin-left: 12px"
            />
          </div>
          <div class="reader__chapter-body">{{ chapter.content }}</div>
          <div class="reader__chapter-nav">
            <el-button :icon="ArrowLeft" :disabled="!chapter || chapter.index === 0" @click="prevChapter">
              上一章
            </el-button>
            <el-button
              :icon="ArrowRight"
              :disabled="!chapter || chapter.index >= totalChapters - 1"
              @click="nextChapter"
            >
              下一章
            </el-button>
          </div>
        </div>
        <el-empty v-else-if="!loading" description="暂无内容" />
      </main>

      <!-- 朗读控制条 -->
      <div v-if="speaking" class="reader__tts">
        <span class="reader__tts-label">
          {{ ttsPaused ? "朗读已暂停" : "朗读中" }}
        </span>
        <el-button size="small" :icon="Microphone" @click="toggleTts">
          {{ ttsPaused ? "继续" : "暂停" }}
        </el-button>
        <el-button size="small" @click="stopTts">停止</el-button>
        <el-button size="small" @click="changeTtsRate(-0.25)">语速-</el-button>
        <span class="reader__tts-rate">{{ ttsRate.toFixed(2) }}x</span>
        <el-button size="small" @click="changeTtsRate(0.25)">语速+</el-button>
        <el-progress
          :percentage="ttsProgress"
          :stroke-width="4"
          :show-text="false"
          style="flex: 1; margin: 0 12px"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.reader {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.reader__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid;
  flex-shrink: 0;
}

.reader__bar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.reader__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.reader__title-name {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 40vw;
}

.reader__title-author {
  color: #909399;
  font-size: 13px;
}

.reader__bar-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.reader__body {
  display: flex;
  flex: 1;
  min-height: 0;
  position: relative;
}

.reader__toc {
  width: 260px;
  border-right: 1px solid;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.reader__toc-title {
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  opacity: 0.75;
  border-bottom: 1px solid;
  flex-shrink: 0;
}

.reader__toc-list {
  overflow-y: auto;
  flex: 1;
}

.reader__toc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  cursor: pointer;
  font-size: 13px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.08);
}

.reader__toc-item:hover {
  opacity: 0.85;
}

.reader__toc-item.is-active {
  font-weight: 600;
  background: rgba(64, 158, 255, 0.12);
}

.reader__toc-idx {
  font-size: 11px;
  opacity: 0.55;
  min-width: 22px;
  text-align: right;
}

.reader__toc-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.reader__settings {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 240px;
  border-left: 1px solid;
  z-index: 10;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-shadow: -4px 0 12px rgba(0, 0, 0, 0.06);
}

.reader__settings-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reader__settings-label {
  font-size: 13px;
  min-width: 32px;
}

.reader__settings-value {
  font-size: 13px;
  min-width: 44px;
  text-align: center;
}

.reader__content {
  flex: 1;
  overflow-y: auto;
  min-width: 0;
}

.reader__article {
  max-width: 720px;
  margin: 0 auto;
  padding: 32px 24px 80px;
}

.reader__chapter-title {
  font-size: 1.5em;
  margin: 0 0 8px;
  text-align: center;
}

.reader__chapter-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  opacity: 0.6;
  margin-bottom: 28px;
}

.reader__chapter-body {
  white-space: pre-wrap;
  word-break: break-word;
  text-align: justify;
}

.reader__chapter-nav {
  display: flex;
  justify-content: space-between;
  margin-top: 48px;
  padding-top: 20px;
  border-top: 1px dashed rgba(128, 128, 128, 0.3);
}

.reader__tts {
  position: fixed;
  bottom: 18px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 12px;
  border: 1px solid rgba(126, 210, 255, 0.25);
  background: rgba(8, 26, 54, 0.94);
  backdrop-filter: blur(8px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45);
  color: #e6f4ff;
}

.reader__tts-label {
  font-size: 12px;
  opacity: 0.75;
  margin-right: 4px;
}

.reader__tts-rate {
  font-size: 13px;
  font-weight: 600;
  min-width: 40px;
  text-align: center;
}
</style>
