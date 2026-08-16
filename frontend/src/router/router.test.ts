import { beforeEach, describe, expect, it } from "vitest";
import router from "@/router";

describe("路由守卫", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("未登录访问受保护路由跳转登录", async () => {
    await router.push("/dashboard");
    await router.isReady();
    expect(router.currentRoute.value.name).toBe("login");
  });

  it("未登录访问文件管理跳转登录", async () => {
    await router.push("/files");
    await router.isReady();
    expect(router.currentRoute.value.name).toBe("login");
  });

  it("已登录可访问受保护路由", async () => {
    localStorage.setItem("guicang.token", "jwt-token");
    await router.push("/dashboard");
    await router.isReady();
    expect(router.currentRoute.value.name).toBe("dashboard");
  });

  it("已登录访问 login 重定向大屏", async () => {
    localStorage.setItem("guicang.token", "jwt-token");
    await router.push("/login");
    await router.isReady();
    expect(router.currentRoute.value.path).toBe("/dashboard");
  });
});
