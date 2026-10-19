-- V14: system management — user roles, system settings, dictionaries.

ALTER TABLE `users`
    ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'staff' AFTER `profession`;

CREATE TABLE IF NOT EXISTS `system_settings` (
    `setting_key`  VARCHAR(100) NOT NULL,
    `setting_value` TEXT        NOT NULL,
    `description`  VARCHAR(255) NOT NULL DEFAULT '',
    `updated_by`   BIGINT       NULL,
    `updated_at`   TIMESTAMP    NULL,
    PRIMARY KEY (`setting_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `dict_items` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `dict_type`  VARCHAR(50)     NOT NULL,
    `code`       VARCHAR(50)     NOT NULL,
    `label`      VARCHAR(100)    NOT NULL,
    `sort`       INT             NOT NULL DEFAULT 0,
    `enabled`    TINYINT         NOT NULL DEFAULT 1,
    `created_at` TIMESTAMP       NULL,
    `updated_at` TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `dict_items_type_code_unique` (`dict_type`, `code`),
    KEY `dict_items_type_idx` (`dict_type`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT IGNORE INTO `system_settings` (`setting_key`, `setting_value`, `description`, `updated_at`)
VALUES ('app.name', '工作台', '站点名称（公开）', NOW()),
       ('app.announcement', '', '首页公告（公开）', NOW()),
       ('ui.theme', 'light', '默认主题（公开）', NOW()),
       ('task.workdays', '1,2,3,4,5', '工作日（1=周一，公开）', NOW());

INSERT IGNORE INTO `dict_items` (`dict_type`, `code`, `label`, `sort`, `enabled`, `created_at`, `updated_at`)
VALUES ('task_priority', '0', '普通', 0, 1, NOW(), NOW()),
       ('task_priority', '1', '重要', 1, 1, NOW(), NOW()),
       ('task_priority', '2', '紧急', 2, 1, NOW(), NOW()),
       ('project_status', 'open', '进行中', 0, 1, NOW(), NOW()),
       ('project_status', 'archived', '已归档', 1, 1, NOW(), NOW());
