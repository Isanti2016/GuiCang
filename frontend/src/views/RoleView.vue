<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createRole,
  deleteRole,
  fetchPermissions,
  fetchRoles,
  updateRole,
  type PermissionVO,
  type RoleVO,
} from '@/api/user'

const roles = ref<RoleVO[]>([])
const permissions = ref<PermissionVO[]>([])
const loading = ref(false)

const dialogOpen = ref(false)
const editing = ref<RoleVO | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  code: '',
  name: '',
  description: '',
  permissionIds: [] as number[],
})

const rules: FormRules = {
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{0,31}$/, message: '小写字母开头，仅字母/数字/下划线', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入角色名', trigger: 'blur' }],
}

async function load(): Promise<void> {
  loading.value = true
  try {
    roles.value = await fetchRoles()
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  Object.assign(form, { code: '', name: '', description: '', permissionIds: [] })
  dialogOpen.value = true
}

function openEdit(role: RoleVO): void {
  editing.value = role
  const codeToId = new Map(permissions.value.map((p) => [p.code, p.id]))
  Object.assign(form, {
    code: role.code,
    name: role.name,
    description: role.description ?? '',
    permissionIds: role.permissionCodes.map((c) => codeToId.get(c)).filter((id): id is number => id !== undefined),
  })
  dialogOpen.value = true
}

async function handleSave(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload = {
    code: form.code,
    name: form.name,
    description: form.description || undefined,
    permissionIds: form.permissionIds,
  }
  if (editing.value) {
    await updateRole(editing.value.id, payload)
    ElMessage.success('已保存')
  } else {
    await createRole(payload)
    ElMessage.success('已创建')
  }
  dialogOpen.value = false
  await load()
}

async function handleDelete(role: RoleVO): Promise<void> {
  await ElMessageBox.confirm(`确认删除角色「${role.name}」？`, '删除确认', { type: 'warning' })
  await deleteRole(role.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(() => {
  void load()
  void fetchPermissions().then((data) => (permissions.value = data))
})
</script>

<template>
  <div class="role-view">
    <el-card shadow="never">
      <template #header>
        <div class="role-view__header">
          <span>角色与权限</span>
          <el-button type="primary" @click="openCreate">新建角色</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="roles" size="small">
        <el-table-column prop="code" label="编码" width="110" />
        <el-table-column prop="name" label="角色名" width="120" />
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="权限点" min-width="260">
          <template #default="{ row }">
            <el-tag v-for="code in row.permissionCodes" :key="code" size="small" style="margin: 2px 4px 2px 0">
              {{ code }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="['admin', 'member', 'guest'].includes(row.code)"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogOpen" :title="editing ? '编辑角色' : '新建角色'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" :disabled="editing ? ['admin', 'member', 'guest'].includes(editing.code) : false" />
        </el-form-item>
        <el-form-item label="角色名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item label="权限点">
          <el-checkbox-group v-model="form.permissionIds">
            <el-checkbox v-for="perm in permissions" :key="perm.id" :value="perm.id">
              {{ perm.name }}（{{ perm.code }}）
            </el-checkbox>
          </el-checkbox-group>
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
.role-view__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
