-- V12: schedule apply batches (apply / undo support for scheduling preview).

CREATE TABLE IF NOT EXISTS `schedule_batches` (
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id`   BIGINT UNSIGNED NOT NULL,
    `userid`       BIGINT UNSIGNED NOT NULL,
    `request_id`   VARCHAR(64)     NOT NULL,
    `change_count` INT             NOT NULL DEFAULT 0,
    `created_at`   TIMESTAMP       NULL,
    `undone_at`    TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `schedule_batches_request_unique` (`project_id`, `request_id`),
    KEY `schedule_batches_project_idx` (`project_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `schedule_batch_items` (
    `id`        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `batch_id`  BIGINT UNSIGNED NOT NULL,
    `task_id`   BIGINT UNSIGNED NOT NULL,
    `old_start` DATETIME        NULL,
    `old_end`   DATETIME        NULL,
    `new_start` DATETIME        NULL,
    `new_end`   DATETIME        NULL,
    PRIMARY KEY (`id`),
    KEY `schedule_batch_items_batch_idx` (`batch_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
