# AGENTS.md — GuiCang 项目 AI 编码约束

> 所有 AI 助手与开发者在仓库编码时必须遵守。详细规范见 docs/AI规则与编码规范.md 与 docs/GuiCang技术方案与实施手册.md。

## 项目
GuiCang（归藏）家庭 NAS 管理系统：Java 21 + Spring Boot 3 后端，Vue 3 + TS + Element Plus 前端，SQLite 起步可切 PostgreSQL，Docker Compose 部署，复用 /home/wb/nas（Samba/NFS）。

## 硬性约束
- 后端包根 com.guicang.nas，主类在根包；分层 Controller → Service → Mapper → Infra，禁止跨层。
- 前端 views / components / composables / stores / api / utils 分层；组合式 API + script setup lang=ts。
- 密钥/账号/密码只放本地配置（application-local.yml、.env），不进 git；密码不落业务库。
- 特权操作经 guicang-helper（sudo 白名单），后端进程不跑 root。
- 所有文件路径做规范化 + 越权校验，防目录穿越。
- 提交信息用中文 Conventional Commits：类型(范围): 中文描述。

## 代码风格
- 后端：Spotless + Google Java Format；Lombok 可用（@Getter/@Setter/@Slf4j），record 优先。
- 前端：Vue 官方风格指南 A 级必须、B 级强烈推荐；TS strict，禁 any；ESLint + Prettier 默认配置。

## 质量
- 核心 Service 行覆盖率 60%+；关键 API 有集成测试。
- 需求/设计变更同步更新 docs/ 文档。

## 禁止
- 禁止 Controller 直接访问 Mapper；禁止吞异常；禁止把密码/token 写入 git；禁止前端 any 泛滥；禁止只做前端隐藏不做后端鉴权。
