# AGENTS.md — GuiCang 项目 AI 编码约束（绑定规范）

> 本文件与 docs/AI规则与编码规范.md 是本项目的强制编码约束。所有 AI 助手与开发者编码时必须遵守；任何偏离须先说明理由并获批。

## 角色与总原则
你是资深 Java 与 Vue 工程师。遵循 SOLID / DRY / KISS / YAGNI / OWASP。
把任务拆成最小可验证单元，逐步实现；只改与任务相关的代码，不做无关清理。
沟通与 git 提交一律用中文。

## 项目与技术栈
GuiCang（归藏）家庭 NAS 管理系统。
后端：Java 21、Spring Boot 3、Maven、MyBatis-Plus、Flyway、SQLite（可切 PostgreSQL）、Redis、JWT、Quartz。
前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts、Axios。

## 后端分层（硬性）
- Controller（@RestController）：只做请求响应、参数校验，不写业务，只转发 Service。
- Service / ServiceImpl（@Service）：业务逻辑与事务；多步数据库操作必须 @Transactional。
- Mapper（MyBatis-Plus）：数据访问；ServiceImpl 不得绕过 Mapper 直接查库。
- Controller 不得直接访问 Mapper；层间只传 DTO；实体只用于承载查询结果。
- 统一返回 Result<T>；全局异常 @RestControllerAdvice；入参校验用 Bean Validation。
- 目录按业务模块聚合（module/<功能> 内含 Controller/Service/Mapper），跨模块公共能力放 common/ 与 infra/；不把各层平铺全局。
- Controller 不得包含 private 业务方法/内部类（响应组装下沉独立组件如 FileStreamResponder）。

## 后端注释（硬性）
- 所有公开 API（Controller 方法、Service 接口与实现类方法）用标准多行 Javadoc：`/** 摘要。` + `@param`（每个参数）+ `@return`（非 void）。
- ServiceImpl 的 @Override 方法也必须有完整 Javadoc，不允许只依赖接口注释。
- 新增/修改代码必须同步补注释。

## 后端命名与风格
- 类 PascalCase、方法/字段 camelCase、常量 UPPER_SNAKE_CASE。
- Controller / Service / Mapper 后缀；构造器注入（不用字段 @Autowired）。
- DTO 用 record；Lombok 可用（@Getter/@Setter/@Slf4j 等），record 优先。
- 格式化：Spotless + Google Java Format。

## 前端约束
- 只用组合式 API + <script setup lang="ts">；禁止 Options API / mixins / this。
- 组件名多单词 PascalCase；defineProps<T>()、defineEmits<T>()、defineModel()。
- 不直接修改 props；v-for 必须 key；组件样式 scoped；模板表达式保持简单。
- Pinia 用 setup store：defineStore('name', () => {...})，用 storeToRefs() 解构，一个域一个 store，getters 纯函数。
- composables 用 use 前缀，异步返回 { data, error, loading }。
- TypeScript strict，禁止 any（用 unknown + 收窄）；接口统一封装在 api/。
- ESLint + Prettier 默认配置。
- 目录固定职责：api/（接口封装）/ components/（公共组件）/ composables/ / layout/ / router/ / stores/ / styles/ / utils/ / views/；新代码放对应目录，不放无关文件到根。

## 代码质量（通用）
- 早返回减少嵌套；事件处理器用 handle 前缀；常量优于魔法值；函数式不可变风格。
- 业务逻辑不放组件；保持可测试；最小改动。
- 注释用中文 Javadoc/JSDoc（/** */）：后端公开 API 与前端导出/处理函数必须有注释；禁止注释替代命名、冗余复制注释。
- 第三方依赖统一：同一类问题只保留一种实现（UI=Element Plus、图标=@element-plus/icons-vue、图表=ECharts、HTTP=axios 统一封装、文档=markdown-it）；不重复引入；声明未使用的依赖立即移除；新依赖先说明理由并经确认。

## 安全红线（最高优先级）
- 密钥/账号/密码只放本地配置，不进 git；密码不落业务库（认证走 PAM/系统账号）。
- 所有文件路径做规范化 + 越权校验，防目录穿越与越权。
- 特权操作只经 guicang-helper（sudo 白名单），后端进程不跑 root。
- 日志不打印敏感信息；禁止 v-html 渲染用户输入。

## 测试
- 后端 JUnit 5，核心 Service 行覆盖率 60%+，关键 API 有集成测试。
- 前端 Vitest + @vue/test-utils，测行为不测实现，优先 mount()。

## 提交
- 中文 Conventional Commits：类型(范围): 中文描述；每完成一个可验证步骤提交一次。

## 禁止清单
- 禁止 Controller 直接访问 Mapper；禁止吞异常/空 catch；禁止把密码 token 写入 git。
- 禁止 Options API / mixins / this / any 泛滥；禁止只做前端隐藏不做后端鉴权。
- 禁止绕过权限校验；禁止无关重构与清理。
