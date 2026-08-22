<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import {
  createRole,
  deleteRole,
  fetchPermissions,
  fetchRoles,
  updateRole,
  type PermissionVO,
  type RoleVO,
} from "@/api/user";

const roles = ref<RoleVO[]>([]);
const permissions = ref<PermissionVO[]>([]);
const loading = ref(false);

const dialogOpen = ref(false);
const editing = ref<RoleVO | null>(null);
const formRef = ref<FormInstance>();
const form = reactive({
  code: "",
  name: "",
  description: "",
  permissionIds: [] as number[],
});

const rules: FormRules = {
  code: [
    { required: true, message: "请输入角色编码", trigger: "blur" },
    { pattern: /^[a-z][a-z0-9_]{0,31}$/, message: "小写字母开头，仅字母/数字/下划线", trigger: "blur" },
  ],
  name: [{ required: true, message: "请输入角色名", trigger: "blur" }],
};

const BUILTIN = ["admin", "member", "guest"];

// 权限点按类型分组（勾选面板用）
const groupedPermissions = computed(() => {
  const groups: { type: string; label: string; items: PermissionVO[] }[] = [];
  for (const type of ["menu", "api"]) {
    const items = permissions.value.filter((p) => p.type === type);
    if (items.length) {
      groups.push({ type, label: type === "menu" ? "菜单/功能" : "接口操作", items });
    }
  }
  return groups;
});

// 统计卡
const stat = computed(() => ({
  total: roles.value.length,
  builtin: roles.value.filter((r) => BUILTIN.includes(r.code)).length,
  custom: roles.value.filter((r) => !BUILTIN.includes(r.code)).length,
  permissions: permissions.value.length,
}));

async function load(): Promise<void> {
  loading.value = true;
  try {
    roles.value = await fetchRoles();
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editing.value = null;
  Object.assign(form, { code: "", name: "", description: "", permissionIds: [] });
  dialogOpen.value = true;
}

function openEdit(role: RoleVO): void {
  editing.value = role;
  const codeToId = new Map(permissions.value.map((p) => [p.code, p.id]));
  Object.assign(form, {
    code: role.code,
    name: role.name,
    description: role.description ?? "",
    permissionIds: role.permissionCodes
      .map((c) => codeToId.get(c))
      .filter((id): id is number => id !== undefined),
  });
  dialogOpen.value = true;
}

async function handleSave(): Promise<void> {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  const payload = {
    code: form.code,
    name: form.name,
    description: form.description || undefined,
    permissionIds: form.permissionIds,
  };
  if (editing.value) {
    await updateRole(editing.value.id, payload);
    ElMessage.success("已保存");
  } else {
    await createRole(payload);
    ElMessage.success("已创建");
  }
  dialogOpen.value = false;
  await load();
}

async function handleDelete(role: RoleVO): Promise<void> {
  await ElMessageBox.confirm(
    `确认删除角色「${role.name}」？（已绑定 ${role.userCount} 个用户）`,
    "删除确认",
    { type: "warning", confirmButtonText: "删除" },
  );
  await deleteRole(role.id);
  ElMessage.success("已删除");
  await load();
}

function permissionName(code: string): string {
  return permissions.value.find((p) => p.code === code)?.name ?? code;
}

function permissionType(code: string): string {
  return permissions.value.find((p) => p.code === code)?.type ?? "api";
}

onMounted(() => {
  void load();
  void fetchPermissions().then((data) => (permissions.value = data));
});
</script>

<template>
  <div class="role-view">
    <!-- 统计卡 -->
    <el-row :gutter="12" class="role-view__stats">
      <el-col :span="6">
        <div class="role-view__stat">
          <div class="role-view__stat-label">角色总数</div>
          <div class="role-view__stat-value">{{ stat.total }}</div>
          <div class="role-view__stat-line" />
        </div>
      </el-col>
      <el-col :span="6">
        <div class="role-view__stat">
          <div class="role-view__stat-label">内置角色</div>
          <div class="role-view__stat-value" style="color: #e8d9a8">{{ stat.builtin }}</div>
          <div class="role-view__stat-line" style="background: #d4af37" />
        </div>
      </el-col>
      <el-col :span="6">
        <div class="role-view__stat">
          <div class="role-view__stat-label">自定义角色</div>
          <div class="role-view__stat-value" style="color: #6ec8ff">{{ stat.custom }}</div>
          <div class="role-view__stat-line" style="background: #6ec8ff" />
        </div>
      </el-col>
      <el-col :span="6">
        <div class="role-view__stat">
          <div class="role-view__stat-label">权限点</div>
          <div class="role-view__stat-value">{{ stat.permissions }}</div>
          <div class="role-view__stat-line" style="background: #67e8a0" />
        </div>
      </el-col>
    </el-row>

    <!-- 页面头部 -->
    <div class="role-view__header">
      <div class="role-view__header-title">
        <h2 class="role-view__heading">角色与权限</h2>
        <span class="role-view__sub">按角色授权功能权限点 · 内置角色受保护</span>
      </div>
      <el-button type="primary" @click="openCreate"><el-icon><Plus /></el-icon>新建角色</el-button>
    </div>

    <!-- 角色卡片 -->
    <div v-loading="loading" class="role-view__cards">
      <el-empty v-if="!loading && roles.length === 0" description="暂无角色" />

      <div v-for="role in roles" :key="role.id" class="role-view__card">
        <div class="role-view__card-head">
          <div class="role-view__card-title">
            <span class="role-view__role-badge" :class="BUILTIN.includes(role.code) ? 'is-builtin' : 'is-custom'">
              {{ role.name.charAt(0) }}
            </span>
            <div class="role-view__title-text">
              <span class="role-view__name">{{ role.name }}</span>
              <code class="role-view__code">{{ role.code }}</code>
            </div>
            <el-tag v-if="BUILTIN.includes(role.code)" size="small" class="role-view__builtin-tag">内置</el-tag>
          </div>
          <div class="role-view__card-actions">
            <el-button link type="primary" size="small" @click="openEdit(role)">编辑</el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="BUILTIN.includes(role.code) || role.userCount > 0"
              :title="role.userCount > 0 ? '已绑定用户不可删除' : ''"
              @click="handleDelete(role)"
            >
              删除
            </el-button>
          </div>
        </div>

        <div class="role-view__desc">{{ role.description || "（暂无描述）" }}</div>

        <div class="role-view__perms">
          <span v-if="role.permissionCodes.length === 0" class="role-view__no-perm">未授权任何权限</span>
          <span
            v-for="code in role.permissionCodes"
            :key="code"
            class="role-view__perm"
            :class="`is-${permissionType(code)}`"
          >
            {{ permissionName(code) }}
            <i class="role-view__perm-code">{{ code }}</i>
          </span>
        </div>

        <div class="role-view__foot">
          <span class="role-view__users">
            <el-icon><User /></el-icon>
            {{ role.userCount }} 个用户
          </span>
        </div>
      </div>
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogOpen" :title="editing ? '编辑角色' : '新建角色'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" :disabled="editing ? BUILTIN.includes(editing.code) : false" placeholder="如 editor" />
        </el-form-item>
        <el-form-item label="角色名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item label="权限点">
          <div class="role-view__perm-groups">
            <div v-for="group in groupedPermissions" :key="group.type" class="role-view__perm-group">
              <div class="role-view__perm-group-title">{{ group.label }}（{{ group.items.length }}）</div>
              <el-checkbox-group v-model="form.permissionIds">
                <el-checkbox v-for="perm in group.items" :key="perm.id" :value="perm.id">
                  {{ perm.name }}
                  <span class="role-view__perm-option-code">{{ perm.code }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.role-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-family: var(--gc-font-sans, inherit);
}

/* 统计卡 */
.role-view__stat {
  background: rgba(7, 22, 46, 0.7);
  border: 1px solid rgba(140, 220, 255, 0.2);
  border-radius: 12px;
  padding: 14px 16px;
  backdrop-filter: blur(8px);
}

.role-view__stat-label {
  font-size: 12px;
  letter-spacing: 2px;
  color: #8fb6dd;
}

.role-view__stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #eaf6ff;
  font-variant-numeric: tabular-nums;
  margin: 4px 0 8px;
}

.role-view__stat-line {
  height: 2px;
  width: 60px;
  background: linear-gradient(90deg, #6ec8ff, transparent);
  border-radius: 1px;
}

/* 页面头部 */
.role-view__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(7, 22, 46, 0.6);
  border: 1px solid rgba(140, 220, 255, 0.18);
  border-radius: 12px;
  padding: 12px 16px;
  backdrop-filter: blur(8px);
}

.role-view__heading {
  margin: 0;
  font-size: 18px;
  letter-spacing: 2px;
  color: #eaf6ff;
}

.role-view__sub {
  font-size: 12px;
  color: #8fb6dd;
  margin-left: 10px;
}

/* 角色卡片 */
.role-view__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 12px;
}

.role-view__card {
  background: rgba(7, 22, 46, 0.7);
  border: 1px solid rgba(140, 220, 255, 0.2);
  border-radius: 12px;
  padding: 14px 16px;
  backdrop-filter: blur(8px);
  transition: border-color 0.15s, transform 0.15s;
}

.role-view__card:hover {
  border-color: rgba(110, 200, 255, 0.45);
  transform: translateY(-2px);
}

.role-view__card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(212, 175, 55, 0.25);
}

.role-view__card-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-view__role-badge {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 15px;
  flex-shrink: 0;
}

.role-view__role-badge.is-builtin {
  color: #f7ecc9;
  background: linear-gradient(135deg, rgba(212, 175, 55, 0.35), rgba(212, 175, 55, 0.15));
  border: 1px solid rgba(212, 175, 55, 0.55);
}

.role-view__role-badge.is-custom {
  color: #dff5ff;
  background: linear-gradient(135deg, rgba(110, 200, 255, 0.35), rgba(64, 140, 220, 0.2));
  border: 1px solid rgba(110, 200, 255, 0.5);
}

.role-view__title-text {
  display: flex;
  flex-direction: column;
}

.role-view__name {
  font-weight: 600;
  color: #eaf6ff;
  font-size: 15px;
}

.role-view__code {
  font-size: 11px;
  color: #8fb6dd;
  background: rgba(110, 200, 255, 0.08);
  padding: 1px 6px;
  border-radius: 4px;
}

.role-view__builtin-tag {
  margin-left: 4px;
}

.role-view__desc {
  margin: 10px 0;
  font-size: 13px;
  color: #9fb8d8;
}

/* 权限点标签 */
.role-view__perms {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 26px;
}

.role-view__perm {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}

.role-view__perm.is-menu {
  color: #e8d9a8;
  background: rgba(212, 175, 55, 0.12);
  border: 1px solid rgba(212, 175, 55, 0.3);
}

.role-view__perm.is-api {
  color: #9fd4ff;
  background: rgba(110, 200, 255, 0.1);
  border: 1px solid rgba(110, 200, 255, 0.28);
}

.role-view__perm-code {
  font-style: normal;
  font-size: 10px;
  opacity: 0.75;
}

.role-view__no-perm {
  color: #8a97a8;
  font-size: 12px;
}

.role-view__foot {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid rgba(140, 220, 255, 0.1);
  display: flex;
  justify-content: flex-end;
}

.role-view__users {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #8fb6dd;
}

/* 权限点分组勾选 */
.role-view__perm-groups {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 320px;
  overflow-y: auto;
}

.role-view__perm-group-title {
  font-size: 12px;
  letter-spacing: 1px;
  color: #e8d9a8;
  border-left: 3px solid rgba(212, 175, 55, 0.6);
  padding-left: 8px;
  margin-bottom: 8px;
}

.role-view__perm-option-code {
  font-size: 11px;
  color: #8fb6dd;
  margin-left: 4px;
}
</style>
