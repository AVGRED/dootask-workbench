# PHP2Shell（CVE-2026-109618 / CVE-2026-109619）应急处理记录

> 触发：阿里云安全通告，PHP 披露两个 CVSS 9.2 的未授权远程代码执行漏洞，统称 php2shell。
> 处理时间：2026-10-22

## 漏洞要点

- 影响：PHP 5.x 起的全部版本（含 8.2–8.5），Apache mod_php、PHP-FPM、php-cgi、FrankenPHP、LiteSpeed 均受影响
- 成因：
  - CVE-2026-109618：OOM 异常路径下请求数据未初始化，清理阶段访问无效内存
  - CVE-2026-109619：全局状态未正确重置，导致跨请求 Use-After-Free
- 后果：未认证攻击者可以 Web 进程权限执行任意代码；官方已在 mod_php 与 FPM 下验证可利用，且已在野利用
- 修复：升级 PHP 到官方修复版本或发行版回移补丁；升级后必须重启全部 PHP 工作进程

## 本机受影响情况

| 项 | 状态 |
|---|---|
| PHP 版本 | 8.3.25（宝塔编译版，2025-10 构建，属于受影响范围） |
| php-fpm-83 | 原为 inactive + enabled（开机自启） |
| WordPress 站点 | `/www/wwwroot/wordpress`，vhost `127.0.0.1.conf` 以 `default_server` 监听 80 并 include `enable-php-83.conf` |
| phpMyAdmin | nginx `listen 888`，`root /www/server/phpmyadmin`，含 PHP handler |
| 星图 | 与 PHP 无关（Spring Boot :9020 + nginx 反代） |

结论：**虽然 PHP-FPM 当时未运行，但开机自启 + 80 端口 PHP handler 同时存在，重启即暴露。**

## 本次处置（已完成）

```bash
# 1. 彻底停用 PHP
systemctl disable php-fpm-83
systemctl stop php-fpm-83
# 结果：inactive / disabled，无进程、无 /tmp/php-cgi*.sock

# 2. 下线 WordPress vhost（保留文件，可回滚）
cd /www/server/panel/vhost/nginx
cp 127.0.0.1.conf /root/wordpress-vhost.conf.bak
mv 127.0.0.1.conf 127.0.0.1.conf.disabled
nginx -t && systemctl reload nginx

# 3. 关闭 phpMyAdmin 的公网入口（仅本机可访问）
iptables -I INPUT -p tcp --dport 888 -s 127.0.0.1 -j ACCEPT
iptables -I INPUT -p tcp --dport 888 -j DROP
service iptables save
```

验证结果：

```
http://<IP>/index.php      -> 404   （不再有 PHP handler）
http://<IP>/wp-login.php   -> 404
http://<IP>:888/           -> 404   （phpMyAdmin 不再执行 PHP）
php-fpm-83                 -> inactive / disabled
php 进程 / socket          -> 无
https://yanyunxiaoguang.cn -> 200（星图不受影响）
```

## 回滚 / 后续升级

如果以后还要用 WordPress：

1. 在宝塔面板「软件商店 → PHP 8.3」升级到官方修复版本（或发行版回移补丁），确认 `php -v` 为修复版本
2. 恢复 vhost：`mv 127.0.0.1.conf.disabled 127.0.0.1.conf`
3. 启动 FPM：`systemctl enable --now php-fpm-83`，并确认所有 worker 已重启
4. 按需放开 888 端口（不建议对公网开放 phpMyAdmin）

## 仍建议处理的暴露面

| 风险 | 建议 |
|---|---|
| 宝塔面板 8888 对公网开放 | 改随机端口 + 开启「面板授权 IP」，或安全组只放行自己 IP |
| SSH 22 对公网开放 + root 密码登录 | 改用密钥登录、禁用密码登录、fail2ban |
| 服务器 root 密码曾在聊天中传输 | 立即 `passwd root` 更换 |
| 阿里云安全组 | 只放行 80/443 与自己常用的运维 IP |
| 无 WAF/日志告警 | 需要时接入阿里云 WAF 或云安全中心 |

## 参考

- 阿里云安全防护指南：https://developer.aliyun.com/article/1773846
- PHP 官方 2026 安全公告归档：https://www.php.net/archive/2026.php
- GHSA-w8hw-w9fx-j79w（CVE-2026-109618）
- GHSA-6wmf-4rv5-p3rp（CVE-2026-109619）
- IPA 告警：https://www.ipa.go.jp/security/security-alert/2026/alert20261022.html

## 加固：SSH 与账号（2026-10-22）

| 项 | 处理 |
|---|---|
| SSH 登录 | 改为**仅密钥**：`PasswordAuthentication no`、`PermitRootLogin prohibit-password`、`KbdInteractiveAuthentication no` |
| 密钥 | 本机 `C:\Users\H\.ssh\xingtu_ed25519`（公钥已写入服务器 `/root/.ssh/authorized_keys`） |
| root 密码 | 已更换为随机强密码，仅用于云控制台 VNC 应急登录（SSH 已不接受密码） |
| 验证 | 密钥登录成功、密码登录被拒；`sshd -T` 显示 `passwordauthentication no` |

连服务器：

```powershell
ssh -i C:\Users\H\.ssh\xingtu_ed25519 root@47.109.88.107
```

sshd 原配置备份：`/root/sshd_config.bak.*`

### 宝塔面板端口

尝试用命令行改端口（写 `port.pl` + 重启 BT-Panel）后，面板自己的路由没跟着更新，访问返回 404，**已回滚到 8888**。
正确做法是在宝塔面板 UI 里改：**设置 → 面板端口**（它会同时更新自身配置），再到阿里云安全组放行新端口、关掉 8888；同时在 **设置 → 授权 IP** 里只放自己的出口 IP。

## 日常加固：面板收口 + 自动备份（2026-10-22）

### 面板只允许本机访问

宝塔面板 8888 不再对公网开放，只允许 127.0.0.1：

```bash
iptables -I INPUT 1 -p tcp --dport 8888 -s 127.0.0.1 -j ACCEPT
iptables -I INPUT 2 -p tcp --dport 8888 -j DROP
service iptables save
```

需要面板时用 SSH 隧道（本机执行）：

```powershell
ssh -i C:\Users\H\.ssh\xingtu_ed25519 -L 8888:127.0.0.1:8888 root@47.109.88.107
# 然后浏览器打开 http://127.0.0.1:8888
```

注意：验证时外网 `47.109.88.107:8888` 已 BLOCKED，但面板自身在本机也返回 404（nginx 页面），
说明面板 UI 在加固前就不可用（可能面板配置/初始化问题）。需要时用 `bt` 命令修复或重装面板，与本加固无关。

### 自动备份

- 脚本：`/opt/xingtu/backup.sh`（700）
- 内容：数据库 `mysqldump`（gzip 校验）+ `/opt/xingtu/data` 上传文件 + 关键配置（env / nginx vhost / systemd unit）
- 位置：`/root/backup/`，文件名带时间戳
- 保留：7 天（`find -mtime +7 -delete`）
- 定时：root crontab 每天 03:00，日志 `/var/log/xingtu-backup.log`
- 已验证：手动执行成功，产出 db/data/config 三个压缩包（当日总计约 220K）

建议后续把 `/root/backup` 同步到阿里云 OSS 做异地备份（宝塔计划任务 + OSS 插件即可）。

## 异地备份

### 已生效：拉到本机（Windows）

- 脚本：`workbench/scripts/pull-backups.ps1`（scp 拉取 `/root/backup/*.gz` 到 `D:\backups\xingtu`）
- 本地保留 14 天
- Windows 计划任务：`XingtuBackupPull`，每天 **03:30**（交互式，需当前用户登录）
- 已实测：首次拉取 7 个文件成功

这样即使服务器整机损坏，最近一天的数据库与上传文件在本机仍有一份。

### 已就绪、待填凭据：阿里云 OSS

- 上传脚本：`/opt/xingtu/oss_upload.py`（依赖已装 oss2 2.19.1）
- 配置模板：`/opt/xingtu/oss.env.example` → 复制为 `oss.env` 并填 4 个值
- `backup.sh` 末尾已挂钩：存在 `oss.env` 时自动上传，远端保留 30 天
- 当前状态：无 `oss.env`，脚本输出 `no oss.env, skip`（不影响本地备份）

需要的信息：bucket 名称/地域、**仅授权该 bucket 的 RAM 子账号** AK（不要主账号 AK）。
推荐 endpoint 用内网地址（`oss-cn-<region>-internal.aliyuncs.com`），上传不走公网流量费。

### 备份链路

```
MySQL + /opt/xingtu/data
   → /root/backup（本地 7 天，03:00）
   → D:\backups\xingtu（本机 14 天，03:30）
   → OSS（待配置，远端 30 天）
```
