-- V30: 会议改成"管理员创建 + 指定参会人"，取消所属项目

CREATE TABLE IF NOT EXISTS `meeting_participants` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `meeting_id` BIGINT NOT NULL,
  `userid` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meeting_user` (`meeting_id`, `userid`),
  KEY `idx_meeting_user_user` (`userid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 删除 project_id 时 MariaDB 会连带删除索引，所以不再单独 DROP INDEX
ALTER TABLE `meetings` DROP COLUMN IF EXISTS `project_id`;
