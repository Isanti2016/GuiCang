<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import {
  createUser,
  deleteUser,
  fetchRoles,
  fetchUsers,
  resetUserPassword,
  setUserStatus,
  updateUser,
  type RoleVO,
  type UserVO,
} from "@/api/user";

const users = ref<UserVO[]>([]);
const roles = ref<RoleVO[]>([]);
const loading = ref(false);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const keyword = ref("");
const enabledFilter = ref<boolean | undefined>(undefined);

// 统计卡
const stat = computed(() => {
  const all = users.value;
  return {
    total: total.value,
    enabled: all.filter((u) => u.enabled).length,
    disabled: all.filter((u) => !u.enabled).length,
    roles: roles.value.length,
  };
});

/** 分页加载用户列表。 */
async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await fetchUsers(
      page.value,
      size.value,
      keyword.value || undefined,
      enabledFilter.value,
    );
    users.value = data.records;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

/** 加载角色列表（下拉选项）。 */
async function loadRoles(): Promise<void> {
  roles.value = await fetchRoles();
}

/** 按关键字/状态筛选（回到第一页）。 */
function handleSearch(): void {
  page.value = 1;
  void load();
}

// ---------- 新建 / 编辑 ----------
const dialogOpen = ref(false);
const editing = ref<UserVO | null>(null);
const formRef = ref<FormInstance>();
const form = reactive({
  username: "",
  displayName: "",
  email: "",
  password: "",
  roleId: undefined as number | undefined,
});

const rules: FormRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    {
      pattern: /^[a-z_][a-z0-9_-]{0,31}$/,
      message: "小写字母/数字/下划线/连字符，字母或下划线开头",
      trigger: "blur",
    },
  ],
  displayName: [{ required: true, message: "请输入显示名", trigger: "blur" }],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 8, message: "密码至少 8 位", trigger: "blur" },
  ],
  roleId: [{ required: true, message: "请选择角色", trigger: "change" }],
};

/** 打开新建用户对话框。 */
function openCreate(): void {
  editing.value = null;
  Object.assign(form, {
    username: "",
    displayName: "",
    email: "",
    password: "",
    roleId: undefined,
  });
  dialogOpen.value = true;
}

/** 打开编辑用户对话框（回填表单）。 */
function openEdit(user: UserVO): void {
  editing.value = user;
  Object.assign(form, {
    username: user.username,
    displayName: user.displayName,
    email: user.email ?? "",
    password: "",
    roleId: user.roleId,
  });
  dialogOpen.value = true;
}

/** 提交新建/编辑用户表单。 */
async function handleSave(): Promise<void> {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  if (editing.value) {
    await updateUser(editing.value.username, {
      displayName: form.displayName,
      email: form.email || undefined,
      roleId: form.roleId as number,
    });
    ElMessage.success("已保存");
  } else {
    await createUser({
      username: form.username,
      displayName: form.displayName,
      email: form.email || undefined,
      password: form.password,
      roleId: form.roleId as number,
    });
    ElMessage.success("已创建");
  }
  dialogOpen.value = false;
  await load();
}

// ---------- 启停 / 重置密码 / 删除 ----------
/** 启用/停用用户（确认后调用后端）。 */
async function handleToggle(user: UserVO): Promise<void> {
  await ElMessageBox.confirm(
    `确认${user.enabled ? "停用" : "启用"}用户「${user.username}」？`,
    "状态变更",
    { type: "warning" },
  );
  await setUserStatus(user.username, !user.enabled);
  ElMessage.success(user.enabled ? "已停用" : "已启用");
  await load();
}

/** 重置用户密码（弹窗输入新密码）。 */
async function handleResetPassword(user: UserVO): Promise<void> {
  const { value } = await ElMessageBox.prompt(
    `为 ${user.username} 设置新密码（至少 8 位）`,
    "重置密码",
    {
      inputType: "password",
      inputPattern: /^.{8,}$/,
      inputErrorMessage: "密码至少 8 位",
      confirmButtonText: "重置",
    },
  );
  await resetUserPassword(user.username, value);
  ElMessage.success("密码已重置");
}

/** 删除用户（默认保留个人目录）。 */
async function handleDelete(user: UserVO): Promise<void> {
  await ElMessageBox.confirm(
    `确认删除用户「${user.username}」？（默认保留个人目录）`,
    "删除确认",
    {
      type: "warning",
      confirmButtonText: "删除",
    },
  );
  await deleteUser(user.username);
  ElMessage.success("已删除");
  await load();
}

/** 用户名首字符徽标。 */
function initialOf(name: string): string {
  return name.charAt(0).toUpperCase();
}

/** 角色 ID 转角色名。 */
function roleName(roleId: number): string {
  return roles.value.find((r) => r.id === roleId)?.name ?? "未知";
}

onMounted(() => {
  void load();
  void loadRoles();
});
</script>

<template>
  <div class="user-view">
    <!-- 统计卡 -->
    <el-row :gutter="12" class="user-view__stats">
      <el-col :xs="12" :span="6">
        <div class="user-view__stat">
          <div class="user-view__stat-label">用户总数</div>
          <div class="user-view__stat-value">{{ stat.total }}</div>
          <div class="user-view__stat-line" />
        </div>
      </el-col>
      <el-col :xs="12" :span="6">
        <div class="user-view__stat">
          <div class="user-view__stat-label">已启用</div>
          <div class="user-view__stat-value" style="color: #67e8a0">
            {{ stat.enabled }}
          </div>
          <div class="user-view__stat-line" style="background: #67e8a0" />
        </div>
      </el-col>
      <el-col :xs="12" :span="6">
        <div class="user-view__stat">
          <div class="user-view__stat-label">已停用</div>
          <div class="user-view__stat-value" style="color: #f5a3a3">
            {{ stat.disabled }}
          </div>
          <div class="user-view__stat-line" style="background: #f5a3a3" />
        </div>
      </el-col>
      <el-col :xs="12" :span="6">
        <div class="user-view__stat">
          <div class="user-view__stat-label">角色数量</div>
          <div class="user-view__stat-value" style="color: #e8d9a8">
            {{ stat.roles }}
          </div>
          <div class="user-view__stat-line" style="background: #d4af37" />
        </div>
      </el-col>
    </el-row>

    <el-card shadow="never" class="user-view__card">
      <div class="user-view__toolbar">
        <div class="user-view__filters">
          <el-input
            v-model="keyword"
            placeholder="按用户名/显示名搜索"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix
              ><el-icon><Search /></el-icon
            ></template>
          </el-input>
          <el-select
            v-model="enabledFilter"
            placeholder="状态"
            clearable
            style="width: 120px"
            @change="handleSearch"
          >
            <el-option label="已启用" :value="true" />
            <el-option label="已停用" :value="false" />
          </el-select>
          <el-button @click="handleSearch">查询</el-button>
        </div>
        <el-button type="primary" @click="openCreate"
          ><el-icon><UserFilled /></el-icon>新建用户</el-button
        >
      </div>

      <el-table
        v-loading="loading"
        :data="users"
        size="small"
        class="user-view__table"
      >
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user-view__user">
              <span
                class="user-view__avatar"
                :class="row.enabled ? 'is-on' : 'is-off'"
              >
                {{ initialOf(row.username) }}
              </span>
              <div class="user-view__user-info">
                <span class="user-view__username">{{ row.username }}</span>
                <span class="user-view__display">{{ row.displayName }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <span class="user-view__role"
              >{{ roleName(row.roleId) }}（{{ row.roleCode }}）</span
            >
          </template>
        </el-table-column>
        <el-table-column
          prop="email"
          label="邮箱"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span
              class="user-view__status"
              :class="row.enabled ? 'is-on' : 'is-off'"
            >
              <i
                class="user-view__dot"
                :class="row.enabled ? 'is-on' : 'is-off'"
              />
              {{ row.enabled ? "启用" : "停用" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)"
              >编辑</el-button
            >
            <el-button
              link
              type="warning"
              size="small"
              @click="handleToggle(row)"
            >
              {{ row.enabled ? "停用" : "启用" }}
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              @click="handleResetPassword(row)"
              >重置密码</el-button
            >
            <el-button
              link
              type="danger"
              size="small"
              :disabled="row.username === 'admin'"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="user-view__pagination"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(v: number) => ((page = v), load())"
      />
    </el-card>

    <el-dialog
      v-model="dialogOpen"
      :title="editing ? '编辑用户' : '新建用户'"
      width="480px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            :disabled="Boolean(editing)"
            placeholder="小写字母/数字，如 zhangsan"
          />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item v-if="!editing" label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="至少 8 位，将同步系统账号与 Samba"
          />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select
            v-model="form.roleId"
            placeholder="选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="`${role.name}（${role.code}）`"
              :value="role.id"
            />
          </el-select>
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
.user-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-family: var(--gc-font-sans, inherit);
}

/* 统计卡 */
.user-view__stat {
  background: rgba(7, 22, 46, 0.7);
  border: 1px solid rgba(140, 220, 255, 0.2);
  border-radius: 12px;
  padding: 14px 16px;
  backdrop-filter: blur(8px);
  position: relative;
  overflow: hidden;
}

.user-view__stat-label {
  font-size: 12px;
  letter-spacing: 2px;
  color: #8fb6dd;
}

.user-view__stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #eaf6ff;
  font-variant-numeric: tabular-nums;
  margin: 4px 0 8px;
}

.user-view__stat-line {
  height: 2px;
  width: 60px;
  background: linear-gradient(90deg, #6ec8ff, transparent);
  border-radius: 1px;
}

/* 卡片 */
.user-view__card {
  border-radius: 12px;
}

.user-view__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.user-view__filters {
  display: flex;
  gap: 8px;
  align-items: center;
}

.user-view__table {
  --el-table-border-color: transparent;
}

/* 用户列：徽标 + 名称 */
.user-view__user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-view__avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 15px;
  flex-shrink: 0;
}

.user-view__avatar.is-on {
  color: #dff5ff;
  background: linear-gradient(
    135deg,
    rgba(110, 200, 255, 0.35),
    rgba(64, 140, 220, 0.25)
  );
  border: 1px solid rgba(110, 200, 255, 0.5);
}

.user-view__avatar.is-off {
  color: #9aa7b8;
  background: rgba(80, 96, 120, 0.25);
  border: 1px solid rgba(120, 136, 160, 0.3);
}

.user-view__user-info {
  display: flex;
  flex-direction: column;
}

.user-view__username {
  font-weight: 600;
  color: #eaf6ff;
}

.user-view__display {
  font-size: 12px;
  color: #8fb6dd;
}

.user-view__role {
  color: #bfdcf8;
  font-size: 13px;
}

/* 状态点 */
.user-view__status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.user-view__status.is-on {
  color: #67e8a0;
}

.user-view__status.is-off {
  color: #f5a3a3;
}

.user-view__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.user-view__dot.is-on {
  background: #67e8a0;
  box-shadow: 0 0 6px rgba(103, 232, 160, 0.8);
}

.user-view__dot.is-off {
  background: #f5a3a3;
  box-shadow: 0 0 6px rgba(245, 163, 163, 0.6);
}

.user-view__pagination {
  margin-top: 12px;
  justify-content: flex-end;
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
