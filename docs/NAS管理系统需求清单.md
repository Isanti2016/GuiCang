# GuiCang（归藏，家庭 NAS 管理系统）—— 需求清单与设计草案（v2）

> 状态：**已实现并提交推送（2026-08-31）**——功能与测试全部完成（后端 225 / 前端 23 全绿），当前进度见 HANDOFF.md「一句话状态」；本文件保留为原始需求与设计决策记录。
> 本机环境已勘察，结论见第 0 节。所有决策已确认，方案定稿。

---

## 0. 本机环境现状（开发 / 测试 / 部署一体机）

### 0.1 硬件与系统
- OS：Ubuntu 24.04.4 LTS（x86_64，内核 7.0.0-28）
- CPU：4 核
- 内存：15 GiB（当前已用约 1.8 GiB，可用约 13 GiB），Swap 20 GiB
- 磁盘：/ 89G（可用 43G）；/home 916G（可用 756G，数据盘）
- 网络：内网 IP 192.168.31.12；Tailscale IP 100.112.98.102（在线，设备名 wb）

### 0.2 已具备（直接复用）
| 组件 | 状态 | 说明 |
|---|---|---|
| Samba 4.19.5 | ✅ 运行中 | 共享根 /home/wb/nas，含 shared/personal/media(photos,videos,music)/backups/private；security=USER；valid users=wb、app_data |
| NFS | ✅ 运行中 | 导出 /home/wb/nas/{shared,personal,media} 到 192.168.0.0/16 |
| Tailscale 1.102.2 | ✅ 运行中 | 外网访问已具备，另有 iphone-14-pro 设备（离线） |
| MariaDB 10.11.14 | ✅ 运行中 | 监听 127.0.0.1:3306（已有，但本项目不混用） |
| Docker 29.1.3 | ✅ 已装 | ⚠️ 缺 compose 插件（docker compose 不可用） |
| Node 24 / npm 12 / pnpm 11.7 | ✅ 已装 | 前端开发就绪 |
| Git 2.43 | ✅ 已装 | 版本管理就绪 |
| curl / wget / Python 3.12 | ✅ 已装 | 安装与脚本可用 |
| 存储根目录 | ✅ 已存在 | /home/wb/nas（属主 wb），已有数据（test.txt 等） |
| PostgreSQL 镜像 | ✅ 已存在 | 本地 Docker 已有 postgres:latest（升级路径，需要时起容器） |
| Nginx 镜像 | ✅ 已存在 | 本地 Docker 已有 nginx:latest |

### 0.3 缺失（待实施阶段再装，本次不动）
| 组件 | 计划来源 | 用途 |
|---|---|---|
| JDK 21 | apt / SDKMAN | 后端构建与运行 |
| Maven 3.9 | apt / SDKMAN | 后端构建 |
| Redis | Docker 容器 | 会话/权限缓存、同步队列、限流 |
| Nginx | Docker（已有 nginx:latest 镜像） | 统一入口/反向代理/静态资源 |
| sqlite3 CLI（可选） | apt | 本地排查数据库 |
| docker-compose-plugin | apt | 支持 docker compose 一键编排 |

### 0.4 端口现状与规划
| 端口 | 现状 | 规划 |
|---|---|---|
| 80 | Apache2 占用 | 迁移 Apache2 到 8081（或停用），Nginx 接管 80 |
| 3306 | MariaDB | 保留（仅本机回环） |
| 445/139 | Samba | 保留 |
| 2049 | NFS | 保留 |
| 3080 | DSH GUI（192.168.31.12:3080） | 保持不动，本方案不占用 |
| 6379 | 空 | Redis（容器，仅本机/内网） |
| 8080 | 空 | 后端 API（内网 + Tailscale） |

---

## 1. 项目概述

- 名称：GuiCang（归藏，家庭 NAS 管理系统）
- 目标：在已有 Samba/NFS 的 Linux 主机上提供 Web 统一入口，管理文件/图片/视频/笔记，支持用户权限、操作审计、系统监控与外部数据同步，支持 Tailscale 外网访问。
- 形态：前后端分离 Web 系统，未来可迁移 iOS / Android。
- 部署位置：本机（Ubuntu 24.04），Docker + Nginx，一键部署。

---

## 2. 已确认与待定问题

### 2.1 数据库选型 —— ✅ 已确认
- 结论：SQLite 起步（默认）+ PostgreSQL 作为升级路径（本机已有 postgres:latest 镜像，需要时 Docker 起容器）。
- 落地：Spring Boot 多 profile（sqlite / postgresql），配置文件切换，业务代码只依赖 DAO 接口。
- 理由：SQLite 零运维适合起步；PG 原生支持并发，真到数百并发时起 PG 容器、改一条配置即可切换。
- 并发口径已确认：「数百用户」= 总注册数；日常读多写少；单文件上限 1G。

### 2.2 日志 —— ✅ 方案已定（应用自带滚动文件）
- 结论：应用日志由 logback 输出标准文本滚动文件，不引入 ELK 重组件。
- 备份方案：若 ES 吃紧，降级为 Loki + Promtail + Grafana（更省，可与监控大屏共用 Grafana）。
- 日志格式：应用输出结构化 JSON；关键错误另有滚动文件日志兜底。

### 2.3 与现有 Samba 的关系 —— ✅ 已确认：打通
- 结论：Web 系统、Samba、NFS 三者共用同一存储根 /home/wb/nas，不另造存储。
- 账号：Samba 账号与系统账号统一（详见第 7 节「账号打通设计」）。
- 目录边界：Web 端管理的就是 /home/wb/nas 下的真实目录，网页增删改与 Samba/NFS 看到的实时一致。

### 2.4 外网登录 —— ✅ 已确认
- 结论：外网仅走 Tailscale（已部署，100.112.98.102），默认不暴露公网端口。
- 服务监听：Nginx 只监听内网 192.168 网段 + Tailscale 100.x 网段。
- TLS：内部自签/本地证书即可；2FA 作为后续可选增强（不在首期）。

### 2.5 手机/监控视频同步 —— ✅ 先设计、二期细化
- 结论：一期不做具体协议，只做「目录级扫描索引」+ 同步任务框架。
- 设计预留：同步能力抽象为接口（数据源 → 扫描 → 增量入库），二期再插 WebDAV/手机 App/监控目录等具体实现。
- 一期可落地：指定目录（如 /home/wb/nas/media/photos、监控上传目录）定时扫描，增量索引到数据库。

### 2.6 并发规模 —— ✅ 已确认
- 总注册数百、同时在线数十、读多写少；单文件上限 1G。
- 一期单请求上传（≤1G）；分片/断点续传放到移动端之前的迭代。

### 2.7 Redis 用途 —— ✅ 已定
- 结论：Redis 用于会话/权限缓存 + 同步任务队列 + 登录限流；单实例足够，非强依赖（挂了只影响缓存，不丢数据）。

---

## 3. 技术架构

| 层 | 选型 | 说明 |
|---|---|---|
| 后端 | Java 21 / Spring Boot 3.x / Maven | 分层解耦、模块化 |
| 前端 | Vue 3 + TypeScript + Vite + Pinia + Element Plus | 已定 Element Plus |
| 数据库 | SQLite（默认）/ PostgreSQL（profile 切换） | DAO 抽象（MyBatis-Plus） |
| 缓存 | Redis | 会话/权限缓存、队列、限流 |
| 日志 | logback 滚动文件 | 应用自带，标准文本 |
| 文件存储 | /home/wb/nas（复用 Samba/NFS 目录） | 不另造存储引擎 |
| 部署 | Docker Compose + Nginx | 一键部署 |
| 外网 | Tailscale（已部署） | 不暴露公网 |
| 认证 | Spring Security + PAM 系统账号 + RBAC | 见第 7 节 |
| API 文档 | SpringDoc / OpenAPI 3（Knife4j） | Swagger UI |
| 定时任务 | Quartz | 目录扫描/同步任务 |

建议引入的开源框架：
- 后端：Spring Security、MyBatis-Plus、Hutool、Knife4j、OSHI（系统监控采集）、Quartz、JWT
- 前端：Vue Router、Pinia、Axios、ECharts（大屏）、Element Plus（已定）

---

## 4. 标准开发目录结构（monorepo）

    nas-management/
    ├── README.md
    ├── .gitignore
    ├── .editorconfig
    ├── docs/
    │   ├── user-guide.md
    │   ├── admin-guide.md
    │   ├── api/
    │   └── architecture.md
    ├── backend/                        # Java Spring Boot
    │   ├── pom.xml
    │   └── src/
    │       ├── main/java/com/family/nas/
    │       │   ├── NasApplication.java
    │       │   ├── common/             # 返回体/异常/工具/常量
    │       │   ├── config/             # 安全/缓存/跨域/多数据源 profile
    │       │   ├── module/
    │       │   │   ├── auth/           # PAM 登录 + JWT
    │       │   │   ├── user/           # 用户与权限（映射系统账号）
    │       │   │   ├── file/           # 文件/目录/上传下载/预览
    │       │   │   ├── monitor/        # OSHI 采集 CPU/内存/磁盘/网络
    │       │   │   ├── sync/           # 同步框架 + 目录扫描
    │       │   │   ├── audit/          # 操作审计
    │       │   │   ├── dashboard/      # 首页大屏聚合
    │       │   │   └── setup/          # 首次初始化向导
    │       │   └── ...
    │       ├── main/resources/
    │       │   ├── application.yml
    │       │   ├── application-sqlite.yml
    │       │   ├── application-postgresql.yml
    │       │   ├── application-local.yml       # 本地密钥/账号（git 忽略）
    │       │   ├── logback-spring.xml
    │       │   └── mapper/
    │       └── test/
    ├── frontend/                       # Vue3 + TS
    │   ├── package.json / vite.config.ts
    │   └── src/
    │       ├── api/  assets/  components/  composables/
    │       ├── layout/  router/  stores/  styles/  utils/
    │       └── views/
    │           ├── login/  dashboard/  files/  monitor/
    │           ├── sync/  audit/  admin/  setup/
    ├── deploy/
    │   ├── docker-compose.yml
    │   ├── nginx/                       # 站点与反代配置
    │   ├── backend/Dockerfile
    │   ├── frontend/Dockerfile
    ├── scripts/
    │   ├── setup.sh                     # 首次初始化（生成密钥、初始化向导标记）
    │   ├── deploy.sh                    # 一键部署
    │   ├── upgrade.sh                   # 版本升级（备份→迁移→启动→回滚）
    │   ├── backup.sh                    # 数据备份
    │   └── user-helper.sh               # 特权账号管理 helper（sudo 白名单，见 7 节）
    └── config/
        └── application-local.yml.example

---

## 5. 前后端编码规范

### 5.1 通用
- UTF-8、LF 换行，提供 .editorconfig。
- 分支：main（稳定）+ develop + feature/*；提交用 Conventional Commits（feat/fix/docs/refactor/test/chore）。
- 提交粒度：按模块/功能完成一部分提交一部分（见第 12 节里程碑）。

### 5.2 后端
- 分层 Controller → Service → Mapper，禁止跨层访问。
- 统一返回体 Result<T>{code,message,data}；@RestControllerAdvice 统一异常。
- Bean Validation 参数校验；RESTful + API 路径版本化（/api/v1）。
- 数据库访问走 DAO 抽象，不散落 SQL；表字段 snake_case，Java camelCase。
- 密码 BCrypt 加密；密钥/账号密码走本地配置，不进 git。
- Spotless / Checkstyle 二选一（待定），提交前跑单测。

### 5.3 前端
- TypeScript strict，禁止 any 泛滥；ESLint + Prettier。
- 组件 PascalCase，页面按 views/模块/ 组织。
- 接口统一在 api/ 封装，页面不直接写 axios。
- 权限：路由守卫 + 按钮级指令 v-permission。

---

## 6. 关键配置本地存储（不进 git）

| 配置项 | 说明 |
|---|---|
| PostgreSQL 账号/密码 | 切换 PG 后使用，仅存本地配置 |
| Redis 密码 | 生产建议设置 |
| JWT 密钥 | 本地随机生成，不可提交 |
| TLS 证书/私钥 | 本地证书 |
| 管理员初始密码 | 首次向导页设置，只入库/本地配置 |
| 外部同步凭据 | 二期涉及 |

- 后端读 application-local.yml（git 忽略），提交 .example 模板；前端用 .env.production.local。
- Docker Compose 用根目录 .env 注入，文件权限 600，不进镜像与仓库。
- setup.sh 生成随机密钥；升级时保留本地配置不被覆盖。

---

## 7. 账号打通设计（Samba / 系统账号 / Web）

### 7.1 目标
Web 用户、Linux 系统用户、Samba 用户（tdbsam）同一套账号；Web 增删改用户即同步到系统与 Samba；登录用系统密码校验。

### 7.2 落地方式（✅ 已确认：方案 A）
- Web 登录认证走 PAM（校验 Linux 系统密码），不维护第二套密码。
- 用户管理通过宿主机上的 scripts/user-helper.sh（root 执行 + sudo 白名单：useradd/userdel/usermod/smbpasswd）完成；后端容器只调用该 helper，不以 root 运行整个后端。
- 新增用户 = 系统用户（shell 用 /usr/sbin/nologin）+ 加入 NAS 组 + smbpasswd -a 设 Samba 密码。
- 初始管理员：首次初始化时新建独立 admin 账号（不绑定现有 wb）。

### 7.3 权限模型
- 应用内 RBAC：角色（管理员/普通用户/自定义）+ 按目录授权（读/写/删除/管理）。
- 文件系统层：每用户/每目录的 Unix 权限 + Samba valid users 同步，保证 Samba/NFS/Web 三端权限一致。
- 后端强制校验（不止前端隐藏），越权访问拦截 + 审计。

---

## 8. 功能模块清单

### 8.1 首次初始化（Setup 向导）
- [ ] 首次访问引导：新建 admin 管理员账号密码、存储根目录（默认 /home/wb/nas）、站点名称
- [ ] 检测未初始化自动跳转向导；初始化后锁定（写入标记）

### 8.2 用户与权限（RBAC）
- [ ] 用户增删改、启用禁用（同步系统账号 + Samba）
- [ ] 角色管理：管理员/普通用户/自定义角色
- [ ] 目录级授权：读/写/删除/管理
- [ ] 不同权限登录后菜单与内容不同（后端强制校验）
- [ ] 密码重置、修改个人密码

### 8.3 文件管理
- [ ] 文件夹：新建/重命名/删除/移动
- [ ] 文件：上传（一期单请求 ≤1G）、下载、重命名、删除、移动
- [ ] 类型：md/txt 优先；图片、视频常用格式
- [ ] 在线预览与编辑：md、txt 网页查看/编辑保存；图片预览；视频播放
- [ ] 缩略图：图片/视频生成缩略图（列表与大屏使用，ffmpeg 抽帧）
- [ ] 分享链接（二期，本期仅列需求）

### 8.4 系统监控
- [ ] CPU/内存/磁盘/网络采集（OSHI + /proc 只读）
- [ ] 首页大屏实时/定时刷新

### 8.5 同步功能（一期：目录扫描框架）
- [ ] 图片/笔记/视频目录扫描，增量索引
- [ ] 外部数据接入（监控/手机）预留接口，二期实现
- [ ] 手动触发 + 定时（Quartz）

### 8.6 操作审计
- [ ] 记录登录、文件操作、用户管理、配置变更
- [ ] 字段：用户/时间/IP/操作/对象/结果；管理员查询筛选导出

### 8.7 首页大屏
- [ ] 存储用量、文件统计、用户/在线数
- [ ] CPU/内存趋势图（ECharts）、最近操作

### 8.8 文档
- [ ] 用户手册、管理员手册、API 文档、部署文档

---

## 9. 部署方案（本机）

- [ ] Docker Compose 编排：backend、frontend（Nginx 托管静态）、Redis
- [ ] Nginx 统一入口，反代 /api 到后端；只监听 192.168 网段 + Tailscale 100.x
- [ ] 数据卷挂载：数据库文件、/home/wb/nas（存储）、日志、本地配置（容器重建不丢数据）
- [ ] 健康检查 /actuator/health
- [ ] scripts/deploy.sh 一键部署；setup.sh 首次配置
- [ ] Apache2 迁 8081 或停用，80 交给 Nginx；3080 不动

---

## 10. 测试与验证（本机 + 192.168 内网）

- [x] 后端单元测试、接口集成测试（225 全绿，2026-08-31）
- [x] 前端关键流程冒烟测试（23 全绿，2026-08-31）
- [ ] 内网多设备验证（PC / 手机浏览器）
- [ ] 上传/下载/预览各文件类型验证（开发期部分验证，未走正式部署）
- [ ] 权限矩阵验证（不同角色内容隔离）
- [ ] 并发压测（按 2.6 确认的规模，用 SQLite 与 PostgreSQL 对比）
- [ ] Tailscale 外网访问验证（iphone-14-pro 上线后可测）
- [ ] 重启/断电后数据完整性验证

---

## 11. 版本升级

- [ ] 语义化版本号
- [ ] 数据库迁移脚本（Flyway，推荐）
- [ ] scripts/upgrade.sh：备份 → 拉新镜像 → 迁移 → 启动 → 失败回滚
- [ ] 升级前自动备份数据库与本地配置
- [ ] 本地配置/数据升级时保留，不被覆盖

---

## 12. 分阶段里程碑与提交计划

| 阶段 | 内容 | Git 提交粒度 |
|---|---|---|
| M0 | 项目骨架：目录、规范、.gitignore、多 profile 骨架、构建脚本 | 骨架提交 |
| M1 | 后端基础：统一返回/异常/安全/DB 初始化/Setup 向导 | 分模块提交 |
| M2 | 认证（PAM+JWT）+ 用户权限（RBAC）+ 账号打通 helper | 认证、用户、helper 分次提交 |
| M3 | 文件管理（目录/上传/下载/预览/编辑/缩略图） | CRUD、上传、预览编辑、缩略图分次提交 |
| M4 | 系统监控 + 首页大屏 | 采集、大屏分次提交 |
| M5 | 同步框架（目录扫描）+ 审计日志 | 同步、审计分次提交 |
| M6 | 部署编排（Docker/Nginx/一键脚本） | 部署提交 |
| M7 | 测试验证 + 文档 | 文档提交 |
| M8 | 升级机制 + 压测调优 | 升级、优化分次提交 |

---

## 13. iOS / Android 迁移预留

- [ ] API 面向移动端：RESTful + JSON + Token，分页/缓存友好
- [ ] 上传支持分片续传（弱网友好）
- [ ] 同步协议抽象为接口，App 复用
- [ ] 后续 App：uni-app / Flutter / React Native（待定，先不做）

---

## 14. 风险与依赖

| 风险 | 影响 | 应对 |
|---|---|---|
| SQLite 写并发瓶颈 | 数百并发写失败 | DAO 抽象 + PostgreSQL profile 一键切换 |
| 后端管理系统账号的权限 | 安全风险 | 方案 A：sudo 白名单 helper，后端不跑 root |
| 大文件上传中断 | 失败/超时 | 分片上传 + 断点续传 |
| 权限越权 | 数据泄露 | 后端强制校验 + 审计 |
| 升级丢数据 | 数据丢失 | 升级前备份 + Flyway 迁移 + 回滚 |
| 端口冲突（80/3080） | 服务不可用 | Apache2 迁 8081，3080 保持不动 |

---

## 15. 结论与下一步

1. 命名：GuiCang（归藏），已确认。
2. 方案已全部定稿（数据库/并发/账号打通/UI/分享链接/缩略图/md 编辑/HTTPS 均已确认）。
3. 下一步：**已完成全部功能与测试**（后端 225 / 前端 23 全绿，详见 HANDOFF.md）；剩余「部署上线 / 验证验收」项见《待办-归藏未完成清单.md》。
