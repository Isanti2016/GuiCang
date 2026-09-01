# GuiCang 项目工作交接

> 新对话开始工作前，请先读本文件，再读 AGENTS.md 与 docs/ 下的文档。

## 一句话状态
**手册全部实施步骤（0.4–8.2）已实现并提交推送**：阶段 0–5 功能全完成，阶段 6 部署配置就绪，阶段 7 测试覆盖（后端 80.8% + 前端 11 冒烟/权限测试）+ 文档，阶段 8 upgrade.sh + 压测基线。最近一轮新增小说阅读器（TXT/EPUB 识别 + 章节阅读 + 进度记忆）与 WebDAV 防火墙修复，并补齐 dav/favorite/notification/share/reader 测试（后端 225 测试 / 前端 23 测试全绿）。**正式环境已部署（2026-09-01）：宿主 systemd 部署**——后端由 systemd 托管（guicang-svc 非 root 身份，开机自启+崩溃自恢复，helper 认证），生产前端由 Nginx 托管于 **8090 端口**（80 保留给 Nextcloud）。Samba 共享（nas-shared/nas-media/personal-admin）已上线。仍可选做：SQLite vs PG 正式对比压测、多设备验收。

## 项目与目标
- GuiCang（归藏）家庭 NAS 管理系统。仓库：https://github.com/Isanti2016/GuiCang
- 本地：/home/codes/guicang。目标：Web 统一管理 Samba/NFS 存储 /home/wb/nas（文件/用户/监控/同步/审计，Tailscale 外网）。

## 关键决策（已确认）
- Java 21 + Spring Boot 3.3.x + Maven；Vue 3 + TS + Vite 6 + Element Plus；SQLite（可切 PG）；Redis；Docker Compose + Nginx。
- 账号：Web 用户 = Linux 系统用户，PAM 认证，sudo 白名单 guicang-helper，后端不跑 root；密码不落库。
- 提交：一律中文 Conventional Commits。

## 已实现（全部提交推送，后端 225 测试 / 前端 23 测试全绿）
- 认证/RBAC：PAM（helper/local 双实现）+ JWT（query token 支持媒体标签）、用户/角色/权限 CRUD、目录级 ACL
- 文件：目录操作、上传≤1G、Range 下载/流、md/txt 编辑、图片/视频缩略图、索引搜索
- 监控/大屏：30s 指标（2h/24h 序列）、聚合 API、前端 ECharts 大屏（30s 轮询）
- 同步/审计：目录扫描增量、Quartz 调度 + 立即执行 + 历史；注解 AOP 全链路审计 + 查询页
- 前端：登录/布局（按权限菜单）/文件管理/同步/审计/大屏
- 脚本：guicang-helper（8 子命令，26 单测 + 9 e2e）、install/dir-permissions/samba-include/setup/deploy/backup/upgrade
- 部署配置：Dockerfile、compose（nginx/backend/redis）、nginx 反代（Range/1100m）、logback 滚动文件日志
- 测试/文档：JaCoCo 80.8%（核心 Service 全达标）、Vitest 冒烟+权限矩阵、docs/压测报告.md（读 600+ req/s 基线）、README/HANDOFF/手册实施记录

## 最近一轮新增功能（2026-08-30 夜间，18 项增强，已提交推送）
- 打包下载：多选/目录 zip 打包下载（/files/zip，临时 zip 1h 自清）
- 分享链接：文件/目录分享（token + 密码 SHA-256 + 过期），免登录下载（/shares/{token}/download）
- 全文检索：md/txt 内容索引（file_index.content 列）+ /files/search-content，前端搜索合并名称+内容
- 重复文件检测：size + SHA-256 分组找重复（/files/duplicates），前端可删
- 文件版本历史：md/txt 编辑留档（file_version 表，保留 20 版）+ 回滚（/files/versions）
- 磁盘告警 + 通知中心：DiskAlertJob 磁盘使用率超阈值告警（24h 去重）+ 站内通知（notification 表）+ 顶部铃铛
- 磁盘配额拦截：上传时校验用户已用空间 vs quota_bytes（sumSizeByPrefix）
- 同步失败告警：SyncRunJob 失败/部分失败生成站内通知
- 相册时间线：GalleryView 网格/时间线切换，按日期分组倒序
- 登录会话管理：登录记录会话（token SHA-256 + ip + UA），会话列表 + 踢下线（JWT 黑名单）
- 两步验证：TOTP（自写 HMAC-SHA1 + Base32，无第三方依赖），登录校验 + 开启/关闭
- 分片上传：>50MB 自动分片（5MB/片）+ 合并（/files/chunk + /files/chunk/complete）
- 文件收藏：星标收藏 + 收藏列表（favorite 表）
- WebDAV：Basic Auth（PAM）+ PROPFIND/GET/PUT/MKCOL/DELETE/MOVE（/dav/**），第三方客户端直接挂载
- 监控录像：接收目录（可配置）→ 每 5 分钟自动归档 cameras/archive/{摄像头}/{日期}/（camera 表自动注册 + 文件名日期解析 + 冲突后缀），Web 端监控录像页按摄像头/日期浏览播放
- 另：修复磁盘告警阈值设置项未注册问题（disk.alert-threshold 现可在系统设置调整）；新增 docs/手机相册备份接入指南.md 与 docs/移动端App方案.md
- 另：移除 ELK 日志链路（logback 滚动文件）；自动整理补齐 audio/document 分类

## 本轮新增（2026-08-31，小说阅读器 + 测试补齐，已提交推送）
- 小说阅读器：TXT/EPUB 识别（TXT 编码探测 BOM/UTF-8/GB18030 + 正则章节；EPUB container→OPF→spine 解析 + Zip Slip 防御），章节阅读 API（/api/v1/reader/novel|chapter|progress），阅读进度按用户+文件持久化（reading_progress 表，V15 迁移）
- 阅读体验：前端 ReaderView 沉浸式阅读页（/reader?path=），目录侧栏、字号/行距/主题（纸质/羊皮纸/夜间）本地记忆、滚动防抖自动保存进度、章节上下翻
- 文件管理入口：FileView 点击/双击 txt|epub 直接进阅读器，卡片与表格操作列新增「阅读」按钮
- 修复生产 bug：Spring Security 6 StrictHttpFirewall 默认拦截 WebDAV 非标准方法（PROPFIND/MKCOL/MOVE 等全部 400）→ 显式 HttpFirewall 白名单 + DavController OPTIONS 显式路由

## 本轮新增（2026-08-31，断点续传 + PWA，已提交推送）
- 分片断点续传：新增 /files/chunk/status 查询已传分片（uploadedChunks + uploadedBytes），前端大文件上传改稳定指纹 uploadId（大小+名字哈希+修改时间），中断重传自动跳过已传分片；complete 仍做完整性兜底校验
- 孤儿分片清理：ChunkPurgeJob 每天 03:30 清理 .guicang-tmp/chunks 下超过 24h 未更新的分片目录
- PWA：manifest.webmanifest（深色主题适配）+ sw.js（网络优先离线回退），index.html 挂 manifest/theme-color/apple-touch-icon；Service Worker 仅生产构建注册（dev 不干扰热更新）
- 测试补齐：dav（12）/favorite（6）/notification（7+5）/share（12）模块零测试清零；reader 模块 EncodingDetector（8）/Txt（16）/Epub（11）/API（10）；后端全量 225 测试、前端 23 测试全绿
- 另：修复 EncodingDetector 64KB 探测头截断误判 GB18030（尾部去 1~3 字节重试）；修复 epub 标题提取顺序 bug

## 部署执行记录（2026-09-01，已完成）
1. ✅ `install-helper.sh`（组 nasusers/nasops、服务账号 guicang-svc uid=1003、helper、sudoers 白名单）——helper health 全绿（pam/samba/svc/storage）
2. ✅ `dir-permissions.sh --apply`（shared/media 顶层 setgid + 快照 /var/backups/guicang-dir-permissions-*.txt）
3. ✅ `samba-include.sh --apply`（GUICANG_DB=backend/data/guicang.db；nas-shared/nas-media/personal-admin 共享已热加载；smb.conf 已备份）
4. ✅ admin 系统账号已存在（uid 1002）并绑定归藏；personal/admin 目录已建（0750 admin:nasusers）
5. ✅ 后端 systemd 化（guicang-backend.service，User=guicang-svc，helper 认证，Restart=always）＋前端 pnpm build → Nginx 托管 8090（80 保留给 Nextcloud，未迁 apache2）
6. ✅ 部署后压测：静态 8318 req/s、API 直连 973 req/s、经 nginx 587 req/s，0 失败（PG 对比留作后续可选项）

## 运维命令（systemd + Nginx）
- 后端：`systemctl status|restart|stop guicang-backend`；日志 `journalctl -u guicang-backend -f`（或 backend/data/logs/systemd.*.log）
- 前端/反代：`systemctl restart nginx`；配置 /etc/nginx/conf.d/guicang.conf（监听 8090）
- 访问：Web `http://192.168.31.12:8090/`（Tailscale 100.112.98.102:8090）；WebDAV `http://192.168.31.12:8090/dav/`（账号=系统账号+密码）；Samba 共享 nas-shared/nas-media/personal-admin
- 后端数据目录 backend/data（SQLite）属 guicang-svc:nasops；**改后端代码后需重新 `mvn package` 并 `systemctl restart`**
- 升级：先 `systemctl stop guicang-backend`，再跑 `scripts/upgrade.sh` 或手动替换 jar

## 注意事项 / 已知坑
- 后端 dev 库 backend/data（gitignore）；测试共享内存 SQLite；Flyway 锁定 9.22.3。
- @Audit SpEL 需 -parameters（已配）；前端 md 渲染 markdown-it html:false。
- 停止后端用端口 PID（ss -ltnp | grep :8080），勿 pkill -f 匹配命令行。
- 需求/设计变更要同步 docs/ 四份文档与本文件。
