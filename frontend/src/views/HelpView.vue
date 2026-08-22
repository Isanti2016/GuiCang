<script setup lang="ts">
import { ref } from "vue";

const active = ref("intro");

interface Section {
  name: string;
  title: string;
  items: { title: string; body: string }[];
}

const sections: Section[] = [
  {
    name: "intro",
    title: "系统简介",
    items: [
      {
        title: "GuiCang 归藏是什么",
        body: "家庭 NAS 管理系统：通过网页统一管理 Linux 系统账号、Samba/NFS 共享存储、文件、监控、同步与审计。账号即系统账号（PAM 认证），Web 与 Samba 使用同一套账号密码。",
      },
      {
        title: "登录与初始化",
        body: "首次访问会进入初始化向导，创建管理员账号并锁定。之后使用系统账号密码登录；密码修改会同时同步 Linux 与 Samba。",
      },
    ],
  },
  {
    name: "files",
    title: "文件管理",
    items: [
      {
        title: "目录结构",
        body: "存储根下分 shared（共享，成员可读写）、media（媒体，只读为主）、personal/<用户名>（个人目录，仅本人与管理员）、backups（备份，仅管理员）。",
      },
      {
        title: "基本操作",
        body: "左侧目录树浏览；工具栏可新建目录、上传文件（单文件 ≤1G）；行内操作支持移动、重命名、删除。双击或点预览打开预览抽屉。",
      },
      {
        title: "预览与编辑",
        body: "md/txt 支持网页内预览（Markdown 渲染、代码高亮）与编辑保存；图片直接显示；视频支持拖动播放（HTTP Range）。图片与视频自动生成缩略图。",
      },
      {
        title: "上传限制",
        body: "单文件不超过 1G；危险扩展名（exe/sh/ps1 等）会被拦截；同名文件上传会提示目标已存在，请先重命名。",
      },
      {
        title: "搜索",
        body: "顶部搜索框按文件名/路径关键字查找，结果仅显示你有权限访问的文件。",
      },
    ],
  },
  {
    name: "users",
    title: "用户与权限",
    items: [
      {
        title: "用户管理",
        body: "管理员在「用户管理」创建用户：用户名即系统账号（小写字母/数字开头），初始密码至少 8 位，创建时同步 Linux 与 Samba。支持编辑、启用/停用、重置密码、删除（默认保留个人目录）。",
      },
      {
        title: "角色与权限点",
        body: "内置角色：admin（全部权限）、member（个人目录 + 共享读写）、guest（共享只读）。可在「角色与权限」自定义角色并勾选功能权限点（文件读写/用户管理/审计查看/同步管理等）。",
      },
      {
        title: "目录级权限",
        body: "除功能权限外，目录按规则控制：admin 全路径管理；member 可写本人 personal 目录与 shared；guest 只读 shared；media 所有登录用户可读。管理员可对特定用户授予额外目录权限。",
      },
    ],
  },
  {
    name: "monitor",
    title: "监控大屏",
    items: [
      {
        title: "实时指标",
        body: "大屏每 30 秒自动刷新，展示磁盘已用/可用、CPU 使用率、内存使用率、负载与文件/用户统计，以及最近操作记录。",
      },
      {
        title: "趋势图",
        body: "CPU 使用率趋势图展示近 2 小时细粒度数据（30s 采样）；监控详情接口还保留 24 小时粗粒度序列。",
      },
    ],
  },
  {
    name: "sync",
    title: "同步任务",
    items: [
      {
        title: "任务与调度",
        body: "管理员可创建目录扫描任务（源目录为存储根下相对路径），配置 Quartz cron 表达式定时执行；也可点「立即执行」手动触发。",
      },
      {
        title: "执行历史",
        body: "每次执行记录开始/结束时间、状态与新增/更新/删除数量；执行结果会更新文件索引，供搜索与大屏统计使用。",
      },
    ],
  },
  {
    name: "audit",
    title: "操作记录",
    items: [
      {
        title: "审计留痕",
        body: "登录、用户/角色变更、文件操作、同步任务等关键操作均记录审计：操作者、动作、对象、来源 IP、结果与时间，支持按用户名/动作/结果筛选分页查询。",
      },
    ],
  },
  {
    name: "faq",
    title: "常见问题",
    items: [
      {
        title: "忘记密码怎么办",
        body: "联系管理员在「用户管理」中重置密码（同时同步系统账号与 Samba）。",
      },
      {
        title: "视频播放不了",
        body: "一期仅支持浏览器原生格式（MP4/H.264）；MKV/HEVC 等格式暂不支持在线播放，可先下载到本地。",
      },
      {
        title: "上传大文件慢或失败",
        body: "单文件上限 1G；请确认目标目录有写权限；网络中断时可重新上传（一期不支持断点续传）。",
      },
      {
        title: "Samba 访问",
        body: "Web 账号即 Samba 账号，同一密码；共享目录权限与 Web 保持一致（nasusers 组 + setgid）。",
      },
    ],
  },
];
</script>

<template>
  <div class="help-view">
    <el-card shadow="never" class="help-view__card">
      <div class="help-view__header">
        <h2 class="help-view__title">使用手册</h2>
        <p class="help-view__desc">
          GuiCang 归藏 · 家庭 NAS 管理系统功能说明与常见问题
        </p>
      </div>

      <el-collapse v-model="active" accordion>
        <el-collapse-item
          v-for="section in sections"
          :key="section.name"
          :name="section.name"
        >
          <template #title>
            <span class="help-view__section-title">{{ section.title }}</span>
          </template>
          <div
            v-for="item in section.items"
            :key="item.title"
            class="help-view__item"
          >
            <h4 class="help-view__item-title">{{ item.title }}</h4>
            <p class="help-view__item-body">{{ item.body }}</p>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<style scoped>
.help-view__card {
  /* 兼容路由页面与抽屉两种容器：页面内限宽居中，抽屉内撑满 */
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  border: 1px solid rgba(126, 210, 255, 0.18);
  background: linear-gradient(160deg, rgba(8, 26, 54, 0.85), rgba(4, 16, 38, 0.9));
  backdrop-filter: blur(10px);
  border-radius: 14px;
}

.help-view__header {
  margin-bottom: 16px;
}

.help-view__title {
  margin: 0 0 6px;
  font-size: 22px;
  letter-spacing: 2px;
  color: #eaf4ff;
  text-shadow: 0 0 12px rgba(0, 224, 255, 0.4);
}

.help-view__desc {
  margin: 0;
  color: rgba(159, 198, 234, 0.75);
  font-size: 13px;
}

.help-view__section-title {
  font-weight: 600;
  letter-spacing: 1px;
}

.help-view__item {
  padding: 6px 4px 10px;
  border-bottom: 1px dashed rgba(64, 158, 255, 0.14);
}

.help-view__item:last-child {
  border-bottom: none;
}

.help-view__item-title {
  margin: 0 0 4px;
  font-size: 14px;
  color: #9fc3ff;
}

.help-view__item-body {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

.help-view :deep(.el-collapse) {
  --el-collapse-border-color: rgba(126, 210, 255, 0.16);
  --el-collapse-header-text-color: #bfe9ff;
  --el-collapse-content-text-color: #cfe7f8;
  --el-collapse-header-bg-color: transparent;
  --el-collapse-content-bg-color: transparent;
  border-top: none;
  border-bottom: none;
}
</style>
