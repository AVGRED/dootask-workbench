-- 旧库种子数据（用于验证迁移脚本）：刻意缺少 v2 新增列（deleted_at / milestone_id / owner_userid），
-- 以验证迁移器能按列存在性兼容旧结构。
-- 密码为 md5('secret123')，登录时会被自动升级为 BCrypt。

DROP TABLE IF EXISTS project_task_work_logs;
DROP TABLE IF EXISTS project_tasks;
DROP TABLE IF EXISTS project_users;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    userid      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email       VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50)  NOT NULL DEFAULT '',
    profession  VARCHAR(50)  NOT NULL DEFAULT '',
    userimg     VARCHAR(255) NOT NULL DEFAULT '',
    password    VARCHAR(50)  NOT NULL DEFAULT '',
    encrypt     VARCHAR(32)  NOT NULL DEFAULT '',
    login_num   INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NULL,
    updated_at  TIMESTAMP NULL,
    PRIMARY KEY (userid)
);

CREATE TABLE projects (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL DEFAULT '',
    `desc`          VARCHAR(255) NOT NULL DEFAULT '',
    userid          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    dialog_id       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    archived_at     TIMESTAMP NULL,
    archived_userid BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NULL,
    updated_at      TIMESTAMP NULL,
    PRIMARY KEY (id)
);

CREATE TABLE project_users (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    userid     BIGINT UNSIGNED NOT NULL,
    owner      TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    PRIMARY KEY (id)
);

CREATE TABLE project_tasks (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id       BIGINT NOT NULL DEFAULT 0,
    project_id      BIGINT NOT NULL DEFAULT 0,
    column_id       BIGINT NOT NULL DEFAULT 0,
    dialog_id       BIGINT NOT NULL DEFAULT 0,
    name            VARCHAR(255) NOT NULL DEFAULT '',
    color           VARCHAR(20)  NOT NULL DEFAULT '',
    `desc`          VARCHAR(500) NOT NULL DEFAULT '',
    start_at        TIMESTAMP NULL,
    end_at          TIMESTAMP NULL,
    archived_at     TIMESTAMP NULL,
    archived_userid BIGINT NOT NULL DEFAULT 0,
    complete_at     TIMESTAMP NULL,
    userid          BIGINT NOT NULL DEFAULT 0,
    p_level         TINYINT NOT NULL DEFAULT 0,
    p_name          VARCHAR(50) NOT NULL DEFAULT '',
    p_color         VARCHAR(20) NOT NULL DEFAULT '',
    sort            INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NULL,
    updated_at      TIMESTAMP NULL,
    PRIMARY KEY (id)
);

CREATE TABLE project_task_work_logs (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id  BIGINT UNSIGNED NOT NULL,
    task_id     BIGINT UNSIGNED NOT NULL,
    userid      BIGINT UNSIGNED NOT NULL,
    request_id  VARCHAR(64) NOT NULL,
    minutes     SMALLINT UNSIGNED NOT NULL,
    work_date   DATE NOT NULL,
    note        VARCHAR(500) NULL,
    created_at  TIMESTAMP NULL,
    updated_at  TIMESTAMP NULL,
    PRIMARY KEY (id)
);

INSERT INTO users (userid, email, nickname, profession, userimg, password, encrypt, login_num, created_at, updated_at)
VALUES (9001, 'legacy@example.com', '旧库用户', '研发', '', '5d7845ac6ee7cfffafc5fe5f35cf666d', '', 0, NOW(), NOW());

INSERT INTO projects (id, name, `desc`, userid, dialog_id, archived_userid, created_at, updated_at)
VALUES (9001, '旧库迁移项目', '来自 legacy_seed', 9001, 0, 0, NOW(), NOW());

INSERT INTO project_users (id, project_id, userid, owner, created_at, updated_at)
VALUES (9001, 9001, 9001, 1, NOW(), NOW());

INSERT INTO project_tasks (id, parent_id, project_id, column_id, dialog_id, name, color, `desc`, start_at, end_at,
                           archived_userid, userid, p_level, p_name, p_color, sort, created_at, updated_at)
VALUES (9001, 0, 9001, 0, 0, '旧库任务', '', '迁移验证', '2026-10-01 09:00:00', '2026-10-02 18:00:00',
        0, 9001, 2, '', '', 0, NOW(), NOW());

INSERT INTO project_task_work_logs (id, project_id, task_id, userid, request_id, minutes, work_date, note, created_at, updated_at)
VALUES (9001, 9001, 9001, 9001, 'legacy-req-1', 90, '2026-10-01', '旧库工时', NOW(), NOW());
