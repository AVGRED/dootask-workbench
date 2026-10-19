-- V3: projects and project members (legacy projects / project_users tables).
-- Source: database/migrations/2021_06_25_182631_create_projects_table.php
--         database/migrations/2021_06_25_182631_create_project_users_table.php

CREATE TABLE IF NOT EXISTS `projects` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name`             VARCHAR(255)    NOT NULL DEFAULT '',
    `desc`             VARCHAR(500)    NOT NULL DEFAULT '',
    `userid`           BIGINT          NOT NULL DEFAULT 0,
    `dialog_id`        BIGINT          NOT NULL DEFAULT 0,
    `archived_at`      TIMESTAMP       NULL,
    `archived_userid`  BIGINT          NOT NULL DEFAULT 0,
    `created_at`       TIMESTAMP       NULL,
    `updated_at`       TIMESTAMP       NULL,
    `deleted_at`       TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `projects_userid_index` (`userid`),
    KEY `projects_deleted_at_index` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `project_users` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT          NOT NULL DEFAULT 0,
    `userid`     BIGINT          NOT NULL DEFAULT 0,
    `owner`      TINYINT         NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP       NULL,
    `updated_at` TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `project_users_project_user_unique` (`project_id`, `userid`),
    KEY `project_users_userid_index` (`userid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
