-- V2: departments tree (legacy table user_departments).
-- Source: database/migrations/2022_11_16_221259_create_user_departments_table.php

CREATE TABLE IF NOT EXISTS `user_departments` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(100)    NOT NULL DEFAULT '',
    `dialog_id`    BIGINT          NOT NULL DEFAULT 0,
    `parent_id`    BIGINT          NOT NULL DEFAULT 0,
    `owner_userid` BIGINT          NOT NULL DEFAULT 0,
    `created_at`   TIMESTAMP       NULL,
    `updated_at`   TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `user_departments_parent_id_index` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
