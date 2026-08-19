<script setup lang="ts">
// 半透明科技背景装饰层：玻璃面板 + 光晕 + 网格（固定于视口底层，不拦截交互）
</script>

<template>
  <div class="tech-bg" aria-hidden="true">
    <!-- 网格 -->
    <div class="tech-bg__grid" />
    <!-- 玻璃面板 -->
    <div class="tech-bg__glass tech-bg__glass--1" />
    <div class="tech-bg__glass tech-bg__glass--2" />
    <div class="tech-bg__glass tech-bg__glass--3" />
    <!-- 光晕 -->
    <div class="tech-bg__glow tech-bg__glow--1" />
    <div class="tech-bg__glow tech-bg__glow--2" />
    <!-- 圆环 -->
    <div class="tech-bg__ring" />
  </div>
</template>

<style scoped>
.tech-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.tech-bg__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(80, 140, 220, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(80, 140, 220, 0.07) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(ellipse at 50% 20%, black 30%, transparent 78%);
}

/* 半透明玻璃面板 */
.tech-bg__glass {
  position: absolute;
  border-radius: 28px;
  border: 1px solid rgba(255, 255, 255, 0.65);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.45), rgba(190, 220, 255, 0.22));
  backdrop-filter: blur(14px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.6),
    0 12px 40px rgba(90, 130, 210, 0.14);
}

.tech-bg__glass--1 {
  width: 400px;
  height: 280px;
  left: -90px;
  top: 14%;
  transform: rotate(-8deg);
}

.tech-bg__glass--2 {
  width: 320px;
  height: 220px;
  right: -70px;
  top: 46%;
  transform: rotate(6deg);
}

.tech-bg__glass--3 {
  width: 220px;
  height: 220px;
  left: 16%;
  bottom: -60px;
  border-radius: 50%;
}

/* 柔和光晕 */
.tech-bg__glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
}

.tech-bg__glow--1 {
  width: 520px;
  height: 520px;
  left: -120px;
  top: -120px;
  background: rgba(110, 175, 255, 0.22);
}

.tech-bg__glow--2 {
  width: 460px;
  height: 460px;
  right: -140px;
  bottom: -140px;
  background: rgba(130, 210, 255, 0.2);
}

/* 装饰圆环 */
.tech-bg__ring {
  position: absolute;
  width: 340px;
  height: 340px;
  right: 12%;
  top: 8%;
  border-radius: 50%;
  border: 1px dashed rgba(110, 160, 240, 0.35);
}
</style>
