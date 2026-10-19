-- V5: project milestones + task.milestone_id.
-- Source: database/migrations/2026_10_15_000002_create_project_milestones_table.php
--         database/migrations/2026_10_15_000011_add_milestone_to_project_tasks.php

CREATE TABLE IF NOT EXISTS `project_milestones` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id`    BIGINT          NOT NULL,
    `name`          VARCHAR(100)    NOT NULL,
    `owner_userid`  BIGINT          NULL,
    `description`   VARCHAR(500)    NULL,
    `target_at`     DATE            NULL,
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'open',
    `reminder_days` SMALLINT        NOT NULL DEFAULT -1,
    `reminded_at`   DATETIME        NULL,
    `sort`          INT UNSIGNED    NOT NULL DEFAULT 0,
    `created_by`    BIGINT          NULL,
    `created_at`    TIMESTAMP       NULL,
    `updated_at`    TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `project_milestones_project_status_sort_idx` (`project_id`, `status`, `sort`),
    KEY `project_milestones_reminder_scan_idx` (`status`, `reminded_at`, `target_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE `project_tasks`
    ADD COLUMN `milestone_id` BIGINT NULL AFTER `project_id`;

ALTER TABLE `project_tasks`
    ADD KEY `project_tasks_project_milestone_idx` (`project_id`, `milestone_id`);
