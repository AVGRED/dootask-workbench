# 星图部署说明

## 1. 后端（jar 方式长期运行）

```powershell
cd D:\repos\workbench\dootask\workbench\server

# 首次：构建 jar
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
mvn -DskipTests package

# 首次：准备配置
Copy-Item deploy\workbench.env.example deploy\workbench.env
# 编辑 deploy\workbench.env，务必修改 JWT_SECRET（至少 32 字符随机串）

# 启动 / 停止
powershell -ExecutionPolicy Bypass -File deploy\run-backend.ps1
powershell -ExecutionPolicy Bypass -File deploy\stop-backend.ps1
```

- 日志：`server/logs/workbench.log`（错误另见 `.log.err`）
- PID 文件：`server/workbench.pid`
- 健康检查：`http://127.0.0.1:9020/actuator/health`
- **JWT_SECRET 为默认值或短于 32 字符时后端会拒绝启动**，这是故意的

开机自启（可选，需管理员 PowerShell）：

```powershell
# 创建一个计划任务，登录时自动拉起
$action = New-ScheduledTaskAction -Execute "powershell.exe" `
  -Argument "-NoProfile -ExecutionPolicy Bypass -File D:\repos\workbench\dootask\workbench\server\deploy\run-backend.ps1"
Register-ScheduledTask -TaskName "WorkbenchBackend" -Action $action -Trigger (New-ScheduledTaskTrigger -AtLogOn) -RunLevel Highest
```

## 2. 前端（构建产物）

```powershell
cd D:\repos\workbench\dootask\workbench\web
pnpm build
# 产物：apps/staff/dist、apps/admin/dist、apps/mobile/dist
```

生产环境用 nginx 或任意静态服务器托管这三个 `dist` 目录，并把 `/api` 反向代理到后端：

```nginx
server {
    listen 5180;
    root D:/repos/workbench/dootask/workbench/web/apps/staff/dist;
    location / { try_files $uri $uri/ /index.html; }   # history 路由必须回退 index.html
    location /api/ { proxy_pass http://127.0.0.1:9020; }
}
```

管理端（5181）、移动端（5182）同理，指向各自的 `dist`。

**不要用 `pnpm dev` 做长期服务**：dev server 无压缩、无缓存、进程随终端退出（今天就因此挂过两次）。

## 3. 备份（务必做）

```powershell
# 数据库
mysqldump -u wb -p workbench > backup\workbench-$(Get-Date -Format yyyyMMdd).sql

# 上传文件与头像
Compress-Archive -Path server\data\uploads\* -DestinationPath backup\uploads-$(Get-Date -Format yyyyMMdd).zip
```

建议做成每日计划任务，保留最近 14 天。

## 4. 上线前检查清单

- [ ] `JWT_SECRET` 已改为随机长串（非默认值）
- [ ] 数据库账号密码已改（不再是 wb/wb）
- [ ] `demo@workbench.local` 的密码已改，或该账号角色已降为 staff
- [ ] `STORAGE_ROOT` 指向持久化磁盘并纳入备份
- [ ] 前端用 build 产物 + nginx，不使用 dev server
- [ ] 后端用 jar + 启动脚本（或计划任务/服务）托管
- [ ] 配置了数据库与上传目录的定期备份
## 5. 测试账号与密码

演示账号 `demo@workbench.local` 的密码已从默认值轮换，新密码在 `deploy/workbench.env` 的 `DEMO_PASSWORD`（该文件已被 gitignore，不会入库）。

运行回归测试前先导出环境变量：

`powershell
$env:WORKBENCH_DEMO_PASSWORD='<deploy/workbench.env 里的 DEMO_PASSWORD>'
powershell -File workbench/scripts/e2e-smoke.ps1 -BaseUrl http://127.0.0.1:9020
pnpm --filter @workbench/e2e test:api
`

管理员重置任意用户密码：

`
PUT /api/v1/users/{userid}/password
{ "password": "新密码（至少 8 位）" }
`

需管理员令牌；重置后旧令牌仍有效直到过期。
