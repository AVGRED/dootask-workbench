-- V10: task attachments (metadata only; bytes live in the configured storage root).

CREATE TABLE IF NOT EXISTS `attachments` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id`    BIGINT UNSIGNED NOT NULL,
    `task_id`       BIGINT UNSIGNED NOT NULL,
    `userid`        BIGINT UNSIGNED NOT NULL,
    `original_name` VARCHAR(255)    NOT NULL,
    `stored_name`   VARCHAR(120)    NOT NULL,
    `mime_type`     VARCHAR(120)    NOT NULL DEFAULT 'application/octet-stream',
    `size_bytes`    BIGINT          NOT NULL DEFAULT 0,
    `checksum`      VARCHAR(64)     NOT NULL DEFAULT '',
    `created_at`    TIMESTAMP       NULL,
    `deleted_at`    TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `attachments_stored_name_unique` (`stored_name`),
    KEY `attachments_task_idx` (`task_id`, `id`),
    KEY `attachments_project_idx` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
