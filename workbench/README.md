# Workbench v2（新架构工程）

按 `docs/design-v2.md` 实施。本目录是重写后的工程根，模块按依赖顺序逐个交付。

## 目录规划

```
workbench/
├── server/                  Spring Boot 3 多模块（Maven）
│   ├── pom.xml              聚合 POM
│   ├── workbench-common/    返回体、异常、分页、审计、幂等
│   ├── workbench-auth/      登录、JWT、权限
│   ├── workbench-user/      用户、部门
│   ├── workbench-project/   项目、成员、设置
│   ├── workbench-task/      任务、看板、标签
│   ├── workbench-planning/  依赖、里程碑、排期、模板、视图
│   ├── workbench-worklog/   工时、容量
│   ├── workbench-report/    日报、周报、报表
│   └── workbench-app/       启动模块（装配、配置、Flyway）
├── web/                    pnpm workspace（Vue 3 + TS）
│   ├── packages/{ui,api,auth,mock}
│   └── apps/{staff,admin,mobile}
└── docs/                   接口契约与本地开发说明
```

## 实施顺序（一个板块一个板块）

每个板块的完成定义：接口可用 + 后端集成测试 + 前端页面可操作 + CI 通过。

| 序号 | 板块 | 交付内容 | 验收 |
|---|---|---|---|
| 1 | 地基 | 多模块骨架、Flyway baseline、统一返回体、JWT 登录、OpenAPI、CI | 能登录并拿到 token；`mvn verify` 绿 |
| 2 | 组织 | 用户 CRUD、部门树、导入导出 | 管理端可增删改查用户与部门 |
| 3 | 项目 | 项目 CRUD、成员、角色、邀请、移交、设置 | 员工端能建项目、邀请成员 |
| 4 | 任务 | 任务 CRUD、子任务、标签、看板、归档 | 看板可拖动、子任务可嵌套 |
| 5 | 规划 | 依赖、里程碑、自动排期、模板、保存视图 | 预演/应用/撤销可用且幂等 |
| 6 | 工时 | 登记、预估、容量汇总 | 容量表与 CSV 导出正确 |
| 7 | 日报 | 每日日报、每周自动汇总 | 重复提交幂等、周一生成周报 |
| 8 | 协作 | 消息、WebSocket、提醒 | 里程碑提醒可达 |
| 9 | 文件 | 上传、预览、附件 | 大文件分块上传成功 |
| 10 | 系统 | 设置、授权、应用中心、审计 | 管理端可配置 |

## 开发约定

- 后端：Controller 薄，逻辑在 Service；DTO/VO 分离；禁止跨模块访问 Mapper；
- 前端：单文件组件 ≤ 300 行；请求统一走 `packages/api`；状态放 Pinia；
- 数据库：所有变更走 Flyway，禁止手改库；
- 接口：`/api/v1`，统一返回体与分页结构；
- 测试：后端 MockMvc + 集成测试；前端 Vitest + 挂载冒烟；
- 提交：一个板块一组提交，commit message 前缀 `feat(<模块>):`。

## 当前状态（板块 1 完成）

后端 `mvn verify` 通过（AuthControllerTest 3 条），前端 `pnpm typecheck` 通过，CI 已接入。

| 层 | 内容 |
|---|---|
| server | 9 个模块；`common`（返回体/异常/分页/全局处理）；`auth`（JWT、过滤器、安全配置、登录接口与测试）；`app`（启动类、`application.yml`、`V1__baseline.sql`） |
| web | `packages/api`（登录类型 + ApiClient）；`apps/staff`（Vite + Element Plus + Pinia，登录页、路由守卫、首页占位） |
| CI | `.github/workflows/workbench-ci.yml`：`mvn -B verify` + `pnpm typecheck` |

## 本地运行

```powershell
# 后端（JDK 21 + Maven 3.9）
cd workbench\server
mvn -q -DskipTests package
mvn spring-boot:run        # 端口 9000，需要本地 MySQL 与已执行的 Flyway 迁移

# 前端
cd workbench\web
pnpm install
pnpm dev:staff     # 员工端，端口 5180，/api 代理到 127.0.0.1:9000
pnpm dev:mobile    # 移动端 H5/PWA，端口 5182
```

## 三个门户

| 门户 | 目录 | 端口 | 说明 |
|---|---|---|---|
| 员工端 | `web/apps/staff` | 5180 | 项目、任务、规划、工时、日报、评论、附件、模板、动态 |
| 管理端 | `web/apps/admin` | 5181 | 用户与部门管理 |
| 移动端 | `web/apps/mobile` | 5182 | H5/PWA：登录、项目、任务（完成/登记工时）、日报、消息 |

移动端是纯 Vite + Vue 3，不引入 Element Plus（体积更小、触控样式自写）；`public/manifest.webmanifest` + `public/sw.js` 提供安装与离线壳缓存（只缓存静态资源，`/api` 始终走网络）。

构建：`pnpm build`（会依次构建 staff/admin/mobile，CI 中已加入该步骤）。

已知限制：登录按旧库规则校验（`md5(明文 + encrypt 盐)`），成功后自动升级为 BCrypt；旧 `users` 表没有 `deleted_at`，查询已做容错。

## 端到端冒烟

```powershell
# 1. 准备库（WSL MySQL/MariaDB，建库 + workbench 用户 + 0.0.0.0 监听）
bash workbench/scripts/e2e-db-setup.sh

# 2. 启动应用（连本地库，Flyway 自动建表）
cd workbench\server
$env:SPRING_APPLICATION_JSON='{"spring":{"datasource":{"url":"jdbc:mysql://127.0.0.1:3306/workbench?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC","username":"wb","password":"wb"}}}'
mvn -pl workbench-app -am spring-boot:run

# 3. 跑冒烟（22 项断言：登录、项目、任务、成员、分页、中文）
powershell -NoProfile -File workbench/scripts/e2e-smoke.ps1 -BaseUrl http://127.0.0.1:9000
```

已完成过的端到端验证覆盖：登录（旧 md5 密码自动升级）、项目 CRUD + 归档/恢复、任务创建/更新/完成/取消完成/删除、成员添加/移除（重复添加被拒）、分页 `total`、中文 UTF-8 往返。

## 旧数据迁移

v2 沿用旧库的表名与主键，因此迁移是「同表搬行、保留 id」：项目成员、任务、依赖、工时的关联关系天然不需要重映射。

```powershell
# 1. 打包（迁移器在 workbench-app 里）
cd workbench\server
mvn -DskipTests package -pl workbench-app -am

# 2. 先 dry-run 看行数（不写库）
powershell -File workbench\scripts\migrate-legacy.ps1 `
  -SourceUrl "jdbc:mysql://127.0.0.1:3306/dootask?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC" `
  -SourceUser root -SourcePassword secret -DryRun

# 3. 确认无误后实际迁移
powershell -File workbench\scripts\migrate-legacy.ps1 `
  -SourceUrl "jdbc:mysql://127.0.0.1:3306/dootask?..." -SourceUser root -SourcePassword secret
```

要点：

- 迁移顺序按外键依赖：`users → departments → projects → project_users → project_milestones → project_tasks → project_task_dependencies → project_task_work_logs`；
- 旧库缺少新列（`deleted_at`、`milestone_id`、`owner_userid`、`request_payload_hash`）时按列存在性自动跳过并填默认值；
- 写入用 `INSERT IGNORE`，重复执行幂等（第二次 `copied=0`）；
- 旧密码是 `md5(明文 + encrypt 盐)`，迁移后**首次登录会自动升级为 BCrypt**，不需要单独重置密码；
- 本地验证环境：`workbench/scripts/seed-legacy.sql` + `legacy-seed-setup.sh`（建 `legacy_seed` 库并灌入 1 用户/项目/任务/工时）。

注意：`e2e-smoke.ps1` 需要 **UTF-8 BOM** 保存，否则 PowerShell 5.1 会按 ANSI 解析导致中文乱码；响应体由脚本自己按 UTF-8 解码（Spring 的 `application/json` 不带 charset）。

## 部署（Docker Compose）

```bash
cd workbench
cp .env.example .env        # 按需改密码/端口/JWT_SECRET
docker compose up -d --build
```

启动后：

| 服务 | 地址 | 说明 |
|---|---|---|
| 员工端 | http://localhost:8080 | nginx 托管 staff，`/api` 反代到 server |
| 管理端 | http://localhost:8081 | nginx 托管 admin |
| 移动端 | http://localhost:8082 | nginx 托管 mobile（含 manifest / sw.js） |
| 后端 | http://localhost:9000 | Spring Boot，Flyway 启动时自动建表 |
| MySQL | localhost:3306 | 数据卷 `workbench_mysql-data` |

部署细节：

- `server` 健康检查走 `/actuator/health`（只暴露 health 端点），`web` 等它 healthy 才启动；
- 附件落在数据卷 `workbench_uploads`（容器内 `/app/data/uploads`），重建容器不丢；
- 后端配置全部走环境变量：`DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET`/`STORAGE_ROOT`/`SERVER_PORT`；
- 旧数据迁移在容器内执行：
  ```bash
  docker compose run --rm -e WORKBENCH_MIGRATE=1 server \
    java -jar app.jar --spring.main.web-application-type=none \
    --workbench.migrate.enabled=true --workbench.migrate.source-url=... --workbench.migrate.exit=true
  ```
- 静态检查（无 Docker 环境也能跑）：`node workbench/scripts/check-deploy.mjs`（16 项：compose 结构、变量、nginx 三端、Dockerfile 多阶段/非 root/健康检查）。

## 浏览器验收（Playwright）

前置：后端已启动（默认 9000），三个 dev server 已启动。

```powershell
# 1. 后端（另开窗口）
cd workbench\server
mvn -pl workbench-app -am spring-boot:run

# 2. 三端 dev server（另开三个窗口；WORKBENCH_API 指向后端）
cd workbench\web
$env:WORKBENCH_API='http://127.0.0.1:9000'
pnpm dev:staff      # 5180
pnpm dev:admin      # 5181
pnpm dev:mobile     # 5182

# 3. 跑验收（使用本机 Chrome，不下载浏览器）
pnpm test:e2e
```

覆盖：员工端「登录 → 建项目 → 建任务 → 完成任务」、管理端「登录 → 用户列表 → 系统设置页」、移动端「登录 → 项目卡片 → 任务页」。失败时在 `workbench/web/apps/e2e/test-results/` 留下截图与 trace（`pnpm exec playwright show-trace <trace.zip>`）。

说明：`playwright.config.ts` 使用 `channel: "chrome"`，复用本机已安装的 Chrome；CI 中通过 `PW_CHANNEL=chromium` 改用 Playwright 自带浏览器（`pnpm exec playwright install --with-deps chromium`）。`.github/workflows/workbench-ci.yml` 的 `browser` job 会起 MySQL 服务容器、打包并启动后端、灌入演示账号（`scripts/seed-demo-user.sql`）、启动三端 dev server 后跑这套用例，失败时上传 `test-results` 产物。

## 设计文档

- `docs/design-v2.md`、`docs/feature-inventory.md`、`docs/feature-daily-report.md`
- `docs/toolchain.md`（JDK/Maven/pnpm 准备）、`docs/legacy-assets.md`（旧系统可复用资产）
