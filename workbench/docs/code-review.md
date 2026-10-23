# 代码审查报告

> 审查范围：`workbench/server`（9 个 Maven 模块）、`workbench/web`（packages/api + staff/admin/mobile 三个应用）、部署配置（nginx / systemd / 备份脚本）
> 审查时间：2026-10-22
> 审查方式：静态检查（编译 / 单测 / lint / typecheck）+ 逐文件人工阅读关键路径（认证、鉴权、上传、SQL、超管能力）+ 生产环境实测

## 一、总体结论

代码整体质量良好：分层清晰、SQL 全部参数化、无 `v-html`、无前端拼接 SQL、权限校验集中在 `ProjectAccess` / `SystemAdmin` 且服务端兜底。本次审查**修复了 7 个问题**（含 2 个安全相关），剩余 9 项为已知取舍或后续优化，不影响当前使用。

| 检查项 | 结果 |
|---|---|
| 后端编译 | ✅ 通过 |
| 后端单测 | ✅ BUILD SUCCESS（common/auth/user/project/task/planning/worklog/report/app 全部 SUCCESS） |
| 前端 typecheck | ✅ 5 个包全部通过 |
| 前端 lint | ✅ 0 error / 3 warning（均为脚本未使用变量） |
| SQL 注入 | ✅ 未发现拼接用户输入 |
| XSS（v-html） | ✅ 无使用 |
| 硬编码密钥 | ✅ 仅 `JwtService` 的默认占位串，且启动时强制拒绝 |

## 二、本次已修复

| # | 级别 | 问题 | 修复 |
|---|---|---|---|
| 1 | P1 | `springdoc` 依赖存在，`/v3/api-docs`、`/swagger-ui` 在安全白名单里，生产会暴露完整接口文档 | `springdoc.api-docs.enabled` / `swagger-ui.enabled` 改为 `${SPRINGDOC_ENABLED:false}`，默认关闭；已验证 `/v3/api-docs` 现在返回前端首页而非文档 |
| 2 | P1 | 登录失败计数 `ConcurrentHashMap` 无上限，攻击者可用随机用户名把内存打满 | 增加 `LOGIN_MAX_KEYS = 5000` 上限，超过即清空重建 |
| 3 | P1 | 单测 `UserControllerTest` 仍用旧的 `UserView` 7 参数构造，跑测试直接 `NoSuchMethodError` | 测试同步到新签名（含 account / superAdmin），后端单测恢复全绿 |
| 4 | P2 | 新建用户密码下限 6 位，而改密码要求 8 位，前后不一致 | `CreateUserRequest.password` 改为 `@Size(min = 8, max = 64)`，管理端输入框提示「至少 8 位」 |
| 5 | P2 | 超管写操作（写日报 / 加待办 / 重置密码 / 删任务删项目）是「业务写入 + 审计写入」两步，没有事务 | `SuperAdminService` 相关方法加 `@Transactional` |
| 6 | P2 | `eslint.config.mjs` 对 e2e 的 `.mjs` 没配浏览器全局，`pnpm lint` 报 7 个 `no-undef` 错误 | 给 `apps/e2e/**/*.mjs` 配 `languageOptions.globals`，lint 恢复 0 error |
| 7 | P2 | 响应头缺 HSTS / Referrer-Policy / Permissions-Policy，且与宝塔自带的 `X-Frame-Options` 重复 | nginx 增加 HSTS(1 年, includeSubDomains)、Referrer-Policy、Permissions-Policy；移除重复的 X-Frame-Options，保留宝塔统一策略 |

## 三、剩余发现（未改动，供决策）

| # | 级别 | 说明 | 建议 |
|---|---|---|---|
| 1 | P2 | 前端 token 存在 `localStorage`，一旦出现 XSS 即可被读取 | 当前无 `v-html`、无第三方脚本，风险可控；后续可评估 httpOnly Cookie + CSRF token |
| 2 | P2 | `SecurityConfig` 白名单里有 `/projects/**`（员工端 history 路由），若将来在根路径新增同名后端接口会被误放行 | 接口统一走 `/api/v1/**`，或在白名单里改为精确路由 |
| 3 | P2 | `ProjectAccess` 注释写「角色 owner/admin/member/viewer」，但代码里没有「系统管理员自动获得项目权限」的分支 | 目前管理端用独立 SQL 查询全量数据，不需要该分支；建议修注释避免误解 |
| 4 | ~~P2~~ | ~~端到端浏览器测试仍引用 `demo@workbench.local`~~ **已修复**：e2e 全部改为读 `WORKBENCH_STAFF_USERNAME/PASSWORD`、`WORKBENCH_ADMIN_*`、`*_URL`；task-access 改为自建项目，mobile 用例自建项目；浏览器配置排除 api 目录。现网实测 API 18/18、浏览器 8/8 全绿 | — |
| 5 | P3 | 生产环境单机：应用、数据库、备份同盘 | 已做本机（Windows）异地拉取；OSS 异地已就绪待填 AK（见 `docs/security-php2shell.md`） |
| 6 | P3 | 登录限流是单机内存实现，重启即清零，多实例不共享 | 单实例部署下可接受；将来多实例换成 Redis 计数 |
| 7 | P3 | 头像按 `userid-时间戳` 命名且公开可读（`/api/v1/avatars/**` 在白名单） | 文件名不可枚举出他人头像（时间戳未知），风险低；如需严格可改为签名 URL |
| 8 | P3 | 超级管理里「删除任务 / 项目」要手工输入 ID | 可改成从项目总览/任务列表带操作按钮进入，减少误删 |
| 9 | P3 | 未配置 CSP（Element Plus 运行时注入 style，严格 CSP 需要 nonce 改造） | 暂不启用；如需可先上 `Content-Security-Policy-Report-Only` 观察 |

## 四、逐项核对过的安全点

| 检查点 | 结论 |
|---|---|
| JWT 密钥 | 启动时拒绝空 / 默认值 / 少于 32 字符；令牌含 `ver`，与 `users.token_version` 比对 |
| 登出 / 强踢 | `token_version + 1`，旧令牌立即 401（已实测） |
| 越权访问任务 | 所有任务/评论/附件/标签入口先 `requireVisibleTask` 或 `ProjectAccess.requireWrite`，非成员返回「任务不存在」 |
| 附件上传 | 大小 ≤20MB、扩展名黑名单（html/svg/js/sh/exe/jar 等）、MIME 白名单、存储名服务端生成、下载按项目权限校验 |
| 头像上传 | ≤2MB、仅 `image/*`、服务端生成文件名；读取时正则校验 + `normalize()` + `startsWith` 防目录穿越 |
| 超管接口 | `/api/v1/super/**` 全部 `requireSuper`，普通管理员 403（已实测） |
| 管理员重置管理员密码 | 仅超级管理员可（已实测 403） |
| 最后一个管理员保护 | 删除 / 降级最后一个管理员被拒绝 |
| 生产配置 | `JWT_SECRET` 随机 48 位、`-Xmx512m`、仅 80/443 对外、888/8888 仅本机、SSH 仅密钥 |

## 五、复跑方式

```powershell
# 后端
cd workbench\server
mvn -s $env:TEMP\mvn-aliyun.xml test

# 前端
cd workbench\web
pnpm lint
pnpm typecheck
```

> 端到端浏览器用例需先准备测试账号，见「剩余发现 #4」。

---

## 2026-10-23 复查（管理端补齐员工端能力之后）

### 自动化结果

| 检查 | 结果 |
|---|---|
| 后端单测（9 模块） | ✅ BUILD SUCCESS |
| 前端 typecheck（5 包） | ✅ 通过 |
| 前端 lint | ✅ 0 error / 4 warning（脚本未使用变量） |
| API e2e | ✅ 18/18 |
| 浏览器 e2e（员工端/管理端/移动端/星图） | ✅ 8/8 |

### 权限复核（生产实测）

| 场景 | 结果 |
|---|---|
| 员工访问 `/super/audit` | 403 ✅ |
| 普通管理员访问 `/super/audit` | 403 ✅ |
| 员工访问 `/admin/projects` | 403 ✅ |
| 未登录访问 `/team/status` | 401 ✅ |
| 超级管理员访问 `/super/audit` | 0 ✅ |
| 管理员修改他人项目/任务/评论 | 允许（设计要求）✅ |

### 本轮修复

1. `ProjectService.requireOwner` 未走管理员旁路 → 超级管理员删不掉别人的项目，已修。
2. `TaskService.roleIn` 同样缺管理员旁路 → 管理员无法在他人项目里建/改任务，已修。
3. `MemberView` 只有 userid → 成员列表只显示数字，已补 `nickname`/`account`。
4. `ProjectControllerTest` 仍用 3 参 `MemberView`，单测编译失败，已同步。
5. 列表空态在看板/甘特 tab 下仍显示，已按 tab 收窄。

### 仍存的问题（不影响使用）

| 级别 | 项 | 说明 |
|---|---|---|
| P3 | 员工也能 `GET /api/v1/users` | 通讯录性质，含邮箱；若需要收紧可改为只返回 id+昵称 |
| P3 | 管理员权限是"全项目 owner" | 符合当前需求；如需审计可依赖 super_audit_logs |
| P3 | e2e 用真实账号跑 | 建议 CI 用 seed 账号，避免污染生产 |
