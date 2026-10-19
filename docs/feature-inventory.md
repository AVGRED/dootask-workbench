# 功能清单（重写对齐基线）

依据现有 `app/Module` 与 `app/Http/Controllers/Api` 的实际实现整理。重写时按此清单逐项对齐，状态列用于跟踪「已迁移 / 已验证」。

## 1. 认证与账号

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 账号密码登录、JWT | `UsersController` | `auth` |
| 注册、邀请码注册 | `PublicController` | `auth` |
| 验证码 / 二次验证 | `UsersController`、`SystemController` | `auth` |
| 修改密码、个人资料、头像 | `UsersController` | `user` |

## 2. 用户与组织

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 用户 CRUD、导入导出 | `UsersController`、`UserImport` | `admin/user` |
| 部门树、成员归属 | `ContactsDepartment`、`ContactsController` | `user` |
| 部门负责人只读视角 | `ProjectController` | `project` |
| 通讯录、成员搜索 | `ContactsController`、`SearchController` | `user` |

## 3. 项目

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 项目 CRUD、归档、删除、收藏 | `ProjectController` | `project` |
| 项目设置（名称/介绍/归档规则/AI/模板共享/排期/负责人视角） | `ProjectController@update` | `project` |
| 成员增删、管理员（deputy） | `ProjectController@user` | `project` |
| 权限（项目可见性、成员可见性） | `ProjectController@permission` | `project` |
| 邀请链接（生成/刷新） | `ProjectController@invite` | `project` |
| 项目移交 | `ProjectController@transfer` | `project` |

## 4. 任务

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 任务 CRUD、批量操作 | `ProjectController` | `task` |
| 子任务（多层，深度上限） | `ProjectTask` | `task` |
| 指派、优先级、截止时间、标签 | `ProjectController` | `task` |
| 看板列、拖拽排序、列表字段 | `ProjectController` | `task` |
| 归档 / 回收站 / 恢复 | `ProjectController` | `task` |
| 任务移动、复制、交接（handoff） | `ProjectTaskHandoffService` | `task` |

## 5. 规划（本工作台核心）

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 任务依赖（FS）、成环检测、风险汇总、甘特依赖 | `ProjectTaskDependencyService` | `planning` |
| 里程碑 CRUD、排序、负责人、提醒（含重试记录） | `ProjectMilestoneService` | `planning` |
| 自动排期：预演 / 应用 / 撤销、幂等批次、工作日与节假日 | `ProjectTaskScheduleService` | `planning` |
| 保存的任务视图（个人 / 共享） | `ProjectTaskViewService` | `planning` |
| 任务模板（树、版本历史、恢复） | `ProjectTaskTemplateController` | `planning` |

## 6. 工时与容量

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 登记 / 删除工时、可见性控制 | `ProjectTaskWorkService` | `worklog` |
| 预估工时、剩余/超出汇总 | `ProjectTaskWorkService` | `worklog` |
| 成员工时容量汇总、93 天限制、CSV 导出 | `ProjectTaskWorkService` | `worklog` |

## 7. 协作与通知

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 会话/消息、WebSocket 推送、幂等投递 | `DialogController`、`MsgTool` | `notify` |
| 任务动态、@提醒、已读 | `ProjectLog`、`DialogController` | `notify` |
| 消息提醒任务（里程碑提醒） | `ProjectMilestoneService` | `notify` |

## 8. 文件

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 上传（分块）、预览、下载 | `UploadController`、`ChunkUpload` | `file` |
| WebDAV 挂载 | `FileDavController` | `file` |
| 附件与任务关联 | `FileController` | `file` |

## 9. 系统与运营

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 系统设置、公开配置 | `SystemController` | `admin/system` |
| 授权（License）、在线更新 | `LicenseController`、`SystemUpdate` | `admin/system` |
| 应用中心 / 插件 | `AppsController`、`Apps` | `admin/system` |
| 仪表盘、报表、导出 | `DashboardController`、`ReportController`、`BillExport` | `admin/report` |
| 全局搜索 | `SearchController` | `task` |
| 徽章 / 签到 等运营小功能 | `Badge`、`Timer` | 待定 |

## 10. AI 能力

| 功能 | 端点/模块 | 目标 |
|---|---|---|
| 任务 AI 分析建议 | `AiTaskSuggestion`、`AssistantController` | `ai` |
| AI 助手会话 | `AI` | `ai` |

## 11. 需要你确认「是否保留」的功能

以下功能在旧系统中存在，但与「项目/任务/工时」主线关系较弱，重写时可选择不做：

- [ ] 远程协助 / 外部协作（`Extranet`）
- [ ] 投诉 / 反馈（`ComplaintController`）
- [ ] 计时器（`Timer`）、签到徽章（`Badge`）
- [ ] 账单导入导出（`BillImport` / `BillExport`）
- [ ] WebDAV 与在线授权更新（`FileDavController`、`OnlineLicense`、`SystemUpdate`）
- [ ] 应用中心 / 插件（`AppsController`）

## 12. 当前进度

- 旧系统：Feature 316 条、Unit 51 条测试全部通过，作为新系统的行为基线；
- 前端：`ProjectPanel`、`TaskDetail` 已拆出 7 个组件，前端测试 47 条；
- 新系统：尚未建工程，方案见 `rewrite-plan-vue3-springboot.md`。
