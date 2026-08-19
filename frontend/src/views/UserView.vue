<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
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
} from '@/api/user'

const users = ref<UserVO[]>([])
const roles = ref<RoleVO[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

async function load(): Promise<void> {
  loading.value = true
  try {
    users.value = await fetchUsers(page.value, size.value, keyword.value || undefined)
  } finally {
    loading.value = false
  }
}

async function loadRoles(): Promise<void> {
  roles.value = await fetchRoles()
}

function handleSearch(): void {
  page.value = 1
  void load()
}

// ---------- 新建 / 编辑 ----------
const dialogOpen = ref(false)
const editing = ref<UserVO | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  username: '',
  displayName: '',
  email: '',
  password: '',
  roleId: undefined as number | undefined,
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-z_][a-z0-9_-]{0,31}$/,
      message: '小写字母/数字/下划线/连字符，字母或下划线开头',
      trigger: 'blur',
    },
  ],
  displayName: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
  ],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, { username: '', displayName: '', email: '', password: '', roleId: undefined })
  dialogOpen.value = true
}

function openEdit(user: UserVO): void {
  editing.value = user
  Object.assign(form, {
    username: user.username,
    displayName: user.displayName,
    email: user.email ?? '',
    password: '',
    roleId: user.roleId,
  })
  dialogOpen.value = true
}

async function handleSave(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (editing.value) {
    await updateUser(editing.value.username, {
      displayName: form.displayName,
      email: form.email || undefined,
      roleId: form.roleId as number,
    })
    ElMessage.success('已保存')
  } else {
    await createUser({
      username: form.username,
      displayName: form.displayName,
      email: form.email || undefined,
      password: form.password,
      roleId: form.roleId as number,
    })
    ElMessage.success('已创建')
  }
  dialogOpen.value = false
  await load()
}

// ---------- 启停 / 重置密码 / 删除 ----------
async function handleToggle(user: UserVO): Promise<void> {
  await setUserStatus(user.username, !user.enabled)
  ElMessage.success(user.enabled ? '已停用' : '已启用')
  await load()
}

async function handleResetPassword(user: UserVO): Promise<void> {
  const { value } = await ElMessageBox.prompt(`为 ${user.username} 设置新密码（至少 8 位）`, '重置密码', {
    inputType: 'password',
    inputPattern: /^.{8,}$/,
    inputErrorMessage: '密码至少 8 位',
  })
  await resetUserPassword(user.username, value)
  ElMessage.success('密码已重置')
}

async function handleDelete(user: UserVO): Promise<void> {
  await ElMessageBox.confirm(`确认删除用户「${user.username}」？（默认保留个人目录）`, '删除确认', {
    type: 'warning',
  })
  await deleteUser(user.username)
  ElMessage.success('已删除')
  await load()
}

onMounted(() => {
  void load()
  void loadRoles()
})
</script>

<template>
  <div class="user-view">
    <el-card shadow="never">
      <template #header>
        <div class="user-view__header">
          <el-input
            v-model="keyword"
            placeholder="按用户名/显示名搜索"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #append>
              <el-button @click="handleSearch">查询</el-button>
            </template>
          </el-input>
          <el-button type="primary" @click="openCreate">新建用户</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="users" size="small">
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="displayName" label="显示名" width="140" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">{{ row.roleName }}（{{ row.roleCode }}）</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="handleToggle(row)">
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button link type="primary" size="small" @click="handleResetPassword(row)">重置密码</el-button>
            <el-button link type="danger" size="small" :disabled="row.username === 'admin'" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="user-view__pagination"
        layout="prev, pager, next"
        :page-size="size"
        :current-page="page"
        :total="Math.max(page * size, 1)"
        @current-change="(v: number) => (page = v) || load()"
      />
    </el-card>

    <el-dialog v-model="dialogOpen" :title="editing ? '编辑用户' : '新建用户'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="Boolean(editing)" placeholder="小写字母/数字，如 zhangsan" />
        </el-form-item>
        <el-form-item label="显示名" prop="displayName">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item v-if="!editing" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" placeholder="选择角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="`${role.name}（${role.code}）`" :value="role.id" />
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
.user-view__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-view__pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
