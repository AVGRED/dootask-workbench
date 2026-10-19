# 项目规划与执行 API

本文覆盖任务依赖、里程碑、已保存任务视图、模板历史、自动排期和工时容量接口。接口统一使用登录态和 DooTask API 响应封装；成功数据位于 `data` 字段。具体参数校验和错误文案以对应控制器及服务实现为准。

## 任务依赖

依赖是同项目主任务之间的 FS（前置完成后后置才能开始）关系。当前不支持跨项目依赖或对子任务设置依赖。项目关闭依赖时，读取返回空列表，写操作会拒绝。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `api/projecttaskdependency/lists` | 按 `task_id` 读取当前任务的前置和后置依赖；只返回当前用户可见的任务摘要。 |
| GET | `api/projecttaskdependency/project` | 按 `project_id` 和甘特图 `task_ids` 批量读取可见依赖边；甘特图任务 ID 最多 5,000 个。 |
| GET | `api/projecttaskdependency/risks` | 汇总当前用户可见的未完成后续任务排期冲突，以及未完成且计划结束时间已过的前置阻塞任务；最多返回50条风险边详情，计数覆盖全部可见依赖。 |
| POST | `api/projecttaskdependency/add` | 传 `task_id`、`related_task_id` 和 `direction`（`predecessor` 或 `successor`）添加关系；服务端校验项目归属、任务权限和循环。 |
| POST | `api/projecttaskdependency/delete` | 传 `task_id`、`dependency_id` 删除关系；调用者须对关系两端均有编辑权限。 |

关系修改会记录任务动态，并在事务提交后推送关系更新。通知只写入关系两端各自的任务讨论，不向整个项目群披露任务信息。

## 项目里程碑

里程碑写操作要求项目负责人或管理员权限；任务关联还要求当前用户可编辑该任务。`target_at` 使用 `YYYY-MM-DD` 日期，不含时间。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `api/projectmilestone/lists` | 获取项目里程碑、可见任务统计、状态分布及 `can_manage`；负责人/管理员还可查看提醒尝试次数、最近尝试时间/失败类别和发送成功时间。 |
| POST | `api/projectmilestone/reminderattempts` | 按 `project_id`、`milestone_id` 分页查询最近的提醒尝试；负责人/管理员可查看，最多保留每个里程碑最新 100 条。 |
| POST | `api/projectmilestone/tasks` | 按 `project_id`、`milestone_id` 分页读取可见且未归档任务；默认每页 30 条，最大 100 条。 |
| POST | `api/projectmilestone/save` | 创建或更新名称、说明、负责人、目标日期、状态和提醒提前天数；提醒值支持 `-1`（关闭）、`0`、`1`、`3`、`7`。 |
| POST | `api/projectmilestone/delete` | 删除里程碑并解除任务关联。 |
| POST | `api/projectmilestone/sort` | 使用完整的里程碑 ID 列表更新排序。 |
| POST | `api/projectmilestone/assign` | 将单个任务关联到里程碑；`milestone_id=0` 表示解除关联。 |

提醒按项目排期时区于当地 09:00 后发送；失败会在目标日期当天重试，持久幂等键避免重复消息。同一键重放必须匹配原会话、消息类型、发送人和消息内容；匹配后会静默重投 WebSocket 更新，不重复触发机器人或 App 推送；投递队列拒绝任务会记为失败并重试；其他唯一约束冲突不会被误当成幂等成功。提醒是弱提醒，不阻止项目操作。
项目负责人和管理员可通过里程碑列表查询当前提醒周期的尝试次数、最近尝试时间、失败类别和成功发送时间，也可分页查询每个里程碑最近 100 条发送尝试；普通成员只获取里程碑业务数据，不返回提醒运维状态。修改目标日期、提醒提前天数或重新开启里程碑会清空该周期状态。
里程碑新增/修改的动态包含名称、日期、状态、提醒设置及负责人前后快照；重复保存相同内容不会更新时间、写入活动或广播项目更新。

## 已保存任务视图

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `api/projecttaskview/lists` | 获取当前用户可见的个人视图、共享视图和默认视图信息。 |
| POST | `api/projecttaskview/save` | 使用 `project_id`、可选 `view_id`、`name`、`visibility`（`personal`/`project`）和 `definition` 创建或更新视图。 |
| POST | `api/projecttaskview/delete` | 删除当前用户有权管理的视图。 |
| POST | `api/projecttaskview/setdefault` | 将指定视图设为当前用户在该项目的默认视图。 |

共享视图的创建和修改仅限项目负责人或管理员；个人视图仅所有者可修改。视图定义中的里程碑 ID 必须属于当前项目。

## 任务模板历史

模板主体的列表、创建和更新沿用项目任务模板接口；以下两个接口提供不可变历史版本的读取与恢复。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `api/projecttasktemplate/revisions` | 按 `project_id`、`template_id` 分页读取当前模板和历史快照；默认每页 20 条，最大 50 条。 |
| POST | `api/projecttasktemplate/restore` | 传 `project_id`、`template_id`、`version` 恢复快照；恢复会写入一个新的模板版本，不覆盖旧历史。 |

读取和恢复模板历史仅限项目负责人或管理员；快照所引用的里程碑必须仍存在于当前项目且名称无歧义。

## 自动排期与批次

自动排期预演、批次读写和规则修改仅允许项目负责人或管理员执行；排期规则读取允许项目成员。项目须开启任务依赖并选择自动模式。请求中的计划时间格式为 `YYYY-MM-DD HH:mm:ss`，按项目排期时区解释；夏令时切换导致不存在的本地时间会被拒绝，避免静默改变任务时长。
专用排期规则更新接口在规则实际改变并提交后发布项目更新；重复提交未改变规则时不额外广播。

甘特图旁的项目设置表单通过 `api/project/update` 与其他项目设置一起提交规则；该入口与专用接口共享工作日、假期、时区、缓冲值校验，排期规则动态会保存完整的前后配置快照，并以活动面板支持的变更格式显示摘要。旧客户端未提交 `task_schedule_holidays` 时会保留现有假期配置；只提交 mode、工作日、时区或缓冲中的一部分时，未提交的字段沿用当前存储值，仍只校验实际提交的字段。整个项目表单无实际字段变化时不会更新时间或广播项目更新。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `api/projecttaskschedule/settings` | 读取排期模式、工作日、节假日例外、时区、缓冲工作日及规则版本。 |
| POST | `api/projecttaskschedule/updatesettings` | 更新项目排期规则；工作日为 ISO 星期一至星期日编号，可设置最多366个 `YYYY-MM-DD` 节假日例外，缓冲范围 0–30 个工作日。 |
| GET | `api/projecttaskschedule/batches` | 查询最近 20 个批次的状态和审计摘要。 |
| POST | `api/projecttaskschedule/preview` | 传 `project_id`、主任务 `task_id`、新 `start_at` 和 `end_at`；返回 `preview_hash`、变更前后值、跳过原因和冲突数。摘要覆盖排期规则、权限设置、活动任务的层级/排期/周期字段、负责人/协助人分配、用于权限继承的归档祖先关系、依赖图，以及包含展开子任务的完整预演变更和跳过结果；其中人员或权限变化及子任务预演结果变化会使旧预演失效。 |
| POST | `api/projecttaskschedule/apply` | 提交上述锚点日期、预演返回的 `preview_hash` 和 `request_id`。重试必须复用相同请求编号及完整参数（项目、锚点任务、起止时间和预演摘要）；同一编号绑定不同参数会被拒绝。服务端会锁定并复算，过期快照会拒绝应用。 |
| POST | `api/projecttaskschedule/undo` | 传 `project_id`、`batch_id` 撤销批次；只有任务当前值仍等于批次写入值时才恢复，避免覆盖后续修改。 |

预演最多处理 2,000 个主任务、20,000 条依赖边、10,000 个展开子任务及 10,000 条最终排期变更；子任务逐层读取会在到达上限时提前停止。超出上限会拒绝请求而不产生部分写入。已完成、缺少计划、计划结束不晚于开始时间、缺少可用前置时间或无权编辑的任务会以跳过原因返回；依赖链中因缺少有效前置时间而跳过的任务不会再作为下游的有效日期来源。无效区间不会作为后继任务的计算依据。应用与撤销在单个事务内完成，遇到数据库死锁最多自动重试 3 次；重复的 `request_id` 不会重复移动任务，新批次会保存完整请求参数摘要，同一编号换参数会被拒绝。成功的 apply/undo 重试会补发任务状态更新，以覆盖提交后、WebSocket 发布前中断的情况。提交后的状态推送和负责人提醒各自隔离失败，通知异常不会改变已提交批次的 API 结果。批次保存周期任务下次重复时间及周期规则的前后值；规则在批次后变化时撤销会失败，避免覆盖后续调整。旧批次未包含周期规则快照时仍按原有字段校验。

## 工时与容量

工时以分钟记录；工作日期及容量日期范围按项目排期时区解释。任务估时与工时写入遵循任务级编辑权限；工时历史按任务可见性读取（能看到该任务即可查看记录，无需编辑权限）；容量汇总仅对项目成员开放。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `api/projecttaskwork/estimate` | 传 `project_id`、`task_id`、`estimated_minutes` 更新主任务估时（0–1,000,000 分钟）。 |
| GET | `api/projecttaskwork/task` | 按任务读取工时历史、汇总和项目当地日期，并返回当前用户是否可修改（`can_edit`）；默认每页 50 条，最大 100 条。 |
| POST | `api/projecttaskwork/add` | 登记 `minutes`（1–1,440）、可选 `work_date`（缺省按首次请求时的项目当地日期）和可选 `note`（最多 500 字）；必传唯一 `request_id`，网络重试必须复用同一编号和原始参数。请求摘要会区分“省略日期”和“显式日期”；同一编号换任务、分钟数、日期填写方式、日期或备注会被拒绝。省略日期时跨日重试仍返回首次保存记录；并发唯一冲突会按请求记录恢复，不依赖数据库约束错误文本格式。登记权限会在事务锁内复查。 |
| POST | `api/projecttaskwork/delete` | 删除本人记录；项目负责人或管理员可以删除成员记录。删除后从历史与汇总中隐藏，但保留幂等墓碑，防止延迟重试重新创建。 |
| GET | `api/projecttaskwork/capacity` | 传 `project_id`、`from`、`to` 汇总最多 93 天的成员计划、实际工时和容量。 |

容量按配置工作日每天 480 分钟计算，并扣除项目节假日例外。只有当前项目成员（含项目负责人）获得期间可用容量；历史工时或计划任务仍属于已离开成员时照常显示，但不再虚增其可用容量。计划估时只分摊有完整计划时间且未归档的主任务；实际工时按登记日期汇总，并纳入从父任务继承负责人/协助人权限的可见子任务记录。归档任务的实际工时保留统计，回收站任务隐藏，永久删除任务时关联工时记录级联清理。工时记录的 `request_id` 在项目和登记人范围内唯一，并与任务、分钟数、日期和备注绑定；相同请求重放返回原记录，不同参数复用编号会被拒绝。用户删除工时后，记录从历史与汇总隐藏，但保留幂等墓碑；旧请求重试不会重新创建已删除的工时。

估时修改、实际工时新增和删除会在数据库事务提交后向任务可见成员发送 `work` 事件；客户端据此同步任务列表数据，并刷新当前打开任务的工时汇总和历史。相同 `request_id` 的成功重放不会再次发送事件。事件推送失败会记录异常，不会把已提交的写入误报为失败。

估时、工时登记/删除及自动排期应用/撤销均写入任务动态；变更记录按动态组件支持的 `record.change` 格式保存前后值。删除工时后，动态仍保留被删除记录的分钟数和工作日期，便于后续审计。

## 实现索引

- 路由注册：`routes/web.php`
- 请求参数和接口注释：`app/Http/Controllers/Api/ProjectTaskDependencyController.php`、`ProjectMilestoneController.php`、`ProjectTaskViewController.php`、`ProjectTaskTemplateController.php`、`ProjectTaskScheduleController.php`、`ProjectTaskWorkController.php`
- 业务校验：对应的 `app/Module/Project*Service.php`
- 用户操作说明：`resources/ai-kb/zh/howto/task/` 下的依赖、里程碑、保存视图和工时文档

本批新增 API 文案已登记在 `language/translate.json` 并同步到 `public/language/api/`。`npm run check:api-lang` 校验 API 原文、翻译源和全部十种运行时数组的键集合、顺序、长度及译文内容，GitHub Actions 会执行此检查。完整生成式路由对照表由 `npm run api:map` 或 `php artisan doc:api-map` 生成；GitHub Actions 静态检查和本地都可用 `npm run api:map:check` 检查是否过期。Node 入口从动态 API 路由与控制器 `@api` 注释生成，不需要启动 Laravel。
