-- V11: task templates and their version snapshots.
-- Source: database/migrations/2026_10_15_000008_create_project_task_templates_table.php (simplified)

CREATE TABLE IF NOT EXISTS `project_task_templates` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id`     BIGINT UNSIGNED NOT NULL,
    `name`           VARCHAR(200)    NOT NULL,
    `content`        TEXT            NULL,
    `milestone_name` VARCHAR(100)    NULL,
    `created_by`     BIGINT UNSIGNED NULL,
    `created_at`     TIMESTAMP       NULL,
    `updated_at`     TIMESTAMP       NULL,
    `deleted_at`     TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `project_task_templates_project_idx` (`project_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `project_task_template_revisions` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `template_id` BIGINT UNSIGNED NOT NULL,
    `revision_no` INT             NOT NULL,
    `snapshot`    TEXT            NOT NULL,
    `created_by`  BIGINT UNSIGNED NULL,
    `created_at`  TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `project_task_template_revisions_unique` (`template_id`, `revision_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
