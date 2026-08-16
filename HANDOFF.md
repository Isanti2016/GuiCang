# GuiCang 项目工作交接

> 新对话开始工作前，请先读本文件，再读 AGENTS.md 与 docs/ 下的四份文档。

## 一句话状态
方案已定稿；开发环境已装好；仓库已初始化并推送到 GitHub；尚未开始写功能代码。下一步从《GuiCang技术方案与实施手册》Step 0.4 开始搭前后端工程骨架。

## 项目与目标
- 名称：GuiCang（归藏），家庭 NAS 管理系统。
- 仓库：https://github.com/Isanti2016/GuiCang
- 本地仓库/工作目录：/home/codes/guicang
- 目标：Web 统一管理现有 Samba/NFS 存储 /home/wb/nas，提供用户权限、文件管理、md/txt 编辑、图片视频预览、监控大屏、同步、审计，Tailscale 外网访问。

## 已确认的关键决策
- 技术栈：Java 21 + Spring Boot 3.3.x + Maven；Vue 3 + TS + Element Plus + Vite；SQLite（默认，可切 PostgreSQL）；Redis；本地 ELK（Filebeat→ES→Kibana）；Docker Compose + Nginx。
- 并发：注册数百、同时在线数十、读多写少；单文件 ≤1G。
- 账号打通：方案 A —— Web 用户 = Linux 系统用户，PAM 认证，sudo 白名单 helper（guicang-helper）管理系统账号，后端不跑 root。
- 初始管理员：首次初始化新建独立 admin。
- md/txt：一期网页内编辑保存；缩略图一期做；视频一期不转码（只播 MP4/H.264）；一期 HTTP；分享链接二期。
- 规范：Lombok 可用（record 优先）；后端 Spotless + Google Java Format；前端 Prettier 默认；核心 Service 行覆盖率 60%+。
- 提交：一律中文 Conventional Commits（类型(范围): 中文描述）。

## 环境现状（本机 Ubuntu 24.04，4C/15G，数据盘 /home 916G）
已安装：Java 21.0.11、Maven 3.9.16（/opt/maven）、Docker Compose 2.40.3、SQLite 3.45.1、Docker 29.1.3、Node 24、pnpm 11、Git 2.43。
已有服务：Samba 4.19.5（共享根 /home/wb/nas）、NFS、Tailscale（IP 100.112.98.102，在线）、MariaDB 10.11（本项目不用）、Apache2（占 80 端口，需迁 8081）、ffmpeg 6.1.1。
已有 Docker 镜像：nginx:latest、postgres:latest、oracle:19c（oracle 与本项目无关）。
端口注意：80 给 Nginx；3080 是 DSH GUI，绝不能动；3306 MariaDB；445/139 Samba；2049 NFS。

## Git 与 SSH
- 本地 /home/codes/guicang，分支 main，origin = git@github.com:Isanti2016/GuiCang.git（SSH）。
- 私钥 /root/.ssh/guicang_github，~/.ssh/config 已配置 github.com，`ssh -T git@github.com` 已验证通过。
- 已推送首个提交：22a93a6 chore: 初始化仓库（README/AGENTS/规范与设计文档）。

## 文档索引（都在 docs/）
- NAS管理系统需求清单.md：需求与决策记录
- NAS系统详细设计.md：模块、数据表、API 设计
- GuiCang技术方案与实施手册.md：技术选型、部署视图、33 步实施步骤（核心执行依据）
- AI规则与编码规范.md：AI 与前后端编码规范
- 根目录 AGENTS.md：AI 硬约束（必读，与 docs/AI规则与编码规范.md 同为本项目绑定规范）

## 下一步（按手册逐 Step 执行，每步验证后中文提交）
- Step 0.4：前端工程初始化（pnpm create vite + Element Plus/Pinia/Router/Axios/ECharts/markdown-it/highlight.js/@vueuse/core + ESLint/Prettier）。
- Step 0.5：后端工程初始化（Spring Boot 3.3 + pom + sqlite/postgresql 多 profile + hello 接口）。
- 之后按 M1→M8 推进；每个 Step 完成一部分提交一部分。

## 注意事项 / 已知坑
- 新会话若要做系统级安装或写工作区外目录，需把沙箱切到 danger-full-access（切换前先关闭 persistent terminal）。
- Maven 实际版本为 3.9.16（手册写 3.9.x；archive.apache.org 慢，dlcdn 快）。
- pdbedit 当前报 tdbsam 打开失败（tdbdump 可读），Step 2.1 做 helper 时先修复再依赖。
- 后端容器不跑 root，特权操作统一走 guicang-helper。
- 需求/设计变更要同步更新 docs/ 四份文档。
