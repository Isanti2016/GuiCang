# syntax=docker/dockerfile:1
# =============================================================================
# GuiCang 后端镜像：多阶段构建（Maven 构建 → JRE 运行）
# 轻量/local 模式：容器内 root 跑 entrypoint 创建隔离管理员账号后启动后端（不接触宿主凭据）
# 生产/helper 模式：设 GUICANG_CONTAINER_ADMIN_USER 为空，entrypoint 直接非 root 启动后端
#
# 构建加速（BuildKit）：
#   1. apt 安装层放在 COPY jar 之前 → 只要镜像基础不变，apt 层命中缓存，不再重复下载
#      ffmpeg/python3/libpam（此前每次 jar 变化都会连带重跑 apt，最耗时）
#   2. Maven 用 --mount=type=cache,target=/root/.m2 持久缓存依赖仓库 → 增量构建不再
#      重复下载全部依赖
#   3. pom.xml 单独 COPY → 依赖解析层仅当 pom 变化时失效
# 构建命令需启用 BuildKit（Docker 23+ 默认开启；显式：DOCKER_BUILDKIT=1）
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# 依赖层：仅 pom.xml 变化时失效（利用层缓存 + .m2 cache mount 双保险）
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -o dependency:go-offline || mvn -q -B dependency:go-offline
# 源码层：代码变化仅重编译，依赖已缓存
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -o -DskipTests package || mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# 运行账号（轻量模式 entrypoint 用 root 建容器内账号；生产 helper 模式可切回 guicang）
RUN groupadd -g 1002 guicang && useradd -u 1002 -g guicang -s /usr/sbin/nologin guicang
# 系统依赖：放在 COPY jar 之前，apt 层命中缓存（不再每次重新下载 ffmpeg 等）
RUN apt-get update && apt-get install -y --no-install-recommends \
        ffmpeg python3 libpam0g \
    && rm -rf /var/lib/apt/lists/*
# 入口：创建容器内管理员账号（local）或直接非 root 启动（helper）
COPY docker-entrypoint.sh /usr/local/bin/guicang-entrypoint
RUN chmod +x /usr/local/bin/guicang-entrypoint
# 应用 jar（变化仅影响本层与之后）
COPY --from=build /build/target/guicang-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/guicang-entrypoint"]
