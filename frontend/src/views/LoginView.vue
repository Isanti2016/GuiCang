<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { useAuthStore } from "@/stores/auth";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const formRef = ref<FormInstance>();
const loading = ref(false);

const form = reactive({
  username: "",
  password: "",
});

const rules: FormRules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};

async function handleLogin(): Promise<void> {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    await authStore.login({ username: form.username, password: form.password });
    ElMessage.success("登录成功");
    const redirect = (route.query.redirect as string) || "/dashboard";
    await router.replace(redirect);
  } catch {
    // 错误提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <!-- 透视科技网格 -->
    <div class="login-page__grid" />
    <!-- 浮动光晕 -->
    <div class="login-page__orb login-page__orb--1" />
    <div class="login-page__orb login-page__orb--2" />
    <div class="login-page__orb login-page__orb--3" />

    <el-card class="login-card">
      <div class="login-card__logo">
        <el-icon :size="40" color="#00e0ff"><Cpu /></el-icon>
      </div>
      <h1 class="login-card__title">GuiCang 归藏</h1>
      <p class="login-card__subtitle">家庭 NAS 管理系统 · 科技中枢</p>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="系统用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button class="login-card__submit" type="primary" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>
      <p class="login-card__hint">账号由管理员创建 · 密码同步系统账号</p>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background:
    radial-gradient(ellipse 55% 45% at 50% 0%, rgba(64, 158, 255, 0.16), transparent 65%),
    linear-gradient(165deg, #eef6ff 0%, #e3eefc 50%, #f8fbff 100%);
}

/* 透视网格（底部拉远形成纵深） */
.login-page__grid {
  position: absolute;
  inset: -20% -30% -40% -30%;
  background-image:
    linear-gradient(rgba(64, 158, 255, 0.14) 1px, transparent 1px),
    linear-gradient(90deg, rgba(64, 158, 255, 0.14) 1px, transparent 1px);
  background-size: 52px 52px;
  transform: perspective(520px) rotateX(58deg) translateY(18%);
  transform-origin: center bottom;
  animation: gc-grid-move 18s linear infinite;
  mask-image: linear-gradient(to top, rgba(0, 0, 0, 0.9), transparent 88%);
}

@keyframes gc-grid-move {
  from {
    background-position: 0 0;
  }
  to {
    background-position: 0 52px;
  }
}

/* 浮动光晕 */
.login-page__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.5;
  pointer-events: none;
}

.login-page__orb--1 {
  width: 420px;
  height: 420px;
  left: -80px;
  top: -60px;
  background: rgba(64, 158, 255, 0.35);
  animation: gc-float 9s ease-in-out infinite;
}

.login-page__orb--2 {
  width: 320px;
  height: 320px;
  right: -60px;
  bottom: 10%;
  background: rgba(0, 200, 255, 0.3);
  animation: gc-float 11s ease-in-out infinite reverse;
}

.login-page__orb--3 {
  width: 180px;
  height: 180px;
  left: 20%;
  bottom: 6%;
  background: rgba(96, 165, 250, 0.35);
  animation: gc-float 8s ease-in-out infinite 1s;
}

@keyframes gc-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-28px);
  }
}

.login-card {
  position: relative;
  width: 400px;
  padding: 8px 8px 4px;
  background: rgba(255, 255, 255, 0.92) !important;
  border: 1px solid rgba(64, 158, 255, 0.35) !important;
  border-radius: 12px;
  box-shadow:
    0 0 24px rgba(64, 158, 255, 0.18),
    0 12px 40px rgba(64, 158, 255, 0.15);
  backdrop-filter: blur(8px);
}

.login-card__logo {
  display: flex;
  justify-content: center;
  margin-bottom: 6px;
}

.login-card__title {
  margin: 0;
  font-size: 26px;
  text-align: center;
  letter-spacing: 4px;
  color: #1f4e8c;
  text-shadow: 0 1px 0 rgba(255, 255, 255, 0.6);
}

.login-card__subtitle {
  margin: 8px 0 22px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  letter-spacing: 2px;
}

.login-card__submit {
  width: 100%;
}

.login-card__hint {
  margin: 14px 0 4px;
  text-align: center;
  color: rgba(31, 78, 140, 0.55);
  font-size: 12px;
}
</style>
