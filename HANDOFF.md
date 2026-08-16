# GuiCang 项目工作交接

> 新对话开始工作前，请先读本文件，再读 AGENTS.md 与 docs/ 下的四份文档。

## 一句话状态
M0（前后端骨架）与 M1（统一返回/异常、Flyway+SQLite 基础表、审计、初始化向导）已完成；Step 2.1 helper 脚本已写好并测试通过（26 单测 + 9 端到端），**但尚未安装到宿主机（等用户确认）**；Step 2.2 认证链路（Security + JWT + PAM 登录 + /auth/me + 登出）已完成并验证。下一步：Step 2.3 RBAC 与用户管理（V2__rbac.sql）。

## 项目与目标
- 名称：GuiCang（归藏），家庭 NAS 管理系统。
- 仓库：https://github.com/Isanti2016/GuiCang
- 本地仓库/工作目录：/home/codes/guicang
- 目标：Web 统一管理现有 Samba/NFS 存储 /home/wb/nas，提供用户权限、文件管理、md/txt 编辑、图片视频预览、监控大屏、同步、审计，Tailscale 外网访问。

## 已确认的关键决策
- 技术栈：Java 21 + Spring Boot 3.3.x + Maven；Vue 3 + TS + Element Plus + Vite 6；SQLite（默认，可切 PostgreSQL）；Redis；本地 ELK；Docker Compose + Nginx。
- 并发：注册数百、同时在线数十、读多写少；单文件 ≤1G。
- 账号打通：方案 A —— Web 用户 = Linux 系统用户，PAM 认证，sudo 白名单 helper（guicang-helper）管理系统账号，后端不跑 root。
- 初始管理员：首次初始化新建独立 admin。
- md/txt：一期网页内编辑保存；缩略图一期做；视频一期不转码（只播 MP4/H.264）；一期 HTTP；分享链接二期。
- 提交：一律中文 Conventional Commits（类型(范围): 中文描述）。

## 环境现状（本机 Ubuntu 24.04，4C/15G，数据盘 /home 916G）
- 已装：Java 21.0.11、Maven 3.9.16（/opt/maven）、Docker Compose 2.40.3、SQLite 3.45.1、Node 24、pnpm 11.7、Git 2.43、python3 3.12。
- 已有服务：Samba 4.19.5（pdbedit -L 正常，账号 wb/app_data）、NFS、Tailscale（100.112.98.102）、MariaDB（不用）、Apache2（80 端口占，需迁 8081）、ffmpeg 6.1.1。
- 端口注意：80 给 Nginx；3080 是 DSH GUI 绝不能动；3306/445/139/2049 保持现状。
- 检查：uid 1002、gid 2000/2001 空闲（尚未创建 nasusers/nasops/guicang-svc）。

## Git 与 SSH
- 分支 main，origin = git@github.com:Isanti2016/GuiCang.git（SSH，私钥 ~/.ssh/guicang_github 已配置）。
- 已提交里程碑：0.4 前端、0.5 后端、1.1 统一返回/异常、1.2 Flyway+SQLite、1.3 审计、1.4 初始化向导、2.1 helper 脚本（未部署）、2.2 认证链路（JWT+PAM 登录）。

## 文档索引（都在 docs/）
- NAS管理系统需求清单.md、NAS系统详细设计.md（helper 已统一命名 guicang-helper）、GuiCang技术方案与实施手册.md、AI规则与编码规范.md；根目录 AGENTS.md 为硬约束。

## 下一步（按手册逐 Step 执行，每步验证后中文提交）
- Step 2.3：RBAC 与用户管理（V2__rbac.sql；用户 CRUD 调 helper 同步系统账号与 Samba；角色权限接入 @PreAuthorize 与目录级校验）。
- 之后 M3 文件管理起。

## 待用户确认事项（重要）
1. **Step 2.1 宿主机安装**：`sudo ./scripts/install-helper.sh`（先 `--dry-run` 预览）。动作：建组 nasusers(2000)/nasops(2001)、服务账号 guicang-svc(1002)、装 /usr/local/bin/guicang-helper + sudoers 白名单。全部为新增/覆盖自身文件，不动现有 wb/app_data 与 Samba 数据；已做幂等与备份。确认后我再执行，或你自己跑。
2. **admin 系统账号创建**：初始化向导目前只落元数据并标记 pending，helper 部署后需补建 admin 系统账号并设密码（配合 Step 2.2 登录）。

## 注意事项 / 已知坑
- 后端 dev 数据库在 backend/data/（已 gitignore）；测试用共享内存 SQLite。
- Flyway 锁定 9.22.3（10 社区版无 SQLite 模块）；迁移脚本主键经 ${db-id-type} placeholder 区分库。
- @Audit 注解 resource 支持 SpEL（#参数名/#result），编译需 -parameters（已配置）。
- pkill -f 会误杀自身 bash（命令行含匹配串），停后端用端口 PID（ss 取 pid）。
- 需求/设计变更要同步更新 docs/ 四份文档与本文档。
