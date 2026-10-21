-- V24: 给用户加独立的登录账号（工号），邮箱可以换成真实邮箱

ALTER TABLE `users` ADD COLUMN `account` VARCHAR(64) NULL AFTER `email`;

-- 存量用户默认用工号邮箱的 @ 前缀作为账号
UPDATE `users` SET `account` = SUBSTRING_INDEX(`email`, '@', 1)
WHERE `account` IS NULL OR `account` = '';

ALTER TABLE `users` ADD UNIQUE KEY `uk_users_account` (`account`);
