# GuiCang Web 前端

GuiCang（归藏）家庭 NAS 管理系统前端，Vue 3 + TypeScript + Vite + Element Plus。

## 技术栈

Vue 3（组合式 API）、TypeScript（strict）、Vite 6、Element Plus、Pinia、Vue Router、Axios、ECharts、markdown-it、highlight.js、@vueuse/core。

## 开发

```bash
pnpm install   # 安装依赖
pnpm dev       # 开发服务器（5173，/api 代理到 127.0.0.1:8080）
pnpm build     # 类型检查 + 生产构建（输出 dist/）
pnpm preview   # 预览生产构建
pnpm lint      # ESLint 检查
pnpm format    # Prettier 格式化
```

## 目录结构

```text
src/
├── api/          # 按模块封装的接口（auth/file/user/monitor/sync/audit/dashboard/setup）
├── assets/       # 静态资源
├── components/   # 通用组件
├── composables/  # useAuth、useFiles、useMonitor 等组合式函数
├── layout/       # 侧边栏/顶栏/主框架
├── router/       # 路由与守卫
├── stores/       # Pinia（auth/files/monitor）
├── styles/       # 主题与全局样式
├── utils/        # axios 实例、权限指令、格式化
└── views/        # 页面（login/setup/dashboard/files/monitor/sync/audit/admin/settings）
```

编码规范见仓库根目录 AGENTS.md 与 docs/AI规则与编码规范.md。
