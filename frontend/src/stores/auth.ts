import { defineStore } from "pinia";
import { ref } from "vue";
import {
  fetchMe,
  login as apiLogin,
  logout as apiLogout,
  type CurrentUserInfo,
  type LoginRequest,
} from "@/api/auth";
import { clearToken, getToken, setToken } from "@/utils/http";

/** 认证状态：token 与当前用户。 */
export const useAuthStore = defineStore("auth", () => {
  const token = ref<string | null>(getToken());
  const user = ref<CurrentUserInfo | null>(null);

  async function login(credentials: LoginRequest): Promise<void> {
    const response = await apiLogin(credentials);
    token.value = response.token;
    user.value = response.user;
    setToken(response.token);
  }

  async function fetchCurrentUser(): Promise<void> {
    user.value = await fetchMe();
  }

  async function logout(): Promise<void> {
    try {
      await apiLogout();
    } finally {
      token.value = null;
      user.value = null;
      clearToken();
    }
  }

  function hasAuthority(authority: string): boolean {
    return user.value?.roles.includes(authority) ?? false;
  }

  function isAdmin(): boolean {
    return user.value?.roles.includes("ROLE_ADMIN") ?? false;
  }

  return {
    token,
    user,
    login,
    fetchCurrentUser,
    logout,
    hasAuthority,
    isAdmin,
  };
});
