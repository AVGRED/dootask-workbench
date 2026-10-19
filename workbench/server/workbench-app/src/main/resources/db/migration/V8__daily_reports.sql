-- V8: daily reports and weekly summaries (new feature, no legacy table).
-- See docs/feature-daily-report.md.

CREATE TABLE IF NOT EXISTS `daily_reports` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `userid`          BIGINT UNSIGNED NOT NULL,
    `report_date`     DATE            NOT NULL,
    `content_done`    TEXT            NOT NULL,
    `content_blocker` TEXT            NULL,
    `content_plan`    TEXT            NULL,
    `minutes`         INT             NULL,
    `status`          TINYINT         NOT NULL DEFAULT 1,
    `submitted_at`    DATETIME        NULL,
    `created_at`      TIMESTAMP       NULL,
    `updated_at`      TIMESTAMP       NULL,
    `deleted_at`      TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `daily_reports_user_date_unique` (`userid`, `report_date`),
    KEY `daily_reports_date_index` (`report_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `weekly_summaries` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `userid`          BIGINT UNSIGNED NOT NULL,
    `week_start`      DATE            NOT NULL,
    `week_end`        DATE            NOT NULL,
    `report_count`    INT             NOT NULL DEFAULT 0,
    `missing_count`   INT             NOT NULL DEFAULT 0,
    `total_minutes`   INT             NOT NULL DEFAULT 0,
    `content`         TEXT            NOT NULL,
    `generated_at`    DATETIME        NULL,
    `created_at`      TIMESTAMP       NULL,
    `updated_at`      TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `weekly_summaries_user_week_unique` (`userid`, `week_start`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
