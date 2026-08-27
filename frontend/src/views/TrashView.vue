<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { computed, onMounted, ref } from "vue";
import {
  emptyTrash,
  fetchTrash,
  purgeTrash,
  restoreTrash,
  type TrashItem,
} from "@/api/file";

const items = ref<TrashItem[]>([]);
const loading = ref(false);

const totalSize = computed(() =>
  items.value.reduce((sum, it) => sum + (it.size ?? 0), 0),
);

/** 格式化字节数为人类可读大小。 */
const formatSize = (bytes: number): string => {
  if (bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(1)} ${units[unit]}`;
};

/** 类型码转中文标签。 */
const kindLabel = (kind: string): string => {
  const labels: Record<string, string> = {
    dir: "目录",
    image: "图片",
    video: "视频",
    note: "文档",
    other: "文件",
  };
  return labels[kind] ?? kind;
};

/** 类型码对应 el-tag 颜色。 */
const kindTag = (kind: string): "primary" | "success" | "warning" | "info" => {
  switch (kind) {
    case "dir":
      return "warning";
    case "image":
      return "success";
    case "video":
      return "danger";
    case "note":
      return "primary";
    default:
      return "info";
  }
};

/** 从原路径提取文件名。 */
const originName = (originalPath: string): string =>
  originalPath.includes("/")
    ? originalPath.substring(originalPath.lastIndexOf("/") + 1)
    : originalPath;

/** 加载回收站列表。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    items.value = await fetchTrash();
  } catch {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false;
  }
}

/** 恢复条目到原位置。 */
async function handleRestore(row: TrashItem): Promise<void> {
  try {
    await restoreTrash(row.id);
    ElMessage.success(`已恢复 ${originName(row.originalPath)}`);
    await load();
  } catch {
    // 拦截器已提示
  }
}

/** 彻底删除单条（二次确认，不可恢复）。 */
async function handlePurge(row: TrashItem): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `彻底删除「${originName(row.originalPath)}」？删除后无法恢复。`,
      "彻底删除",
      {
        type: "warning",
        confirmButtonText: "彻底删除",
        cancelButtonText: "取消",
      },
    );
  } catch {
    return;
  }
  try {
    await purgeTrash(row.id);
    ElMessage.success("已彻底删除");
    await load();
  } catch {
    // 拦截器已提示
  }
}

/** 清空回收站（二次确认）。 */
async function handleEmpty(): Promise<void> {
  if (items.value.length === 0) return;
  try {
    await ElMessageBox.confirm(
      `清空回收站（共 ${items.value.length} 项，${formatSize(totalSize.value)}）？此操作不可恢复。`,
      "清空回收站",
      { type: "warning", confirmButtonText: "清空", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await emptyTrash();
    ElMessage.success("回收站已清空");
    await load();
  } catch {
    // 拦截器已提示
  }
}

onMounted(load);
</script>

<template>
  <div class="trash">
    <el-card shadow="never" class="trash__panel">
      <template #header>
        <div class="trash__header">
          <div>
            <span class="trash__title">回收站</span>
            <span class="trash__count"
              >共 {{ items.length }} 项 · {{ formatSize(totalSize) }}</span
            >
          </div>
          <el-button
            type="danger"
            plain
            :disabled="items.length === 0"
            :loading="loading"
            @click="handleEmpty"
          >
            清空回收站
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="items"
        empty-text="回收站空空如也，删除的文件会出现在这里"
      >
        <el-table-column label="名称" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="trash__name">{{ originName(row.originalPath) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="原位置" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="trash__origin">{{ row.originalPath }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="kindTag(row.kind)" size="small" effect="plain">
              {{ kindLabel(row.kind) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{
            formatSize(row.size ?? 0)
          }}</template>
        </el-table-column>
        <el-table-column label="删除时间" width="180">
          <template #default="{ row }">
            {{
              new Date(row.deletedAt).toLocaleString("zh-CN", { hour12: false })
            }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              link
              @click="handleRestore(row)"
            >
              恢复
            </el-button>
            <el-button
              size="small"
              type="danger"
              link
              @click="handlePurge(row)"
            >
              彻底删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.trash {
  position: relative;
  z-index: 1;
}

.trash__panel {
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

.trash__panel :deep(.el-card__header) {
  border-bottom: 1px solid rgba(212, 175, 55, 0.22);
}

.trash__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.trash__title {
  font-size: 17px;
  font-weight: 600;
  color: #eaf6ff;
  letter-spacing: 1px;
}

.trash__count {
  margin-left: 12px;
  font-size: 12px;
  color: rgba(159, 198, 234, 0.75);
}

.trash__name {
  color: #d9f1ff;
}

.trash__origin {
  color: rgba(159, 198, 234, 0.72);
  font-size: 12px;
}

.trash__panel :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(110, 200, 255, 0.08);
  --el-table-header-text-color: #bfe9ff;
  --el-table-border-color: rgba(126, 210, 255, 0.12);
  --el-table-row-hover-bg-color: rgba(110, 200, 255, 0.08);
  --el-table-text-color: #cfe7f8;
  --el-table-expanded-cell-bg-color: transparent;
  background: transparent;
}

.trash__panel :deep(.el-table__inner-wrapper::before) {
  display: none;
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
