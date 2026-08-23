# GuiCang 后端镜像：多阶段构建（Maven 构建 → JRE 运行）
# 轻量/local 模式：容器内 root 跑 entrypoint 创建隔离管理员账号后启动后端（不接触宿主凭据）
# 生产/helper 模式：设 GUICANG_CONTAINER_ADMIN_USER 为空，entrypoint 直接非 root 启动后端
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# 运行账号（轻量模式 entrypoint 用 root 建容器内账号；生产 helper 模式可切回 guicang）
RUN groupadd -g 1002 guicang && useradd -u 1002 -g guicang -s /usr/sbin/nologin guicang
COPY --from=build /build/target/guicang-backend-*.jar app.jar
# 入口：创建容器内管理员账号（local）或直接非 root 启动（helper）
COPY docker-entrypoint.sh /usr/local/bin/guicang-entrypoint
RUN chmod +x /usr/local/bin/guicang-entrypoint
# ffmpeg（视频缩略图抽帧）+ python3/libpam（local 认证走 guicang-pam-verify.py 直调 libpam）
RUN apt-get update && apt-get install -y --no-install-recommends \
        ffmpeg python3 libpam0g \
    && rm -rf /var/lib/apt/lists/*
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/guicang-entrypoint"]
