# GuiCang 后端镜像：多阶段构建（Maven 构建 → JRE 运行）
# 运行用户：guicang-svc（uid 1002，非 root）；特权操作经宿主 guicang-helper
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
# 非 root 运行（与宿主机 guicang-svc 保持一致，uid 1002）
RUN groupadd -g 1002 guicang && useradd -u 1002 -g guicang -s /usr/sbin/nologin guicang
COPY --from=build /build/target/guicang-backend-*.jar app.jar
# ffmpeg（视频缩略图抽帧）
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
USER guicang
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
