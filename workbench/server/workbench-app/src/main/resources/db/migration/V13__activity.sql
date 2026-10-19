-- V13: project activity / audit log.

CREATE TABLE IF NOT EXISTS `activity_logs` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id`  BIGINT UNSIGNED NOT NULL,
    `task_id`     BIGINT UNSIGNED NULL,
    `userid`      BIGINT UNSIGNED NOT NULL,
    `action`      VARCHAR(64)     NOT NULL,
    `target_type` VARCHAR(32)     NOT NULL DEFAULT '',
    `target_id`   BIGINT UNSIGNED NULL,
    `detail`      VARCHAR(2000)   NOT NULL DEFAULT '',
    `created_at`  TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `activity_logs_project_idx` (`project_id`, `id`),
    KEY `activity_logs_task_idx` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
