#!/usr/bin/env bash
# 准备旧库迁移的端到端环境：建 legacy_seed 库、授权给 wb、导入种子数据。
# 用法：bash workbench/scripts/legacy-seed-setup.sh [/path/to/seed-legacy.sql]
set -eu

SEED_FILE="${1:-/mnt/d/repos/workbench/dootask/workbench/scripts/seed-legacy.sql}"

sudo -n mariadb -e "CREATE DATABASE IF NOT EXISTS legacy_seed DEFAULT CHARACTER SET utf8mb4;"
sudo -n mariadb -e "GRANT ALL PRIVILEGES ON legacy_seed.* TO 'wb'@'%'; FLUSH PRIVILEGES;"

mariadb -h127.0.0.1 -uwb -pwb legacy_seed < "$SEED_FILE"
mariadb -h127.0.0.1 -uwb -pwb legacy_seed -e "SELECT (SELECT COUNT(*) FROM users) AS users, (SELECT COUNT(*) FROM projects) AS projects, (SELECT COUNT(*) FROM project_tasks) AS tasks, (SELECT COUNT(*) FROM project_task_work_logs) AS worklogs;"
