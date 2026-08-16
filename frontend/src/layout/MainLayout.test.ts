import { beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { createPinia, setActivePinia, type Pinia } from "pinia";
import ElementPlus from "element-plus";
import MainLayout from "@/layout/MainLayout.vue";
import router from "@/router";
import { useAuthStore } from "@/stores/auth";

vi.mock("@/api/auth", () => ({
  login: vi.fn(),
  fetchMe: vi.fn(),
  logout: vi.fn(),
}));

/** 挂载 MainLayout（显式使用同一 pinia，保证 store 状态一致）。 */
function mountLayout(pinia: Pinia) {
  return mount(MainLayout, {
    global: { plugins: [pinia, router, ElementPlus] },
  });
}

describe("MainLayout 菜单权限", () => {
  beforeEach(async () => {
    setActivePinia(createPinia());
    localStorage.setItem("guicang.token", "jwt-token");
    vi.clearAllMocks();
    await router.replace("/dashboard");
    await router.isReady();
  });

  it("admin 看到全部菜单", async () => {
    const pinia = createPinia();
    const store = useAuthStore(pinia);
    store.token = "jwt-token";
    store.user = {
      username: "admin",
      uid: 1,
      home: null,
      shell: null,
      roles: ["ROLE_ADMIN"],
    };

    const text = mountLayout(pinia).text();
    expect(text).toContain("同步任务");
    expect(text).toContain("操作记录");
  });

  it("member 只看到大屏与文件菜单", async () => {
    const pinia = createPinia();
    const store = useAuthStore(pinia);
    store.token = "jwt-token";
    store.user = {
      username: "bob",
      uid: 2,
      home: null,
      shell: null,
      roles: ["ROLE_MEMBER", "file.read", "dashboard.view"],
    };

    const text = mountLayout(pinia).text();
    expect(text).toContain("监控大屏");
    expect(text).toContain("文件管理");
    expect(text).not.toContain("同步任务");
    expect(text).not.toContain("操作记录");
  });
});
