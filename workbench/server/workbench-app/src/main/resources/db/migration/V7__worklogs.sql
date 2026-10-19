-- V7: task work logs (legacy project_task_work_logs table).
-- Source: database/migrations/2026_10_16_000005_create_project_task_work_logs_table.php
--         database/migrations/2026_10_17_000005_add_request_payload_hash_to_project_task_work_logs.php
--         database/migrations/2026_10_17_000006_soft_delete_project_task_work_logs.php

CREATE TABLE IF NOT EXISTS `project_task_work_logs` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id`           BIGINT UNSIGNED NOT NULL,
    `task_id`              BIGINT UNSIGNED NOT NULL,
    `userid`               BIGINT UNSIGNED NOT NULL,
    `request_id`           VARCHAR(64)     NOT NULL,
    `request_payload_hash` VARCHAR(64)     NULL,
    `minutes`              SMALLINT UNSIGNED NOT NULL,
    `work_date`            DATE            NOT NULL,
    `note`                 VARCHAR(500)    NULL,
    `created_at`           TIMESTAMP       NULL,
    `updated_at`           TIMESTAMP       NULL,
    `deleted_at`           TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `task_work_logs_request_unique` (`project_id`, `userid`, `request_id`),
    KEY `task_work_logs_capacity_idx` (`project_id`, `work_date`, `userid`),
    KEY `task_work_logs_task_history_idx` (`task_id`, `project_id`, `work_date`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
