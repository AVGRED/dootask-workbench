-- V20: token version for logout / forced re-login.

ALTER TABLE `users`
    ADD COLUMN `token_version` INT NOT NULL DEFAULT 0;
