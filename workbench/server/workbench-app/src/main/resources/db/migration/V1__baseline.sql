-- V1: baseline matching the legacy dootask schema (users table only for now).
-- Source: database/migrations/2021_06_25_182631_create_users_table.php
-- Deviations from the legacy definition, both required by the new login flow:
--   * password widened to varchar(100) so BCrypt (60 chars) fits after upgrade
--   * deleted_at added for soft delete (legacy table had no such column)

CREATE TABLE IF NOT EXISTS `users` (
    `userid`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `identity`       VARCHAR(255)    NOT NULL DEFAULT '',
    `az`             VARCHAR(10)     NOT NULL DEFAULT '',
    `email`          VARCHAR(100)    NOT NULL DEFAULT '',
    `nickname`       VARCHAR(255)    NOT NULL DEFAULT '',
    `profession`     VARCHAR(255)    NOT NULL DEFAULT '',
    `userimg`        VARCHAR(255)    NOT NULL DEFAULT '',
    `encrypt`        VARCHAR(50)     NOT NULL DEFAULT '',
    `password`       VARCHAR(100)    NOT NULL DEFAULT '',
    `changepass`     TINYINT         NOT NULL DEFAULT 0,
    `login_num`      INT             NOT NULL DEFAULT 0,
    `last_ip`        VARCHAR(20)     NOT NULL DEFAULT '',
    `last_at`        TIMESTAMP       NULL,
    `line_ip`        VARCHAR(20)     NOT NULL DEFAULT '',
    `line_at`        TIMESTAMP       NULL,
    `task_dialog_id` BIGINT          NOT NULL DEFAULT 0,
    `created_ip`     VARCHAR(20)     NOT NULL DEFAULT '',
    `created_at`     TIMESTAMP       NULL,
    `updated_at`     TIMESTAMP       NULL,
    `deleted_at`     TIMESTAMP       NULL,
    PRIMARY KEY (`userid`),
    UNIQUE KEY `users_email_unique` (`email`),
    KEY `users_deleted_at_index` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
