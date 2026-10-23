-- V28: 状态墙照片（与头像分开，避免影响顶栏/星图）

ALTER TABLE `users` ADD COLUMN `wall_photo` VARCHAR(255) NOT NULL DEFAULT '';
