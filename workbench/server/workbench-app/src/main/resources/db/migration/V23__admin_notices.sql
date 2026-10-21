-- V23: 管理员发布的通知（保留发布记录，同时给每个员工写一条站内通知）

CREATE TABLE IF NOT EXISTS `admin_notices` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `title`      VARCHAR(120)    NOT NULL DEFAULT '',
    `content`    VARCHAR(1000)   NOT NULL DEFAULT '',
    `created_by` BIGINT          NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `admin_notices_created_idx` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
