# syntax=docker/dockerfile:1

# ---- build stage：一次构建三个门户 ----
FROM node:20-bookworm-slim AS build
WORKDIR /web

RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages/api/package.json packages/api/
COPY apps/staff/package.json apps/staff/
COPY apps/admin/package.json apps/admin/
COPY apps/mobile/package.json apps/mobile/
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm --filter @workbench/staff build \
    && pnpm --filter @workbench/admin build \
    && pnpm --filter @workbench/mobile build

# ---- runtime stage：nginx 同时托管三端 ----
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /web/apps/staff/dist /usr/share/nginx/html/staff
COPY --from=build /web/apps/admin/dist /usr/share/nginx/html/admin
COPY --from=build /web/apps/mobile/dist /usr/share/nginx/html/mobile

EXPOSE 8080 8081 8082

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
