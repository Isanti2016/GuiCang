<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import TechBackground from "@/components/TechBackground.vue";
import HelpView from "@/views/HelpView.vue";
import { useAuthStore } from "@/stores/auth";
import { changePassword } from "@/api/auth";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const title = computed(() => (route.meta.title as string) || "归藏 NAS");
const username = computed(() => authStore.user?.username ?? "");
const isAdmin = computed(() => authStore.isAdmin());

const menus = computed(() => {
  const items = [
    { path: "/dashboard", title: "监控大屏", icon: "Odometer" },
    { path: "/files", title: "文件管理", icon: "Folder" },
    { path: "/monitor", title: "监控详情", icon: "DataLine", adminOnly: true },
    { path: "/gallery", title: "相册", icon: "Picture" },
    { path: "/trash", title: "回收站", icon: "Delete" },
    { path: "/admin/users", title: "用户管理", icon: "User", adminOnly: true },
    {
      path: "/admin/roles",
      title: "角色与权限",
      icon: "Lock",
      adminOnly: true,
    },
    { path: "/sync", title: "同步任务", icon: "Clock", adminOnly: true },
    { path: "/audit", title: "操作记录", icon: "List", adminOnly: true },
  ];
  return items.filter((item) => !item.adminOnly || isAdmin.value);
});

/** 退出登录并回到登录页。 */
async function handleLogout(): Promise<void> {
  await authStore.logout();
  await router.replace("/login");
}

const pwdDialogOpen = ref(false);
const pwdFormRef = ref<FormInstance | null>(null);
const pwdSubmitting = ref(false);
const pwdForm = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: "请输入当前密码", trigger: "blur" }],
  newPassword: [
    { required: true, message: "请输入新密码", trigger: "blur" },
    { min: 8, message: "新密码至少 8 位", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error("两次输入的密码不一致"));
        } else {
          callback();
        }
      },
      trigger: "blur",
    },
  ],
};

/** 打开修改密码对话框（清空表单）。 */
function openPasswordDialog(): void {
  pwdForm.oldPassword = "";
  pwdForm.newPassword = "";
  pwdForm.confirmPassword = "";
  pwdDialogOpen.value = true;
}

/** 提交修改密码：校验后改密并强制重新登录。 */
async function submitPassword(): Promise<void> {
  const form = pwdFormRef.value;
  if (!form) return;
  const valid = await form.validate().catch(() => false);
  if (!valid) return;
  pwdSubmitting.value = true;
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword);
    ElMessage.success("密码修改成功，请重新登录");
    pwdDialogOpen.value = false;
    await handleLogout();
  } catch {
    // 错误提示由拦截器处理
  } finally {
    pwdSubmitting.value = false;
  }
}

/** 处理下拉菜单命令（修改密码/使用手册/退出登录）。 */
async function handleCommand(command: string): Promise<void> {
  if (command === "logout") {
    await handleLogout();
  } else if (command === "help") {
    helpOpen.value = true;
  } else if (command === "password") {
    openPasswordDialog();
  }
}

/** 使用手册抽屉开关。 */
const helpOpen = ref(false);

onMounted(() => {
  // 已登录但未加载用户信息时补拉（刷新页面后）
  if (authStore.token && !authStore.user) {
    void authStore.fetchCurrentUser().catch(() => {
      // 失败由拦截器处理（401 会跳登录）
    });
  }
});
</script>

<template>
  <div class="main-layout-wrap">
    <TechBackground />
    <el-container class="main-layout">
      <el-aside width="200px" class="main-layout__aside">
        <div class="main-layout__brand">GuiCang 归藏</div>
        <el-menu router :default-active="route.path" class="main-layout__menu">
          <el-menu-item
            v-for="menu in menus"
            :key="menu.path"
            :index="menu.path"
          >
            <el-icon><component :is="menu.icon" /></el-icon>
            <span>{{ menu.title }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="main-layout__header">
          <span class="main-layout__title">{{ title }}</span>
          <el-dropdown @command="handleCommand">
            <span class="main-layout__user">
              {{ username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="help" divided
                  >使用手册</el-dropdown-item
                >
                <el-dropdown-item command="logout" divided
                  >退出登录</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-header>

        <el-main class="main-layout__content">
          <router-view />
        </el-main>
      </el-container>
    </el-container>

    <el-dialog
      v-model="pwdDialogOpen"
      title="修改密码"
      width="420px"
      align-center
      append-to-body
      class="main-layout__pwd-dialog"
    >
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="90px"
        @submit.prevent="submitPassword"
      >
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogOpen = false">取消</el-button>
        <el-button
          type="primary"
          :loading="pwdSubmitting"
          @click="submitPassword"
        >
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="helpOpen"
      title="使用手册"
      size="min(860px, 92vw)"
      append-to-body
      class="main-layout__help-drawer"
    >
      <HelpView />
    </el-drawer>
  </div>
</template>

<style scoped>
.main-layout-wrap {
  position: relative;
  height: 100vh;
}

.main-layout {
  position: relative;
  z-index: 1;
  height: 100vh;
}

.main-layout__aside {
  background: linear-gradient(
    180deg,
    rgba(6, 18, 40, 0.92) 0%,
    rgba(3, 12, 28, 0.94) 100%
  );
  border-right: 1px solid rgba(212, 175, 55, 0.35);
  position: relative;
}

.main-layout__aside::after {
  /* 侧栏细网格点缀 */
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(126, 210, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(126, 210, 255, 0.05) 1px, transparent 1px);
  background-size: 26px 26px;
}

.main-layout__brand {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #eaf6ff;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
  text-shadow: 0 0 14px rgba(110, 200, 255, 0.5);
  border-bottom: 1px solid rgba(212, 175, 55, 0.4);
}

.main-layout__menu {
  border-right: none;
  background: transparent;
  --el-menu-text-color: #9fc6ea;
  --el-menu-hover-bg-color: rgba(110, 200, 255, 0.1);
  --el-menu-active-color: #bfe9ff;
  --el-menu-bg-color: transparent;
}

.main-layout__menu :deep(.el-menu-item.is-active) {
  position: relative;
}

.main-layout__menu :deep(.el-menu-item.is-active)::before {
  /* 金色细线指示 */
  content: "";
  position: absolute;
  left: 0;
  top: 20%;
  bottom: 20%;
  width: 2px;
  background: linear-gradient(
    180deg,
    rgba(212, 175, 55, 0.95),
    rgba(212, 175, 55, 0.4)
  );
  border-radius: 1px;
}

.main-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(5, 16, 36, 0.66);
  border-bottom: 1px solid rgba(212, 175, 55, 0.3);
  backdrop-filter: blur(6px);
}

.main-layout__title {
  font-size: 16px;
  font-weight: 500;
  color: #bfe9ff;
}

.main-layout__user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #bfe9ff;
}

.main-layout__content {
  /* 内容区透明，由全局深色科技背景承接 */
  background: transparent;
  padding: 16px;
}

.main-layout__pwd-dialog {
  --el-dialog-bg-color: rgba(6, 20, 44, 0.96);
  --el-dialog-border-radius: 14px;
}

.main-layout__pwd-dialog :deep(.el-dialog__title) {
  color: #eaf6ff;
}

.main-layout__help-drawer {
  --el-drawer-bg-color: rgba(5, 16, 36, 0.96);
}

.main-layout__help-drawer :deep(.el-drawer__header) {
  color: #eaf6ff;
  margin-bottom: 8px;
  border-bottom: 1px solid rgba(212, 175, 55, 0.25);
  padding-bottom: 14px;
}

.main-layout__help-drawer :deep(.el-drawer__title) {
  color: #eaf6ff;
  font-size: 16px;
  letter-spacing: 1px;
}

.main-layout__help-drawer :deep(.el-drawer__close-btn) {
  color: #bfe9ff;
}

.main-layout__help-drawer :deep(.el-drawer__close-btn:hover) {
  color: #d4af37;
}
</style>
