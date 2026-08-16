import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import ElementPlus, { ElMessage } from "element-plus";
import LoginView from "@/views/LoginView.vue";
import router from "@/router";
import * as authApi from "@/api/auth";
import { useAuthStore } from "@/stores/auth";

vi.mock("@/api/auth", () => ({
  login: vi.fn(),
  fetchMe: vi.fn(),
  logout: vi.fn(),
}));

vi.mock("element-plus", async (importOriginal) => {
  const actual = await importOriginal<typeof import("element-plus")>();
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn() },
  };
});

describe("LoginView 冒烟", () => {
  beforeEach(async () => {
    setActivePinia(createPinia());
    localStorage.clear();
    vi.clearAllMocks();
    await router.replace("/login");
    await router.isReady();
  });

  it("渲染登录表单", async () => {
    const wrapper = mount(LoginView, {
      global: { plugins: [createPinia(), router, ElementPlus] },
    });
    expect(wrapper.find("h1").text()).toContain("GuiCang");
    expect(wrapper.find('input[type="password"]').exists()).toBe(true);
    expect(wrapper.find(".login-card__submit").exists()).toBe(true);
  });

  it("提交表单调用登录并保存令牌", async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: "jwt-token",
      user: {
        username: "admin",
        uid: 1003,
        home: null,
        shell: null,
        roles: ["ROLE_ADMIN"],
      },
    });

    const pinia = createPinia();
    const wrapper = mount(LoginView, {
      global: { plugins: [pinia, router, ElementPlus] },
    });

    await wrapper.find('input[placeholder="系统用户名"]').setValue("admin");
    await wrapper.find('input[type="password"]').setValue("pass");
    await wrapper.find(".login-card__submit").trigger("click");
    await flushPromises();

    expect(authApi.login).toHaveBeenCalledWith({
      username: "admin",
      password: "pass",
    });
    expect(useAuthStore(pinia).token).toBe("jwt-token");
    expect(localStorage.getItem("guicang.token")).toBe("jwt-token");
    expect(ElMessage.success).toHaveBeenCalled();
  });

  it("定义用户名密码必填校验规则", () => {
    const wrapper = mount(LoginView, {
      global: { plugins: [createPinia(), router, ElementPlus] },
    });
    const rules = (wrapper.vm as unknown as { rules: Record<string, unknown> })
      .rules;
    expect(JSON.stringify(rules.username)).toContain("required");
    expect(JSON.stringify(rules.password)).toContain("required");
  });
});
