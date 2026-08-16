# GuiCang 前端镜像：构建产物由 Nginx 托管（部署拓扑见 docker-compose.yml）
FROM node:24-alpine AS build
WORKDIR /build
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
# Nginx 反代配置（deploy/nginx 下的 guicang.conf 由 compose 挂载覆盖）
EXPOSE 80
