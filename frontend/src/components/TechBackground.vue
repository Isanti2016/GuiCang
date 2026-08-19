<script setup lang="ts">
// 深海鲸语背景装饰层：冰蓝玻璃面板 + 金色细线圆环 + 冰蓝光晕（不拦截交互）
</script>

<template>
  <div class="tech-bg" aria-hidden="true">
    <!-- 冰蓝网格（淡出） -->
    <div class="tech-bg__grid" />
    <!-- 深海玻璃面板 -->
    <div class="tech-bg__glass tech-bg__glass--1" />
    <div class="tech-bg__glass tech-bg__glass--2" />
    <div class="tech-bg__glass tech-bg__glass--3" />
    <!-- 冰蓝光晕 -->
    <div class="tech-bg__glow tech-bg__glow--1" />
    <div class="tech-bg__glow tech-bg__glow--2" />
    <!-- 金色细线圆环 -->
    <div class="tech-bg__ring" />
    <!-- 金色细线弧 -->
    <div class="tech-bg__arc" />
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
    linear-gradient(rgba(126, 210, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(126, 210, 255, 0.06) 1px, transparent 1px);
  background-size: 46px 46px;
  mask-image: radial-gradient(ellipse at 50% 20%, black 30%, transparent 78%);
}

/* 深海玻璃面板（冰蓝半透明） */
.tech-bg__glass {
  position: absolute;
  border-radius: 28px;
  border: 1px solid rgba(140, 220, 255, 0.28);
  background: linear-gradient(135deg, rgba(30, 70, 130, 0.32), rgba(8, 26, 55, 0.22));
  backdrop-filter: blur(14px);
  box-shadow:
    inset 0 1px 0 rgba(190, 235, 255, 0.25),
    0 12px 40px rgba(2, 10, 26, 0.45);
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

/* 冰蓝光晕 */
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
  background: rgba(70, 160, 235, 0.18);
}

.tech-bg__glow--2 {
  width: 460px;
  height: 460px;
  right: -140px;
  bottom: -140px;
  background: rgba(110, 200, 255, 0.16);
}

/* 金色细线圆环 */
.tech-bg__ring {
  position: absolute;
  width: 340px;
  height: 340px;
  right: 12%;
  top: 8%;
  border-radius: 50%;
  border: 1px dashed rgba(212, 175, 55, 0.5);
}

/* 金色细线弧（左下方） */
.tech-bg__arc {
  position: absolute;
  width: 260px;
  height: 130px;
  left: 8%;
  bottom: 16%;
  border: 1px solid rgba(212, 175, 55, 0.45);
  border-radius: 50%;
  border-bottom: none;
  transform: rotate(-12deg);
}
</style>
