# GuiCang（归藏，家庭 NAS 管理系统）—— 详细设计文档 v1

> 状态：方案讨论阶段（未开始编码、未安装新环境）。本文档是后续实施的依据，与《NAS管理系统需求清单.md》配套：需求清单管决策，本文档管设计细化。

---

## 0. 已确认决策摘要

| 编号 | 决策点 | 结论 |
|---|---|---|
| D1 | 数据库 | SQLite 起步（默认）+ PostgreSQL profile 预留（需要时 Docker 装）；本机 MariaDB 已有但不用，避免三库混用 |
| D2 | 并发 | 总注册数百、同时在线数十、读多写少；单文件上限 1G |
| D3 | 账号打通 | 方案 A：Web 用户 = Linux 系统用户，PAM 认证，sudo 白名单 helper 管理系统账号 |
| D4 | UI 框架 | Element Plus（Vue 3 生态最流行、admin 场景成熟、与 ECharts 配合好） |
| D5 | 分享链接 | 二期做，本期只列入需求 |
| D6 | 存储 | Web/Samba/NFS 共用 /home/wb/nas，不另造存储 |
| D7 | 日志 | 本地 ELK 轻量化：Filebeat → Elasticsearch → Kibana，不用 Logstash |
| D8 | 外网 | 仅 Tailscale，不暴露公网端口 |
| D9 | 同步 | 一期只做目录级扫描索引框架，协议二期细化 |
| D10 | 部署 | 本机 Docker Compose + Nginx，一键部署 |
| D11 | 产品命名 | GuiCang（归藏），已确认 |
| D12 | 初始管理员 | 首次初始化新建独立 admin 账号 |
| D13 | md/txt | 一期支持网页内编辑与保存 |
| D14 | 缩略图 | 一期实现图片/视频缩略图（列表与大屏） |
| D15 | PG 镜像 | 本机已有 postgres:latest，需要时起容器切换 |

环境更正说明：本机未装 PostgreSQL 系统包，但本地 Docker 已有 postgres:latest 镜像（需要时起容器）；已装并运行 MariaDB 10.11（本项目不混用）。ffmpeg 6.1.1 可用；Samba tdbsam 已有 wb、app_data 两个账号；本地另有 nginx:latest、oracle:19c 镜像。

---

## 1. 设计目标与约束

- 目标：家庭/小团队 NAS 的 Web 管理入口，管文件、管用户、看监控、留审计，支持 Tailscale 外网。
- 非目标（一期不做）：公网暴露、复杂转码、多机集群、分享外链、移动端原生 App。
- 约束：
  - 单机部署（4C/15G，数据盘 916G）；
  - 复用已有 Samba/NFS 与 /home/wb/nas；
  - 单文件 ≤ 1G；
  - 注册用户数百、在线数十、读多写少；
  - 后端不整进程跑 root（特权操作走白名单 helper）。

---

## 2. 总体架构

### 2.1 架构分层

```text
┌──────────────────────────────────────────────────────────────┐
│  浏览器（PC / 手机网页）          Tailscale 外网 / 192.168 内网 │
└───────────────────────────┬──────────────────────────────────┘
                            │ HTTPS(二期)/HTTP
┌───────────────────────────▼──────────────────────────────────┐
│ Nginx（80）：静态资源 + /api 反向代理 + / 前端 SPA            │
└───────┬───────────────────────────────┬──────────────────────┘
        │ /api                           │ 静态文件
┌───────▼────────┐              ┌────────▼────────┐
│ backend (8080) │              │ frontend 静态包  │
│ Spring Boot 3  │              │ Vue3 + Element   │
└───┬───┬───┬───┘              └─────────────────┘
    │   │   │
    │   │   └──► SQLite 文件（/data/nas.db，可切 PostgreSQL）
    │   └──────► Redis（会话缓存/队列/限流）
    └──────────► Filebeat → Elasticsearch → Kibana（日志链路）

宿主机特权边界（sudo 白名单，非 root 后端）：
    backend ──sudo──► /usr/local/bin/guicang-helper
                        ├─ verify（PAM 校验登录）
                        ├─ user-add / user-del / user-mod / passwd
                        ├─ samba-sync（同步 Samba tdbsam）
                        └─ metrics（主机 CPU/内存/磁盘/网络）

文件层：backend 以服务账号 nasapp 读写 /home/wb/nas（组权限），
        Samba / NFS 直接读写同一目录，三端一致。
```

### 2.2 组件职责

| 组件 | 职责 | 运行方式 |
|---|---|---|
| frontend | Vue3 SPA，页面与交互 | Nginx 托管静态文件 |
| backend | REST API、业务逻辑、鉴权、审计、同步调度、监控聚合 | Docker 容器（nasapp 用户） |
| guicang-helper | 特权操作：PAM 校验、系统用户管理、Samba 同步、主机指标 | 宿主机脚本，sudo 白名单 |
| Redis | 会话/权限缓存、登录限流、同步队列 | Docker 容器 |
| SQLite | 业务数据（用户 profile、RBAC、文件索引、审计、配置） | 卷挂载文件 |
| Elasticsearch + Kibana | 日志检索与可视化 | Docker 容器 |
| Filebeat | 采集后端结构化日志 | Docker 容器 |
| Nginx | 统一入口、反代、静态资源 | Docker 容器（或宿主机，二选一，见 7 节） |

### 2.3 关键数据流

- 登录：前端 → Nginx → backend → guicang-helper verify(PAM) → 查 RBAC → 发 JWT → 记审计。
- 文件浏览：前端 → backend 查目录（filesystem）→ 返回列表；下载/预览走 HTTP Range 流。
- 文件写入：前端 multipart 上传 → backend 校验权限与大小 → 流式写盘 → 更新 file_index → 审计。
- 监控：backend 定时调用 helper metrics → 缓存最近 N 条 → 大屏轮询。
- 日志：backend logback 输出 JSON 到日志卷 → Filebeat → ES → Kibana。
- 同步：Quartz 触发 → 扫描目录 → 与 file_index 对比 → 增量更新 → 写 sync_history。

---

## 3. 账号与权限体系（重点）

### 3.1 三套凭证统一模型

| 场景 | 凭证存储 | 说明 |
|---|---|---|
| Web 登录 | Linux /etc/shadow（PAM 校验） | 密码不存应用库 |
| Samba 访问 | tdbsam（passdb.tdb） | 由 helper 同步 |
| SSH/系统登录 | /etc/shadow | 新建用户默认 nologin |

- 统一密码策略：创建用户与修改密码时，把同一密码同时写入 Linux 与 Samba，用户体验为单一账号密码。
- 应用库不存密码，只存应用元数据（显示名、角色、是否启用、个人目录、配额等），以 username 为主键关联系统账号。

### 3.2 登录认证流程（PAM）

1. 前端 POST /api/v1/auth/login（username + password）。
2. backend 调用 helper：echo 密码经 stdin 传给 guicang-helper verify username（密码不进 argv、不进日志）。
3. helper 用 PAM 校验系统密码，输出 JSON：ok / uid / gid / home / shell。
4. backend 查 sys_user 表：账号是否存在、是否启用、角色权限。
5. 成功签发 JWT（HS256，含 username、uid、角色）；失败统一提示并记审计。

### 3.3 guicang-helper 设计

位置与权限：/usr/local/bin/guicang-helper，root:root，mode 750，组 nasops。
sudoers 白名单：%nasops ALL=(root) NOPASSWD:/usr/local/bin/guicang-helper

子命令：
```text
verify <username>              # stdin 读密码，PAM 校验，输出 JSON
user-add  --user --pass --home --groups   # useradd + 设密码 + smbpasswd
user-del  --user [--remove-home]          # userdel + smbpasswd -x
user-mod  --user --field=value            # usermod（启用/禁用/改 shell）
passwd    --user                          # stdin 新密码，同步 Linux + Samba
samba-sync --user                          # 仅同步 Samba 密码
metrics                                  # 输出主机指标 JSON
health                                   # 自检：PAM/系统/Samba 可用性
```

安全约束：
- 密码只经 stdin 传递，禁止出现在命令行参数、环境变量与日志；
- helper 每个子命令做参数白名单校验（用户名合法性、禁止特殊字符）；
- 所有调用写 helper 自身日志，便于排障与审计。

### 3.4 用户生命周期

- 创建：useradd（-m -s /usr/sbin/nologin，加入 nasusers 组）→ 写 Linux 密码 → smbpasswd -a → 建个人目录 → 写 sys_user。
- 禁用：usermod -L（锁 Linux）→ 删除 Samba 账号或 smbpasswd -d → sys_user.enabled=false。
- 启用：usermod -U + 重设 Samba 密码 → sys_user.enabled=true。
- 删除：先确认 → 停用 → userdel（-r 可选，默认保留个人目录并标记归档）→ smbpasswd -x → 删 sys_user 关联。
- 改密：同时改 Linux + Samba，并记录审计。

### 3.5 RBAC 模型

- 角色：内置 admin（全部）、member（个人目录 + 指定共享）、guest（只读共享，可选）。
- 权限点：目录级（对某路径的 read/write/delete/manage）+ 功能级（用户管理、审计查看、同步管理、系统配置）。
- 用户可挂一个主角色 + 若干附加目录权限；后端每次文件操作都做「用户对目标路径权限」判定，前端只负责隐藏入口。

### 3.6 目录与文件系统权限

```text
/home/wb/nas/
├── shared/            2775  root:nasusers   # 共享，全员组内可写（setgid 继承组）
├── personal/<user>/   0750  user:nasusers   # 个人，仅本人 + 管理员可访问
├── media/             2755  root:nasusers   # 媒体，读多写少，上传目录单独授权
│   ├── photos/
│   ├── videos/
│   └── music/
├── backups/           0750  root:nasusers   # 备份，默认仅管理员
└── private/           0700  app_data        # 保留现状，暂不纳入 Web 管理
```

- 后端以服务账号 nasapp（属 nasops + nasusers）运行，通过组权限读写存储。
- 权限调整脚本在 M2 执行，先对现有目录备份权限快照，再逐步收紧。

### 3.7 Samba 集成

- 新用户创建后，helper 同步 smbpasswd；共享配置用 include 片段按用户生成 valid users / write list。
- 保持现有 smb.conf 的 force user/group 逻辑按目录收口，避免 Web 写入的文件 Samba 读不到。
- 注意：现有 pdbedit -L 在本机报 tdbsam 打开失败（tdbdump 可读），M2 先修复该问题再依赖 pdbedit。

---

## 4. 后端详细设计

### 4.1 工程结构与分层

```text
backend/src/main/java/com/family/nas/
├── NasApplication.java
├── common/        # Result、异常体系、工具、常量、审计注解
├── config/        # Security、Redis、Flyway、多数据源、跨域、Jackson
├── module/
│   ├── auth/      # 登录、JWT、PAM 调用
│   ├── user/      # 用户/角色/权限，helper 调用
│   ├── file/      # 目录列表、上传下载、预览、路径安全
│   ├── monitor/   # 指标采集与聚合
│   ├── sync/      # 同步框架、目录扫描、任务调度
│   ├── audit/     # 审计记录与查询
│   ├── dashboard/ # 大屏聚合
│   └── setup/     # 首次初始化向导
└── infra/         # helper 客户端、存储访问、OSHI 等基础设施
```

分层约定：Controller（参数校验、出参）→ Service（业务）→ Repository/Mapper（数据）→ Infra（helper/文件系统）。

### 4.2 数据库表设计（SQLite/PostgreSQL 通用，Flyway 管理）

```sql
-- 用户应用元数据（密码不在此，由系统/PAM 管理）
CREATE TABLE sys_user (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  username     TEXT NOT NULL UNIQUE,      -- 对应系统账号
  uid          INTEGER,                   -- 系统 uid
  display_name TEXT,
  email        TEXT,
  enabled      INTEGER NOT NULL DEFAULT 1,
  role_id      INTEGER NOT NULL,          -- 主角色
  home_path    TEXT,                      -- 个人目录
  quota_bytes  INTEGER,                   -- 可选配额
  created_at   TEXT NOT NULL,
  updated_at   TEXT NOT NULL
);

CREATE TABLE sys_role (
  id   INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL UNIQUE,   -- admin / member / guest / custom
  name TEXT NOT NULL,
  description TEXT
);

CREATE TABLE sys_permission (
  id       INTEGER PRIMARY KEY AUTOINCREMENT,
  code     TEXT NOT NULL UNIQUE,   -- 如 file.read / user.manage / audit.view
  name     TEXT NOT NULL,
  type     TEXT NOT NULL,          -- menu / api / dir
  resource TEXT                     -- 目录路径（type=dir 时）
);

CREATE TABLE sys_role_permission (
  role_id       INTEGER NOT NULL,
  permission_id INTEGER NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE sys_user_dir (        -- 用户对目录的附加授权（超出主角色）
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  username  TEXT NOT NULL,
  path      TEXT NOT NULL,
  perm      TEXT NOT NULL,         -- read / write / manage
  UNIQUE (username, path, perm)
);

-- 文件索引（搜索/统计/大屏用，文件系统仍是真源）
CREATE TABLE file_index (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  path       TEXT NOT NULL UNIQUE,
  name       TEXT NOT NULL,
  ext        TEXT,
  kind       TEXT NOT NULL,        -- dir / file / image / video / note / other
  size       INTEGER,
  mtime      INTEGER,
  owner      TEXT,
  indexed_at INTEGER
);

CREATE TABLE sync_task (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL,
  source_type TEXT NOT NULL,       -- directory（一期）
  source_config TEXT,              -- 源目录等参数
  cron        TEXT,                -- Quartz cron
  enabled     INTEGER NOT NULL DEFAULT 1,
  last_run_at INTEGER,
  last_status TEXT,
  created_at  TEXT NOT NULL
);

CREATE TABLE sync_history (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  task_id    INTEGER NOT NULL,
  started_at INTEGER NOT NULL,
  finished_at INTEGER,
  status     TEXT,                 -- running / success / failed
  added      INTEGER DEFAULT 0,
  updated    INTEGER DEFAULT 0,
  deleted    INTEGER DEFAULT 0,
  error      TEXT
);

CREATE TABLE audit_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  username    TEXT,
  action      TEXT NOT NULL,       -- login / file.upload / user.create ...
  resource    TEXT,                -- 对象路径/用户名
  ip          TEXT,
  user_agent  TEXT,
  result      TEXT,                -- success / failed
  detail      TEXT,
  created_at  INTEGER NOT NULL
);

CREATE TABLE sys_config (
  key        TEXT PRIMARY KEY,     -- site.name / storage.root / setup.done
  value      TEXT,
  updated_at TEXT
);
```

索引建议：file_index(path)、file_index(kind)、audit_log(created_at)、audit_log(username)、sync_history(task_id)。

### 4.3 REST API 清单（前缀 /api/v1）

| 模块 | 方法与路径 | 说明 |
|---|---|---|
| 认证 | POST /auth/login | 登录，返回 JWT |
| 认证 | POST /auth/logout | 退出（令牌失效） |
| 认证 | GET /auth/me | 当前用户信息与权限 |
| 认证 | PUT /auth/password | 修改本人密码（同步系统+Samba） |
| 用户 | GET /users | 用户列表（分页） |
| 用户 | POST /users | 新增（helper 建系统账号） |
| 用户 | GET /users/{name} | 用户详情 |
| 用户 | PUT /users/{name} | 编辑（昵称/角色/状态） |
| 用户 | DELETE /users/{name} | 删除 |
| 用户 | PUT /users/{name}/status | 启用/禁用 |
| 角色 | GET/POST/PUT/DELETE /roles | 角色管理 |
| 权限 | GET /permissions | 权限点列表 |
| 文件 | GET /files/list?path= | 目录列表 |
| 文件 | POST /files/mkdir | 新建目录 |
| 文件 | POST /files/upload | 上传（multipart，≤1G） |
| 文件 | GET /files/download?path= | 下载（带 Range） |
| 文件 | GET /files/stream?path= | 预览/流媒体（带 Range） |
| 文件 | PUT /files/rename | 重命名 |
| 文件 | POST /files/move | 移动 |
| 文件 | DELETE /files | 删除 |
| 文件 | PUT /files/write | 保存 md/txt 文本内容 |
| 文件 | GET /files/search?q= | 文件名搜索（走 file_index） |
| 监控 | GET /monitor/overview | 实时快照 |
| 监控 | GET /monitor/series?metric=&range= | 历史序列 |
| 同步 | GET/POST /sync/tasks | 任务列表/新建 |
| 同步 | PUT/DELETE /sync/tasks/{id} | 编辑/删除任务 |
| 同步 | POST /sync/tasks/{id}/run | 立即执行 |
| 同步 | GET /sync/history | 执行历史 |
| 审计 | GET /audit/logs | 审计查询（筛选/分页） |
| 大屏 | GET /dashboard/summary | 大屏聚合数据 |
| 初始化 | GET /setup/status | 是否已初始化 |
| 初始化 | POST /setup/init | 初始化（管理员/存储根） |

### 4.4 关键流程

上传（一期单请求，≤1G）：
1. 前端选文件，POST multipart；Nginx client_max_body_size 设为 1100M。
2. backend 校验登录、目标目录写权限、单文件 ≤1G、扩展名白名单。
3. 流式写盘（临时文件 → 原子改名），不整读内存。
4. 更新 file_index、写审计；返回结果。
5. 二期扩展：分片上传 + 断点续传（为移动端准备）。

下载/预览（Range 必需，视频播放依赖）：
1. 校验路径与读权限，规范化路径防穿越（拒绝 ..、绝对路径越界）。
2. 支持 HTTP Range（206），视频前端 video 标签直接播放，图片 img 直接显示。
3. md/txt 预览与编辑：后端返回文本，前端渲染（markdown-it + 代码高亮）；一期支持网页编辑并保存（PUT /files/write）。
4. 缩略图：图片用 Thumbnailator 生成，视频用 ffmpeg 抽帧，存缓存目录 /data/thumbs，列表与大屏引用。

路径安全：
- 所有路径经统一 normalize（基于存储根解析，symlink 检测）；
- 拒绝隐藏文件系统目录；删除操作加入回收策略（一期可先直接删，但审计留痕）。

### 4.5 监控采集

- helper metrics 返回：cpu 使用率、loadavg、内存、磁盘（重点 /home/wb/nas）、网络收发、uptime。
- backend 每 30s 采集一次，内存保留最近 1h（细粒度）与 24h（粗粒度），供大屏；不强制落库，二期可落库做趋势。

### 4.6 日志与审计

- 运行日志：logback 输出结构化 JSON 到 /var/log/nas/app-*.json.log（按天滚动、保留 30 天）。
- 采集：Filebeat 读日志卷 → Elasticsearch → Kibana 索引 nas-app-*。
- 审计日志：单独入库 audit_log（结构化、可查询、可导出），与运行日志职责分离。

### 4.7 多数据源 profile

- application-sqlite.yml：SQLite + WAL，本地文件 /data/nas.db。
- application-postgresql.yml：预留（起 PG 容器切换），只改 spring.datasource 与 driver。
- Flyway 迁移脚本同时兼容两库（避免用 SQLite 专有语法）。

---

## 5. 前端详细设计

### 5.1 技术栈

Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus + ECharts + Axios + markdown-it。

### 5.2 页面与路由

| 路由 | 页面 | 权限 |
|---|---|---|
| /setup | 首次初始化向导 | 未初始化时强制进入 |
| /login | 登录 | 公开 |
| / | 首页大屏（重定向 /dashboard） | 登录即可 |
| /dashboard | 大屏：存储/用户/监控图表/最近操作 | 登录即可 |
| /files | 文件管理（目录树 + 列表 + 预览 + md/txt 编辑） | 按目录权限 |
| /monitor | 系统监控详情 | 管理员 |
| /sync | 同步任务管理 | 管理员 |
| /audit | 操作记录 | 管理员 |
| /admin/users | 用户管理 | 管理员 |
| /admin/roles | 角色与权限 | 管理员 |
| /settings | 系统设置 | 管理员 |

### 5.3 状态管理（Pinia）

- auth store：token、当前用户、权限点集合、登录/登出动作。
- files store：当前路径、目录列表、预览状态。
- monitor store：大屏轮询数据。

### 5.4 权限控制

- 路由守卫：未登录跳 /login；无权限菜单不显示、路由直接拦截。
- 按钮级：v-permission 指令控制上传/删除/管理等按钮。
- 前端只做体验层，真正校验在后端。

### 5.5 首页大屏

- 卡片：存储用量、文件总数（按类型）、用户数、在线数、同步任务状态。
- 图表：CPU/内存趋势折线、磁盘环形图、最近操作列表；图片/视频缩略图墙。
- 刷新：30s 轮询 + 手动刷新。

---

## 6. 同步框架设计

### 6.1 抽象接口（为二期扩展预留）

```text
SyncSource {
  id: string
  name: string
  scan(ctx) -> ChangeSet { added[], updated[], deleted[] }
}
```

### 6.2 一期实现：DirectorySyncSource

- 扫描指定目录（如 /home/wb/nas/media/photos、监控上传目录）。
- 与 file_index 对比：新增/变更/删除，增量更新索引。
- 按 kind 归类（image/video/note），为预览与大屏统计服务。

### 6.3 任务调度

- Quartz 管理 sync_task 的 cron；立即执行走 POST run。
- 每次执行写 sync_history，失败记录错误，大屏可见。

### 6.4 二期扩展点

- WebDAV 源、手机 App 上传源、监控目录/NVR 源，均实现 SyncSource 接口接入，不动主流程。

---

## 7. 部署设计

### 7.1 拓扑与端口

| 服务 | 端口 | 暴露范围 |
|---|---|---|
| Nginx | 80（二期 443） | 192.168 网段 + Tailscale 100.x |
| backend | 8080 | 仅 compose 内网 |
| Redis | 6379 | 仅 compose 内网 |
| Elasticsearch | 9200 | 仅本机回环 |
| Kibana | 5601 | 仅本机回环 |
| 现有 Samba/NFS/MariaDB/Apache2 | 445/139/2049/3306/8081 | 保持不变（Apache2 迁 8081） |
| DSH GUI | 3080 | 保持不动 |

### 7.2 Docker Compose 服务与卷

```yaml
services:
  nginx:       # 80:80，静态 + 反代 /api
  backend:     # 8080，nasapp 用户，sudo helper
  redis:       # 6379，持久化 AOF
  elasticsearch:  # 9200，堆 512m-1g，单节点
  kibana:      # 5601
  filebeat:    # 采集 nas-logs 卷
  # 切 PostgreSQL 时增加 postgres 服务
volumes:
  nas-data:    # SQLite /data/nas.db
  nas-logs:    # /var/log/nas
  nas-config:  # 本地配置（application-local.yml 等）
bind mount:
  /home/wb/nas -> backend:/nas（rw，组权限）
  /usr/local/bin/guicang-helper + /etc/sudoers.d/nasops -> backend（helper 调用）
```

### 7.3 Nginx 要点

- 反代 /api → backend:8080；client_max_body_size 1100m；
- 静态托管前端 dist，SPA 回退 index.html；
- 仅监听 192.168.31.12 与 100.112.98.102，ufw 同步放行内网 + Tailscale 网段。

### 7.4 ELK 要点

- Filebeat 只采集 nas-logs 卷的 app-*.json.log；
- ES 单节点 discovery.type=single-node，ES_JAVA_OPTS 设 -Xms512m -Xmx1g；
- Kibana 本机回环访问；预留 Loki 降级方案。

### 7.5 一键脚本

- setup.sh：生成 JWT 密钥与本地配置模板、初始化 .env、准备目录权限。
- deploy.sh：docker compose up -d + 健康检查。
- upgrade.sh：备份 → 拉新镜像 → Flyway 迁移 → 启动 → 失败回滚。
- backup.sh：备份 SQLite + 本地配置 + 关键目录权限快照。

---

## 8. 安全设计

- 认证：PAM 系统密码 + JWT（HS256，密钥本地生成，有效期 2h）。
- 授权：RBAC + 目录级 ACL，后端强制校验，越权即拒并审计。
- 传输：一期内网/Tailscale 明文 HTTP，二期自签 HTTPS。
- 数据：密码不落库；密钥与账号密码走本地配置（600 权限，git 忽略）。
- 攻击面：后端非 root；helper 白名单 + stdin 传密；路径规范化防穿越；登录限流（Redis）。
- 审计：关键操作全留痕（谁、何时、何 IP、做了什么、结果）。

---

## 9. 备份与升级

### 9.1 备份策略

- 每日：SQLite 全量备份 + 本地配置 + 权限快照（backup.sh，保留 7 份）。
- 数据盘 /home/wb/nas 是用户数据真源，备份策略由用户自行决定（系统不重复搬大数据）。

### 9.2 升级流程

1. upgrade.sh 先自动备份；2. 拉新镜像；3. 起新 backend（Flyway 自动迁移）；4. 健康检查通过则完成；5. 失败回滚到上一镜像与备份。

### 9.3 数据库迁移

Flyway 管理版本化 SQL；本地配置与数据卷升级时保留不覆盖。

---

## 10. 测试方案

- 单元测试：Service 核心逻辑、路径规范化、权限判定、同步 diff。
- 集成测试：Auth（PAM mock/真环境）、文件 CRUD、上传下载 Range。
- 前端冒烟：登录、文件浏览、上传、预览、大屏。
- 权限矩阵：不同角色访问不同目录，验证越权被拒。
- 压测：读多写少场景，SQLite 与 PostgreSQL 对比（JWT + 文件列表 + 下载）。
- 验收：192.168 内网 PC/手机浏览器；Tailscale 外网（iphone 上线后）；重启/断电数据完整性。

---

## 11. 里程碑与验收标准

| 阶段 | 交付物 | 验收标准 |
|---|---|---|
| M0 骨架 | monorepo、.gitignore/.editorconfig、backend 空工程（多 profile）、frontend 空工程、scripts 占位、git 首次提交 | 本地起 hello 接口，前端可访问 |
| M1 后端基础 | Result/异常/Security 骨架、Flyway、SQLite、sys_config/audit_log、Setup 状态与初始化 | 首次访问跳向导，完成初始化后锁定 |
| M2 账号打通 | PAM 登录、JWT、RBAC、用户 CRUD、helper、目录权限脚本、Samba 同步 | 可登录、建用户、Samba 可访问、权限隔离 |
| M3 文件管理 | 目录/上传/下载/预览（md/txt 编辑保存/图片/视频 Range）、缩略图、路径安全、file_index | 网页管理 /home/wb/nas，视频可播、md 可编辑、缩略图可见 |
| M4 监控大屏 | helper metrics、采集、monitor/dashboard API、大屏 ECharts | 大屏实时显示 CPU/内存/磁盘/存储统计 |
| M5 同步审计 | SyncSource 接口、目录扫描、Quartz、sync API、审计全链路与查询页 | 定时扫描索引，操作可查 |
| M6 部署 | Dockerfile、compose、Nginx、Filebeat/ES/Kibana、deploy/setup 脚本、ufw、Apache2 迁移 | 一键部署，80 访问，日志进 Kibana |
| M7 测试文档 | 单测/集成/冒烟、用户/管理员/API/部署文档 | 内网多设备 + Tailscale 验证通过 |
| M8 升级压测 | upgrade.sh、Flyway 版本脚本、压测报告、SQLite vs PG | 升级可回滚，压测达标 |

---

## 12. 风险登记

| 风险 | 等级 | 应对 |
|---|---|---|
| helper 特权接口被滥用 | 高 | 白名单 + stdin + 参数校验 + helper 自身审计 |
| PAM 在容器内不可用 | 高 | 认证经宿主 helper，不依赖容器内 PAM |
| SQLite 写锁 | 中 | WAL + 读多写少 + PG 预留 |
| ES 内存压力 | 中 | 512m-1g 堆 + Filebeat + Loki 备选 |
| 大文件/视频 Range 实现缺陷 | 中 | 集成测试覆盖 Range 与断点 |
| 现有 Samba 配置改动破坏访问 | 中 | 先备份配置，灰度调整，M2 前验收 Samba |
| pdbedit 当前报错 | 低 | M2 修复后再依赖 |

---

## 13. 最终确认结果（方案定稿）

1. 命名：GuiCang（归藏），已确认。
2. 初始管理员：新建独立 admin 账号。
3. md/txt：一期网页内编辑并保存。
4. 视频：一期不转码，只播浏览器原生 MP4/H.264，MKV/HEVC 二期 ffmpeg 转码。
5. 缩略图：一期实现（图片/视频），用于列表与大屏。
6. HTTPS：一期 HTTP（内网+Tailscale）。
7. 数据库：SQLite 起步 + PG（本机已有 postgres:latest 镜像，需要时起容器）。

→ 方案定稿，下一步进入 M0 骨架（届时才安装 JDK/Maven/Redis/ES/Kibana/compose 插件等）。

---

## 14. 实施顺序

方案已定稿 → M0 骨架 → M1 基础 → M2 账号 → M3 文件 → M4 监控 → M5 同步审计 → M6 部署 → M7 测试文档 → M8 升级压测。每阶段完成一部分提交一部分（git）。

