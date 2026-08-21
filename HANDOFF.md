# GuiCang 项目工作交接

> 新对话开始工作前，请先读本文件，再读 AGENTS.md 与 docs/ 下的文档。

## 一句话状态
**手册全部实施步骤（0.4–8.2）已实现并提交推送**：阶段 0–5 功能全完成，阶段 6 部署配置就绪，阶段 7 测试覆盖（后端 80.8% + 前端 11 冒烟/权限测试）+ 文档，阶段 8 upgrade.sh + 压测基线。最近一轮新增四个家庭 NAS 功能（回收站/修改密码/相册/大屏增强）已完成并提交推送。剩余仅为**需用户确认的宿主部署执行**（install-helper / dir-permissions / samba-include / 容器部署 / 正式压测与 PG 对比）。

## 项目与目标
- GuiCang（归藏）家庭 NAS 管理系统。仓库：https://github.com/Isanti2016/GuiCang
- 本地：/home/codes/guicang。目标：Web 统一管理 Samba/NFS 存储 /home/wb/nas（文件/用户/监控/同步/审计，Tailscale 外网）。

## 关键决策（已确认）
- Java 21 + Spring Boot 3.3.x + Maven；Vue 3 + TS + Vite 6 + Element Plus；SQLite（可切 PG）；Redis；本地 ELK；Docker Compose + Nginx。
- 账号：Web 用户 = Linux 系统用户，PAM 认证，sudo 白名单 guicang-helper，后端不跑 root；密码不落库。
- 提交：一律中文 Conventional Commits。

## 已实现（全部提交推送，后端 110 测试 / 前端 11 测试全绿）
- 认证/RBAC：PAM（helper/local 双实现）+ JWT（query token 支持媒体标签）、用户/角色/权限 CRUD、目录级 ACL
- 文件：目录操作、上传≤1G、Range 下载/流、md/txt 编辑、图片/视频缩略图、索引搜索
- 监控/大屏：30s 指标（2h/24h 序列）、聚合 API、前端 ECharts 大屏（30s 轮询）
- 同步/审计：目录扫描增量、Quartz 调度 + 立即执行 + 历史；注解 AOP 全链路审计 + 查询页
- 前端：登录/布局（按权限菜单）/文件管理/同步/审计/大屏
- 脚本：guicang-helper（8 子命令，26 单测 + 9 e2e）、install/dir-permissions/samba-include/setup/deploy/backup/upgrade
- 部署配置：Dockerfile、compose（nginx/backend/redis/es/kibana/filebeat）、nginx 反代（Range/1100m）、filebeat.yml、logback JSON
- 测试/文档：JaCoCo 80.8%（核心 Service 全达标）、Vitest 冒烟+权限矩阵、docs/压测报告.md（读 600+ req/s 基线）、README/HANDOFF/手册实施记录

## 最近一轮新增功能（家庭 NAS 增强，已提交推送）
- 回收站：文件删除改为软删除（移入 .guicang-trash），回收站列表/恢复/彻底删除/清空（TrashController + V5__trash.sql + TrashView.vue）
- 修改本人密码：PUT /auth/password（校验旧密码后经系统账号改密），MainLayout 下拉弹窗
- 相册：后端 media() 递归收集图片/视频，前端 GalleryView 瀑布流 + 灯箱预览（键盘翻页/类型筛选）
- 大屏增强：DashboardSummary.userStorage（按 personal/<user>/ 聚合）、文件类型分布饼图 + 用户存储条图

## 待用户确认执行（下一步，均不触碰 /home/wb/nas 现有数据）
1. `sudo ./scripts/install-helper.sh`（建组/服务账号/装 helper/sudoers）——可先 --dry-run
2. `sudo ./scripts/dir-permissions.sh --apply`（NAS 目录 setgid 权限，先备份快照）
3. `sudo ./scripts/samba-include.sh --apply`（Samba 共享段，smb.conf 备份 + testparm）
4. 补建 admin 系统账号（初始化向导已落元数据 pending）
5. `bash scripts/setup.sh && bash scripts/deploy.sh`（80 端口需先迁 Apache2 至 8081）
6. 部署后正式压测（SQLite vs PG 对比，见 docs/压测报告.md）

## 注意事项 / 已知坑
- 后端 dev 库 backend/data（gitignore）；测试共享内存 SQLite；Flyway 锁定 9.22.3。
- @Audit SpEL 需 -parameters（已配）；前端 md 渲染 markdown-it html:false。
- 停止后端用端口 PID（ss -ltnp | grep :8080），勿 pkill -f 匹配命令行。
- 需求/设计变更要同步 docs/ 四份文档与本文件。
