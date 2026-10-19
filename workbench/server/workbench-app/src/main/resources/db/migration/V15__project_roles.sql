-- V15: project membership roles (owner / admin / member / viewer).

ALTER TABLE `project_users`
    ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'member' AFTER `owner`;

UPDATE `project_users` SET `role` = 'owner' WHERE `owner` = 1;
