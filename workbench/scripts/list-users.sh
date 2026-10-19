#!/usr/bin/env bash
mariadb -h127.0.0.1 -uwb -pwb workbench -e "SELECT userid,email,nickname,role FROM users ORDER BY userid;"
