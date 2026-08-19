<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";

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
    { path: "/admin/users", title: "用户管理", icon: "User", adminOnly: true },
    { path: "/admin/roles", title: "角色与权限", icon: "Lock", adminOnly: true },
    { path: "/sync", title: "同步任务", icon: "Clock", adminOnly: true },
    { path: "/audit", title: "操作记录", icon: "List", adminOnly: true },
  ];
  return items.filter((item) => !item.adminOnly || isAdmin.value);
});

async function handleLogout(): Promise<void> {
  await authStore.logout();
  await router.replace("/login");
}

async function handleCommand(command: string): Promise<void> {
  if (command === "logout") {
    await handleLogout();
  } else if (command === "help") {
    await router.push("/help");
  }
}

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
  <el-container class="main-layout">
    <el-aside width="200px" class="main-layout__aside">
      <div class="main-layout__brand">GuiCang 归藏</div>
      <el-menu router :default-active="route.path" class="main-layout__menu">
        <el-menu-item v-for="menu in menus" :key="menu.path" :index="menu.path">
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
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              <el-dropdown-item command="help" divided>使用手册</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main-layout__content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.main-layout {
  height: 100vh;
}

.main-layout__aside {
  background: linear-gradient(180deg, #ffffff 0%, #e9f2fd 100%);
  border-right: 1px solid rgba(64, 158, 255, 0.2);
  position: relative;
}

.main-layout__aside::after {
  /* 侧栏细网格点缀 */
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(64, 158, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(64, 158, 255, 0.05) 1px, transparent 1px);
  background-size: 26px 26px;
}

.main-layout__brand {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1f4e8c;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
  border-bottom: 1px solid rgba(64, 158, 255, 0.18);
}

.main-layout__menu {
  border-right: none;
  background: transparent;
  --el-menu-text-color: #2b3a4a;
  --el-menu-hover-bg-color: rgba(64, 158, 255, 0.1);
  --el-menu-active-color: #1f4e8c;
  --el-menu-bg-color: transparent;
}

.main-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.72);
  border-bottom: 1px solid rgba(64, 158, 255, 0.2);
  backdrop-filter: blur(6px);
}

.main-layout__title {
  font-size: 16px;
  font-weight: 500;
  color: #1f4e8c;
}

.main-layout__user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #1f4e8c;
}

.main-layout__content {
  /* 内容区透明，由全局深色科技背景承接 */
  background: transparent;
  padding: 16px;
}
</style>
