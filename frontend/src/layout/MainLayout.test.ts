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
    expect(text).toContain("自动整理");
    expect(text).toContain("操作记录");
  });

  it("member 只看到非管理菜单（大屏/文件/相册/回收站）", async () => {
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
    expect(text).toContain("相册");
    expect(text).toContain("回收站");
    expect(text).not.toContain("自动整理");
    expect(text).not.toContain("操作记录");
    expect(text).not.toContain("用户管理");
  });
});
