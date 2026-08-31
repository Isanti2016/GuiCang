# GuiCang（归藏）

家庭 NAS 管理系统：Web 统一管理现有 Samba/NFS 存储 `/home/wb/nas`，提供用户权限、文件管理、md/txt 编辑、图片视频预览、监控大屏、同步、审计，Tailscale 外网访问。

## 技术栈

- 后端：Java 21 + Spring Boot 3.3 + Maven + MyBatis-Plus + Flyway + Quartz + SQLite（可切 PostgreSQL）
- 前端：Vue 3 + TypeScript + Vite 6 + Element Plus + Pinia + ECharts
- 部署：Docker Compose + Nginx + Redis

## 已实现功能

- **账号与权限**：PAM 认证（宿主机系统账号）+ JWT；RBAC 角色/权限点；目录级 ACL；两步验证（TOTP）；登录会话管理（在线设备列表 + 踢下线）
- **文件管理**：目录列表/新建/重命名/移动/删除；上传（≤1G 流式 + 大文件分片 >50MB）；下载/预览（HTTP Range 206）；批量打包下载（zip）；文件分享链接（密码/有效期）；md/txt 网页编辑 + 历史版本回滚；全文检索（名称 + 内容）；重复文件检测；文件收藏（星标）；图片/视频缩略图
- **相册**：瀑布流 + 时间线视图（按日期分组）+ 灯箱预览
- **监控大屏**：30s 主机指标采集（CPU/内存/磁盘/网络）；大屏聚合 + ECharts 可视化
- **同步**：目录扫描增量 + 自动整理；Quartz 定时 + 立即执行 + 历史；失败告警
- **审计**：注解 AOP 全链路留痕（登录/文件/用户/同步/角色），筛选分页查询
- **告警与通知**：磁盘空间告警、同步失败告警，站内通知中心（铃铛未读数）
- **磁盘配额**：按用户配额拦截（上传超限拒绝）
- **WebDAV**：Basic Auth（PAM）+ PROPFIND/GET/PUT/MKCOL/DELETE/MOVE，第三方客户端（Windows/macOS/手机）直接挂载 /dav/
- **监控录像**：接收目录自动归档（按 摄像头/日期，摄像头自动注册），Web 端按摄像头+日期浏览播放（Range 流式）；接入说明见 docs/监控录像接入.md
- **小说阅读器**：TXT/EPUB 识别（TXT 编码探测 + 章节索引，EPUB 标准解析）+ 沉浸式阅读页（目录/字号/行距/主题/进度记忆），文件管理点击 txt|epub 直接阅读
- **初始化向导**：首次访问创建 admin，完成后锁定

## 目录结构

```text
backend/    Spring Boot 后端（common/config/module.*/infra）
frontend/   Vue 3 前端（api/components/composables/layout/router/stores/views）
deploy/     docker-compose、nginx、.env.example
scripts/    guicang-helper（特权脚本）、install/dir-permissions/samba-include、
            setup/deploy/backup/upgrade
docs/       需求/设计/技术方案/规范文档
```

## 快速开始（开发）

后端：

```bash
cd backend
mvn spring-boot:run                 # 默认 sqlite + local PAM 校验（本机开发）
GUICANG_AUTH_VERIFIER=local mvn spring-boot:run
```

前端：

```bash
cd frontend
pnpm install
pnpm dev                            # 5173，/api 代理到 127.0.0.1:8080
```

测试：

```bash
cd backend && mvn verify            # 102 个后端测试 + JaCoCo 覆盖率报告（80%+）
cd frontend && pnpm test            # 11 个前端测试（Vitest）
```

## 部署

### 方式一：Docker 一键启动（推荐，轻量）

无需宿主机特权前置，容器内自建隔离管理员账号，开箱即用：

```bash
bash scripts/docker-start.sh          # 一键：生成 .env → 构建镜像 → 启动 → 健康检查
# 访问 http://<主机IP>:80/ （默认端口，.env 的 GUICANG_HTTP_PORT 可改）
# 登录：admin / 密码见 deploy/.env 的 GUICANG_CONTAINER_ADMIN_PASSWORD

bash scripts/docker-start.sh stop     # 停止（保留数据卷）
bash scripts/docker-start.sh restart  # 重启
bash scripts/docker-start.sh status   # 状态
bash scripts/docker-start.sh logs     # 日志
bash scripts/docker-start.sh down     # 停止并移除容器/网络（保留数据卷）
bash scripts/docker-start.sh full     # 完整模式：宿主机 helper 认证
```

说明：
- 轻量模式拓扑为 `nginx(:80) → backend(:8080 内部)`，无 Redis/ELK 依赖，内存占用小。
- 认证为**容器内系统账号**（entrypoint 自动创建 admin），与宿主机凭据完全隔离。
- 存储根默认 `/home/wb/nas`（bind 挂载），可用 `.env` 的 `GUICANG_STORAGE_ROOT` 修改。
- 生产环境（需要宿主机系统账号/Samba 一致认证）请用 `docker-start.sh full`。

### 方式二：完整部署（生产，宿主机账号体系）

```bash
# 1. 宿主特权边界（需确认，见 HANDOFF）
sudo ./scripts/install-helper.sh
sudo ./scripts/dir-permissions.sh --apply
sudo ./scripts/samba-include.sh --apply

# 2. 一键部署
bash scripts/setup.sh               # 生成 .env 与密钥
bash scripts/deploy.sh              # compose 构建启动 + 健康检查

# 3. 升级/备份
bash scripts/upgrade.sh             # 备份 → 新镜像 → Flyway → 失败回滚
bash scripts/backup.sh              # SQLite + 配置 + 权限快照（保留 7 份）
```

## 文档

- `HANDOFF.md`：工作交接与进度
- `docs/`：需求清单、详细设计、技术方案与实施手册、AI 规则与编码规范

## 开发约束

所有编码**强制**遵循根目录 `AGENTS.md` 与 `docs/AI规则与编码规范.md`（绑定约束）；提交信息用中文 Conventional Commits。
