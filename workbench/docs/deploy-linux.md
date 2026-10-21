# 星图 · 生产部署（阿里云 Linux + nginx + systemd）

> 目标：一个域名、一个 jar，员工端 / 管理端 / 移动端 + 接口全在这台机器上。

## 一、拓扑

```
用户 → https://yanyunxiaoguang.cn（nginx :443，Let's Encrypt/宝塔证书）
        └── proxy_pass → 127.0.0.1:9020（xingtu.service，Spring Boot jar）
              ├── /        员工端（jar 内 static/）
              ├── /admin/  管理端（jar 内 static/admin/）
              ├── /m/      移动端（jar 内 static/m/）
              ├── /api/**  接口
              └── MariaDB 127.0.0.1:3306（库 workbench）
```

三个前端由同一个 jar 托管，所以**只有一个域名、没有 CORS**；SPA 深链接由 `SpaWebConfig` 回退到各自 index.html。

## 二、本机构建

```powershell
cd workbench
powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
# 产物：server/workbench-app/target/workbench-app-0.1.0-SNAPSHOT.jar（约 43MB，含三个前端）
```

构建脚本做了三件事：`pnpm build`（base 分别是 `/`、`/admin/`、`/m/`）→ 复制到 `static/` → `mvn package`。

## 三、服务器初始化（只需一次）

```bash
dnf install -y java-21-alibaba-dragonwell-headless
useradd -r -s /sbin/nologin xingtu
mkdir -p /opt/xingtu/{logs,data}
```

MariaDB 建库（root 密码见 `/root/xingtu-credentials.txt`）：

```sql
CREATE DATABASE workbench CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'workbench'@'localhost' IDENTIFIED BY '<密码>';
GRANT ALL PRIVILEGES ON workbench.* TO 'workbench'@'localhost';
FLUSH PRIVILEGES;
```

## 四、部署 / 更新

```bash
# 上传 jar（本机执行）
scp server/workbench-app/target/workbench-app-0.1.0-SNAPSHOT.jar root@47.109.88.107:/opt/xingtu/workbench.jar

# 服务器执行
systemctl restart xingtu
curl -fsS http://127.0.0.1:9020/actuator/health
```

## 五、环境变量 `/opt/xingtu/workbench.env`

| 变量 | 说明 |
|---|---|
| `SERVER_PORT` | 9020（只监听本机，由 nginx 反代） |
| `SPRING_DATASOURCE_*` | MariaDB 连接，库 `workbench` |
| `JWT_SECRET` | ≥32 字符随机串，泄露等于所有令牌可伪造 |
| `STORAGE_ROOT` | 附件/头像目录，默认 `/opt/xingtu/data/uploads` |
| `JAVA_OPTS` | `-Xms256m -Xmx512m`（2G 内存机器的安全值） |

## 六、nginx 站点

文件：`/www/server/panel/vhost/nginx/yanyunxiaoguang.cn.conf`

要点：
- 80 只保留 `/\.well-known/acme-challenge/` 用于续期，其余 301 到 https
- 443 `proxy_pass http://127.0.0.1:9020`，带 `X-Forwarded-Proto`、`client_max_body_size 20m`
- 静态资源压缩交给 nginx（jar 内是已经构建好的 dist）

原始个人站配置备份：`/root/yanyunxiaoguang-personal-site.conf.bak`，站点文件仍在 `/var/www/personal-space`（未删除，回滚只需恢复配置并 reload nginx）。

## 七、日常运维

```bash
systemctl status xingtu          # 服务状态
tail -f /opt/xingtu/logs/app.log # 应用日志
journalctl -u xingtu -n 100      # systemd 日志
systemctl restart xingtu         # 重启
```

备份（建议加进 crontab）：

```bash
mysqldump -uroot -p'<root密码>' --single-transaction workbench | gzip > /root/backup/workbench-$(date +%F).sql.gz
tar czf /root/backup/uploads-$(date +%F).tar.gz /opt/xingtu/data/uploads
```

## 八、升级前检查清单

1. `scripts\build-release.ps1` 本机构建通过
2. 上传 jar 前先 `mysqldump` 备份数据库
3. `systemctl restart xingtu` 后 `curl /actuator/health` 为 UP
4. 浏览器确认 `/`、`/admin/`、`/m/` 三个入口都能打开
5. 回滚：换回上一个 jar + `systemctl restart xingtu`

## 九、已知限制

- 单机部署：应用与数据库同机，机器挂了就全挂；需要高可用再拆库
- 2G 内存：不要随意调大 `-Xmx`，也不要和 WordPress/PHP 同时跑（本机已停 `php-fpm-83`）
- 证书续期：站点 443 用的是宝塔 `ssl/` 下证书，续期由宝塔任务负责；若换 acme.sh 需改路径
- 附件/头像存本机磁盘，迁机器时要一起搬 `/opt/xingtu/data`
