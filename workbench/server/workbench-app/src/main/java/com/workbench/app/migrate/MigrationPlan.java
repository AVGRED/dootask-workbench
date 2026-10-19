package com.workbench.app.migrate;

import java.util.List;

/**
 * 旧库 → v2 的迁移计划：按外键依赖顺序排列，逐表声明需要搬运的列。
 *
 * <p>v2 沿用了旧库的表名与主键，因此迁移是「同表同列搬行」，保留原始 id，
 * 这样关联关系（项目成员、任务、依赖、工时）天然不需要重映射。</p>
 */
public final class MigrationPlan {

    private MigrationPlan() {
    }

    /** 一张表：源名、目标名（通常相同）、列清单、说明。 */
    public record TableStep(String sourceTable, String targetTable, List<String> columns, String description) {
    }

    /** 迁移顺序：被引用的表在前。 */
    public static List<TableStep> steps() {
        return List.of(
                new TableStep("users", "users",
                        List.of("userid", "email", "nickname", "profession", "userimg", "password", "encrypt",
                                "login_num", "created_at", "updated_at"),
                        "用户（密码保留旧 md5 格式，首次登录自动升级为 BCrypt）"),
                new TableStep("user_departments", "departments",
                        List.of("id", "name", "parent_id", "owner_userid", "created_at", "updated_at"),
                        "部门"),
                new TableStep("projects", "projects",
                        List.of("id", "name", "desc", "userid", "dialog_id", "archived_at", "archived_userid",
                                "created_at", "updated_at", "deleted_at"),
                        "项目"),
                new TableStep("project_users", "project_users",
                        List.of("id", "project_id", "userid", "owner", "created_at", "updated_at"),
                        "项目成员"),
                new TableStep("project_milestones", "project_milestones",
                        List.of("id", "project_id", "name", "description", "owner_userid", "target_at", "status",
                                "reminder_days", "reminded_at", "sort", "created_by", "created_at", "updated_at"),
                        "里程碑"),
                new TableStep("project_tasks", "project_tasks",
                        List.of("id", "parent_id", "project_id", "milestone_id", "column_id", "dialog_id", "name",
                                "color", "desc", "start_at", "end_at", "archived_at", "archived_userid",
                                "complete_at", "userid", "owner_userid", "p_level", "p_name", "p_color", "sort",
                                "created_at", "updated_at", "deleted_at"),
                        "任务"),
                new TableStep("project_task_dependencies", "project_task_dependencies",
                        List.of("id", "project_id", "predecessor_task_id", "successor_task_id", "created_by",
                                "created_at", "updated_at"),
                        "任务依赖"),
                new TableStep("project_task_work_logs", "project_task_work_logs",
                        List.of("id", "project_id", "task_id", "userid", "request_id", "request_payload_hash",
                                "minutes", "work_date", "note", "created_at", "updated_at", "deleted_at"),
                        "工时"));
    }

    /** 摘要表格（纯文本，便于日志与脚本解析）。 */
    public static String formatSummary(List<TableStep> steps, List<long[]> counts, boolean dryRun) {
        StringBuilder builder = new StringBuilder();
        builder.append(dryRun ? "MIGRATION DRY-RUN" : "MIGRATION RESULT").append('\n');
        long total = 0;
        for (int i = 0; i < steps.size(); i++) {
            long count = counts.get(i)[0];
            long copied = counts.get(i)[1];
            total += copied;
            builder.append(String.format("%-32s rows=%-8d copied=%-8d %s%n",
                    steps.get(i).targetTable(), count, copied, steps.get(i).description()));
        }
        builder.append("TOTAL copied=").append(total).append('\n');
        return builder.toString();
    }
}
