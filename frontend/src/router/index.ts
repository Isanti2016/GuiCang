import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from "vue-router";
import { getToken } from "@/utils/http";

const routes: RouteRecordRaw[] = [
  {
    path: "/login",
    name: "login",
    component: () => import("@/views/LoginView.vue"),
    meta: { public: true },
  },
  {
    path: "/",
    component: () => import("@/layout/MainLayout.vue"),
    children: [
      { path: "", redirect: "/dashboard" },
      {
        path: "dashboard",
        name: "dashboard",
        component: () => import("@/views/DashboardView.vue"),
        meta: { title: "监控大屏" },
      },
      {
        path: "files",
        name: "files",
        component: () => import("@/views/FileView.vue"),
        meta: { title: "文件管理" },
      },
      {
        path: "gallery",
        name: "gallery",
        component: () => import("@/views/GalleryView.vue"),
        meta: { title: "相册" },
      },
      {
        path: "cameras",
        name: "cameras",
        component: () => import("@/views/CameraView.vue"),
        meta: { title: "监控录像" },
      },
      {
        path: "trash",
        name: "trash",
        component: () => import("@/views/TrashView.vue"),
        meta: { title: "回收站" },
      },
      {
        path: "monitor",
        name: "monitor",
        component: () => import("@/views/MonitorView.vue"),
        meta: { title: "监控详情", adminOnly: true },
      },
      {
        path: "admin/users",
        name: "admin-users",
        component: () => import("@/views/UserView.vue"),
        meta: { title: "用户管理", adminOnly: true },
      },
      {
        path: "admin/roles",
        name: "admin-roles",
        component: () => import("@/views/RoleView.vue"),
        meta: { title: "角色与权限", adminOnly: true },
      },
      {
        path: "help",
        name: "help",
        component: () => import("@/views/HelpView.vue"),
        meta: { title: "使用手册" },
      },
      {
        path: "sync",
        name: "sync",
        component: () => import("@/views/SyncView.vue"),
        meta: { title: "同步任务", adminOnly: true },
      },
      {
        path: "audit",
        name: "audit",
        component: () => import("@/views/AuditView.vue"),
        meta: { title: "操作记录", adminOnly: true },
      },
      {
        path: "logs",
        name: "logs",
        component: () => import("@/views/LogView.vue"),
        meta: { title: "系统日志", adminOnly: true },
      },
      {
        path: "settings",
        name: "settings",
        component: () => import("@/views/SystemSettingsView.vue"),
        meta: { title: "系统设置", adminOnly: true },
      },
      {
        path: "admin/sessions",
        name: "admin-sessions",
        component: () => import("@/views/SessionView.vue"),
        meta: { title: "会话管理", adminOnly: true },
      },
    ],
  },
  {
    // 小说阅读器：独立全屏页面（不套 MainLayout），沉浸式阅读体验
    path: "/reader",
    name: "reader",
    component: () => import("@/views/ReaderView.vue"),
    meta: { title: "阅读" },
  },
  { path: "/:pathMatch(.*)*", redirect: "/dashboard" },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 路由守卫：未登录跳 /login；adminOnly 路由需 ROLE_ADMIN
router.beforeEach((to) => {
  const authenticated = Boolean(getToken());
  if (!to.meta.public && !authenticated) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (to.name === "login" && authenticated) {
    return { path: "/dashboard" };
  }
  return true;
});

export default router;
