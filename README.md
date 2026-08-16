# GuiCang（归藏）

家庭 NAS 管理系统：Web 统一管理现有 Samba/NFS 存储 `/home/wb/nas`，提供用户权限、文件管理、md/txt 编辑、图片视频预览、监控大屏、同步、审计，Tailscale 外网访问。

## 技术栈

- 后端：Java 21 + Spring Boot 3.3 + Maven + MyBatis-Plus + Flyway + Quartz + SQLite（可切 PostgreSQL）
- 前端：Vue 3 + TypeScript + Vite 6 + Element Plus + Pinia + ECharts
- 部署：Docker Compose + Nginx + Redis + ELK（Filebeat/ES/Kibana）

## 已实现功能

- **账号与权限**：PAM 认证（宿主机系统账号）+ JWT；RBAC 角色/权限点；目录级 ACL（personal/shared/media + 附加授权）
- **文件管理**：目录列表/新建/重命名/移动/删除；上传（≤1G 流式+原子改名）；下载/预览（HTTP Range 206）；md/txt 网页编辑保存；图片/视频缩略图；名称搜索（file_index）
- **监控大屏**：30s 主机指标采集（CPU/内存/磁盘/网络，2h/24h 序列）；大屏聚合（存储/文件统计/用户/最近操作）+ ECharts 可视化
- **同步**：目录扫描增量（SyncSource 抽象，二期可扩展 WebDAV 等源）；Quartz 定时任务 + 立即执行 + 历史
- **审计**：注解 AOP 全链路留痕（登录/文件/用户/同步/角色），筛选分页查询
- **初始化向导**：首次访问创建 admin，完成后锁定

## 目录结构

```text
backend/    Spring Boot 后端（common/config/module.*/infra）
frontend/   Vue 3 前端（api/components/composables/layout/router/stores/views）
deploy/     docker-compose、nginx、filebeat、.env.example
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
