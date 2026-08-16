<script setup lang="ts">
const projectName = "GuiCang 归藏";
</script>

<template>
  <div class="landing">
    <h1 class="landing__title">{{ projectName }}</h1>
    <p class="landing__desc">家庭 NAS 管理系统 · 工程骨架已就绪</p>
    <el-button type="primary">Element Plus 已接入</el-button>
  </div>
</template>

<style scoped>
.landing {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.landing__title {
  margin: 0;
  font-size: 32px;
}

.landing__desc {
  margin: 0;
  color: var(--el-text-color-secondary);
}
</style>
