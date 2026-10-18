# 非 Docker 部署核对

## 结论

可以把 DooTask 核心服务部署到宿主机运行，但仓库目前没有受支持的一键免 Docker 安装/升级流程。README 的安装方式和 `bin/install`、`./cmd install/update` 都依赖 Docker Compose；不要用这些命令做非 Docker 部署。

非 Docker 部署不会改变任务、项目、里程碑、模板和保存视图的数据模型或业务规则，但需要自行管理 PHP/Swoole、数据库、Redis、反向代理、后台任务和升级。容器化 App Store 插件管理依赖 Docker Socket；未提供替代运行时，因此插件安装/升级及依赖该插件的能力不能假设可用。全文搜索依赖 Manticore 插件，需单独提供兼容服务并配置 `SEARCH_HOST`/`SEARCH_PORT`。在线授权依赖仓库中的 `doo.so` FFI 库，免 Docker 主机必须自行提供兼容的库文件和 FFI 配置；默认读取 `/usr/lib/doo/doo.so`，可通过 `DOOTASK_LIBRARY_PATH` 指定其他绝对路径。

## 从仓库确认的运行要求

- Composer 要求 PHP `^8.3`；仓库部署基线和当前容器镜像使用 PHP 8.4。还需安装 `composer.json` 声明的 PHP 扩展，以及 LaravelS 使用的 Swoole 扩展。
- `hhxsv5/laravel-s` 当前列在 `require-dev`。按仓库容器安装方式执行 `composer install --optimize-autoloader` 会安装它；不要加 `--no-dev`，除非先把运行时依赖移到 `require` 并完成发布验证。
- 需要 MySQL/MariaDB 和 Redis。按所启用的功能，另需 Manticore Search、AI 服务、邮件服务和 `doo.so` 授权库。
- 前端构建要求 Node.js 20+。`cmd` 的生产构建实际调用 `npx vite build -- fromcmd`。
- LaravelS 负责 HTTP 和 WebSocket。Nginx/其他反向代理必须代理普通请求和 `/ws`，并将静态资源回退到 LaravelS 前先检查 `public/` 文件。
- 定时任务不能遗漏：仓库容器每分钟请求本机 `/crontab`；该入口投递自动归档、提醒、周期任务、Manticore 同步等 Swoole 后台任务。免 Docker 环境必须设置定时器并确保请求来自本机/受信内网地址。里程碑提醒由每分钟任务按各项目排期时区判断，在提醒日当地 09:00 后投递，并在投递失败时于目标日期前持续重试；项目时区未设置或无效时回退到应用时区。`.env` 的 `TIMEZONE` 默认是 `PRC`，应按部署地区设置，确保回退时区和其他系统提醒符合预期。
- 里程碑提醒用 Laravel 原子缓存锁避免多实例并发处理同一提醒，并用消息表的持久化唯一幂等键阻止重试时重复创建聊天消息。幂等键关联里程碑的提醒周期号；重放时还会校验会话、类型、发送人和规范化后的消息内容，并静默重投 WebSocket 更新，不重复触发机器人或 App 推送。投递队列拒绝任务会记为失败并在当日重试；周期号只在目标日期或提醒提前天数变化、从而重置提醒时递增，普通名称/负责人编辑不会改变重试键。单机可使用默认文件缓存；多实例部署时，各节点必须配置相同的共享缓存后端（建议 Redis）和一致的 `CACHE_PREFIX`，并确保所有调度节点连接同一缓存服务，否则各节点的本地文件锁无法互斥。消息在线推送仍由异步任务完成；若异步任务持续不可用，聊天记录会保留，客户端重新同步可补齐消息。

## 手工部署步骤（供运维验证后采用）

以下是基于现有配置整理的宿主机流程，不代表仓库已提供免 Docker 支持承诺。生产投用前需在目标操作系统完成 PHP 扩展、Swoole、服务守护、反向代理及恢复演练。

1. 安装 PHP 8.4、Composer、Node.js 20、MySQL/MariaDB、Redis、Nginx，以及 Swoole 和 `composer.json` 所需 PHP 扩展。为应用准备独立系统用户和可写的 `storage/`、`bootstrap/cache/`、上传目录。克隆代码后可用 `sudo install -d -o dootask -g dootask -m 0750 /srv/dootask/public/uploads` 创建示例上传目录，并按实际路径和运行用户调整。
2. 克隆代码并安装依赖、创建环境文件：

   ```bash
   git clone https://github.com/kuaifan/dootask.git
   cd dootask
   cp .env.example .env
   composer install --optimize-autoloader
   npm ci
   ```

3. 编辑 `.env`：设置 `APP_ENV=production`、`APP_DEBUG=false`、正确的 `APP_URL`、适合部署地区的 `TIMEZONE`、数据库和 Redis 连接；设置 `LARAVELS_LISTEN_IP=127.0.0.1` 及未占用的 `LARAVELS_LISTEN_PORT`。生产环境会校验请求主机名，仅接受 `APP_URL` 主机及其子域；若确实通过其他域名访问，用逗号分隔配置 `APP_TRUSTED_HOSTS` 的精确主机名。若授权库不在默认目录，设置 `DOOTASK_LIBRARY_PATH=/实际路径/doo.so`。首次安装时让 `APP_KEY` 保持未设置，已有实例则保留原有 `APP_KEY`。只有部署了对应服务时才配置 Manticore/AI 地址。
4. 首次安装生成密钥，构建网页并运行数据库迁移：

   ```bash
   php artisan key:generate
   npx vite build -- fromcmd
   php artisan migrate --force
   ```

   只有全新空数据库需要项目默认初始数据时，才在审阅 `database/seeders/DatabaseSeeder.php` 后单独执行 `php artisan db:seed --force`。默认 Seeder 会插入示例数据；`SettingsTableSeeder` 在特定旧设置格式下还会清空设置表，因此不要在已有实例上执行。

   升级已有实例时保留原 `APP_KEY`，不要运行 `key:generate`，否则现有加密数据和会话可能无法读取。不要在生产数据上使用 `migrate:fresh` 或重新 seed；迁移前先备份数据库和上传文件。
5. 在安装依赖、配置 `.env` 并完成数据库迁移后，用应用运行用户执行部署预检：

   ```bash
   php artisan dootask:doctor
   ```

   命令检查 PHP/Composer 声明的扩展、Swoole/LaravelS、FFI 与授权库文件是否可加载、生产环境/密钥/调试配置、LaravelS 回环监听、运行目录和 `public/uploads` 写权限、数据库迁移状态、数据库与 Redis 连通，以及缓存锁能否阻止竞争获取并在释放后重获。存在失败项时会返回非零状态并列出原因；通过只代表这些本机前置条件可用，不替代跨进程/多实例锁验证、WebSocket 授权消息、备份恢复、多客户端和真实负载验收。预检会尝试加载授权库、实际连接数据库与 Redis，并短暂创建、竞争、释放一个随机缓存锁。自动化流水线可执行 `php artisan dootask:doctor --json` 获取 `status`、通过/失败计数和逐项结果；命令仍会在失败时返回非零退出码。JSON 的诊断详情可能包含本机路径或服务地址，应按运维诊断信息处理。
6. 由 systemd 或 Supervisor 以应用用户长期运行 `php bin/laravels start -i`，配置故障自动重启。由 Nginx 将动态 HTTP 请求和 `/ws` 转发至 `LARAVELS_LISTEN_IP: LARAVELS_LISTEN_PORT`，同时提供 `public/` 中的静态文件和上传文件。
7. 配置每分钟从本机调用 `http://127.0.0.1:<LARAVELS_LISTEN_PORT>/crontab`。必须在公网 Nginx 层拒绝 `/crontab`、其子路径和大小写变体，并在转发时清空客户端传入的 `Client-IP`、以 `$remote_addr` 覆盖 `X-Forwarded-For`；不要将该路径作为公网可访问的定时任务接口，也不要把 LaravelS 监听端口暴露到公网。若 Nginx 位于负载均衡器后面，仅对明确可信的代理网段启用 `ngx_http_realip_module`，再把校验后的 `$remote_addr` 转发给应用。
8. 如需在线授权续期，另设每小时运行一次 `php artisan online-license:renew` 的 systemd timer/cron，并验证 `doo.so` 可由 PHP FFI 加载。它是容器 supervisor 配置的替代运行安排，仓库没有免 Docker 服务单元。
9. 升级前备份数据库、`.env`、`public/uploads` 和本地应用数据；部署新代码后执行 Composer 安装、前端构建及 `php artisan migrate --force`，再运行 `php artisan dootask:doctor` 并平滑重启 LaravelS。免 Docker 环境不能使用 `./cmd update`。

   本项目新增功能的迁移会创建表或增加字段；对应 `down()` 会删除这些新表/字段以及其中的数据。回退应用代码时通常应保留新增 schema，避免运行会回退整批迁移的 `migrate:rollback`。只有确认不再需要新功能数据、并已备份数据库后，才执行 schema 回退；恢复到迁移前状态时应使用升级前的完整数据库备份。

## 数据库与文件备份恢复 runbook

`./cmd mysql backup/recovery` 通过 Docker 容器执行，不能用于免 Docker 部署。非 Docker 环境应由运维在维护窗口分别备份数据库、应用状态文件和上传目录，并把备份复制到另一台主机或受控备份存储。备份目录和凭据文件必须仅允许运维/应用服务用户读取；`.env`、数据库凭据和其中的 `APP_KEY` 属于敏感数据。

为保证数据库与文件引用一致，先暂停新写入：开启维护页（如已通过 LaravelS 配置维护模式），停止 `dootask-crontab.timer` 和正在运行的 `dootask-crontab.service`，停止 LaravelS 服务，并等待已有后台任务结束。不要在上传仍可写入时并行打数据库和文件快照。MariaDB/MySQL 凭据放入仅运维可读的客户端文件，例如 `/etc/dootask/mariadb-client.cnf`（`[client]` 段包含 `host`、`user`、`password`；权限设为 `0600`）；不要把密码写进命令行、shell 历史或日志。以下变量由运维按实际环境填写：

```bash
set -euo pipefail
db_name=dootask_prod
app_dir=/srv/dootask
backup_root=/srv/backups/dootask
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_dir="$backup_root/$stamp"
install -d -m 0700 "$backup_root"
mkdir -m 0700 "$backup_dir"

mariadb-dump --defaults-extra-file=/etc/dootask/mariadb-client.cnf \
  --single-transaction --routines --triggers --events --hex-blob \
  --default-character-set=utf8mb4 "$db_name" \
  | gzip -c > "$backup_dir/database.sql.gz"
tar -czf "$backup_dir/app-state.tar.gz" \
  --directory="$app_dir" .env public/uploads storage/app
gzip -t "$backup_dir/database.sql.gz"
tar -tzf "$backup_dir/app-state.tar.gz" >/dev/null
(cd "$backup_dir" && sha256sum database.sql.gz app-state.tar.gz > SHA256SUMS)
```

`--single-transaction` 只为事务型表（通常为 InnoDB）提供一致性快照；混有非事务表时，必须继续保持写入暂停或按数据库备份手册使用锁定方案。完成两个归档并校验后，再启动 `dootask.service` 和 `dootask-crontab.timer`。按组织策略加密并异地复制整个备份目录；单独留在应用主机上的副本不算可恢复备份。

恢复演练必须恢复到独立空库和临时目录，不覆盖生产数据：

```bash
cd /srv/backups/dootask/<backup-timestamp>
sha256sum -c SHA256SUMS
gzip -t database.sql.gz
tar -tzf app-state.tar.gz >/dev/null
target_commit='<target-commit>'
install -d -m 0700 /srv/dootask-restore
git clone https://github.com/kuaifan/dootask.git /srv/dootask-restore/code
git -C /srv/dootask-restore/code checkout "$target_commit"
cd /srv/dootask-restore/code
composer install --optimize-autoloader
mariadb --defaults-extra-file=/etc/dootask/mariadb-restore.cnf \
  -e "CREATE DATABASE dootask_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
gzip -dc database.sql.gz | mariadb \
  --defaults-extra-file=/etc/dootask/mariadb-restore.cnf dootask_restore
tar -xzf app-state.tar.gz --directory=/srv/dootask-restore/code
# Update only this isolated copy's .env to use dootask_restore; keep the backup's APP_KEY.
npm ci
npx vite build -- fromcmd
```

将恢复凭据文件限定为只访问恢复目标库；在恢复代码副本中仅修改该副本的 `.env`，将数据库连接改指向 `dootask_restore`，保留备份中的 `APP_KEY`，并保持服务只绑定本机、不得接入生产流量。恢复后比较迁移状态、关键表行数、抽样业务记录，以及 `public/uploads` 中文件数量/摘要与数据库引用。记录从停写到服务恢复的总时间和数据丢失窗口；确认归档与比较结果后，按团队清理流程销毁临时恢复库、临时目录和恢复凭据。该 runbook 是供运维审阅的范例；2026-10-18 的本地 MariaDB 空库及 demo 用户记录恢复和上传目录 tar 清单冒烟见[验收证据](release-evidence/2026-10-18-wsl-local.md)，尚未在目标生产版本、脱敏业务数据和实际文件引用上验证。

## systemd 与 Nginx 配置样例

以下样例假设代码位于 `/srv/dootask`、PHP 位于 `/usr/bin/php`、LaravelS 监听 `127.0.0.1:5200`。按实际路径、用户、PHP 版本和数据库服务名调整；应用用户必须能写入 `storage/`、`bootstrap/cache/`、上传目录及仓库内需要更新的运行配置目录。

### LaravelS 服务

保存为 `/etc/systemd/system/dootask.service`：

```ini
[Unit]
Description=DooTask LaravelS application server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=dootask
Group=dootask
WorkingDirectory=/srv/dootask
Environment=APP_ENV=production
Environment=APP_DEBUG=false
ExecStart=/usr/bin/php /srv/dootask/bin/laravels start -i
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
KillSignal=SIGTERM

[Install]
WantedBy=multi-user.target
```

`.env` 由 Laravel 在应用目录读取，不要把整份文件直接作为 systemd `EnvironmentFile` 加载。启动并查看日志：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now dootask.service
sudo systemctl status dootask.service
sudo journalctl -u dootask.service -f
```

### 每分钟调度入口

保存服务为 `/etc/systemd/system/dootask-crontab.service`：

```ini
[Unit]
Description=Run DooTask scheduled jobs
After=dootask.service

[Service]
Type=oneshot
User=dootask
Group=dootask
ExecStart=/usr/bin/curl --fail --silent --show-error --max-time 50 http://127.0.0.1:5200/crontab
```

保存定时器为 `/etc/systemd/system/dootask-crontab.timer`：

```ini
[Unit]
Description=Run DooTask scheduler every minute

[Timer]
OnCalendar=*-*-* *:*:00
AccuracySec=1s
Persistent=true
Unit=dootask-crontab.service

[Install]
WantedBy=timers.target
```

`/crontab` 不应对公网开放。下面的 Nginx 样例会拒绝外部 HTTP 调用；定时器直接请求只监听回环地址的 LaravelS。

### 在线授权续期（可选）

仅在使用在线授权且已确认 PHP FFI 能加载 `doo.so` 时启用。保存为 `/etc/systemd/system/dootask-license.service`：

```ini
[Unit]
Description=Renew DooTask online license
After=network-online.target dootask.service

[Service]
Type=oneshot
User=dootask
Group=dootask
WorkingDirectory=/srv/dootask
ExecStart=/usr/bin/php /srv/dootask/artisan online-license:renew
```

保存为 `/etc/systemd/system/dootask-license.timer`：

```ini
[Unit]
Description=Renew DooTask online license hourly

[Timer]
OnBootSec=5min
OnUnitActiveSec=1h
Unit=dootask-license.service

[Install]
WantedBy=timers.target
```

启用定时任务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now dootask-crontab.timer
sudo systemctl enable --now dootask-license.timer  # 不使用在线授权时跳过
systemctl list-timers 'dootask-*'
```

### Nginx HTTP 与 WebSocket 代理

可保存为 `/etc/nginx/conf.d/dootask.conf`。此配置服务静态文件并将动态请求和 WebSocket 转发给 LaravelS。示例监听 HTTP 80；启用本机 TLS 时需按证书增加 HTTPS server。若 HTTPS 在可信前置代理终止、本机 Nginx 收到的是 HTTP，应确保本机 Nginx 只接受该代理流量，并在动态请求和 WebSocket 两处把 `X-Forwarded-Proto` 固定为 `https`，或仅传递经网络边界验证的代理协议头；否则示例中的 `$scheme` 会把外部 HTTPS 识别为 HTTP。不要直接信任公网客户端提供的 `X-Forwarded-Proto`。Docker Nginx 默认也会基于自身 `$scheme` 设置该头；TLS 在前置代理终止时，应显式设置 `APP_SCHEME=https`，不要把客户端传入值透传给应用。

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    ""      close;
}

upstream dootask_laravels {
    server 127.0.0.1:5200;
    keepalive 16;
}

server {
    listen 80;
    server_name dootask.example.com;
    root /srv/dootask/public;
    index index.html;
    client_max_body_size 1024m;

    location ~* ^/crontab($|/) {
        return 404;
    }

    location = / {
        try_files /index.html @dootask_laravels;
    }

    location = /ws {
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $http_host;
        proxy_set_header Client-IP "";
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Real-PORT $remote_port;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_pass http://dootask_laravels;
    }

    location / {
        try_files $uri @dootask_laravels;
    }

    location @dootask_laravels {
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $http_host;
        proxy_set_header Client-IP "";
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Real-PORT $remote_port;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_pass http://dootask_laravels;
    }

    location ~ /\.(?!well-known).* {
        deny all;
    }
}
```

检查并加载配置，再从本机验证 LaravelS 健康状态、HTTP 页面和 WebSocket 连接：

```bash
sudo nginx -t
sudo systemctl reload nginx
curl --fail http://127.0.0.1:5200/health
```

上面的 `curl` 只验证 HTTP 存活。应使用带有效授权令牌的兼容 WebSocket 客户端连接 `ws://127.0.0.1:5200/ws?action=web&token=<有效令牌>&language=zh&platform=web`，验证连接认证及消息往返；仅收到 `101 Switching Protocols` 不能证明应用消息流程可用。

## 当前限制和后续发布要求

- 已在 Ubuntu 22.04 WSL 非 Docker 环境用 PHP 8.4.26、Swoole 6.2.3、LaravelS 3.8.7 和 MariaDB 10.6 验证空库全量迁移、LaravelS HTTP/WebSocket 监听及 `/health` 返回 `200 ok`；免 Docker Nginx 代理验证 `/health` 返回 200、`/crontab` 及其尾斜杠/子路径/大小写变体在伪造内网转发头时仍返回 404、`/ws` 升级握手返回 101；Docker Nginx 模板替换运行时占位后也启动验证了任务入口返回 404；假上游实测 `Client-IP` 被剔除且伪造的 `X-Forwarded-For` 被替换为 Nginx 对端地址；Laravel Redis store 的缓存读写、锁互斥和释放均通过；五个 systemd service/timer 样例通过 `systemd-analyze verify`。这不是干净 Linux 宿主机的安装验收；WSL 没有 systemd 守护进程，无法验证服务启停与定时器触发；带授权的 WebSocket 消息交互、多实例共享缓存锁、浏览器/多客户端、邮件、Manticore 和真实负载也未验证。
- 2026-10-18 另在临时隔离 MariaDB 空库跑通 251 条全量迁移、最近六条迁移回退/重升，以及含两条 demo 记录的 dump→独立库恢复并摘要比对；两个 Laravel CLI 进程对临时 Redis 锁的互斥、释放、TTL 到期也通过。Nginx 文档配置 `nginx -t` 和 5 个 systemd 单元样例 `systemd-analyze verify` 返回成功。原始本地证据见[发布验收记录](release-evidence/2026-10-18-wsl-local.md)。这些本地冒烟仍不能替代真实生产数据/文件恢复、两份完整应用实例的业务并发或干净主机服务运行。
- `/health` 是轻量存活探针，不检查数据库、Redis 或授权库状态；业务页面/API 仍依赖授权 FFI 库 `doo.so`。无该库的环境可启动 LaravelS 并使用存活探针，但不能据此判断业务功能完整可用。
- 当前已完成空库迁移和两条 demo 用户记录的 dump/恢复冒烟，并补充非 Docker 备份/恢复 runbook；尚未在生产同版本脱敏数据库副本上演练升级与恢复；上线前仍应验证升级完成、旧版代码回退时保留新 schema、以及从升级前完整备份恢复三条路径。
- `app/Tasks/ManticoreSyncTask.php` 和授权 FFI 初始化已去掉 `/var/www` 应用目录假设；仍需提供 Manticore、授权库，并验证后台子进程在目标系统上的权限和生命周期。
- `docker-compose.yml` 中的 App Store 服务挂载 Docker Socket。没有 Docker 时应按“不提供容器化插件管理”规划；不能把关闭 Docker 理解为所有附加服务都自动可用。
- 要将免 Docker 部署提升为正式支持方式，仍需在干净 Linux 主机上验证这些 systemd/Nginx 样例、环境变量、备份恢复和回滚步骤，并完成安装、升级、WebSocket、定时任务和重启恢复验收。
- 发布前剩余的数据库升级/恢复、多实例锁、WebSocket 消息、多端操作和代表性负载验收步骤及证据模板见[发布验收清单](release-acceptance-checklist.md)。
