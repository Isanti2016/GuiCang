# GuiCang 技术方案与实施手册 v1

> 产品名：GuiCang（中文名：归藏）—— 家庭 NAS 管理系统
> 状态：方案定稿，未实施。本文档是后续逐步实施的执行依据。
> 配套：NAS管理系统需求清单.md（决策）、NAS系统详细设计.md（模块与数据设计）、本文档（技术选型 + 部署视图 + 步骤）。

---

## 0. 项目标识

| 项 | 值 |
|---|---|
| 产品名 | GuiCang（归藏） |
| 仓库名 | guicang-nas |
| 后端 Maven 坐标 | com.guicang:guicang-backend |
| 后端包名 | com.guicang.nas |
| 前端包名 | guicang-web |
| 容器前缀 | guicang- |
| Docker 网络 | guicang-net |

---

## 1. 技术栈定版

### 1.1 语言与运行时

| 层 | 语言/运行时 | 版本 |
|---|---|---|
| 后端 | Java（LTS） | 21 |
| 后端构建 | Maven | 3.9.x |
| 前端 | TypeScript | 5.x（strict） |
| 前端运行时 | Node.js | 24（本机已有） |
| 前端包管理 | pnpm | 11（本机已有） |

说明：版本在 pom.xml / package.json 中锁定，避免漂移；小版本以实施时最新稳定版为准。

### 1.2 后端依赖清单

| 依赖 | 版本（参考） | 用途 |
|---|---|---|
| spring-boot-starter-web | 3.3.x | Web MVC |
| spring-boot-starter-security | 3.3.x | 安全框架 |
| spring-boot-starter-validation | 3.3.x | 参数校验 |
| spring-boot-starter-jdbc | 3.3.x | JDBC/事务 |
| spring-boot-starter-quartz | 3.3.x | 定时同步任务 |
| mybatis-plus-spring-boot3-starter | 3.5.x | ORM/DAO 抽象 |
| flyway-core | Boot 管理 | 数据库迁移 |
| sqlite-jdbc | 3.46.x | SQLite 驱动 |
| postgresql | 42.7.x | PostgreSQL 驱动（切换用） |
| jjwt-api / impl / jackson | 0.12.x | JWT 签发与校验 |
| hutool-all | 5.8.x | 通用工具 |
| oshi-core | 6.6.x | 主机指标采集（备用，主用 helper） |
| thumbnailator | 0.4.20 | 图片缩略图 |
| springdoc-openapi-starter-webmvc-ui | 2.6.x | Swagger/API 文档 |
| logstash-logback-encoder | 8.x | 结构化 JSON 日志 |
| lombok | 可选 | 简化样板代码（建议少用） |
| spring-boot-starter-test | 3.3.x | 单元/集成测试 |

### 1.3 前端依赖清单

| 依赖 | 版本（参考） | 用途 |
|---|---|---|
| vue | 3.5.x | 框架 |
| vite | 6.x | 构建 |
| typescript | 5.x | 类型 |
| element-plus | 2.8.x | UI 组件库 |
| pinia | 2.2.x | 状态管理 |
| vue-router | 4.4.x | 路由 |
| axios | 1.7.x | HTTP |
| echarts | 5.5.x | 图表 |
| markdown-it | 14.x | md 渲染 |
| highlight.js | 11.x | 代码高亮 |
| @vueuse/core | 11.x | 组合式工具 |
| eslint + prettier | 最新 | 代码风格 |

### 1.4 第三方软件清单

| 软件 | 版本 | 部署方式 | 用途 | 许可说明 |
|---|---|---|---|---|
| Samba | 4.19.5（系统已有） | 宿主机服务 | 文件共享 | GPL，自托管无问题 |
| NFS | 系统已有 | 宿主机服务 | 文件共享 | 内核/系统 |
| Tailscale | 1.102（系统已有） | 宿主机服务 | 外网访问 | 免费个人版 |
| MariaDB | 10.11（系统已有） | 宿主机服务 | 不混用，保持现状 | GPL |
| Apache2 | 系统已有 | 迁 8081 | 不冲突保留 | Apache-2.0 |
| Docker | 29.1.3 | 宿主机 | 容器运行时 | 自托管 |
| Nginx | 1.27（镜像已有） | Docker | 反代/静态 | BSD |
| Redis | 7.4 | Docker | 缓存/队列/限流 | 自托管内部使用无问题；介意许可可换 Valkey（BSD） |
| PostgreSQL | 17（镜像已有） | Docker（升级路径） | 关系库 | PostgreSQL License |
| Elasticsearch | 8.15 | Docker | 日志检索 | Elastic Basic 免费层 |
| Kibana | 8.15 | Docker | 日志可视化 | 同上 |
| Filebeat | 8.15 | Docker | 日志采集 | 同上 |
| ffmpeg | 6.1.1（系统已有） | 宿主机/镜像 | 视频缩略图（二期转码） | 自用无问题 |
| SQLite | 3.x（随 JDBC 内嵌） | 内嵌 | 默认数据库 | Public Domain |

---

## 2. 系统部署视图

### 2.1 逻辑视图

```text
浏览器（内网 / Tailscale）
   │ HTTP
   ▼
Nginx :80（静态前端 + /api 反代）
   │
   ▼
backend :8080（Spring Boot，非 root）
   ├── SQLite /data/guicang.db（可切 PostgreSQL :5432）
   ├── Redis :6379（缓存/队列/限流）
   ├── /nas（挂载 /home/wb/nas，文件读写）
   └── sudo → /usr/local/bin/guicang-helper（宿主机特权）

日志链路：backend JSON 日志 → 日志卷 → Filebeat → ES :9200 → Kibana :5601
```

### 2.2 物理部署视图（Docker Compose 拓扑）

```text
宿主机 Ubuntu 24.04（4C/15G）
│
├── 系统服务（保留）：smbd、nmbd、nfs-server、tailscaled、docker、mariadb、apache2(:8081)
├── 特权脚本：/usr/local/bin/guicang-helper（root:nasops 750）
├── 存储：/home/wb/nas（bind 进 backend 容器 /nas）
│
└── Docker Compose（网络 guicang-net）
    ├── nginx        :80 → 主机 80（仅内网+Tailscale 网段放行）
    ├── backend      :8080（内部）挂 nas-data/nas-logs/nas-config + /nas + helper
    ├── redis        :6379（内部）
    ├── elasticsearch:9200（仅回环）
    ├── kibana       :5601（仅回环）
    └── filebeat     （读 nas-logs 卷）
    （切换 PG 时）postgres :5432（内部，卷 guicang-pgdata）
```

### 2.3 容器与进程清单

| 名称 | 镜像 | 端口映射 | 卷/挂载 | 说明 |
|---|---|---|---|---|
| guicang-nginx | nginx:latest | 80:80 | ./deploy/nginx:/etc/nginx/conf.d:ro | 反代 + 静态 |
| guicang-backend | 自建 Dockerfile | 无（内部 8080） | guicang-data:/data、guicang-logs:/var/log/guicang、guicang-config:/etc/guicang、/home/wb/nas:/nas、helper 与 sudoers | 业务服务 |
| guicang-redis | redis:7 | 无 | guicang-redis:/data | 缓存 |
| guicang-es | elasticsearch:8.15 | 127.0.0.1:9200:9200 | guicang-es:/usr/share/elasticsearch/data | 日志 |
| guicang-kibana | kibana:8.15 | 127.0.0.1:5601:5601 | 无 | 可视化 |
| guicang-filebeat | elastic/filebeat:8.15 | 无 | guicang-logs:/var/log/guicang:ro | 采集 |
| guicang-postgres（可选） | postgres:latest | 无（内部 5432） | guicang-pgdata:/var/lib/postgresql/data | 升级路径 |

### 2.4 网络与端口

| 端口 | 服务 | 暴露 | 防火墙 |
|---|---|---|---|
| 80 | Nginx | 0.0.0.0 | ufw 仅放行 192.168.0.0/16 与 100.64.0.0/10 |
| 443 | 二期 HTTPS | 同上 | 同上 |
| 8080 | backend | 仅 guicang-net 内部 | 不暴露 |
| 6379 | redis | 仅内部 | 不暴露 |
| 9200/5601 | ES/Kibana | 仅 127.0.0.1 | 不暴露 |
| 5432 | postgres（可选） | 仅内部 | 不暴露 |
| 445/139/2049 | Samba/NFS | 内网 | 保持现状 |
| 3306 | MariaDB | 127.0.0.1 | 保持现状 |
| 8081 | Apache2（迁移后） | 127.0.0.1 | 保持现状 |
| 3080 | DSH GUI | 保持现状 | 不动 |

### 2.5 数据卷与宿主机挂载

| 卷/挂载 | 容器内路径 | 内容 | 持久化 |
|---|---|---|---|
| guicang-data | /data | SQLite 库、缩略图、临时文件 | 是 |
| guicang-logs | /var/log/guicang | 应用 JSON 日志 | 是 |
| guicang-config | /etc/guicang | 本地配置（密钥/账号） | 是 |
| bind /home/wb/nas | /nas | 用户文件真源 | 是（数据盘） |
| bind /usr/local/bin/guicang-helper | /opt/guicang/helper（ro） | 特权脚本 | 否 |
| bind /etc/sudoers.d/guicang | 容器内 sudoers 注入 | 白名单 | 否 |

### 2.6 宿主机账户与权限

| 账户/组 | 类型 | 用途 |
|---|---|---|
| nasusers（gid 2000） | 组 | 所有 NAS 用户 + 服务账号；文件组权限载体 |
| nasops（gid 2001） | 组 | sudo 白名单组（仅服务账号） |
| guicang-svc（uid 1002） | 用户 | 后端容器运行身份，主组 nasops、附加组 nasusers |
| admin（uid 1003） | 用户 | 首次初始化创建，NAS 管理员，nologin |
| wb（1000）、app_data（1001） | 用户 | 现有用户，保留不动；wb 可作为初始成员 |

sudoers（/etc/sudoers.d/guicang）：
```text
%nasops ALL=(root) NOPASSWD:/usr/local/bin/guicang-helper
```

---

## 3. 后端框架设计

### 3.1 工程结构与分层

```text
backend/src/main/java/com/guicang/nas/
├── NasApplication.java
├── common/     # Result、BizException、常量、审计注解、工具
├── config/     # Security、Redis、Flyway、数据源、Jackson、跨域
├── module/
│   ├── auth/   # 登录、JWT、PAM 调用、登出、改密
│   ├── user/   # 用户/角色/权限、helper 调用
│   ├── file/   # 目录/上传/下载/预览/编辑/缩略图/搜索
│   ├── monitor/# 指标采集与聚合
│   ├── sync/   # 同步框架、目录扫描、任务调度
│   ├── audit/  # 审计记录与查询
│   ├── dashboard/ # 大屏聚合
│   └── setup/  # 首次初始化
└── infra/      # helper 客户端、存储访问、缩略图服务、文件索引
```

分层规则：Controller（校验+出参）→ Service（业务）→ Mapper（数据）→ Infra（helper/文件系统）。禁止 Controller 直接访问 Mapper 或 Infra。

### 3.2 模块依赖方向

```text
module.* 只能依赖 common/config/infra；
module 之间尽量经 Service 接口调用（如 file 依赖 user 的权限查询），避免互相直接 import Mapper。
```

### 3.3 安全认证链路

Spring Security 过滤器链（顺序）：
```text
1. CorsFilter
2. JwtAuthenticationFilter   # 解析 Bearer，加载用户与权限到 SecurityContext
3. ExceptionTranslationFilter
4. AuthorizationFilter
放行：/api/v1/auth/login、/api/v1/setup/**、/actuator/health、swagger 页面
```

登录流程：
```text
POST /api/v1/auth/login {username,password}
→ 自定义 AuthenticationProvider
→ 调 guicang-helper verify（密码经 stdin）
→ 成功：查 sys_user 与角色权限
→ 签发 JWT（HS256，claims：sub=username, uid, roles[], exp=2h）
→ 记审计 → 返回 token + 用户信息
```

### 3.4 权限判定

- 角色：admin / member / guest / custom，映射 GrantedAuthority（ROLE_xxx）。
- 功能级：@PreAuthorize 按角色校验（如 ADMIN 才可访问用户管理）。
- 目录级：自定义 PermissionEvaluator，按 sys_user_dir 与角色判定对 path 的 read/write/manage；文件服务每次操作都校验。
- 前端只做入口隐藏，最终以后端为准。

### 3.5 文件处理机制

上传（一期单请求 ≤1G）：
```text
校验权限/大小/扩展名 → 写 /data/tmp/<uuid>.part → 校验完成 →
同卷 rename 到目标路径（原子）→ 更新 file_index → 审计 → 异步生成缩略图
```

下载/预览（HTTP Range，视频播放必需）：
```text
校验读权限与路径 → 用 HttpRange 计算片段 → 随机读 + 流式返回 206/200
```

md/txt 编辑保存：
```text
扩展名白名单 .md/.txt → 写 tmp 再 rename → 审计 → 更新 file_index
```

缩略图：
```text
图片：Thumbnailator 生成 256px WebP → /data/thumbs/<sha1>.webp
视频：ffmpeg -ss 00:00:01 -vframes 1 抽帧 → 同上
列表与大屏返回 thumb URL；未生成则返回占位
```

### 3.6 同步框架

```text
SyncSource 接口：scan(ctx) -> ChangeSet {added, updated, deleted}
DirectorySyncSource（一期）：遍历目录 → 与 file_index 对比(path+mtime+size) → 增量
Quartz：每条 sync_task 动态注册 JobDetail + cron Trigger；立即执行用手动 trigger
执行结果写 sync_history，失败记录 error
```

### 3.7 监控采集

- 主用 guicang-helper metrics 输出 JSON（cpu、load、mem、swap、disk、net、uptime）。
- backend 每 30s 调一次，内存保留最近 2h 细粒度序列；GET /monitor/series 返回。
- OSHI 作为容器内兜底（若 helper 不可用）。

### 3.8 日志与审计链路

```text
logback + logstash-logback-encoder → JSON 文件 /var/log/guicang/app-YYYY-MM-DD.json.log（滚动 30 天）
Filebeat 采集 → ES 索引 guicang-app-YYYY.MM.DD → Kibana index pattern guicang-app-*
业务审计单独写 audit_log 表（结构化、可查询、可导出），与运行日志职责分离
```

---

## 4. 前端框架设计

### 4.1 工程结构

```text
frontend/src/
├── api/          # 按模块封装（auth/file/user/monitor/sync/audit/dashboard/setup）
├── assets/       # 静态资源
├── components/   # 通用组件（FileTree、FileTable、PreviewPane、Thumb）
├── composables/  # useAuth、useFiles、useMonitor、useThumb
├── layout/       # 侧边栏/顶栏/主框架
├── router/       # 路由 + 守卫
├── stores/       # auth、files、monitor
├── styles/       # 主题与全局样式
├── utils/        # axios 实例、权限指令、格式化
└── views/        # login/setup/dashboard/files/monitor/sync/audit/admin/settings
```

### 4.2 路由与状态

- 路由守卫：未登录 → /login；未初始化 → /setup；无权限路由直接拦截。
- Pinia：auth（token、用户、权限集合）、files（当前目录、列表、预览态）、monitor（轮询数据）。
- axios 拦截器：自动带 token，401 跳登录，统一错误提示。

### 4.3 关键页面与组件

- FileTree + FileTable：目录树与文件列表；上传/下载/重命名/移动/删除按钮按权限显示。
- PreviewPane：md（markdown-it + highlight.js，支持编辑保存）、txt、图片、视频。
- Thumb 组件：图片/视频缩略图懒加载，失败回退占位。
- Dashboard：ECharts 折线/环形图 + 存储卡片 + 缩略图墙 + 最近操作。

---

## 5. 数据库与迁移版本规划

### 5.1 表清单（详见 NAS系统详细设计.md 4.2）

sys_user、sys_role、sys_permission、sys_role_permission、sys_user_dir、file_index、sync_task、sync_history、audit_log、sys_config。

### 5.2 Flyway 脚本规划（按里程碑拆分，每步提交）

| 脚本 | 对应步骤 | 内容 |
|---|---|---|
| V1__base_config.sql | Step 1.2 | sys_config、audit_log |
| V2__rbac.sql | Step 2.3 | sys_user/role/permission/role_permission/user_dir |
| V3__file_index.sql | Step 3.6 | file_index |
| V4__sync.sql | Step 5.2 | sync_task、sync_history |

约束：SQL 同时兼容 SQLite 与 PostgreSQL（不用 SQLite 专有语法，主键用 BIGINT 自增/IDENTITY 兼容写法，Flyway 里按库分支时用 placeholder 区分）。

---

## 6. 配置与环境变量

### 6.1 本地配置（不进 git）

```text
backend/src/main/resources/application-local.yml   # 密钥/账号（git 忽略）
deploy/.env                                       # compose 环境变量（git 忽略）
/etc/guicang/application-local.yml                # 宿主机本地配置（容器挂载）
```

### 6.2 .env 示例（模板提交，真实值本地生成）

```text
# deploy/.env.example（进 git）
GUICANG_DB_PROFILE=sqlite
GUICANG_JWT_SECRET=<setup.sh 生成>
GUICANG_REDIS_PASSWORD=<本地生成>
GUICANG_POSTGRES_USER=guicang
GUICANG_POSTGRES_PASSWORD=<本地生成>
GUICANG_STORAGE_ROOT=/home/wb/nas
GUICANG_ADMIN_INITIAL=<首次向导填写>
```

---

## 7. 实施步骤（Step-by-Step）

> 每步完成后提交 git；提交信息用 Conventional Commits。

### 阶段 0：环境与骨架

**Step 0.1 环境准备**
- 动作：安装 JDK 21、Maven 3.9、docker-compose-plugin、（可选）sqlite3 CLI
- 验证：java -version、mvn -v、docker compose version 均正常
- 提交：无（环境不进 git）

**Step 0.2 仓库初始化**
- 动作：创建 guicang-nas，git init -b main，写 .gitignore、.editorconfig、README
- 验证：git status 干净，忽略规则生效
- 提交：chore: 初始化仓库骨架

**Step 0.3 目录骨架**
- 动作：创建 backend/frontend/deploy/scripts/docs/config 目录与占位说明
- 验证：目录结构符合设计
- 提交：chore: 建立 monorepo 目录骨架

**Step 0.4 前端工程初始化**
- 动作：pnpm create vite（vue-ts），装 element-plus/pinia/vue-router/axios/echarts/markdown-it/highlight.js/@vueuse/core，配 ESLint/Prettier
- 验证：pnpm dev 能起，pnpm build 通过
- 提交：chore(frontend): 初始化 Vue3+TS+Element Plus 工程

**Step 0.5 后端工程初始化**
- 动作：生成 Spring Boot 工程 pom + 多 profile yml + NasApplication + hello 接口
- 验证：mvn spring-boot:run 起服务，hello 可访问
- 提交：chore(backend): 初始化 Spring Boot 工程与多 profile

### 阶段 1：后端基础与初始化

**Step 1.1 统一返回与异常**
- 动作：Result、BizException、@RestControllerAdvice、Bean Validation
- 验证：错误返回统一 JSON 结构
- 提交：feat(backend): 统一返回体与全局异常

**Step 1.2 Flyway + SQLite + 基础表**
- 动作：接入 Flyway，SQLite WAL，V1__base_config.sql（sys_config、audit_log）
- 验证：启动自动建库建表
- 提交：feat(backend): 接入 Flyway 与 SQLite 基础表

**Step 1.3 审计基础设施**
- 动作：审计注解 + AOP + 写 audit_log，登录/关键操作埋点
- 验证：操作后 audit_log 有记录
- 提交：feat(backend): 审计日志基础设施

**Step 1.4 Setup 初始化**
- 动作：GET /setup/status、POST /setup/init（新建 admin，调 helper user-add，写 sys_config 标记）
- 验证：未初始化跳向导，完成后锁定
- 提交：feat(backend): 首次初始化向导

### 阶段 2：账号与权限

**Step 2.1 宿主机账户与 helper**
- 动作：建 nasusers/nasops/guicang-svc；写 /usr/local/bin/guicang-helper（verify/user-add/user-del/user-mod/passwd/samba-sync/metrics/health）+ sudoers
- 验证：sudo -n guicang-helper health 正常；verify 能校验系统密码
- 提交：chore(scripts): 特权账号管理 helper

**Step 2.2 认证链路**
- 动作：Security 过滤器链 + JWT + PAM 登录 + 登出 + /auth/me
- 验证：登录成功拿 token，访问受保护接口
- 提交：feat(backend): PAM 登录与 JWT 认证

**Step 2.3 RBAC 与用户管理**
- 动作：V2__rbac.sql；用户/角色/权限 CRUD；用户 CRUD 调 helper 同步系统账号与 Samba
- 验证：建用户后系统账号与 Samba 账号同步，不同角色菜单/内容隔离
- 提交：feat(backend): RBAC 与用户管理

**Step 2.4 目录权限与 Samba 同步**
- 动作：目录权限调整脚本（nasusers 组、setgid）、Samba 共享 include 生成
- 验证：Web 创建的文件 Samba/NFS 可读，权限三端一致
- 提交：chore(scripts): 目录权限与 Samba 同步

### 阶段 3：文件管理

**Step 3.1 目录操作**
- 动作：列表/新建/重命名/移动/删除 + 路径规范化与越权校验
- 验证：网页可管理 /home/wb/nas，越权被拒
- 提交：feat(backend): 目录列表与文件操作

**Step 3.2 上传下载**
- 动作：上传（≤1G 流式 + 原子改名）、下载/流（Range）
- 验证：1G 文件上传下载成功，视频拖动播放
- 提交：feat(backend): 文件上传与 Range 下载

**Step 3.3 md/txt 预览与编辑**
- 动作：读取文本 + PUT /files/write 保存
- 验证：md 渲染正确，编辑保存后内容落盘
- 提交：feat(backend): md/txt 预览与编辑

**Step 3.4 图片预览与缩略图**
- 动作：图片预览 + Thumbnailator 缩略图
- 验证：列表与大屏缩略图可见
- 提交：feat(backend): 图片预览与缩略图

**Step 3.5 视频播放与缩略图**
- 动作：视频 Range 播放 + ffmpeg 抽帧缩略图
- 验证：MP4/H.264 可播，视频有封面
- 提交：feat(backend): 视频播放与缩略图

**Step 3.6 文件索引与搜索**
- 动作：V3__file_index.sql；文件索引维护 + GET /files/search
- 验证：按名搜索命中
- 提交：feat(backend): 文件索引与搜索

### 阶段 4：监控与大屏

**Step 4.1 指标采集**
- 动作：helper metrics + backend 定时采集缓存
- 验证：monitor 接口返回主机指标
- 提交：feat(backend): 主机指标采集

**Step 4.2 监控与大屏 API**
- 动作：GET /monitor/overview、/monitor/series、/dashboard/summary
- 验证：聚合数据正确
- 提交：feat(backend): 监控与大屏聚合

**Step 4.3 前端页面**
- 动作：登录/布局/路由守卫、大屏页面（ECharts）
- 验证：内网浏览器可登录并看大屏
- 提交：feat(frontend): 布局登录与大屏

### 阶段 5：同步与审计前端

**Step 5.1 同步框架**
- 动作：SyncSource 接口 + DirectorySyncSource
- 验证：目录扫描产生增量 ChangeSet
- 提交：feat(backend): 同步框架与目录扫描

**Step 5.2 任务调度**
- 动作：V4__sync.sql；Quartz 任务 + sync API + history
- 验证：定时任务执行，历史可查
- 提交：feat(backend): 同步任务调度

**Step 5.3 前端文件/同步/审计页**
- 动作：文件管理页、同步管理页、审计查询页
- 验证：全流程页面可用
- 提交：feat(frontend): 文件/同步/审计页面

### 阶段 6：部署

**Step 6.1 镜像构建**
- 动作：backend/frontend Dockerfile
- 验证：两镜像可构建
- 提交：build(deploy): 前后端 Dockerfile

**Step 6.2 Compose 编排**
- 动作：docker-compose.yml（nginx/backend/redis/es/kibana/filebeat）+ 卷 + 网络
- 验证：docker compose up -d 全栈启动
- 提交：build(deploy): Docker Compose 编排

**Step 6.3 Nginx 与防火墙**
- 动作：Nginx 反代/静态配置、ufw 规则、Apache2 迁 8081
- 验证：80 访问正常，内网+Tailscale 可达，公网不可达
- 提交：build(deploy): Nginx 与防火墙

**Step 6.4 日志链路**
- 动作：Filebeat/ES/Kibana 配置
- 验证：应用日志可检索
- 提交：build(deploy): ELK 日志链路

**Step 6.5 一键脚本**
- 动作：setup.sh/deploy.sh/backup.sh
- 验证：新机器按脚本可一键部署
- 提交：feat(scripts): 一键部署与备份脚本

### 阶段 7：测试与文档

**Step 7.1 后端测试**
- 动作：Service 单测、Auth/文件/上传 Range 集成测试
- 验证：测试通过
- 提交：test(backend): 单元与集成测试

**Step 7.2 前端与权限验收**
- 动作：前端冒烟 + 权限矩阵验证
- 验证：不同角色内容隔离
- 提交：test(frontend): 冒烟与权限验收用例

**Step 7.3 文档**
- 动作：用户手册、管理员手册、API 文档、部署文档
- 验证：按文档可操作
- 提交：docs: 使用与部署文档

### 阶段 8：升级与压测

**Step 8.1 升级机制**
- 动作：upgrade.sh（备份→拉镜像→迁移→回滚）+ Flyway 新版本脚本
- 验证：升级可回滚
- 提交：feat(scripts): 版本升级与回滚

**Step 8.2 压测与调优**
- 动作：读多写少压测，SQLite 与 PostgreSQL 对比，调优
- 验证：达到并发目标
- 提交：perf: 压测报告与调优

---

## 8. 验收清单（最终）

- [ ] 首次访问引导创建 admin，初始化后锁定
- [ ] 用户增删改同步系统账号与 Samba，权限隔离有效
- [ ] md/txt 网页编辑保存、图片预览、视频播放、缩略图可见
- [ ] 单文件 1G 上传下载成功，Range 拖动播放正常
- [ ] 大屏实时显示 CPU/内存/磁盘/存储统计
- [ ] 目录扫描同步可定时执行，历史可查
- [ ] 关键操作留审计，可查询导出
- [ ] 一键部署成功，80 访问，日志进 Kibana
- [ ] Tailscale 外网可访问，公网不可达
- [ ] 升级可回滚，压测达标

---

## 9. 风险与备注

- helper 是安全关键件：参数白名单 + stdin 传密 + 自身日志，先做单测。
- pdbedit 当前报错，Step 2.1 先修复再依赖。
- SQLite→PostgreSQL 切换必须在 Step 3 前用 Flyway 兼容写法验证一次。
- Redis 若在意许可，直接换 Valkey（同协议兼容），compose 里改镜像即可。

