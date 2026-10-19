-- V17: project tags and task-tag links (subtasks reuse project_tasks.parent_id).

CREATE TABLE IF NOT EXISTS `project_tags` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT UNSIGNED NOT NULL,
    `name`       VARCHAR(50)     NOT NULL,
    `color`      VARCHAR(20)     NOT NULL DEFAULT '',
    `created_at` TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `project_tags_project_name_unique` (`project_id`, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `project_task_tags` (
    `id`      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT UNSIGNED NOT NULL,
    `tag_id`  BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `project_task_tags_unique` (`task_id`, `tag_id`),
    KEY `project_task_tags_tag_idx` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
