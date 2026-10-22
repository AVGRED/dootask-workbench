-- V25: 超级管理员（唯一最高权限），当前指定给 401001

ALTER TABLE `users` ADD COLUMN `super_admin` TINYINT NOT NULL DEFAULT 0;

UPDATE `users` SET `super_admin` = 1, `role` = 'admin' WHERE `account` = '401001';
