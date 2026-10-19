# 工作台重写方案（Vue 3 + Spring Boot 3）

> 决策已定：Element Plus、MySQL 8.0、模块化单体（预留拆分）、垂直切片起步、沿用现有表结构。

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 前端 | Vue 3.4 + TypeScript + Vite 5 + Pinia + Vue Router 4 + **Element Plus** |
| 请求 | VueRequest v2（Vue 3 版）；需要缓存/失效时改 `@tanstack/vue-query` |
| 后端 | Spring Boot 3.2 + JDK 21 + **MyBatis-Plus 3.5** + Flyway |
| 数据库 | **MySQL 8.0**（沿用现有表结构，增量字段走 Flyway） |
| 缓存/任务 | Redis 7 + Spring Cache + `@Async` |
| 鉴权 | Spring Security + JWT |
| 文档 | springdoc-openapi → 生成前端 TS 类型 |
| 实时 | 一期降级轮询，二期 WebSocket |

## 2. 后端模块（模块化单体）

```
com.workbench
├── common/     返回体、异常、分页、审计
├── auth/       登录、JWT、RBAC + 项目级角色
├── user/       用户、部门
├── project/    项目、成员、设置、里程碑
├── task/       任务、子任务、标签、看板
├── planning/   依赖、自动排期、模板
├── worklog/    工时、容量
├── notify/     消息、提醒（可独立服务）
└── report/     甘特、统计（可独立服务）
```

拆分点：`notify`、`file`、`report`。模块间只经 Service 接口调用，不跨模块访问 Mapper。

## 3. 数据与契约

- 直接复用 `dootask` 现有表；Flyway `V1__baseline.sql` 只建表，不改历史结构；
- API 用 REST，统一返回 `{code, message, data, traceId}`；
- 错误码集中定义，禁止散落字符串；
- OpenAPI 为唯一契约源，前端类型自动生成。

## 4. 垂直切片（第一版，3–4 天）

**范围**：登录 + 项目列表 + 里程碑 CRUD/排序。

交付物：

1. `workbench-server`：多模块骨架、Flyway baseline、JWT 登录、`/api/auth/login`、`/api/projects`、`/api/projects/{id}/milestones`（GET/POST/PUT/DELETE/sort）；
2. `workbench-web`：Vite + Vue3 + TS + Element Plus + Pinia；登录页、项目页、里程碑弹窗（列表/新增/编辑/删除/排序）；
3. 测试：后端 MockMvc 覆盖登录与里程碑五个端点；前端一个里程碑组件的挂载测试；
4. CI：后端 `mvn verify`，前端 `typecheck + vitest`。

验收标准：

- 用现有 MySQL 数据能登录并看到真实项目；
- 里程碑增删改排序生效且刷新后保持；
- 无 Swoole/无 PHP 依赖，`mvn spring-boot:run` + `npm run dev` 即可运行。

## 5. 后续阶段

| 阶段 | 内容 | 工期 |
|---|---|---|
| P1 | 用户/项目/成员/权限 | 1.5 周 |
| P2 | 任务、看板、标签、子任务 | 2 周 |
| P3 | 依赖、自动排期（preview/apply/undo）、模板 | 3 周 |
| P4 | 工时、容量、视图、甘特 | 2 周 |
| P5 | 通知、提醒、附件 | 1.5 周 |
| P6 | 数据迁移对账、灰度切流 | 2 周 |

## 6. 迁移策略

- **行为基线**：现有 `tests/Feature` 316 条用例是行为契约，逐条翻译为 Spring 集成测试，重点覆盖排期、依赖成环、里程碑取消关联、工时可见性；
- **只读期**：新后端先接同一库只读运行，与旧系统比对同一查询结果；
- **灰度**：新旧前端共存（`/v2` 前缀），按项目放量；
- **回滚**：旧系统只读保留一个发布周期。

## 7. 风险

| 风险 | 应对 |
|---|---|
| 行为细节丢失 | 以旧测试为验收基线，逐条对照 |
| 模块间耦合 | 只经 Service 接口调用，禁止跨模块 Mapper |
| 实时消息缺失 | 一期轮询，二期 WebSocket |
| 数据迁移出错 | 双读对账 + 可回滚脚本 |

## 8. 多端形态（已定）

| 决策 | 结果 |
|---|---|
| 端 | **员工端 + 管理端 + 移动端（H5/PWA）** |
| 登录 | 统一账号，登录后按角色进入对应端 |
| 交付 | **两端同步推进**（同一阶段内员工端与管理端一起交付） |
| 旧系统 | **并行运行 + 灰度切流**，旧系统只读保留一个发布周期 |

### 工程结构

```
workbench/
├── packages/
│   ├── ui/      业务组件（里程碑、工时、容量…）
│   ├── api/     OpenAPI 生成的客户端与类型
│   ├── auth/    token、权限指令、路由守卫
│   └── mock/    演示数据
├── apps/
│   ├── staff/   员工端
│   ├── admin/   管理端
│   └── mobile/  移动端（PWA，复用 staff 的业务组件）
└── server/      Spring Boot 模块化单体
```

- 权限在后端收口：`/api/staff/**` 与 `/api/admin/**` 分命名空间，RBAC + 数据范围（本人 / 项目成员 / 部门 / 全部）；
- 移动端复用 `packages/ui`，仅布局与交互适配，不复制业务逻辑；
- 每个阶段验收包含三端页面（移动端可后置到阶段末）。

### 调整后的阶段

| 阶段 | 内容 | 工期 |
|---|---|---|
| P0 | monorepo 骨架、OpenAPI 生成、登录 + 权限、CI（三端可启动） | 1.5 周 |
| P1 | 用户/部门/项目/成员：管理端 CRUD + 员工端查看 | 2 周 |
| P2 | 任务、子任务、看板、标签（两端） | 2.5 周 |
| P3 | 依赖、里程碑、自动排期、模板（两端） | 3.5 周 |
| P4 | 工时、容量、视图、甘特 | 2.5 周 |
| P5 | 消息、提醒、附件、审计日志 | 2 周 |
| P6 | 移动端适配、数据对账、灰度切流 | 2 周 |

合计约 **16 周**（含缓冲）。若移动端只做只读视图，可压到 14 周。

## 9. 待补信息

- 团队人数与可并行度（决定排期能否压缩）；
- 是否需要保留旧系统并行运行、以及切换时间点；
- 部署目标（单机 / K8s / 云托管）。
