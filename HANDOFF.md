# GuiCang 项目工作交接

> 新对话开始工作前，请先读本文件，再读 AGENTS.md 与 docs/ 下的四份文档。

## 一句话状态
阶段 0–5 全部完成（M0 骨架 / M1 基础 / M2 账号 RBAC / M3 文件 / M4 监控大屏 / M5 同步审计），阶段 6 部署配置已写好（Dockerfile/compose/nginx/ELK/一键脚本）**但未执行**（需确认后部署）。后端 78 个测试全绿，前端 lint/build 通过。下一步：用户确认后部署（阶段 6 执行），之后阶段 7 测试文档、8 升级压测。

## 项目与目标
- 名称：GuiCang（归藏），家庭 NAS 管理系统。仓库：https://github.com/Isanti2016/GuiCang
- 本地：/home/codes/guicang。目标：Web 统一管理 Samba/NFS 存储 /home/wb/nas（文件/用户/监控/同步/审计，Tailscale 外网）。

## 已确认的关键决策
- Java 21 + Spring Boot 3.3.x + Maven；Vue 3 + TS + Vite 6 + Element Plus；SQLite（可切 PG）；Redis；本地 ELK；Docker Compose + Nginx。
- 账号：Web 用户 = Linux 系统用户，PAM 认证，sudo 白名单 guicang-helper 管理账号，后端不跑 root。
- 密码不落业务库；JWT HS256（密钥经 GUICANG_JWT_SECRET 注入，开发有内置默认值）。
- 提交：一律中文 Conventional Commits。

## 环境现状（本机 Ubuntu 24.04）
- Java 21.0.11 / Maven 3.9.16 / Node 24 / pnpm 11.7 / SQLite 3.45 / python3 3.12 / ffmpeg 6.1.1。
- Samba 4.19.5（pdbedit -L 正常，账号 wb/app_data）、NFS、Tailscale(100.112.98.102)、MariaDB（不用）、Apache2（80 端口占，部署阶段需迁 8081）。
- uid 1002、gid 2000/2001 空闲（未创建 nasusers/nasops/guicang-svc）。

## 已实现功能（全部提交并推送）
- 后端 API：认证（login/me/logout，PAM 校验 helper/local 双实现）、用户/角色/权限 CRUD（RBAC）、
  文件（list/mkdir/rename/move/delete/upload≤1G/download/stream Range/text/write/thumbnail/search）、
  监控（overview/series 30s 采集）、大屏 summary、同步任务（Quartz + 立即执行 + 历史）、审计（注解 AOP + 查询）、
  初始化向导。Flyway V1–V4。统一返回/全局异常。登录/文件/用户/同步全链路审计。
- 前端：登录、布局（侧边栏按权限）、大屏（ECharts 30s 轮询）、文件管理（树+表格+预览 md/图片/视频+编辑保存）、
  同步任务页、审计查询页。axios 拦截器（401 跳登录），img/video 媒体 URL 带 query token。
- 脚本：guicang-helper（8 子命令 + PAM 校验器，26 单测 + 9 e2e）、install-helper.sh（幂等安装）、
  dir-permissions.sh / samba-include.sh（目录权限与 Samba 共享生成）、setup.sh / deploy.sh / backup.sh。
- 部署配置：前后端 Dockerfile、docker-compose.yml（nginx/backend/redis/es/kibana/filebeat）、
  nginx 反代（Range + 1100m）、filebeat.yml、.env.example、logback JSON 日志。

## 待用户确认 / 后续步骤
1. **阶段 2 宿主准备**（影响现有 Samba/目录，须确认后执行）：
   - `sudo ./scripts/install-helper.sh`（建 nasusers/nasops/guicang-svc、装 helper、sudoers 白名单）
   - `sudo ./scripts/dir-permissions.sh --apply`（NAS 目录 setgid 权限，先备份快照）
   - `sudo ./scripts/samba-include.sh --apply`（生成 Samba 共享段，smb.conf 备份 + testparm）
   - 然后补建 admin 系统账号（初始化向导已落元数据 pending）
2. **阶段 6 部署**：`bash scripts/setup.sh` → `bash scripts/deploy.sh`（注意 80 端口：Apache2 需先迁 8081）。
3. 阶段 7：后端核心 Service 覆盖率、前端冒烟、权限矩阵验收；阶段 8：upgrade.sh、压测。

## 注意事项 / 已知坑
- 后端 dev 库 backend/data（gitignore）；测试用共享内存 SQLite。
- Flyway 锁定 9.22.3（10 社区版无 SQLite 模块）；@Audit SpEL 需 -parameters（已配）。
- 前端 markdown 渲染用 markdown-it（html:false 转义原始 HTML，v-html 仅用于 md 渲染结果）。
- 停止后端用端口 PID（ss -ltnp | grep :8080），勿用 pkill -f 匹配命令行（会误杀自身 bash）。
- 需求/设计变更要同步 docs/ 四份文档与本文件。
