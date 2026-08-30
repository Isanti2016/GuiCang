<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import TechBackground from "@/components/TechBackground.vue";
import HelpView from "@/views/HelpView.vue";
import { useAuthStore } from "@/stores/auth";
import {
  changePassword,
  disableTotp,
  enableTotp,
  totpStatus,
} from "@/api/auth";
import {
  listNotifications,
  markAllRead,
  markRead,
  unreadCount,
  type Notification,
} from "@/api/notification";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

/** 是否为移动端窄屏（<768px），用于切换抽屉式导航。 */
const isMobile = ref(false);

/** 移动端抽屉菜单开关。 */
const drawerOpen = ref(false);

function updateViewport(): void {
  isMobile.value = window.innerWidth < 768;
  if (!isMobile.value) {
    drawerOpen.value = false;
  }
}

/** 移动端：选中菜单后关闭抽屉。 */
function handleMenuSelect(): void {
  if (isMobile.value) {
    drawerOpen.value = false;
  }
}

const title = computed(() => (route.meta.title as string) || "归藏 NAS");
const username = computed(() => authStore.user?.username ?? "");
const notifications = ref<Notification[]>([]);
const unread = ref(0);
const isAdmin = computed(() => authStore.isAdmin());

/** 菜单分组（概览 / 存储 / 系统管理），管理员分组按权限过滤。 */
interface MenuItem {
  path: string;
  title: string;
  icon: string;
  adminOnly?: boolean;
}

interface MenuGroup {
  title: string;
  icon: string;
  items: MenuItem[];
  adminOnly?: boolean;
}

const menuGroups = computed<MenuGroup[]>(() => {  const groups: MenuGroup[] = [
    {
      title: "概览",
      icon: "Odometer",
      items: [
        { path: "/dashboard", title: "监控大屏", icon: "Odometer" },
        { path: "/monitor", title: "监控详情", icon: "DataLine", adminOnly: true },
      ],
    },
    {
      title: "存储",
      icon: "Folder",
      items: [
        { path: "/files", title: "文件管理", icon: "Folder" },
        { path: "/gallery", title: "相册", icon: "Picture" },
        { path: "/cameras", title: "监控录像", icon: "VideoCamera" },
        { path: "/trash", title: "回收站", icon: "Delete" },
      ],
    },
    {
      title: "系统管理",
      icon: "Setting",
      adminOnly: true,
      items: [
        { path: "/admin/users", title: "用户管理", icon: "User" },
        { path: "/admin/roles", title: "角色与权限", icon: "Lock" },
        { path: "/sync", title: "自动整理", icon: "Clock" },
        { path: "/audit", title: "操作记录", icon: "List" },
        { path: "/logs", title: "系统日志", icon: "Document" },
        { path: "/settings", title: "系统设置", icon: "Setting" },
        { path: "/admin/sessions", title: "会话管理", icon: "Monitor" },
      ],
    },
  ];
  return groups
    .map((g) => ({ ...g, items: g.items.filter((i) => !i.adminOnly || isAdmin.value) }))
    .filter((g) => (g.adminOnly ? isAdmin.value : g.items.length > 0));
});

/** 当前路由所在分组名（用于默认展开对应菜单组）。 */
const defaultOpeneds = computed(() => {
  const group = menuGroups.value.find((g) =>
    g.items.some((i) => i.path === route.path),
  );
  return group ? [group.title] : [];
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
  } else if (command === "totp") {
    void openTotpDialog();
  }
}

/** 加载通知列表与未读数。 */
async function loadNotifications(): Promise<void> {
  try {
    notifications.value = await listNotifications();
    unread.value = await unreadCount();
  } catch {
    /* 忽略加载失败 */
  }
}

/** 点击单条通知标记已读。 */
async function handleMarkRead(item: Notification): Promise<void> {
  if (item.readFlag === 1) return;
  try {
    await markRead(item.id);
    await loadNotifications();
  } catch {
    /* 忽略 */
  }
}

/** 全部标记已读。 */
async function handleMarkAllRead(): Promise<void> {
  try {
    await markAllRead();
    await loadNotifications();
  } catch {
    /* 忽略 */
  }
}

/** 简单时间格式化。 */
function formatNotifTime(ts: number): string {
  const d = new Date(ts);
  const pad = (x: number) => String(x).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 打开两步验证弹窗。 */
async function openTotpDialog(): Promise<void> {
  totpDialog.value = true;
  totpSecret.value = "";
  await loadTotpStatus();
}

/** 加载两步验证状态。 */
async function loadTotpStatus(): Promise<void> {
  totpLoading.value = true;
  try {
    totpEnabled.value = await totpStatus();
  } catch {
    /* 忽略 */
  } finally {
    totpLoading.value = false;
  }
}

/** 开启两步验证（生成密钥）。 */
async function handleEnableTotp(): Promise<void> {
  try {
    totpSecret.value = await enableTotp();
    ElMessage.success("已生成密钥，请录入 Authenticator");
  } catch {
    /* 拦截器已提示 */
  }
}

/** 关闭两步验证。 */
async function handleDisableTotp(): Promise<void> {
  await ElMessageBox.confirm("确认关闭两步验证？", "关闭两步验证", { type: "warning" });
  await disableTotp();
  totpEnabled.value = false;
  totpSecret.value = "";
  ElMessage.success("已关闭两步验证");
}

/** 关闭弹窗。 */
function closeTotpDialog(): void {
  totpDialog.value = false;
  totpSecret.value = "";
}

/** 使用手册抽屉开关。 */
const helpOpen = ref(false);
const totpDialog = ref(false);
const totpLoading = ref(false);
const totpEnabled = ref(false);
const totpSecret = ref("");

onMounted(() => {
  // 已登录但未加载用户信息时补拉（刷新页面后）
  if (authStore.token && !authStore.user) {
    void authStore.fetchCurrentUser().catch(() => {
      // 失败由拦截器处理（401 会跳登录）
    });
  }
  updateViewport();
  window.addEventListener("resize", updateViewport);
  if (authStore.token) {
    void loadNotifications();
  }
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", updateViewport);
});
</script>

<template>
  <div class="main-layout-wrap">
    <TechBackground />
    <el-container class="main-layout">
      <el-aside
        v-show="!isMobile"
        width="200px"
        class="main-layout__aside"
      >
        <div class="main-layout__brand">
          <img src="/logo.svg" alt="GuiCang" class="main-layout__logo" />
          <span>GuiCang 归藏</span>
        </div>
        <el-menu
          router
          :default-active="route.path"
          :default-openeds="defaultOpeneds"
          class="main-layout__menu"
        >
          <el-sub-menu
            v-for="group in menuGroups"
            :key="group.title"
            :index="group.title"
          >
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>
            <el-menu-item
              v-for="menu in group.items"
              :key="menu.path"
              :index="menu.path"
            >
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="main-layout__header">
          <div class="main-layout__header-left">
            <button
              v-if="isMobile"
              class="main-layout__burger"
              aria-label="打开菜单"
              @click="drawerOpen = true"
            >
              <el-icon :size="22"><Menu /></el-icon>
            </button>
            <span class="main-layout__title">{{ title }}</span>
          </div>
          <el-popover placement="bottom-end" width="340" trigger="click">
            <template #reference>
              <el-badge
                :value="unread"
                :hidden="unread === 0"
                class="main-layout__bell"
              >
                <el-icon :size="18"><Bell /></el-icon>
              </el-badge>
            </template>
            <div style="max-height: 360px; overflow: auto">
              <div
                style="
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  margin-bottom: 8px;
                "
              >
                <span style="font-weight: 500">通知</span>
                <el-button link size="small" @click="handleMarkAllRead"
                  >全部已读</el-button
                >
              </div>
              <el-empty
                v-if="notifications.length === 0"
                description="暂无通知"
                :image-size="60"
              />
              <div
                v-for="n in notifications"
                :key="n.id"
                @click="handleMarkRead(n)"
                style="
                  padding: 8px 4px;
                  border-bottom: 1px solid #eee;
                  cursor: pointer;
                  font-size: 13px;
                "
              >
                <div style="font-weight: 500">{{ n.title }}</div>
                <div style="color: #666; margin: 2px 0">{{ n.content }}</div>
                <div style="color: #999; font-size: 12px">
                  {{ formatNotifTime(n.createdAt) }}
                </div>
              </div>
            </div>
          </el-popover>
          <el-dropdown @command="handleCommand">
            <span class="main-layout__user">
              {{ username }}
              <el-icon v-if="!isMobile"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="totp">两步验证</el-dropdown-item>
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

    <!-- 移动端抽屉导航 -->
    <el-drawer
      v-model="drawerOpen"
      direction="ltr"
      :with-header="false"
      size="220px"
      class="main-layout__drawer"
    >
      <div class="main-layout__drawer-brand">
        <img src="/logo.svg" alt="GuiCang" class="main-layout__logo" />
        <span>GuiCang 归藏</span>
      </div>
      <el-menu
        router
        :default-active="route.path"
        :default-openeds="defaultOpeneds"
        class="main-layout__menu main-layout__drawer-menu"
        @select="handleMenuSelect"
      >
        <el-sub-menu
          v-for="group in menuGroups"
          :key="group.title"
          :index="group.title"
        >
          <template #title>
            <el-icon><component :is="group.icon" /></el-icon>
            <span>{{ group.title }}</span>
          </template>
          <el-menu-item
            v-for="menu in group.items"
            :key="menu.path"
            :index="menu.path"
          >
            <el-icon><component :is="menu.icon" /></el-icon>
            <span>{{ menu.title }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-drawer>

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

    <!-- 两步验证 -->
    <el-dialog v-model="totpDialog" title="两步验证" width="440px">
      <div v-loading="totpLoading">
        <template v-if="totpEnabled">
          <el-alert type="success" :closable="false" title="已开启两步验证" />
          <div style="margin: 12px 0; color: #666; font-size: 13px">
            登录时需输入 Authenticator 中的 6 位动态码。
          </div>
          <el-button type="danger" plain @click="handleDisableTotp"
            >关闭两步验证</el-button
          >
        </template>
        <template v-else>
          <template v-if="totpSecret">
            <el-alert type="warning" :closable="false" title="请先保存密钥" />
            <div style="margin: 12px 0">
              <div style="margin-bottom: 4px; font-size: 13px">
                密钥（手动输入到 Google Authenticator / Authy）：
              </div>
              <el-input :model-value="totpSecret" readonly />
            </div>
          </template>
          <el-button type="primary" @click="handleEnableTotp">
            {{ totpSecret ? "重新生成" : "开启两步验证" }}
          </el-button>
        </template>
      </div>
      <template #footer>
        <el-button @click="closeTotpDialog">关闭</el-button>
      </template>
    </el-dialog>
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
  gap: 8px;
  color: #eaf6ff;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
  text-shadow: 0 0 14px rgba(110, 200, 255, 0.5);
  border-bottom: 1px solid rgba(212, 175, 55, 0.4);
}

.main-layout__logo {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  box-shadow: 0 0 12px rgba(110, 200, 255, 0.45);
  flex-shrink: 0;
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

.main-layout__header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.main-layout__burger {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(126, 210, 255, 0.25);
  border-radius: 8px;
  background: rgba(110, 200, 255, 0.08);
  color: #bfe9ff;
  cursor: pointer;
  flex-shrink: 0;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;
}

.main-layout__burger:hover {
  background: rgba(110, 200, 255, 0.18);
  border-color: rgba(212, 175, 55, 0.5);
}

.main-layout__title {
  font-size: 16px;
  font-weight: 500;
  color: #bfe9ff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-layout__user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #bfe9ff;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

/* ---------- 移动端抽屉导航 ---------- */
.main-layout__drawer {
  --el-drawer-bg-color: rgba(4, 14, 32, 0.98);
}

.main-layout__drawer-brand {
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
  margin-bottom: 8px;
}

/* ---------- 移动端内容区 ---------- */
@media (max-width: 768px) {
  .main-layout__content {
    padding: 10px;
  }
}
</style>
