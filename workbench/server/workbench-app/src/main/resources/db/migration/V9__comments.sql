-- V9: task comments and notifications (new feature).

CREATE TABLE IF NOT EXISTS `task_comments` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id`            BIGINT UNSIGNED NOT NULL,
    `project_id`         BIGINT UNSIGNED NOT NULL,
    `userid`             BIGINT UNSIGNED NOT NULL,
    `content`            VARCHAR(2000)   NOT NULL,
    `mentioned_userids`  VARCHAR(500)    NOT NULL DEFAULT '',
    `created_at`         TIMESTAMP       NULL,
    `updated_at`         TIMESTAMP       NULL,
    `deleted_at`         TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `task_comments_task_idx` (`task_id`, `id`),
    KEY `task_comments_project_idx` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notifications` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `userid`     BIGINT UNSIGNED NOT NULL,
    `type`       VARCHAR(32)     NOT NULL,
    `title`      VARCHAR(255)    NOT NULL,
    `payload`    VARCHAR(1000)   NOT NULL DEFAULT '',
    `read_at`    DATETIME        NULL,
    `created_at` TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `notifications_user_read_idx` (`userid`, `read_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
