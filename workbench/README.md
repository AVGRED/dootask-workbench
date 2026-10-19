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
pnpm dev --filter @workbench/staff   # 端口 5180，/api 代理到 127.0.0.1:9000
```

已知限制：登录按旧库规则校验（`md5(明文 + encrypt 盐)`），成功后自动升级为 BCrypt；旧 `users` 表没有 `deleted_at`，查询已做容错。

## 设计文档

- `docs/design-v2.md`、`docs/feature-inventory.md`、`docs/feature-daily-report.md`
- `docs/toolchain.md`（JDK/Maven/pnpm 准备）、`docs/legacy-assets.md`（旧系统可复用资产）
