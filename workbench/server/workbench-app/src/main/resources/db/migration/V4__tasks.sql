-- V4: project tasks (legacy project_tasks table) + owner_userid.
-- Source: database/migrations/2021_06_25_182631_create_project_tasks_table.php
-- Deviation: owner_userid added (legacy stores owners in a separate relation table,
-- which the new task API does not use yet).

CREATE TABLE IF NOT EXISTS `project_tasks` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `parent_id`       BIGINT          NOT NULL DEFAULT 0,
    `project_id`      BIGINT          NOT NULL DEFAULT 0,
    `column_id`       BIGINT          NOT NULL DEFAULT 0,
    `dialog_id`       BIGINT          NOT NULL DEFAULT 0,
    `name`            VARCHAR(255)    NOT NULL DEFAULT '',
    `color`           VARCHAR(20)     NOT NULL DEFAULT '',
    `desc`            VARCHAR(500)    NOT NULL DEFAULT '',
    `start_at`        TIMESTAMP       NULL,
    `end_at`          TIMESTAMP       NULL,
    `archived_at`     TIMESTAMP       NULL,
    `archived_userid` BIGINT          NOT NULL DEFAULT 0,
    `complete_at`     TIMESTAMP       NULL,
    `userid`          BIGINT          NOT NULL DEFAULT 0,
    `owner_userid`    BIGINT          NOT NULL DEFAULT 0,
    `p_level`         TINYINT         NOT NULL DEFAULT 0,
    `p_name`          VARCHAR(50)     NOT NULL DEFAULT '',
    `p_color`         VARCHAR(20)     NOT NULL DEFAULT '',
    `sort`            INT             NOT NULL DEFAULT 0,
    `created_at`      TIMESTAMP       NULL,
    `updated_at`      TIMESTAMP       NULL,
    `deleted_at`      TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `project_tasks_project_id_index` (`project_id`),
    KEY `project_tasks_owner_userid_index` (`owner_userid`),
    KEY `project_tasks_complete_at_index` (`complete_at`),
    KEY `project_tasks_deleted_at_index` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
