-- V18: personal dashboard layout and employee status.

CREATE TABLE IF NOT EXISTS `user_dashboard_layouts` (
    `userid`      BIGINT UNSIGNED NOT NULL,
    `layout_json` TEXT            NOT NULL,
    `updated_at`  TIMESTAMP       NULL,
    PRIMARY KEY (`userid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_status` (
    `userid`     BIGINT UNSIGNED NOT NULL,
    `status`     VARCHAR(20)     NOT NULL DEFAULT 'idle',
    `note`       VARCHAR(200)    NOT NULL DEFAULT '',
    `updated_at` TIMESTAMP       NULL,
    PRIMARY KEY (`userid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
