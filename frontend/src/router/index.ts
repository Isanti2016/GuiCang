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
    ],
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
