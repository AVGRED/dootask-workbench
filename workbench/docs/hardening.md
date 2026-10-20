# 审查修复记录（2026-10-21）

本轮针对整体审查发现的问题做修复，按严重程度排列。

## P0 安全

### 1. 仓库内不再出现真实管理员密码

**问题**：本机演示 admin 口令，同时被写进 5 个文件（E2E 脚本、CI 种子 SQL、文档），拿到仓库即等于拿到后台口令。

**修复**：

| 文件 | 处理 |
|---|---|
| `scripts/seed-ci-users.sql` | 改用 CI 专用密码的 md5（`Ci-Only-9f3kQ2!x`），注释不再写明文 |
| `.github/workflows/workbench-ci.yml` | `WORKBENCH_ADMIN_PASSWORD` 改为该 CI 专用值 |
| `apps/e2e/screenshot-*.mjs`、`capture-showcase*.mjs` | 改为读 `process.env.WORKBENCH_ADMIN_PASSWORD` |
| `docs/ui-upgrade.md` | 密码替换为 `<CI 专用密码>` 占位符 |

验证：`git grep -I "<旧的 admin 口令>" -- workbench` 返回 0 条。

> 注意：本机运行截图脚本前需先 `$env:WORKBENCH_ADMIN_PASSWORD='...'`。

## P0 性能悬崖

### 2. 星图边的 O(n²) 爆炸

**问题**：`StarMapService` 对每个项目内成员两两配对，节点上限 500 但边没有上限。500 人一个项目 ≈ 12.5 万条边（几十 MB JSON），前端 SVG 必然卡死。

**修复**：

- 单项目参与配对的成员上限 `MAX_MEMBERS_PER_PROJECT = 60`
- 返回边数上限 `MAX_EDGES = 2000`，超出即停止
- 响应新增 `truncated` 标记；前端在右下角显示「关系过多，仅展示前 2000 条连线」

验证：`/api/v1/starmap` 返回 `truncated=false`（当前 3 人）。

## P1 工程质量

### 3. 路由懒加载 + vendor 拆包

- 三端 router 全部改为 `component: () => import("...")`
- `vite.config.ts` 增加 `manualChunks`：`vendor-vue` / `vendor-element`

构建结果（staff）：

| chunk | 大小 |
|---|---|
| `vendor-element` | 918 KB |
| `vendor-vue` | 111 KB |
| 页面 chunk | 4–17 KB each |

首屏仍需加载 Element Plus 全量（918KB / gzip ~300KB）。**后续可做**：用 `unplugin-vue-components` 按需引入，预计再降 60%。

### 4. 死代码清理

- 删除 `apps/staff/src/views/HomeView.vue`（0 引用）

## 第二批（P1 第二组）

### 5. ESLint 门禁 + Prettier 配置

- 新增 eslint.config.mjs（flat config：js + typescript-eslint + eslint-plugin-vue）
- pnpm lint / lint:fix / ormat 脚本；.prettierrc.json
- CI web job 增加 pnpm lint 步骤
- 结果：0 error（3 个 e2e 脚本的 unused 警告）

### 6. Element Plus 按需引入

- unplugin-vue-components + unplugin-element-plus，移除 pp.use(ElementPlus)
- 语言包改由 el-config-provider 提供（中文化保持）
- 构建对比（staff）：入口 chunk 1037KB → 108KB，总 JS 约 2MB → 722KB；页面按路由懒加载

## 第三批（剩余审查项）

### 7. 任务存在性泄露

原先「任务不存在」与「无权访问该项目」返回不同错误，可据此探测任务是否存在。

- **TaskService**：新增 `requireVisibleTask` / `requireVisibleWriteTask`，读写都先做可见性判定，缺失或无权限统一 `404 任务不存在`；`delete` 的子任务检查移到权限判定之后，避免泄露子任务数量。
- **CommentService**：新增 `requireVisibleProjectId`，评论读写同样统一 404。
- **AttachmentService / TagService**：可见性检查补 `roleOf` 判定，统一 404。
- **回归测试**：`tests/api/task-access.spec.ts` 断言局外人对「存在但无权」与「不存在」的任务拿到完全一致的 code 与 message（25 → 26 项）。

## 未处理（明确留待后续）

| 项 | 原因 |
|---|---|
| ESLint / Prettier 门禁 | 需要新增依赖并配置规则，本轮未做，仅靠 `vue-tsc` |
| 星图浏览器测试 | 只有 API 断言，节点/连线渲染无回归保护 |
| ~~任务存在性泄露~~ | 已修复：TaskService / CommentService / AttachmentService / TagService 统一返回 404「任务不存在」 |
| 暗色对比度工具化 | 仍为人工检查 |
| CI 浏览器 job 实跑 | 结构已修，需真实 Actions 跑一轮 |
