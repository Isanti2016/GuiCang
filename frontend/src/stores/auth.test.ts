import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useAuthStore } from "@/stores/auth";
import * as authApi from "@/api/auth";

vi.mock("@/api/auth", () => ({
  login: vi.fn(),
  fetchMe: vi.fn(),
  logout: vi.fn(),
}));

describe("auth store 行为", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    vi.clearAllMocks();
  });

  it("login 保存 token 与用户信息", async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      token: "jwt-token",
      user: {
        username: "admin",
        uid: 1003,
        home: null,
        shell: null,
        roles: ["ROLE_ADMIN", "user.manage"],
      },
    });

    const store = useAuthStore();
    await store.login({ username: "admin", password: "pass" });

    expect(store.token).toBe("jwt-token");
    expect(store.user?.username).toBe("admin");
    expect(localStorage.getItem("guicang.token")).toBe("jwt-token");
    expect(store.isAdmin()).toBe(true);
    expect(store.hasAuthority("user.manage")).toBe(true);
  });

  it("logout 清空 token 与用户", async () => {
    vi.mocked(authApi.logout).mockResolvedValue();

    const store = useAuthStore();
    store.token = "jwt-token";
    localStorage.setItem("guicang.token", "jwt-token");

    await store.logout();

    expect(store.token).toBeNull();
    expect(store.user).toBeNull();
    expect(localStorage.getItem("guicang.token")).toBeNull();
  });
});
