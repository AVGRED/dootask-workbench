-- V27: 超级管理员操作审计

CREATE TABLE IF NOT EXISTS `super_audit_logs` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `operator_userid` BIGINT          NOT NULL,
    `action`          VARCHAR(64)     NOT NULL DEFAULT '',
    `target_userid`   BIGINT          NULL,
    `detail`          VARCHAR(500)    NOT NULL DEFAULT '',
    `created_at`      TIMESTAMP       NULL,
    PRIMARY KEY (`id`),
    KEY `super_audit_created_idx` (`created_at`),
    KEY `super_audit_operator_idx` (`operator_userid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
