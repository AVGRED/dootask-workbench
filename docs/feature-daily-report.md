# 功能设计：每日日报与每周汇总

## 1. 需求

- 员工**每个工作日提交一篇日报**（当天做了什么、遇到的问题、明日计划、可选工时）；
- **每周自动生成汇总**（按人 + 按项目），无需人工操作；
- 管理端可查看下属/全员的日报与周报，员工端只能看自己的。

## 2. 规则（需确认的默认值已标注）

| 规则 | 默认 |
|---|---|
| 提交周期 | 每个自然日一篇；周末是否强制：**否** |
| 截止时间 | 次日 10:00 前补交算准时；之后标记「迟交」 |
| 必填 | 今日完成（≥1 条）；遇到的问题、明日计划选填 |
| 关联 | 每条完成项可关联任务（`task_id` 选填） |
| 工时 | 可选填分钟数；填了则同步生成一条工时记录（复用 `worklog`） |
| 周报生成 | 每周一 02:00 汇总上一个自然周（周一 00:00 至周日 23:59） |
| 补交 | 允许补交 7 天内的日报；超过标记缺失 |
| 可见性 | 本人 + 直属上级 + 部门负责人；项目相关条目对项目成员可见 |

## 3. 数据模型（MySQL）

```sql
-- 日报
CREATE TABLE daily_reports (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  userid BIGINT NOT NULL,
  report_date DATE NOT NULL,
  content_done TEXT NOT NULL,          -- 今日完成
  content_blocker TEXT NULL,           -- 遇到的问题
  content_plan TEXT NULL,              -- 明日计划
  minutes INT NULL,                    -- 可选工时
  status TINYINT NOT NULL DEFAULT 1,   -- 1 准时 / 2 迟交 / 3 补交
  submitted_at DATETIME NULL,
  created_at DATETIME, updated_at DATETIME,
  deleted_at DATETIME NULL,
  UNIQUE KEY uk_user_date (userid, report_date)
);

-- 周报（自动生成，只读）
CREATE TABLE weekly_summaries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  userid BIGINT NOT NULL,
  week_start DATE NOT NULL,            -- 周一
  week_end DATE NOT NULL,              -- 周日
  report_count INT NOT NULL,           -- 本周日报数
  missing_count INT NOT NULL,          -- 缺失天数
  total_minutes INT NOT NULL,
  content TEXT NOT NULL,               -- 汇总正文（Markdown）
  generated_at DATETIME,
  UNIQUE KEY uk_user_week (userid, week_start)
);
```

## 4. API（沿用统一返回体）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/daily-reports?from=&to=&userid=` | 列表，员工只能查自己 |
| GET | `/api/v1/daily-reports/{date}` | 按日期取当天日报 |
| POST | `/api/v1/daily-reports` | 提交/覆盖当天日报（幂等：同日唯一） |
| PUT | `/api/v1/daily-reports/{id}` | 修改（仅本人、7 天内） |
| DELETE | `/api/v1/daily-reports/{id}` | 软删除（仅本人） |
| GET | `/api/v1/weekly-summaries?weekStart=&userid=` | 周报列表 |
| POST | `/api/v1/weekly-summaries/regenerate` | 手动重算（管理端） |

## 5. 每周自动汇总

- 定时任务（Spring `@Scheduled`，周一 02:00）遍历上一周；
- 对每位有日报或有任务的员工生成一条 `weekly_summaries`；
- 正文模板：
  - 本周提交 X 篇、缺失 Y 天、累计工时 Z；
  - 按完成项聚合出主要工作（可用规则拼接，后续接 AI 摘要）；
  - 缺失日期列表；
- 幂等：`uk_user_week` 唯一键，重复执行只更新 `content` 与 `generated_at`。

## 6. 前端

- **员工端**：首页卡片「今日日报」→ 编辑页（完成项列表 + 问题 + 明日计划 + 工时）；历史列表按周分组；
- **管理端**：成员日报列表（按人/部门/日期筛选）、缺失人员提醒、周报查看；
- **移动端**：复用员工端编辑页，布局适配。

## 7. 验收用例

1. 同一天重复提交只保留一条（覆盖）；
2. 次日 10:00 后提交标记为迟交；
3. 7 天后禁止补交；
4. 未填完成项拒绝提交；
5. 填了工时则生成对应工时记录，删除日报不自动删除工时（需人工处理）；
6. 周一 02:00 自动生成上一周周报，重复执行不产生重复行；
7. 员工无法查看他人日报，管理端按部门可见。

## 8. 与重写方案的关系

- 后端落在 `report` 模块（`daily` 子包），复用 `auth` 与 `user`；
- 前端落在 `packages/ui/report`，员工端与管理端各一个页面；
- 迁移期旧系统不需要实现该功能，新系统上线即可用。
