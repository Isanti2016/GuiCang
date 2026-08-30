<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
  type UploadUserFile,
} from "element-plus";
import MarkdownIt from "markdown-it";
import hljs from "highlight.js";
import "highlight.js/styles/github-dark.css";
import {
  deleteFile,
  downloadFileAsBlob,
  listFiles,
  mkdir,
  moveFile,
  readText,
  renameFile,
  searchFiles,
  streamUrl,
  thumbnailUrl,
  uploadWithProgress,
  writeText,
  type FileEntry,
} from "@/api/file";

const md = new MarkdownIt({
  html: false,
  linkify: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang }).value;
      } catch {
        /* 高亮失败回退 */
      }
    }
    return "";
  },
});

// ============ 状态 ============
const currentDir = ref("");
const list = ref<FileEntry[]>([]);
const loading = ref(false);
const viewMode = ref<"grid" | "list">(
  (localStorage.getItem("gc.view-mode") as "grid" | "list") || "grid",
);
const selected = ref<Set<string>>(new Set());
const searchResults = ref<FileEntry[]>([]);
const searchKeyword = ref("");

const RECENT_KEY = "gc.recent-dirs";
const recentDirs = ref<string[]>([]);

const breadcrumbs = computed(() => {
  const parts = currentDir.value ? currentDir.value.split("/") : [];
  return parts.map((part, index) => ({
    name: part,
    path: parts.slice(0, index + 1).join("/"),
  }));
});

const selectedEntries = computed(() =>
  list.value.filter((e) => selected.value.has(e.path)),
);

// ============ 目录导航 ============
const locations = [
  { name: "根目录", path: "" },
  { name: "共享", path: "shared" },
  { name: "媒体", path: "media" },
  { name: "个人", path: "personal" },
];

/** 记忆最近访问目录（最多 8 个，持久化到 localStorage）。 */
function rememberDir(path: string): void {
  if (!path) return;
  recentDirs.value = [
    path,
    ...recentDirs.value.filter((d) => d !== path),
  ].slice(0, 8);
  localStorage.setItem(RECENT_KEY, JSON.stringify(recentDirs.value));
}

/** 切换当前目录并重新加载列表。 */
async function navigate(path: string): Promise<void> {
  currentDir.value = path;
  selected.value = new Set();
  searchResults.value = [];
  searchKeyword.value = "";
  rememberDir(path);
  await loadList();
}

/** 加载当前目录文件列表。 */
async function loadList(): Promise<void> {
  loading.value = true;
  try {
    list.value = await listFiles(currentDir.value);
  } finally {
    loading.value = false;
  }
}

// ============ 视图切换 ============
/** 切换网格/列表视图（持久化）。 */
function switchView(mode: "grid" | "list"): void {
  viewMode.value = mode;
  localStorage.setItem("gc.view-mode", mode);
}

// ============ 选择 ============
/** 是否处于选择模式（选择模式下单击条目为勾选，否则为打开）。 */
const selectionMode = ref(false);

/** 勾选/取消勾选条目。 */
function toggleSelect(entry: FileEntry): void {
  if (selected.value.has(entry.path)) selected.value.delete(entry.path);
  else selected.value.add(entry.path);
}

/** 清空勾选并退出选择模式。 */
function clearSelection(): void {
  selected.value = new Set();
  selectionMode.value = false;
}

/** 切换选择模式（关闭时清空勾选）。 */
function toggleSelectionMode(): void {
  selectionMode.value = !selectionMode.value;
  if (!selectionMode.value) {
    selected.value = new Set();
  }
}

// ============ 条目交互 ============
/** 单击条目：选择模式下切换勾选；否则目录进入、可预览文件打开预览。 */
function handleEntryClick(entry: FileEntry): void {
  if (selectionMode.value) {
    toggleSelect(entry);
    return;
  }
  openEntry(entry);
}

/** 打开条目：目录进入、可预览文件预览、其余提示。 */
function openEntry(entry: FileEntry): void {
  if (entry.dir) {
    void navigate(entry.path);
  } else if (canPreview(entry)) {
    void openPreview(entry);
  } else {
    ElMessage.info(`「${entry.name}」不支持内联预览，可下载查看`);
  }
}

/** 双击条目（兼容习惯，与单击一致）。 */
function handleEntryDblClick(entry: FileEntry): void {
  openEntry(entry);
}

/** 表格行点击：点 selection 勾选列时不打开条目。 */
function handleRowClick(
  row: FileEntry,
  column: { type?: string } = {},
): void {
  if (column.type !== "selection") openEntry(row);
}

/** 表格勾选变化：同步勾选集。 */
function handleSelectionChange(rows: FileEntry[]): void {
  selected.value = new Set(rows.map((r) => r.path));
}

/** 点击卡片勾选角标：仅切换勾选，不打开。 */
function handleCheckClick(event: Event, entry: FileEntry): void {
  event.stopPropagation();
  toggleSelect(entry);
}

/** 格式化字节数为人类可读大小。 */
const formatSize = (size: number): string => {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`;
};

/** 格式化修改时间为本地字符串。 */
const formatTime = (mtime: number): string =>
  mtime ? new Date(mtime).toLocaleString("zh-CN", { hour12: false }) : "--";

/** 是否支持内联预览（图片/视频/文档）。 */
const canPreview = (entry: FileEntry): boolean =>
  ["image", "video", "note"].includes(entry.kind);

const kindLabel: Record<string, string> = {
  dir: "目录",
  image: "图片",
  video: "视频",
  note: "文档",
  other: "文件",
};

// ============ 新建目录 ============
const mkdirDialog = ref(false);
const mkdirForm = reactive({ name: "" });
const mkdirFormRef = ref<FormInstance>();
const mkdirRules: FormRules = {
  name: [{ required: true, message: "请输入目录名", trigger: "blur" }],
};

/** 新建目录（校验后调用后端）。 */
async function handleMkdir(): Promise<void> {
  if (!mkdirFormRef.value) return;
  const valid = await mkdirFormRef.value.validate().catch(() => false);
  if (!valid) return;
  const path = currentDir.value
    ? `${currentDir.value}/${mkdirForm.name}`
    : mkdirForm.name;
  await mkdir(path);
  ElMessage.success("目录已创建");
  mkdirDialog.value = false;
  mkdirForm.name = "";
  await loadList();
}

// ============ 上传（拖拽 + 进度） ============
const uploadDialog = ref(false);
const uploadFiles = ref<UploadUserFile[]>([]);
const uploadProgress = ref(0);
const uploading = ref(false);

/** 逐文件上传（带总体进度）。el-upload 的 file-list 元素为 UploadUserFile，取 raw 原生 File。 */
async function handleUpload(): Promise<void> {
  if (uploadFiles.value.length === 0) return;
  uploading.value = true;
  uploadProgress.value = 0;
  let done = 0;
  const total = uploadFiles.value.length;
  try {
    for (const item of uploadFiles.value) {
      const file = item.raw ?? (item as unknown as File);
      if (!file) continue;
      await uploadWithProgress(currentDir.value, file, (percent) => {
        uploadProgress.value = Math.round(
          ((done + percent / 100) / total) * 100,
        );
      });
      done += 1;
    }
    ElMessage.success(`已上传 ${done} 个文件`);
    uploadDialog.value = false;
    uploadFiles.value = [];
    await loadList();
  } finally {
    uploading.value = false;
  }
}

// ============ 重命名 / 移动 / 删除 ============
const renameDialog = ref(false);
const renameForm = reactive({ name: "" });
const renameTarget = ref<FileEntry | null>(null);
const renameFormRef = ref<FormInstance>();

/** 打开重命名对话框。 */
function openRename(entry: FileEntry): void {
  renameTarget.value = entry;
  renameForm.name = entry.name;
  renameDialog.value = true;
}

/** 提交重命名。 */
async function handleRename(): Promise<void> {
  if (!renameFormRef.value || !renameTarget.value) return;
  const valid = await renameFormRef.value.validate().catch(() => false);
  if (!valid) return;
  await renameFile(renameTarget.value.path, renameForm.name);
  ElMessage.success("已重命名");
  renameDialog.value = false;
  await loadList();
}

/** 删除一个或多个条目（确认后软删除进回收站）。 */
async function handleDelete(entries: FileEntry[]): Promise<void> {
  const names = entries.map((e) => e.name).join("、");
  const hasDir = entries.some((e) => e.dir);
  await ElMessageBox.confirm(
    `确认删除「${names}」？${hasDir ? "目录将递归删除。" : ""}`,
    "删除确认",
    { type: "warning", confirmButtonText: "删除" },
  );
  for (const entry of entries) {
    await deleteFile(entry.path, entry.dir);
  }
  ElMessage.success("已删除");
  clearSelection();
  await loadList();
}

/** 批量删除勾选条目。 */
async function handleBatchDelete(): Promise<void> {
  if (selectedEntries.value.length === 0) return;
  await handleDelete(selectedEntries.value);
}

const moveDialog = ref(false);
const moveTarget = ref<FileEntry | null>(null);

interface TreeNode {
  name: string;
  path: string;
  children?: TreeNode[];
  leaf?: boolean;
}

const treeProps = { label: "name", children: "children", isLeaf: "leaf" };

/** 懒加载移动目标目录树（el-tree 节点展开时触发）。 */
async function loadMoveTree(
  node: { level?: number; path?: string },
  resolve: (data: TreeNode[]) => void,
): Promise<void> {
  const base = node.level === 0 ? "" : (node.path ?? "");
  const children = await listFiles(base);
  resolve(
    children
      .filter((c) => c.dir)
      .map((c) => ({ name: c.name, path: c.path, leaf: false })),
  );
}

/** 打开移动对话框。 */
async function openMove(entry: FileEntry): Promise<void> {
  moveTarget.value = entry;
  moveTargetNode.value = "";
  moveDialog.value = true;
}

/** 提交移动。 */
async function handleMove(): Promise<void> {
  if (!moveTarget.value || !moveTargetNode.value) return;
  await moveFile(moveTarget.value.path, moveTargetNode.value);
  ElMessage.success("已移动");
  moveDialog.value = false;
  await loadList();
}

const moveTargetNode = ref<string>("");

/** 选中目录树节点为目标目录。 */
function handleMoveNodeClick(node: TreeNode): void {
  moveTargetNode.value = node.path;
}

// ============ 下载 ============
/** 下载文件为本地文件。 */
async function handleDownload(entry: FileEntry): Promise<void> {
  if (entry.dir) {
    ElMessage.info("目录请使用打包功能（暂未提供）");
    return;
  }
  try {
    await downloadFileAsBlob(entry.path);
    ElMessage.success("开始下载");
  } catch {
    ElMessage.error("下载失败");
  }
}

// ============ 预览 ============
const previewOpen = ref(false);
const previewEntry = ref<FileEntry | null>(null);
const previewMode = ref<"view" | "edit">("view");
const previewText = ref("");
const editingText = ref("");

/** 打开预览抽屉（文档读取内容）。 */
async function openPreview(entry: FileEntry): Promise<void> {
  previewEntry.value = entry;
  previewOpen.value = true;
  if (entry.kind === "note") {
    previewMode.value = "view";
    previewText.value = await readText(entry.path);
    editingText.value = previewText.value;
  }
}

const renderedMarkdown = computed(() => md.render(previewText.value));

/** 进入编辑模式。 */
function enterEdit(): void {
  editingText.value = previewText.value;
  previewMode.value = "edit";
}

/** 保存编辑内容并回到预览模式。 */
async function handleSave(): Promise<void> {
  if (!previewEntry.value) return;
  await writeText(previewEntry.value.path, editingText.value);
  previewText.value = editingText.value;
  previewMode.value = "view";
  ElMessage.success("已保存");
}

// ============ 搜索 ============
/** 按关键字搜索文件。 */
async function handleSearch(): Promise<void> {
  if (!searchKeyword.value.trim()) {
    searchResults.value = [];
    return;
  }
  searchResults.value = await searchFiles(searchKeyword.value.trim());
}

/** 跳转到搜索结果所在目录并定位。 */
function useSearchResult(entry: FileEntry): void {
  searchResults.value = [];
  searchKeyword.value = "";
  const slash = entry.path.lastIndexOf("/");
  void navigate(slash >= 0 ? entry.path.substring(0, slash) : "");
  void nextTick(() => {
    // 定位到目标文件（列表/网格中闪烁高亮）
  });
}

// ============ 初始化 ============
onMounted(() => {
  const saved = localStorage.getItem(RECENT_KEY);
  if (saved) {
    try {
      recentDirs.value = JSON.parse(saved) as string[];
    } catch {
      recentDirs.value = [];
    }
  }
  void loadList();
});
</script>

<template>
  <div class="file-manager">
    <!-- 左侧导航 -->
    <aside class="file-manager__nav">
      <div class="file-manager__nav-search">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文件"
          clearable
          @keyup.enter="handleSearch"
          @clear="searchResults = []"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <template v-if="searchResults.length > 0">
        <div class="file-manager__nav-title">搜索结果</div>
        <div class="file-manager__search-list">
          <div
            v-for="item in searchResults"
            :key="item.path"
            class="file-manager__search-item"
            @click="useSearchResult(item)"
          >
            <el-icon :color="item.dir ? '#6ec8ff' : '#8fb6dd'">
              <Folder v-if="item.dir" />
              <Document v-else />
            </el-icon>
            <span class="file-manager__search-name">{{ item.name }}</span>
            <span class="file-manager__search-path">{{ item.path }}</span>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="file-manager__nav-title">位置</div>
        <div
          class="file-manager__nav-item"
          v-for="loc in locations"
          :key="loc.path"
          @click="navigate(loc.path)"
        >
          <el-icon color="#6ec8ff"><FolderOpened /></el-icon>
          <span>{{ loc.name }}</span>
        </div>

        <div
          v-if="recentDirs.length"
          class="file-manager__nav-title file-manager__nav-title--recent"
        >
          最近访问
        </div>
        <div
          v-for="dir in recentDirs"
          :key="dir"
          class="file-manager__nav-item"
          @click="navigate(dir)"
        >
          <el-icon color="#d4af37"><Clock /></el-icon>
          <span class="file-manager__nav-path">{{ dir }}</span>
        </div>
      </template>
    </aside>

    <!-- 主区 -->
    <main class="file-manager__main">
      <!-- 工具栏 -->
      <div class="file-manager__toolbar">
        <el-breadcrumb separator="/" class="file-manager__breadcrumb">
          <el-breadcrumb-item @click="navigate('')">根目录</el-breadcrumb-item>
          <el-breadcrumb-item
            v-for="crumb in breadcrumbs"
            :key="crumb.path"
            @click="navigate(crumb.path)"
          >
            {{ crumb.name }}
          </el-breadcrumb-item>
        </el-breadcrumb>

        <div class="file-manager__actions">
          <el-radio-group v-model="viewMode" size="small" @change="switchView">
            <el-radio-button value="grid"
              ><el-icon><Grid /></el-icon
            ></el-radio-button>
            <el-radio-button value="list"
              ><el-icon><List /></el-icon
            ></el-radio-button>
          </el-radio-group>
          <el-button size="small" @click="loadList"
            ><el-icon><Refresh /></el-icon>刷新</el-button
          >
          <el-button
            size="small"
            :type="selectionMode ? 'primary' : 'default'"
            plain
            @click="toggleSelectionMode"
          >
            <el-icon><Select /></el-icon>{{ selectionMode ? "完成" : "选择" }}
          </el-button>
          <el-button
            size="small"
            type="primary"
            plain
            @click="mkdirDialog = true"
          >
            <el-icon><FolderAdd /></el-icon>新建目录
          </el-button>
          <el-button size="small" type="primary" @click="uploadDialog = true">
            <el-icon><Upload /></el-icon>上传
          </el-button>
        </div>
      </div>

      <!-- 多选工具条（选择模式或有勾选时显示） -->
      <transition name="gc-fade">
        <div
          v-if="selectionMode || selected.size > 0"
          class="file-manager__selectbar"
        >
          <span>
            已选 {{ selected.size }} 项
            <el-button
              link
              size="small"
              type="primary"
              @click="
                selected =
                  new Set(
                    list.filter((e) => !selected.has(e.path)).map((e) => e.path),
                  )
              "
              >全选</el-button
            >
            <el-button
              v-if="selectionMode"
              link
              size="small"
              @click="toggleSelectionMode"
              >退出选择</el-button
            >
          </span>
          <div class="file-manager__selectbar-actions">
            <el-button size="small" type="danger" plain @click="handleBatchDelete"
              >批量删除</el-button
            >
            <el-button size="small" @click="clearSelection">取消选择</el-button>
          </div>
        </div>
      </transition>

      <!-- 内容区 -->
      <div v-loading="loading" class="file-manager__content">
        <!-- 网格视图 -->
        <div v-if="viewMode === 'grid'" class="file-manager__grid">
          <div
            v-for="entry in list"
            :key="entry.path"
            class="file-manager__card"
            :class="{ 'is-selected': selected.has(entry.path) }"
            @click="handleEntryClick(entry)"
            @dblclick="handleEntryDblClick(entry)"
          >
            <!-- 勾选角标：hover 或选择模式可见，点击仅勾选不打开 -->
            <span
              v-if="selectionMode || selected.has(entry.path)"
              class="file-manager__card-check"
              :class="{ 'file-manager__card-check--on': selected.has(entry.path) }"
              @click="handleCheckClick($event, entry)"
            >
              <el-icon v-if="selected.has(entry.path)"><Check /></el-icon>
            </span>
            <div class="file-manager__card-media">
              <img
                v-if="['image', 'video'].includes(entry.kind)"
                :src="thumbnailUrl(entry.path)"
                loading="lazy"
              />
              <el-icon
                v-else
                :size="34"
                :color="entry.dir ? '#6ec8ff' : '#8fb6dd'"
              >
                <Folder v-if="entry.dir" />
                <Document v-else-if="entry.kind === 'other'" />
                <Notebook v-else-if="entry.kind === 'note'" />
              </el-icon>
              <span class="file-manager__card-kind">{{
                kindLabel[entry.kind]
              }}</span>
            </div>
            <div class="file-manager__card-info">
              <div class="file-manager__card-name" :title="entry.name">
                {{ entry.name }}
              </div>
              <div class="file-manager__card-meta">
                {{ entry.dir ? "--" : formatSize(entry.size) }} ·
                {{ formatTime(entry.mtime).slice(5, 16) }}
              </div>
            </div>
            <div class="file-manager__card-actions">
              <el-button
                link
                size="small"
                @click.stop="openPreview(entry)"
                :disabled="!canPreview(entry)"
                >预览</el-button
              >
              <el-button
                link
                size="small"
                @click.stop="handleDownload(entry)"
                :disabled="entry.dir"
                >下载</el-button
              >
              <el-button link size="small" @click.stop="openMove(entry)"
                >移动</el-button
              >
              <el-button link size="small" @click.stop="openRename(entry)"
                >重命名</el-button
              >
              <el-button
                link
                type="danger"
                size="small"
                @click.stop="handleDelete([entry])"
                >删除</el-button
              >
            </div>
          </div>

          <el-empty
            v-if="!loading && list.length === 0"
            description="目录为空"
          />
        </div>

        <!-- 列表视图 -->
        <el-table
          v-else
          v-loading="loading"
          :data="list"
          size="small"
          class="file-manager__table"
          @row-click="handleRowClick"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="40" />
          <el-table-column label="" width="46">
            <template #default="{ row }">
              <el-icon v-if="row.dir" color="#6ec8ff"><Folder /></el-icon>
              <img
                v-else-if="['image', 'video'].includes(row.kind)"
                :src="thumbnailUrl(row.path)"
                class="file-manager__thumb"
                loading="lazy"
              />
              <el-icon v-else color="#8fb6dd"><Document /></el-icon>
            </template>
          </el-table-column>
          <el-table-column
            prop="name"
            label="名称"
            min-width="220"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span
                class="file-manager__name"
                @click="
                  row.dir
                    ? navigate(row.path)
                    : canPreview(row) && openPreview(row)
                "
                >{{ row.name }}</span
              >
            </template>
          </el-table-column>
          <el-table-column prop="size" label="大小" width="110">
            <template #default="{ row }">{{
              row.dir ? "--" : formatSize(row.size)
            }}</template>
          </el-table-column>
          <el-table-column prop="mtime" label="修改时间" width="170">
            <template #default="{ row }">{{ formatTime(row.mtime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="250">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                @click="openPreview(row)"
                :disabled="!canPreview(row)"
                >预览</el-button
              >
              <el-button
                link
                type="primary"
                size="small"
                @click="handleDownload(row)"
                :disabled="row.dir"
                >下载</el-button
              >
              <el-button link type="primary" size="small" @click="openMove(row)"
                >移动</el-button
              >
              <el-button
                link
                type="primary"
                size="small"
                @click="openRename(row)"
                >重命名</el-button
              >
              <el-button
                link
                type="danger"
                size="small"
                @click="handleDelete([row])"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </div>
    </main>

    <!-- 预览抽屉 -->
    <el-drawer
      v-model="previewOpen"
      :title="previewEntry?.name"
      size="60%"
      class="file-manager__drawer"
    >
      <template v-if="previewEntry">
        <div
          v-if="previewEntry.kind === 'image'"
          class="file-manager__preview-image"
        >
          <img
            :src="streamUrl(previewEntry.path)"
            style="max-width: 100%; border-radius: 8px"
          />
        </div>
        <video
          v-else-if="previewEntry.kind === 'video'"
          :src="streamUrl(previewEntry.path)"
          controls
          style="width: 100%; border-radius: 8px"
        />
        <div v-else-if="previewEntry.kind === 'note'">
          <div class="file-manager__preview-toolbar">
            <el-button
              v-if="previewMode === 'view'"
              size="small"
              type="primary"
              @click="enterEdit"
              >编辑</el-button
            >
            <template v-else>
              <el-button size="small" type="success" @click="handleSave"
                >保存</el-button
              >
              <el-button size="small" @click="previewMode = 'view'"
                >取消</el-button
              >
            </template>
          </div>
          <el-input
            v-if="previewMode === 'edit'"
            v-model="editingText"
            type="textarea"
            :rows="20"
          />
          <div
            v-else
            class="file-manager__markdown"
            v-html="renderedMarkdown"
          />
        </div>
        <el-empty v-else description="该类型暂不支持预览，可下载查看" />
      </template>
    </el-drawer>

    <!-- 新建目录 -->
    <el-dialog v-model="mkdirDialog" title="新建目录" width="400px">
      <el-form ref="mkdirFormRef" :model="mkdirForm" :rules="mkdirRules">
        <el-form-item label="目录名" prop="name">
          <el-input
            v-model="mkdirForm.name"
            placeholder="新目录名"
            @keyup.enter="handleMkdir"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mkdirDialog = false">取消</el-button>
        <el-button type="primary" @click="handleMkdir">创建</el-button>
      </template>
    </el-dialog>

    <!-- 上传 -->
    <el-dialog v-model="uploadDialog" title="上传文件" width="500px">
      <el-upload
        v-model:file-list="uploadFiles"
        drag
        multiple
        :auto-upload="false"
        :on-change="() => (uploadProgress = 0)"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            单文件 ≤1G · 支持多选 · 危险扩展名会被拦截
          </div>
        </template>
      </el-upload>
      <el-progress
        v-if="uploading"
        :percentage="uploadProgress"
        :stroke-width="10"
        class="file-manager__upload-progress"
      />
      <template #footer>
        <el-button @click="uploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload"
          >上传</el-button
        >
      </template>
    </el-dialog>

    <!-- 重命名 -->
    <el-dialog v-model="renameDialog" title="重命名" width="400px">
      <el-form ref="renameFormRef" :model="renameForm" :rules="mkdirRules">
        <el-form-item label="新名称" prop="name">
          <el-input v-model="renameForm.name" @keyup.enter="handleRename" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialog = false">取消</el-button>
        <el-button type="primary" @click="handleRename">确定</el-button>
      </template>
    </el-dialog>

    <!-- 移动 -->
    <el-dialog v-model="moveDialog" title="移动到目录" width="460px">
      <el-tree
        node-key="path"
        :props="treeProps"
        :expand-on-click-node="false"
        lazy
        :load="loadMoveTree"
        highlight-current
        @node-click="handleMoveNodeClick"
      />
      <div class="file-manager__move-target">
        目标：{{ moveTargetNode || "（根目录）" }}
      </div>
      <template #footer>
        <el-button @click="moveDialog = false">取消</el-button>
        <el-button type="primary" @click="handleMove">移动</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.file-manager {
  display: flex;
  gap: 14px;
  height: calc(100vh - 120px);
  font-family: var(--gc-font-sans, inherit);
}

/* ---------- 左侧导航（玻璃面板） ---------- */
.file-manager__nav {
  width: 220px;
  flex-shrink: 0;
  background: rgba(7, 22, 46, 0.7);
  border: 1px solid rgba(140, 220, 255, 0.2);
  border-radius: 12px;
  padding: 12px;
  backdrop-filter: blur(8px);
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}

.file-manager__nav-title {
  font-size: 12px;
  letter-spacing: 2px;
  color: #8fb6dd;
  margin: 10px 4px 4px;
}

.file-manager__nav-title--recent {
  border-top: 1px solid rgba(212, 175, 55, 0.3);
  padding-top: 10px;
}

.file-manager__nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 8px;
  cursor: pointer;
  color: #bfdcf8;
  font-size: 13px;
  transition: background 0.15s;
}

.file-manager__nav-item:hover {
  background: rgba(110, 200, 255, 0.12);
}

.file-manager__nav-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-manager__search-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.file-manager__search-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
}

.file-manager__search-item:hover {
  background: rgba(110, 200, 255, 0.12);
}

.file-manager__search-name {
  font-weight: 500;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-manager__search-path {
  color: #8fb6dd;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------- 主区 ---------- */
.file-manager__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-manager__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: rgba(7, 22, 46, 0.6);
  border: 1px solid rgba(140, 220, 255, 0.18);
  border-radius: 12px;
  padding: 10px 14px;
  backdrop-filter: blur(8px);
}

.file-manager__breadcrumb {
  font-size: 13px;
}

.file-manager__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 多选工具条 */
.file-manager__selectbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  background: rgba(212, 175, 55, 0.1);
  border: 1px solid rgba(212, 175, 55, 0.45);
  border-radius: 10px;
  font-size: 13px;
  color: #e8d9a8;
}

.file-manager__selectbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.gc-fade-enter-active,
.gc-fade-leave-active {
  transition: opacity 0.2s;
}

.gc-fade-enter-from,
.gc-fade-leave-to {
  opacity: 0;
}

/* ---------- 内容区 ---------- */
.file-manager__content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: rgba(7, 22, 46, 0.45);
  border: 1px solid rgba(140, 220, 255, 0.14);
  border-radius: 12px;
  padding: 12px;
}

/* 网格视图 */
.file-manager__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: 12px;
}

.file-manager__card {
  position: relative;
  background: rgba(9, 28, 58, 0.75);
  border: 1px solid rgba(140, 220, 255, 0.16);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition:
    border-color 0.15s,
    transform 0.15s,
    box-shadow 0.15s;
}

.file-manager__card:hover {
  border-color: rgba(110, 200, 255, 0.5);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(2, 10, 26, 0.5);
}

.file-manager__card.is-selected {
  border-color: rgba(212, 175, 55, 0.8);
  box-shadow:
    0 0 0 1px rgba(212, 175, 55, 0.4),
    0 8px 24px rgba(2, 10, 26, 0.5);
}

/* 勾选角标：hover 或选择模式显示，点击仅勾选不打开 */
.file-manager__card-check {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 6;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.75);
  background: rgba(3, 12, 28, 0.55);
  color: transparent;
  font-size: 14px;
  cursor: pointer;
  opacity: 0;
  transition:
    opacity 0.15s,
    background 0.15s,
    color 0.15s;
}

.file-manager__card:hover .file-manager__card-check,
.file-manager__card-check--on {
  opacity: 1;
}

.file-manager__card-check--on {
  background: #d4af37;
  border-color: #d4af37;
  color: #061228;
}

.file-manager__card-media {
  height: 104px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(4, 14, 32, 0.6);
  position: relative;
  overflow: hidden;
}

.file-manager__card-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.file-manager__card-kind {
  position: absolute;
  right: 6px;
  top: 6px;
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 8px;
  background: rgba(4, 12, 28, 0.72);
  color: #bfe9ff;
  border: 1px solid rgba(140, 220, 255, 0.25);
}

.file-manager__card-info {
  padding: 8px 10px;
}

.file-manager__card-name {
  font-size: 13px;
  font-weight: 500;
  color: #eaf6ff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-manager__card-meta {
  margin-top: 3px;
  font-size: 11px;
  color: #8fb6dd;
  font-variant-numeric: tabular-nums;
}

.file-manager__card-actions {
  display: none;
  padding: 0 8px 8px;
  gap: 2px;
}

.file-manager__card:hover .file-manager__card-actions {
  display: flex;
  flex-wrap: wrap;
}

/* 列表视图 */
.file-manager__table {
  --el-table-border-color: transparent;
}

.file-manager__thumb {
  width: 32px;
  height: 32px;
  object-fit: cover;
  border-radius: 6px;
}

.file-manager__name {
  cursor: pointer;
  font-weight: 500;
}

.file-manager__name:hover {
  color: #6ec8ff;
}

/* 预览 */
.file-manager__preview-image {
  text-align: center;
}

.file-manager__preview-toolbar {
  margin-bottom: 10px;
}

.file-manager__markdown {
  font-size: 14px;
  line-height: 1.8;
  color: #cfddf0;
}

.file-manager__markdown :deep(h1),
.file-manager__markdown :deep(h2),
.file-manager__markdown :deep(h3) {
  color: #eaf6ff;
  border-bottom: 1px solid rgba(212, 175, 55, 0.35);
  padding-bottom: 6px;
}

.file-manager__markdown :deep(code) {
  background: rgba(110, 200, 255, 0.1);
  color: #9fd4ff;
  border-radius: 4px;
  padding: 1px 6px;
}

.file-manager__markdown :deep(pre) {
  background: rgba(4, 14, 32, 0.8);
  border: 1px solid rgba(140, 220, 255, 0.18);
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
}

.file-manager__markdown :deep(pre code) {
  background: transparent;
  padding: 0;
}

.file-manager__markdown :deep(blockquote) {
  border-left: 3px solid rgba(212, 175, 55, 0.6);
  margin: 8px 0;
  padding-left: 12px;
  color: #9fb8d8;
}

.file-manager__markdown :deep(a) {
  color: #6ec8ff;
}

/* 上传进度 */
.file-manager__upload-progress {
  margin-top: 12px;
}

/* 移动 */
.file-manager__move-target {
  margin-top: 10px;
  color: #8fb6dd;
  font-size: 13px;
}

/* ---------- 移动端适配 ---------- */
@media (max-width: 768px) {
  .file-manager {
    flex-direction: column;
    height: auto;
    min-height: calc(100vh - 80px);
    gap: 10px;
  }

  /* 左侧导航转为顶部横向滚动条 */
  .file-manager__nav {
    width: 100%;
    flex-direction: row;
    overflow-x: auto;
    overflow-y: hidden;
    align-items: center;
    flex-wrap: nowrap;
    padding: 8px 10px;
  }

  .file-manager__nav-search {
    min-width: 140px;
    flex-shrink: 0;
  }

  .file-manager__nav-title,
  .file-manager__nav-title--recent {
    display: none;
  }

  .file-manager__nav-item {
    flex-shrink: 0;
    white-space: nowrap;
  }

  .file-manager__search-list {
    display: flex;
    gap: 6px;
    overflow-x: auto;
  }

  .file-manager__search-item {
    flex-shrink: 0;
    flex-direction: column;
    align-items: flex-start;
    min-width: 120px;
  }

  .file-manager__main {
    min-width: 0;
  }

  .file-manager__toolbar {
    flex-wrap: wrap;
    gap: 6px;
  }

  /* 工具栏按钮组换行 */
  .file-manager__toolbar-actions {
    flex-wrap: wrap;
  }

  /* 预览抽屉在移动端近全宽 */
  .file-manager__drawer {
    width: 94% !important;
  }
}
</style>
