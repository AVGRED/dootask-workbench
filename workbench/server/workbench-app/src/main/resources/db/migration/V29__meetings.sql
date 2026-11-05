-- V29: 会议（先支持粘贴腾讯会议链接，后续可切换成 API 自动创建）

CREATE TABLE `meetings` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(120) NOT NULL,
  `project_id` BIGINT NULL,
  `owner_userid` BIGINT NOT NULL,
  `start_at` DATETIME NOT NULL,
  `duration_minutes` INT NOT NULL DEFAULT 60,
  `join_url` VARCHAR(600) NOT NULL,
  `meeting_code` VARCHAR(40) NOT NULL DEFAULT '',
  `platform` VARCHAR(20) NOT NULL DEFAULT 'tencent',
  `description` VARCHAR(500) NOT NULL DEFAULT '',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_meetings_start` (`start_at`),
  KEY `idx_meetings_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
