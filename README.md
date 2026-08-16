# GuiCang（归藏）

家庭 NAS 管理系统。

- 后端：Java 21 + Spring Boot 3 + Maven
- 前端：Vue 3 + TypeScript + Element Plus + Vite
- 数据库：SQLite（默认），可切 PostgreSQL
- 存储：复用现有 Samba/NFS 目录 /home/wb/nas
- 部署：Docker Compose + Nginx

## 文档
- docs/NAS管理系统需求清单.md：需求与决策记录
- docs/NAS系统详细设计.md：模块、数据与 API 设计
- docs/GuiCang技术方案与实施手册.md：技术选型、部署视图与实施步骤
- docs/AI规则与编码规范.md：AI 与编码规范

## 开发约束
所有编码遵循 AGENTS.md 与 docs/AI规则与编码规范.md；提交信息用中文 Conventional Commits。
