-- V6: task dependencies (legacy project_task_dependencies table).
-- Source: database/migrations/2026_10_15_000001_create_project_task_dependencies_table.php

CREATE TABLE IF NOT EXISTS `project_task_dependencies` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id`           BIGINT          NOT NULL,
    `predecessor_task_id`  BIGINT          NOT NULL,
    `successor_task_id`    BIGINT          NOT NULL,
    `created_by`           BIGINT          NULL,
    `created_at`           TIMESTAMP       NULL,
    `updated_at`           TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `project_task_dependencies_edge_unique` (`predecessor_task_id`, `successor_task_id`),
    KEY `project_task_dependencies_project_predecessor_idx` (`project_id`, `predecessor_task_id`),
    KEY `project_task_dependencies_project_successor_idx` (`project_id`, `successor_task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
