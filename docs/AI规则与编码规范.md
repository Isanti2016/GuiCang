# GuiCang AI 规则与编码规范 v2（已确认，绑定约束）

> 状态：已确认。本文件与根目录 AGENTS.md 一起构成本项目强制编码约束；所有 AI 助手与开发者编码时必须遵守，偏离须先说明并获批。
> 来源：[awesome-cursorrules](https://github.com/PatrickJS/awesome-cursorrules)（Java Spring Boot / Vue3 Composition API / Vue+Pinia / JS-TS Code Quality）、[Vue 官方风格指南](https://cn.vuejs.org/style-guide/)、[Spring Boot 官方文档](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)、[阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)、[Conventional Commits](https://www.conventionalcommits.org/zh-hans/)。

---

## 1. 角色与总原则
- 角色：资深 Java 与 Vue 工程师。
- 原则：SOLID / DRY / KISS / YAGNI / OWASP。
- 工作方式：任务拆最小可验证单元，逐步实现；最小改动，只改任务相关代码，不做无关清理。
- 语言：沟通与 git 提交一律中文。

## 2. 项目与技术栈（锁定，不得擅改）
- 后端：Java 21、Spring Boot 3.3.x、Maven、MyBatis-Plus、Flyway、SQLite（可切 PostgreSQL）、Redis、JWT、Quartz。
- 前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts、Axios。
- 新依赖须先说明理由并经确认；包根 com.guicang.nas，主类在根包。

## 3. 后端规范

### 3.0 目录结构（package-by-feature 聚合）
- 按业务模块聚合：`module/<功能>`（如 module/auth、module/file、module/user）内含该模块的 Controller/Service/ServiceImpl/Mapper/实体，类型以后缀区分（FileController、FileService、FileServiceImpl、FileIndexMapper）。
- 跨模块公共能力放 `common/`（Result、BizException、全局异常、安全上下文）与 `infra/`（存储、账号/PAM、监控采集、缩略图等基础设施）。
- 配置类放 `config/`；Flyway 迁移放 `resources/db/migration/`。
- 新建模块时先建 `module/<功能>` 目录，不把 Controller/Service/Mapper 平铺到全局单一目录。

### 3.1 分层硬约束
- Controller 只做请求响应与参数校验，不写业务，只转发 Service；不得包含 private 业务方法或内部类（响应组装细节下沉到独立组件，如 FileStreamResponder）。
- Service / ServiceImpl 做业务与事务；多步数据库操作必须 @Transactional。
- Mapper（MyBatis-Plus）做数据访问；ServiceImpl 不得绕过 Mapper 直接查库。
- Controller 不得直接访问 Mapper；层间只传 DTO；实体只用于承载查询结果。

### 3.2 实体与映射
- 实体类加 MyBatis-Plus 注解（@TableName / @TableId / @TableField）。
- 主键策略按库选择（SQLite/PG 用自增或 ASSIGN_ID，统一约定）。
- Lombok 可用（@Getter/@Setter），record 优先用于 DTO。

### 3.3 Mapper（DAO）
- 接口继承 BaseMapper<T>；复杂查询用 XML 或注解 SQL。
- 多表查询返回 DTO，避免实体直接跨层。

### 3.4 Service
- 接口 + ServiceImpl（@Service）；构造器注入依赖，不用字段 @Autowired。
- 业务校验在前，数据库操作清晰，多步写操作 @Transactional。

### 3.5 DTO
- 用 record；紧凑构造器做入参校验（非空、格式等）。
- 出参用 VO/Result，不把实体直接返回前端。

### 3.6 Controller
- @RestController + @RequestMapping 类级路径；RESTful 资源路径（/users/{id}），不用动词路径。
- @GetMapping/@PostMapping/@PutMapping/@DeleteMapping；入参用 Bean Validation。
- 方法不写业务，只调 Service，返回 Result<T>。

### 3.7 统一返回与异常
- 统一返回体 Result<T>{code,message,data}。
- 全局异常 @RestControllerAdvice；业务异常用统一 BizException；禁止吞异常、空 catch。

### 3.8 命名、注入与风格
- 类 PascalCase、方法/字段 camelCase、常量 UPPER_SNAKE_CASE。
- Controller/Service/Mapper 后缀；构造器注入。
- 格式化：Spotless + Google Java Format；日志 SLF4J 结构化 JSON，敏感信息脱敏。

### 3.9 后端注释规范（硬性）
- 所有公开 API（Controller 方法、Service 接口与实现类方法、公共工具方法）必须使用标准多行 Javadoc：`/** 摘要。` + 空行 + 每个参数的 `@param 名 中文说明` + 非 void 返回的 `@return 中文说明`（+ `@throws` 当方法声明异常）。
- ServiceImpl 的 @Override 方法同样需要完整 Javadoc（摘要可从接口方法复制/改写，参数与返回标注完整），不允许只依赖接口注释。
- 私有方法可不加 Javadoc；禁止用注释替代清晰命名、禁止复制冗余注释。
- 新增/修改代码必须同步补注释，否则视为未完成。

## 4. 前端规范

### 4.0 目录结构
- 固定目录职责：`api/`（接口封装，页面不直接写 axios）、`components/`（跨页面公共组件）、`composables/`（use 前缀复用逻辑）、`layout/`（布局壳）、`router/`（路由与守卫）、`stores/`（Pinia）、`styles/`（全局样式）、`utils/`（无状态工具）、`views/`（页面视图）。
- 新功能优先复用既有目录；页面私有组件就近放在该页面同级或 components/ 下；不放无关文件到根。

### 4.1 组合式 API 约束
- 只用组合式 API + <script setup lang="ts">。
- 禁止 Options API / mixins / this。

### 4.2 组件规范
- 组件名多单词 PascalCase；defineProps<T>()、defineEmits<T>()、defineModel()。
- 不直接修改 props；v-for 必须 key；组件样式 scoped；模板表达式保持简单。

### 4.3 Pinia 状态管理
- setup store：defineStore('name', () => {...})；storeToRefs() 解构状态。
- 一个域一个 store；getters 纯函数；业务编排在 actions。

### 4.4 composables
- use 前缀；异步返回 { data, error, loading }；可复用响应式逻辑。

### 4.5 TypeScript
- strict；禁止 any（用 unknown + 收窄）；API 响应、props、store 显式类型。
- 接口统一封装在 api/，页面不直接写 axios。

### 4.6 测试
- Vitest + @vue/test-utils；测行为不测实现；优先 mount()。

### 4.7 Vue 官方 A/B/C 级规则
- A 级必须：组件名多单词、props 详细定义、v-for 带 key、避免 v-if 与 v-for 同元素、scoped 样式。
- B 级强烈推荐：PascalCase 文件名、模板 PascalCase、props camelCase、简单表达式、指令简写、引号属性值。
- C 级推荐：options 顺序、属性顺序、SFC 顶层顺序保持一致。

## 5. 通用代码质量
- 早返回减少嵌套；事件处理器 handle 前缀；常量优于魔法值；函数式不可变风格。
- 业务逻辑不放组件；保持可测试；最小改动，不做无关重构。

### 5.1 注释规范
- 后端：公开 API（Controller 方法、Service 接口方法、公共工具方法）必须有 Javadoc（/** */），说明用途与关键参数；实现类内部私有方法视复杂度补充。
- 前端：api/ 的接口函数与接口类型、utils/、composables/、stores/ 的导出函数、页面视图中的处理函数（handle*、render*、load*、格式化函数等）必须有 /** */ 注释；简单响应式状态 ref/reactive 可不加。
- 注释用中文；禁止用注释代替清晰命名；禁止复制代码块的冗余注释。

### 5.2 第三方依赖统一
- 新依赖须先说明理由并经确认（见 §2）；能用既有依赖解决的问题不重复引入。
- 同一类问题只保留一种实现：UI 用 Element Plus、图标用 @element-plus/icons-vue、图表用 ECharts、HTTP 用 axios（统一封装于 utils/http.ts）、文档渲染用 markdown-it + highlight.js。
- 引入后必须实际使用；发现声明但未使用的依赖（如冗余 npm 包）应立即移除。
- 依赖版本集中在 package.json / pom.xml，锁定 major 版本，升级须单独提交并跑全量测试。

## 6. 安全红线（最高优先级）
- 密钥/账号/密码只放本地配置（application-local.yml、.env），不进 git；密码不落业务库。
- 文件路径规范化 + 越权校验，防目录穿越与越权访问。
- 特权操作只经 guicang-helper（sudo 白名单），后端进程不跑 root。
- 日志不打印敏感信息；禁止 v-html 渲染用户输入。

## 7. 测试要求
- 后端 JUnit 5，核心 Service 行覆盖率 60%+，关键 API 集成测试。
- 前端 Vitest + @vue/test-utils，测行为不测实现。

## 8. 提交规范（中文 Conventional Commits）
- 格式：类型(范围): 中文描述。
- 类型：feat/fix/docs/refactor/perf/test/build/chore/style。
- 每完成一个可独立验证步骤提交一次。

## 9. 禁止清单
- 禁止 Controller 直接访问 Mapper；禁止吞异常/空 catch；禁止把密码 token 写入 git。
- 禁止 Options API / mixins / this / any 泛滥；禁止只做前端隐藏不做后端鉴权。
- 禁止绕过权限校验；禁止无关重构与清理。

## 10. 已确认取舍点
1. Lombok：可以使用（@Getter/@Setter/@Slf4j 等；record 仍优先）。
2. 后端格式化：Spotless + Google Java Format。
3. 前端分号/引号：Prettier 默认（双引号、保留分号）。
4. 单测覆盖：核心 Service 行覆盖率 60%+。
