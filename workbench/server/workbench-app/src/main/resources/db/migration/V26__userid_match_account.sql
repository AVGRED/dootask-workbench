-- V26: 用户 ID 与账号（工号）对齐，例如 401001 的 userid 也变成 401001
-- 纯数字账号才参与映射；非数字账号（如 admin）保持原样。

CREATE TABLE `_user_id_map` (
    `old_id` BIGINT NOT NULL,
    `new_id` BIGINT NOT NULL,
    PRIMARY KEY (`old_id`)
);

INSERT INTO `_user_id_map` (`old_id`, `new_id`)
SELECT `userid`, CAST(`account` AS UNSIGNED)
FROM `users`
WHERE `account` REGEXP '^[0-9]{4,}$' AND `userid` <> CAST(`account` AS UNSIGNED);

UPDATE `users` u JOIN `_user_id_map` m ON u.`userid` = m.`old_id` SET u.`userid` = m.`new_id`;
UPDATE `activity_logs` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `admin_notices` t JOIN `_user_id_map` m ON t.`created_by` = m.`old_id` SET t.`created_by` = m.`new_id`;
UPDATE `attachments` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `daily_reports` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `notifications` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `personal_todos` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `projects` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `project_milestones` t JOIN `_user_id_map` m ON t.`created_by` = m.`old_id` SET t.`created_by` = m.`new_id`;
UPDATE `project_milestones` t JOIN `_user_id_map` m ON t.`owner_userid` = m.`old_id` SET t.`owner_userid` = m.`new_id`;
UPDATE `project_tasks` t JOIN `_user_id_map` m ON t.`owner_userid` = m.`old_id` SET t.`owner_userid` = m.`new_id`;
UPDATE `project_tasks` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `project_task_dependencies` t JOIN `_user_id_map` m ON t.`created_by` = m.`old_id` SET t.`created_by` = m.`new_id`;
UPDATE `project_task_templates` t JOIN `_user_id_map` m ON t.`created_by` = m.`old_id` SET t.`created_by` = m.`new_id`;
UPDATE `project_task_template_revisions` t JOIN `_user_id_map` m ON t.`created_by` = m.`old_id` SET t.`created_by` = m.`new_id`;
UPDATE `project_task_work_logs` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `project_users` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `schedule_batches` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `system_settings` t JOIN `_user_id_map` m ON t.`updated_by` = m.`old_id` SET t.`updated_by` = m.`new_id`;
UPDATE `task_comments` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `user_dashboard_layouts` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `user_departments` t JOIN `_user_id_map` m ON t.`owner_userid` = m.`old_id` SET t.`owner_userid` = m.`new_id`;
UPDATE `user_status` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;
UPDATE `weekly_summaries` t JOIN `_user_id_map` m ON t.`userid` = m.`old_id` SET t.`userid` = m.`new_id`;

DROP TABLE `_user_id_map`;

ALTER TABLE `users` AUTO_INCREMENT = 401008;
