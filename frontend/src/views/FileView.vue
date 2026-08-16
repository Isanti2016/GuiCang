<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import MarkdownIt from "markdown-it";
import hljs from "highlight.js";
import "highlight.js/styles/github.css";
import {
  deleteFile,
  listFiles,
  mkdir,
  moveFile,
  readText,
  renameFile,
  searchFiles,
  streamUrl,
  thumbnailUrl,
  upload,
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
        // 高亮失败回退
      }
    }
    return "";
  },
});

// ---------- 目录树 ----------
const treeData = ref<FileEntry[]>([
  { name: "根目录", path: "", dir: true, size: 0, mtime: 0, kind: "dir" },
]);
const currentDir = ref("");
const breadcrumbs = computed(() => {
  const parts = currentDir.value ? currentDir.value.split("/") : [];
  return parts.map((part, index) => ({
    name: part,
    path: parts.slice(0, index + 1).join("/"),
  }));
});

async function loadTree(): Promise<void> {
  treeData.value = [
    { name: "根目录", path: "", dir: true, size: 0, mtime: 0, kind: "dir" },
  ];
}

async function handleNodeClick(node: FileEntry): Promise<void> {
  if (node.dir) {
    await navigate(node.path);
  }
}

async function navigate(path: string): Promise<void> {
  currentDir.value = path;
  await loadList();
}

// ---------- 文件列表 ----------
const list = ref<FileEntry[]>([]);
const loading = ref(false);
async function loadList(): Promise<void> {
  loading.value = true;
  try {
    list.value = await listFiles(currentDir.value);
  } finally {
    loading.value = false;
  }
}

const formatSize = (size: number): string => {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`;
};

const formatTime = (mtime: number): string =>
  mtime ? new Date(mtime).toLocaleString("zh-CN", { hour12: false }) : "--";

const canPreview = (entry: FileEntry): boolean =>
  ["image", "video", "note"].includes(entry.kind);

// ---------- 新建目录 ----------
const mkdirDialog = ref(false);
const mkdirForm = reactive({ name: "" });
const mkdirFormRef = ref<FormInstance>();
const mkdirRules: FormRules = {
  name: [{ required: true, message: "请输入目录名", trigger: "blur" }],
};

async function handleMkdir(): Promise<void> {
  if (!mkdirFormRef.value) return;
  const valid = await mkdirFormRef.value.validate().catch(() => false);
  if (!valid) return;
  const path = currentDir.value
    ? `${currentDir.value}/${mkdirForm.name}`
    : mkdirForm.name;
  await mkdir(path);
  ElMessage.success("已创建");
  mkdirDialog.value = false;
  mkdirForm.name = "";
  await loadList();
}

// ---------- 上传 ----------
const uploadDialog = ref(false);
const uploadFiles = ref<File[]>([]);
const uploading = ref(false);

async function handleUpload(): Promise<void> {
  if (uploadFiles.value.length === 0) return;
  uploading.value = true;
  try {
    for (const file of uploadFiles.value) {
      await upload(currentDir.value, file);
    }
    ElMessage.success("上传完成");
    uploadDialog.value = false;
    uploadFiles.value = [];
    await loadList();
  } finally {
    uploading.value = false;
  }
}

// ---------- 重命名 / 移动 / 删除 ----------
const renameDialog = ref(false);
const renameForm = reactive({ name: "" });
const renameTarget = ref<FileEntry | null>(null);
const renameFormRef = ref<FormInstance>();

async function handleRename(): Promise<void> {
  if (!renameFormRef.value || !renameTarget.value) return;
  const valid = await renameFormRef.value.validate().catch(() => false);
  if (!valid) return;
  await renameFile(renameTarget.value.path, renameForm.name);
  ElMessage.success("已重命名");
  renameDialog.value = false;
  await loadList();
}

async function handleDelete(entry: FileEntry): Promise<void> {
  const isDir = entry.dir;
  const message = isDir
    ? `目录「${entry.name}」将递归删除，确认？`
    : `确认删除「${entry.name}」？`;
  await ElMessageBox.confirm(message, "删除确认", { type: "warning" });
  await deleteFile(entry.path, isDir);
  ElMessage.success("已删除");
  await loadList();
}

const moveDialog = ref(false);
const moveTarget = ref<FileEntry | null>(null);
const moveDir = ref("");
const moveCandidates = ref<FileEntry[]>([]);

async function openMove(entry: FileEntry): Promise<void> {
  moveTarget.value = entry;
  moveDir.value = "";
  moveDialog.value = true;
  await refreshMoveCandidates();
}

async function refreshMoveCandidates(): Promise<void> {
  moveCandidates.value = await listFiles(moveDir.value);
}

async function handleMove(): Promise<void> {
  if (!moveTarget.value) return;
  await moveFile(moveTarget.value.path, moveDir.value);
  ElMessage.success("已移动");
  moveDialog.value = false;
  await loadList();
}

// ---------- 预览（md/txt/图片/视频） ----------
const previewOpen = ref(false);
const previewEntry = ref<FileEntry | null>(null);
const previewMode = ref<"view" | "edit">("view");
const previewText = ref("");
const editingText = ref("");

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

function enterEdit(): void {
  editingText.value = previewText.value;
  previewMode.value = "edit";
}

async function handleSave(): Promise<void> {
  if (!previewEntry.value) return;
  await writeText(previewEntry.value.path, editingText.value);
  previewText.value = editingText.value;
  previewMode.value = "view";
  ElMessage.success("已保存");
}

// ---------- 搜索 ----------
const searchKeyword = ref("");
const searchResults = ref<FileEntry[]>([]);

async function handleSearch(): Promise<void> {
  if (!searchKeyword.value.trim()) {
    searchResults.value = [];
    return;
  }
  searchResults.value = await searchFiles(searchKeyword.value.trim());
}

function useSearchResult(entry: FileEntry): void {
  searchResults.value = [];
  searchKeyword.value = "";
  const slash = entry.path.lastIndexOf("/");
  if (slash >= 0) {
    void navigate(entry.path.substring(0, slash));
  }
}

onMounted(async () => {
  await loadTree();
  await loadList();
});

const refresh = (): void => {
  void loadList();
  void loadTree();
};
</script>

<template>
  <el-container class="file-manager">
    <el-aside width="240px" class="file-manager__tree">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索文件"
        clearable
        @keyup.enter="handleSearch"
        @clear="searchResults = []"
      >
        <template #append>
          <el-button @click="handleSearch">搜</el-button>
        </template>
      </el-input>

      <template v-if="searchResults.length > 0">
        <el-scrollbar class="file-manager__search">
          <div
            v-for="item in searchResults"
            :key="item.path"
            class="file-manager__search-item"
            @click="useSearchResult(item)"
          >
            <el-icon><Document /></el-icon>
            <span class="file-manager__search-name">{{ item.name }}</span>
            <span class="file-manager__search-path">{{ item.path }}</span>
          </div>
        </el-scrollbar>
      </template>

      <el-tree
        v-else
        :data="treeData"
        node-key="path"
        :props="{ label: 'name', children: 'children' }"
        :expand-on-click-node="false"
        @node-click="handleNodeClick"
      />
    </el-aside>

    <el-main class="file-manager__main">
      <div class="file-manager__toolbar">
        <el-breadcrumb separator="/">
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
          <el-button size="small" @click="refresh">刷新</el-button>
          <el-button size="small" type="primary" @click="mkdirDialog = true"
            >新建目录</el-button
          >
          <el-button size="small" type="primary" @click="uploadDialog = true"
            >上传</el-button
          >
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        size="small"
        @row-dblclick="openPreview"
      >
        <el-table-column label="" width="44">
          <template #default="{ row }">
            <el-icon v-if="row.dir" color="#409eff"><Folder /></el-icon>
            <img
              v-else-if="['image', 'video'].includes(row.kind)"
              :src="thumbnailUrl(row.path)"
              class="file-manager__thumb"
              loading="lazy"
            />
            <el-icon v-else color="#909399"><Document /></el-icon>
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
            >
              {{ row.name }}
            </span>
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
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="openPreview(row)"
              :disabled="!canPreview(row)"
            >
              预览
            </el-button>
            <el-button link type="primary" size="small" @click="openMove(row)"
              >移动</el-button
            >
            <el-button
              link
              type="primary"
              size="small"
              @click="
                ((renameTarget = row),
                (renameForm.name = row.name),
                (renameDialog = true))
              "
            >
              重命名
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-main>

    <!-- 预览抽屉 -->
    <el-drawer v-model="previewOpen" :title="previewEntry?.name" size="60%">
      <template v-if="previewEntry">
        <div
          v-if="previewEntry.kind === 'image'"
          class="file-manager__preview-image"
        >
          <img :src="streamUrl(previewEntry.path)" style="max-width: 100%" />
        </div>
        <video
          v-else-if="previewEntry.kind === 'video'"
          :src="streamUrl(previewEntry.path)"
          controls
          style="width: 100%"
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
        <el-empty v-else description="该类型暂不支持预览" />
      </template>
    </el-drawer>

    <!-- 新建目录 -->
    <el-dialog v-model="mkdirDialog" title="新建目录" width="400px">
      <el-form ref="mkdirFormRef" :model="mkdirForm" :rules="mkdirRules">
        <el-form-item label="目录名" prop="name">
          <el-input v-model="mkdirForm.name" placeholder="新目录名" />
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
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
      </el-upload>
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
          <el-input v-model="renameForm.name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialog = false">取消</el-button>
        <el-button type="primary" @click="handleRename">确定</el-button>
      </template>
    </el-dialog>

    <!-- 移动 -->
    <el-dialog v-model="moveDialog" title="移动到目录" width="500px">
      <div class="file-manager__move-toolbar">
        <el-button
          size="small"
          @click="
            moveDir = '';
            refreshMoveCandidates();
          "
          >根目录</el-button
        >
        <el-button
          size="small"
          :disabled="!moveDir"
          @click="
            moveDir = moveDir.includes('/')
              ? moveDir.substring(0, moveDir.lastIndexOf('/'))
              : '';
            refreshMoveCandidates();
          "
        >
          上一级
        </el-button>
      </div>
      <el-scrollbar max-height="300px">
        <div
          v-for="dir in moveCandidates.filter((d) => d.dir)"
          :key="dir.path"
          class="file-manager__move-item"
          @click="
            moveDir = dir.path;
            refreshMoveCandidates();
          "
        >
          <el-icon color="#409eff"><Folder /></el-icon>
          <span>{{ dir.name }}</span>
        </div>
        <el-empty
          v-if="moveCandidates.filter((d) => d.dir).length === 0"
          description="当前目录无子目录"
          :image-size="60"
        />
      </el-scrollbar>
      <div class="file-manager__move-target">
        目标：{{ moveDir || "（根目录）" }}
      </div>
      <template #footer>
        <el-button @click="moveDialog = false">取消</el-button>
        <el-button type="primary" @click="handleMove">移动</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.file-manager {
  height: calc(100vh - 120px);
}

.file-manager__tree {
  border-right: 1px solid var(--el-border-color-light);
  padding: 8px;
}

.file-manager__search {
  max-height: 400px;
}

.file-manager__search-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 4px;
  cursor: pointer;
  border-radius: 4px;
}

.file-manager__search-item:hover {
  background: var(--el-fill-color-light);
}

.file-manager__search-name {
  font-weight: 500;
}

.file-manager__search-path {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-manager__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.file-manager__actions {
  display: flex;
  gap: 8px;
}

.file-manager__thumb {
  width: 28px;
  height: 28px;
  object-fit: cover;
  border-radius: 4px;
}

.file-manager__name {
  cursor: pointer;
}

.file-manager__name:hover {
  color: var(--el-color-primary);
}

.file-manager__preview-image {
  text-align: center;
}

.file-manager__preview-toolbar {
  margin-bottom: 8px;
}

.file-manager__markdown :deep(img) {
  max-width: 100%;
}

.file-manager__markdown :deep(pre) {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}

.file-manager__move-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.file-manager__move-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 4px;
  cursor: pointer;
  border-radius: 4px;
}

.file-manager__move-item:hover {
  background: var(--el-fill-color-light);
}

.file-manager__move-target {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
