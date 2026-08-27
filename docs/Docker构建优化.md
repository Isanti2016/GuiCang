# Docker 镜像构建优化记录

> 背景：GuiCang 后端镜像构建耗时（曾达 5-10 分钟+），以下优化已落地于 `backend/Dockerfile`。

## 慢点诊断（优化前）

1. **apt 层缓存反复失效（最耗时）**
   - 原 Dockerfile 顺序：`COPY jar` → `RUN apt-get install ffmpeg python3 libpam0g`
   - 每次 jar 内容变化 → COPY 层变化 → 其后的 apt 层缓存全部失效 → 每次构建都重新
     `apt update` + 下载数百 MB（实测 apt 阶段 271 秒+）
2. **Maven 依赖无持久缓存**
   - 每次构建在容器内全新 `~/.m2`，`dependency:go-offline` 重新下载全部依赖

## 优化手段（已落地）

1. **调整层顺序：apt 提前**
   - `RUN apt-get install ...` 移到 `COPY jar` **之前** → apt 层不再依赖 jar 内容，
     只要基础镜像与安装清单不变就命中缓存，避免重复下载 ffmpeg 等
2. **BuildKit cache mount 持久化 Maven 仓库**
   ```dockerfile
   RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package
   ```
   → `~/.m2` 缓存在宿主机，增量构建只下载新增依赖；`pom.xml` 未变时几乎零下载
3. **pom.xml 单独 COPY（依赖层隔离）**
   - `COPY pom.xml .` + `go-offline` 一层，仅 pom 变化时失效；源码变化只触发编译层

## 使用方法

```bash
# Docker 23+ 默认启用 BuildKit；老版本显式开启
export DOCKER_BUILDKIT=1
bash scripts/docker-start.sh    # 内部 docker compose build 即走 BuildKit
```

- **首次构建**：填充 cache（依赖下载 + apt 安装），仍较慢
- **二次构建（代码小改）**：apt 命中缓存、Maven 命中 .m2 → 秒级~分钟级
- 清缓存：`docker builder prune`（释放磁盘）；`docker builder prune --filter type=exec.cachemount`（仅清 Maven 缓存）

## 前端镜像说明

- 轻量模式前端**不构建镜像**：`frontend/dist` 由宿主机构建后挂载进 nginx（`docker-compose.lite.yml`）
- 如需容器内构建（`build: ../frontend`），同样可用 BuildKit cache mount 缓存
  `pnpm store` 与 node_modules（当前未启用，因宿主 npm registry 访问慢）
