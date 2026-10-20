# 工作台 v2 · UI 升级说明

> 范围：`workbench/web`（员工端 staff / 管理端 admin / 移动端 mobile）。
> 本次升级只换视觉与交互层，不改后端接口与权限模型。

## 1. 结论先说

| 项目 | 结果 |
|---|---|
| 品牌色 | `#409eff` → **靛蓝 `#4f46e5`** |
| 主题 | 亮色 + **暗色**（右上角切换，localStorage 记忆，首次跟随系统） |
| 组件库 | **仍用 Element Plus**（未替换），通过 CSS 变量整体换肤 |
| 页面覆盖 | 员工端 9 页 + 管理端 7 页 + 移动端 7 页，全部接入设计令牌 |
| 硬编码颜色 | `.vue`/`.css` 中仅剩 bento 板块调色板（设计数据）与品牌底白字 |
| 新增公共组件 | `AppHeader`、`EmptyState`、`Skeleton` |

## 2. 设计令牌

位置：`web/apps/{staff,admin,mobile}/src/styles/tokens.css`（三份保持一致）。

### 颜色

```css
--wb-brand-50 … --wb-brand-800   /* 品牌色阶，600 = #4f46e5 */
--wb-bg / --wb-surface / --wb-surface-2      /* 页面底 / 卡片 / 次级面 */
--wb-border / --wb-border-strong             /* 描边两级 */
--wb-text / --wb-text-2 / --wb-text-3        /* 文字三级 */
--wb-success / --wb-warning / --wb-danger    /* 语义色 */
```

暗色通过 `html.dark { … }` 覆盖同名变量，组件无需改代码。Element Plus 的 `--el-*` 变量在令牌文件里被映射到 `--wb-*`，所以按钮、输入框、表格等自动跟随主题。

### 其他

| 类别 | 令牌 |
|---|---|
| 圆角 | `--wb-radius-sm` 8 / `--wb-radius` 12 / `--wb-radius-lg` 16 |
| 阴影 | `--wb-shadow-1`（卡片）/ `--wb-shadow-2`（悬浮/弹层） |
| 字体 | `--wb-font`、`--wb-font-mono` |
| 动效 | `--wb-duration` 160ms、`--wb-ease` cubic-bezier(.2,0,.2,1) |

### 使用约定

- 新页面只用 `var(--wb-*)`，不要再写十六进制色值（品牌底上的白字除外）。
- 卡片 = `background: var(--wb-surface); border: 1px solid var(--wb-border); border-radius: var(--wb-radius-lg); box-shadow: var(--wb-shadow-1);`
- 卡片 hover = `box-shadow: var(--wb-shadow-2); transform: translateY(-2px);`
- 表格加 `class="task-table"`，指标卡用 `.metrics > .metric`，区块标题用 `.section-title`。

## 3. 公共组件

| 组件 | 路径 | 用途 |
|---|---|---|
| `AppHeader` | `apps/staff/src/components/layout/AppHeader.vue` | 员工端统一顶栏：品牌 + 首页/项目/日报（按路由高亮）+ 状态胶囊 + 主题 + 用户菜单 |
| `EmptyState` | `apps/{staff,admin,mobile}/src/components/common/EmptyState.vue` | 空状态：图标 + 标题 + 描述 + 可选主操作 |
| `Skeleton` | 同上 | 骨架屏：`variant="lines" \| "table" \| "grid"` |

管理端没有用 `AppHeader`，用布局内的分段 tab（状态墙 / 项目总览 / 每日日报），视觉语言与 `AppHeader` 一致（64px、毛玻璃、胶囊选中态）。

## 4. 逐页改动

### 员工端

| 页面 | 改动 |
|---|---|
| 登录 | 左右分栏：左侧靛蓝渐变品牌区 + 三条能力说明，右侧表单 + 明暗切换 |
| 首页（Bento） | 问候条（按小时 + 今日待办/项目/工时摘要）、骨架屏、板块标题圆点、hover 上浮、移除按钮 hover 才显示、布局操作下移为工具条 |
| 项目列表 | 统一顶栏，卡片网格 + 更多菜单新增「报表」入口 |
| 任务 | 工具栏卡片、表格主题化、优先级彩色徽标（P0 红 / P1 橙 / P2 靛蓝） |
| 看板 | 列头圆点 + 数量徽标、卡片描述/P 徽标/负责人、拖拽半透明 + 目标列高亮、空列虚线框 |
| 甘特 | panel 卡片 + 图例 + 今日红线 + 斑马行 + 渐变任务条，宽度撑满 |
| 规划 | 四项风险指标卡、表格主题化、中文空状态 |
| 项目报表 | 五个指标卡、每日工时图（带日期轴）、成员工时表；**补上此前缺失的路由** |
| 日报 | 双栏：左侧写作（字数统计 + 快捷工时），右侧近 7 天条 + 近期提交 + 周报 |

### 管理端

| 页面 | 改动 |
|---|---|
| 登录 | 深色品牌区分栏，与员工端登录一致 |
| 状态墙 | 状态筛选胶囊（带人数）、在线状态点、在做/日报/近 7 天工时进度条、卡片 hover |
| 项目总览 | 三张统计卡（总数 / 平均完成度 / 存在逾期）、卡片令牌化 + hover、进度条 |
| 每日日报 | 已提交 / 未提交 / 提交率统计卡、日报卡（头像 + 工时徽标 + 分段内容）、空状态、未交名单 chips |
| 用户管理 | page-bar 标题栏、工具栏卡片、表格主题化 |
| 系统设置 | page-bar + panel 卡片 |

### 移动端

顶栏毛玻璃 + 品牌方块；底部 tab 胶囊选中态（暗色下为半透明品牌色）；卡片统一圆角/描边/阴影；输入框聚焦光晕；「我的」页新增「外观」卡片切换明暗。

## 5. 截图清单

目录：`workbench/screenshots/`

| 截图 | 说明 |
|---|---|
| `ui-dashboard-light.png` / `ui-dashboard-dark.png` | 员工端首页 明/暗 |
| `ui-projects-light.png` | 项目列表 |
| `ui-tasks.png` | 任务列表 |
| `ui-board.png` | 看板 |
| `ui-gantt.png` | 甘特 |
| `ui-planning.png` | 规划 |
| `ui-project-report.png` | 项目报表 |
| `ui-report.png` | 我的日报 |
| `ui-login-light.png` / `ui-login-dark.png` | 员工端登录 |
| `ui-admin-light.png` / `ui-admin-dark.png` | 状态墙 明/暗 |
| `ui-admin-projects.png` | 项目总览 |
| `ui-admin-reports.png` | 每日日报 |
| `ui-admin-users.png` | 用户管理 |
| `ui-admin-settings.png` | 系统设置 |
| `ui-admin-login-light.png` / `ui-admin-login-dark.png` | 管理端登录 |
| `mobile-{home,projects,report,profile}-{light,dark}.png` | 移动端 4 页 × 明暗 |

重新生成（需要后端 9020 与三端 dev server 在跑）：

```powershell
$env:WORKBENCH_DEMO_PASSWORD='<demo 密码>'
cd workbench/web/apps/e2e
node screenshot-theme.mjs          # 员工端 + 管理端 明暗
node screenshot-pages.mjs          # 任务 / 看板
node screenshot-staff-pages.mjs    # 甘特 / 规划 / 报表
node screenshot-report.mjs         # 我的日报
node screenshot-login.mjs          # 员工端登录
node screenshot-admin-pages.mjs    # 管理端四个页面
node screenshot-admin-login.mjs    # 管理端登录
node screenshot-mobile-all.mjs     # 移动端 4 页 × 明暗
```

## 6. 验收命令

```powershell
cd workbench/web
pnpm typecheck                      # 5 个包
pnpm --filter @workbench/e2e test:api   # 14 项 API 测试

cd ../..
$env:WORKBENCH_DEMO_PASSWORD='<demo 密码>'
powershell -File workbench/scripts/e2e-smoke.ps1 -BaseUrl http://127.0.0.1:9020   # 191 项
```

## 7. 已知例外与后续

| 项 | 说明 |
|---|---|
| Bento 板块调色板 | `composables/bentoGrid.ts` 里 16 个十六进制值是有意保留的板块配色；暗色下由 CSS 覆盖为中性卡片 |
| 暗色对比度 | 未用工具做 WCAG 全量比对，仅人工检查主要页面 |
| 视觉回归 | 目前是脚本截图人工比对，没有像素级 diff，改样式时需重跑截图 |
| 移动端表格页 | 移动端没有表格类页面，`task-table` 样式仅在桌面端生效 |
| 组件库边界 | 仍依赖 Element Plus；若后续要换库，先迁移 `AppHeader`/`EmptyState`/`Skeleton` 这些自研组件之外的页面样式 |
