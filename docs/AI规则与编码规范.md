# GuiCang AI 规则与编码规范（待确认）

> 用途：你确认后，本规范固化为仓库根目录的 `AGENTS.md`（给 AI/agent 看）+ 工具配置（.editorconfig / ESLint / Prettier / Spotless 等），后续所有编码与 AI 生成代码都遵循。
> 来源：[Vue 官方风格指南](https://cn.vuejs.org/style-guide/)、[Spring Boot 官方文档](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)、[阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)、[Conventional Commits](https://www.conventionalcommits.org/zh-hans/)、社区 Java 编码规范。

---

## 1. AI 开发总约束（AGENTS.md 核心）

### 1.1 项目背景（一句话）
GuiCang（归藏）家庭 NAS 管理系统：Java 21 + Spring Boot 3 后端，Vue 3 + TypeScript + Element Plus 前端，SQLite 起步可切 PostgreSQL，Docker Compose 部署，复用现有 Samba/NFS 存储。

### 1.2 技术栈锁定
- 后端：Java 21、Spring Boot 3.3.x、Maven、MyBatis-Plus、Flyway、JWT、Quartz、SQLite/PostgreSQL。
- 前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts、Axios。
- 不得擅自引入新语言/框架/依赖；确需引入须先说明理由并经确认。

### 1.3 目录与分层
- 后端包根 `com.guicang.nas`，主类在根包（Spring Boot 官方要求）。
- 分层：Controller → Service → Mapper → Infra，禁止跨层访问。
- 前端分层：views / components / composables / stores / api / utils / layout / router。

### 1.4 安全红线（最高优先级）
- 密钥/账号/密码只放本地配置（application-local.yml、.env），一律不进 git。
- 密码不落业务库：认证走 PAM/系统账号，库中只存应用元数据。
- 所有文件路径做规范化 + 越权校验，防目录穿越与越权访问。
- 特权操作只经 `guicang-helper`（sudo 白名单），后端进程不跑 root。
- 日志不打印敏感信息（密码、token、密钥）。

### 1.5 提交与质量
- 提交信息一律用中文 Conventional Commits（见第 4 节）。
- 每完成一个可独立验证的步骤提交一次。
- 核心 Service 必须有单元测试；关键 API 有集成测试。
- 需求/设计变更同步更新 docs 下的三份文档。

### 1.6 禁止事项
- 禁止 Controller 直接访问 Mapper。
- 禁止吞异常、空 catch。
- 禁止把密码/token 写入 git。
- 禁止前端 `any` 泛滥。
- 禁止只在前端隐藏入口、后端不做权限校验。

---

## 2. 后端编码规范（Java 21 + Spring Boot 3）

### 2.1 工程结构
- 单一根包：`com.guicang.nas`；启动类 `NasApplication` 位于根包。
- 模块包：common / config / module(业务) / infra；见技术方案手册 3.1。

### 2.2 命名
- 类/接口/record：PascalCase；方法/字段：camelCase；常量：UPPER_SNAKE_CASE。
- Controller 以 `Controller` 结尾，Service 以 `Service` 结尾，Mapper 以 `Mapper` 结尾。
- 传输对象用 `record`（如 `LoginRequest`、`FileItemVO`），实体字段不可变优先。

### 2.3 代码风格（Java 17+ 特性）
- 清晰优先于花哨；不可变优先，减少共享可变状态。
- 依赖注入用构造器注入，禁止字段注入。
- 优先 `record`、`final` 字段、`Optional` 谨慎使用。
- 格式化用 Spotless + Google Java Format；Lombok 可用（@Getter/@Setter/@Slf4j），但 record 优先。

### 2.4 异常与返回
- 统一返回体 `Result<T>{code,message,data}`，全局异常经 `@RestControllerAdvice`。
- 业务异常用统一 `BizException`，禁止裸抛与吞异常。
- 异常信息明确可定位，不把堆栈直接返回前端。

### 2.5 参数校验
- 用 Bean Validation（jakarta.validation），Controller 入参校验，不做重复手写 if。

### 2.6 集合、判空、常量、日志、并发
- 判空用 `isEmpty()`；避免魔法值（提常量）。
- 日志用 SLF4J + 结构化 JSON（logstash-logback-encoder），敏感字段脱敏。
- 线程池显式指定参数，不用 `Executors` 快捷方法。

### 2.7 测试
- JUnit 5；核心 Service 单测；Auth/文件/上传 Range 集成测试。

---

## 3. 前端编码规范（Vue 3 + TypeScript + Element Plus）

### 3.1 Vue 官方 A 级（必须遵守）
- 组件名使用多个单词（防与 HTML 元素冲突）。
- props 必须详细定义（类型、必填、默认值）。
- `v-for` 必须带 `key`。
- 避免 `v-if` 与 `v-for` 用于同一元素。
- 组件样式使用 scoped。

### 3.2 Vue 官方 B 级（强烈推荐）
- 单文件组件文件名用 PascalCase；组件名用全单词。
- 模板中组件名用 PascalCase；props 用 camelCase。
- 模板表达式保持简单，复杂逻辑拆到 computed。
- 属性值加引号；多属性换行；使用指令简写（: 与 @）。

### 3.3 Vue 官方 C 级（推荐）
- 组件 options 顺序、元素属性顺序、SFC 顶层顺序保持一致（按官方建议）。

### 3.4 TypeScript
- `strict: true`，禁止 `any` 泛滥（确需时用 `unknown` + 类型收窄）。
- API 响应、Pinia 状态、props 均有明确类型。

### 3.5 工程约定
- 统一组合式 API + `<script setup lang="ts">`。
- 接口统一封装在 api/，页面不直接写 axios。
- ESLint + Prettier 统一风格；Element Plus 按需或全量引入二选一并在入口统一。
- 权限：路由守卫 + `v-permission` 指令，后端为准。

---

## 4. 提交规范（中文 Conventional Commits）

格式：`类型(范围): 中文描述`

| 类型 | 用途 |
|---|---|
| feat | 新功能 |
| fix | 缺陷修复 |
| docs | 文档 |
| refactor | 重构（不改行为） |
| perf | 性能优化 |
| test | 测试 |
| build | 构建/依赖/部署 |
| chore | 杂项（配置、脚本等） |
| style | 代码格式（不影响逻辑） |

示例：
- `feat(file): 新增目录列表与路径越权校验`
- `fix(auth): 修复 JWT 过期后重复跳转登录页`
- `docs: 更新部署文档的端口规划`
- `chore(scripts): 新增一键部署脚本`

---

## 5. 已确认的取舍点

1. **Lombok**：可以使用（@Getter/@Setter/@Slf4j 等；record 仍优先）。
2. **后端格式化**：Spotless + Google Java Format。
3. **前端分号/引号**：跟随 Prettier 默认（双引号、保留分号）。
4. **单测覆盖目标**：核心 Service 行覆盖率 60%+。

