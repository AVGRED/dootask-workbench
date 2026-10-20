-- V22: 个人待办（不属于任何项目，每人每天自己记的事）

CREATE TABLE IF NOT EXISTS `personal_todos` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `userid`     BIGINT          NOT NULL DEFAULT 0,
    `title`      VARCHAR(200)    NOT NULL DEFAULT '',
    `done`       TINYINT         NOT NULL DEFAULT 0,
    `todo_date`  DATE            NULL,
    `sort`       INT             NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP       NULL,
    `updated_at` TIMESTAMP       NULL,
    `deleted_at` TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `personal_todos_user_idx` (`userid`, `deleted_at`, `done`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
