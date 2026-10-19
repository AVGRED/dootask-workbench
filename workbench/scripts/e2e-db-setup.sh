#!/usr/bin/env bash
set -u
export DEBIAN_FRONTEND=noninteractive

sudo -n service mariadb start >/tmp/mdb.log 2>&1 || sudo -n service mysql start >>/tmp/mdb.log 2>&1 || true
sleep 2

sudo -n mysqladmin create workbench 2>/dev/null || true
sudo -n mariadb -e "CREATE DATABASE IF NOT EXISTS workbench DEFAULT CHARACTER SET utf8mb4;"
sudo -n mariadb -e "CREATE USER IF NOT EXISTS 'wb'@'%' IDENTIFIED BY 'wb';"
sudo -n mariadb -e "GRANT ALL PRIVILEGES ON workbench.* TO 'wb'@'%'; FLUSH PRIVILEGES;"

printf '[mysqld]\nbind-address=0.0.0.0\n' | sudo -n tee /etc/mysql/mariadb.conf.d/99-bind.cnf >/dev/null
sudo -n service mariadb restart >/tmp/mdb2.log 2>&1 || true
sleep 3
sudo -n ss -lntp 2>/dev/null | grep 3306 || echo "no 3306"
sudo -n mariadb -e "SELECT user, host FROM mysql.user WHERE user='wb';" 2>&1 | tail -2
