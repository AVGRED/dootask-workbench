-- V16: kanban columns per project.

CREATE TABLE IF NOT EXISTS `project_task_columns` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT UNSIGNED NOT NULL,
    `name`       VARCHAR(50)     NOT NULL,
    `sort`       INT             NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP       NULL,
    `updated_at` TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `project_task_columns_project_idx` (`project_id`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
