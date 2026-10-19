-- V19: employee avatar (static 3D-style image).

ALTER TABLE `users`
    ADD COLUMN `avatar` VARCHAR(255) NOT NULL DEFAULT '' AFTER `nickname`;
