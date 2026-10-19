# 旧系统可复用资产清单

重写时先翻这份清单，规则照搬、实现重写。路径均相对 `dootask/`。

## 1. 数据模型（直接沿用）

| 资产 | 位置 | 说明 |
|---|---|---|
| 全部表结构 | `database/migrations/` | 作为 `V1__baseline.sql` 来源，只加不改 |
| 软删与审计字段 | 各表 `deleted_at/created_at/updated_at` | 新系统统一继承 `BaseEntity` |
| 关键索引 | migrations | 任务按项目/负责人、工时按用户+日期、日报按用户+日期 |

## 2. 业务规则（照搬语义）

| 域 | 规则要点 | 旧实现 |
|---|---|---|
| 依赖 | FS 类型、BFS 成环检测、风险汇总口径、甘特查询分块 | `app/Module/ProjectTaskDependencyService.php` |
| 里程碑 | 目标日/提醒天数、取消关联批量解绑并留痕、提醒重试与幂等键 | `ProjectMilestoneService.php` |
| 自动排期 | 预演不改库、应用走事务与幂等批次、撤销按批次回滚、工作日/节假日/时区/缓冲天数 | `ProjectTaskScheduleService.php` |
| 保存视图 | 个人/共享、定义归一化、失效里程碑自动清理 | `ProjectTaskViewService.php` |
| 工时 | 可见性（成员可读、仅本人/负责人可删）、`request_id` 幂等、容量 93 天上限 | `ProjectTaskWorkService.php` |
| 任务 | 子任务深度上限、复制/移动/交接、归档与回收站 | `app/Models/ProjectTask.php`、`ProjectTaskHandoffService.php` |
| 权限 | 项目级角色（负责人/管理员/成员/只读）、部门负责人只读视角 | `ProjectController` |
| 消息 | 投递幂等（键 + 载荷哈希 + 重放匹配） | `app/Models/WebSocketDialogMsg.php` |

## 3. 行为契约（测试）

- `tests/Feature/` 共 316 条用例，覆盖依赖、里程碑、排期、工时、视图、模板、深树、提醒、幂等；
- 迁移方式：一条 PHP 用例 → 一个 Spring `@SpringBootTest`，保持断言语义（尤其错误码与拒绝条件）；
- 测试基座可参考 `tests/Feature/Concerns/ProjectPlanningFixture.php`，新系统需要等价的 fixture。

## 4. 前端可复用原型

| 组件 | 文件 | 复用点 |
|---|---|---|
| 里程碑管理 | `MilestoneManager.vue` | 表单字段、排序交互、任务展开 |
| 提醒记录 | `MilestoneReminderAttempts.vue` | 分页、失败重试、请求序号保护 |
| 工时面板 | `TaskWork.vue` | 预估/登记/删除、只读态、幂等请求 id |
| 容量汇总 | `ProjectCapacityOverview.vue` | 区间选择、93 天校验、CSV 导出 |
| 项目设置 | `ProjectSettingDrawer.vue` | 排期字段与归一化 |
| 邀请/移交 | `ProjectInviteDialog.vue`、`ProjectTransferDialog.vue` | 轻量流程 |
| 样式 | `resources/assets/scss/` | 布局与间距可直接参考 |

## 5. 工具与校验（可直接移植）

| 资产 | 位置 | 用途 |
|---|---|---|
| CSV 转义 | `resources/assets/js/utils/capacityCsv.js` | 公式注入、引号、BOM、CRLF |
| 排期解析 | `resources/assets/js/utils/projectSchedule.js` | 工作日/节假日归一化 |
| 工时格式化 | `resources/assets/js/utils/workMinutes.js` | 分钟↔小时、求和 |
| 表格分组/筛选 | `utils/projectTableGroups.js`、`projectFlowFilter.js` | 分组与筛选语义 |
| 模板连线检查 | `scripts/check-vue-wiring.mjs` | 拆分后悬空引用检测，移植到新前端 |
| 挂载冒烟 | `tests/component-mount.test.mjs` + `tests/helpers/jsdom-setup.mjs` | Vue 组件运行时校验思路 |

## 6. 已知缺陷（必须在新系统避免）

1. 工时查询了不存在的列（`users.name/avatar` vs `nickname/userimg`）；
2. 容量汇总 `chunkById` 列名与 select 别名不一致；
3. `AbstractModel::updateInstance` 用 `$this->$k` 触发 `HasAttributes::$changes` 保护属性，导致排期批次插入缺列；
4. 公开系统设置接口缺键时 500（需默认值兜底）；
5. 旧子任务树深 >5 / 后代 >50 时复制/移动失败；
6. WebSocket 消息投递无 Swoole worker 时失败，需要幂等重试兜底。

## 7. 操作与运维知识

- 本地测试隔离脚本 `scripts/release-run-unit-tests-isolated.sh`（临时 MariaDB/Redis + FFI 桩）；
- 备份脚本 `scripts/backup-local-bundle.sh`（bundle + 云同步副本）；
- CI 差异说明 `docs/local-testing.md`（CI 用真实 doo.so 与完整 seeder）。

## 8. 明确丢弃

动态路由、LaravelS/Swoole 常驻写法、iView 组件、巨型 Controller、Vue 2 的 `$set`/事件总线、`AbstractModel` 这类魔术基类。
