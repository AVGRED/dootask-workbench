# 2026-10-19 工作台工程收口记录

本记录只覆盖本轮新增的自动化测试与校验结果，**不改变** [发布验收清单](../release-acceptance-checklist.md) 中六个外部门槛的状态（真实数据库副本、干净 Linux 主机、授权 WebSocket、双实例共享锁、多端实机、性能基线仍未验收）。

## 环境

- 工作区基线：`6f111cd97`（`pro`，v1.9.66）；其后为工作台的未提交改动
- WSL：Ubuntu 22.04，PHP 8.4.26，PHPUnit 11.5.55，MariaDB 10.6（隔离临时实例，每次运行后销毁）
- Windows：Node.js 24.15.0，npm 11.12.1

## 新增的自动化资产

| 资产 | 作用 |
|---|---|
| `scripts/check-ai-kb.mjs`（`npm run check:ai-kb`） | 按 `_schema/frontmatter.md` 的规则校验 462 个 chunk：frontmatter 严格 YAML、必填字段、枚举、id 唯一与 id/feature/type 一致性、feature 与 tool 词表、正文长度、`[[id]]` 交叉引用、禁用指代词；不引入新的 npm 依赖 |
| `.github/workflows/tests.yml` | 静态检查新增 AI 知识库校验步骤 |
| `tests/Feature/Concerns/ProjectPlanningFixture.php` | 规划功能测试基座：用户/项目/任务/里程碑构造、`DooSo` 授权 mock、`RequestContext` 隔离、私有任务与可见成员辅助方法 |
| `tests/Feature/ProjectTaskDependencyFeatureTest.php` | 14 项：方向语义、自依赖、跨项目、子任务、重复边幂等、环路、反向边、开关关闭、非成员、私有任务不泄露、IDOR 删除 |
| `tests/Feature/ProjectTaskWorkFeatureTest.php` | 18 项：估时边界、工时校验、请求编号幂等与冲突、删除墓碑、删除权限、容量 93 天上限、工作日/节假日口径、离职成员容量 |
| `tests/Feature/ProjectMilestoneFeatureTest.php` | 20 项：名称/说明/状态/日期/提醒校验、负责人成员校验、重复保存不重置提醒周期、排序完整集合、任务关联与解除、删除解链、成员权限、统计与可见性、提醒记录只读 |
| `tests/Feature/ProjectTaskViewFeatureTest.php` | 16 项：个人/共享可见性、共享视图维护权限、定义归一化、日期区间回退、里程碑归属、默认视图、非成员拒绝 |
| `tests/Feature/ProjectTaskScheduleFeatureTest.php` | 17 项：规则默认值与校验、依赖/自动模式前置条件、停用完成起点、预演级联与缓冲工作日、完成项跳过、摘要过期、幂等 apply、undo 与人工改期冲突、批次清单 |
| `scripts/release-run-unit-tests-isolated.sh --filter` | 支持按测试类过滤，便于隔离库中单类迭代 |

## 测试结果

| 套件 | 结果 |
|---|---|
| `Unit`（隔离 MariaDB） | **51 tests / 120 assertions PASS** |
| 新增 5 个 Feature 测试类 | **85 tests / 165 assertions PASS** |
| 既有 37 项 Feature（任务交接、跨项目模板） | PASS（未受本轮改动影响） |
| 完整 `Feature` 套件（273 项） | 48 项失败均为**既有环境依赖**：缺少 `APP_KEY`、Redis 未启动、WebDAV 授权缺失；新增类无一失败 |

## 本轮发现并修复的缺陷

1. **工时接口查询了不存在的列**（`app/Module/ProjectTaskWorkService.php`）：`users` 表只有 `nickname` / `userimg`，代码却 `select userid, name, avatar`，导致登记工时、工时历史、成员容量汇总全部 500。改为查询真实列，并在返回结构中显式映射为前端使用的 `name` / `avatar`。
2. **容量汇总的分块查询列名不匹配**（同一文件）：`chunkById(..., 'project_tasks.id')` 与 `select project_tasks.id` 的结果列名不一致，任何存在任务的项目都会抛 `RuntimeException`。改为 `'id'`。
3. **排期批次的 `changes` 永远写不进数据库**（`app/Models/AbstractModel.php`）：`updateInstance()` 用 `$this->$k = $v` 赋值，键名 `changes` 正好命中 Eloquent `HasAttributes::$changes` 保护属性，写入落到属性而非模型字段，插入语句缺少 NOT NULL 列 `changes`（SQL 1364），自动排期的 apply / undo 完全不可用。改为通过 `setAttribute()` 写入，并对已声明 cast 的数组属性跳过预编码，避免二次编码。

## 静态检查

| 检查 | 结果 |
|---|---|
| `composer stan`（PHP 8.4，`app/` 全量） | 0 错误 |
| `npm run lint` | 0 errors / 97 warnings（既有基线） |
| `npm run check:lang` | 阻断错误 0，迁移待办 0 |
| `npm run check:api-lang` | 1,136 条 × 10 种语言一致 |
| `npm run api:map:check` | 24 控制器 / 363 方法，映射最新 |
| `npm run check:ai-kb` | 462 chunks 通过；2 条存量正文超 1500 字（仅告警） |
| `node --test` 三个前端用例 | 28/28 通过；深链目录 21 个 ID 一致 |

## 范围限制

- 未在真实脱敏数据库、干净 Linux 主机、双实例、桌面/窄屏/Electron 上验收。
- 完整 Feature 套件的 48 项环境性失败未在本轮处理（缺少测试用 `APP_KEY`、Redis 与 WebDAV 授权）。
- 知识库正文长度上限（1500 字）与 2 个存量 chunk 存在偏差，校验器按告警处理，未改写存量内容。
