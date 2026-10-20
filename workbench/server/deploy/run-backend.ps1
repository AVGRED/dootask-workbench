# 启动星图后端（jar 方式，脱离终端长期运行）
# 用法：powershell -ExecutionPolicy Bypass -File deploy/run-backend.ps1

$ErrorActionPreference = "Stop"
$serverDir = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $PSScriptRoot "workbench.env"

if (-not (Test-Path $envFile)) {
  Write-Host "缺少配置文件：$envFile" -ForegroundColor Red
  Write-Host "请先执行：Copy-Item deploy/workbench.env.example deploy/workbench.env，并修改 JWT_SECRET" -ForegroundColor Yellow
  exit 1
}

# 读取 KEY=VALUE 环境变量（忽略注释与空行）
Get-Content $envFile -Encoding UTF8 | ForEach-Object {
  $line = $_.Trim()
  if ($line -eq "" -or $line.StartsWith("#")) { return }
  $index = $line.IndexOf("=")
  if ($index -le 0) { return }
  $key = $line.Substring(0, $index).Trim()
  $value = $line.Substring($index + 1).Trim()
  [Environment]::SetEnvironmentVariable($key, $value, "Process")
}

# 拒绝默认密钥，避免带示例配置上线
if ($env:JWT_SECRET -eq "请替换成至少32字符的随机串" -or $env:JWT_SECRET.Length -lt 32) {
  Write-Host "JWT_SECRET 未修改或长度不足 32 字符，拒绝启动" -ForegroundColor Red
  exit 1
}

$jar = Get-ChildItem (Join-Path $serverDir "workbench-app\target\workbench-app-*.jar") |
  Where-Object { $_.Name -notlike "*sources*" } |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

if (-not $jar) {
  Write-Host "找不到 jar，请先在 server 目录执行：mvn -DskipTests package" -ForegroundColor Red
  exit 1
}

$logDir = Join-Path $serverDir "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir "workbench.log"
$pidFile = Join-Path $serverDir "workbench.pid"

# 已在运行则先提示
if (Test-Path $pidFile) {
  $oldPid = (Get-Content $pidFile | Select-Object -First 1)
  if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
    Write-Host "后端已在运行（PID $oldPid），如需重启先执行 deploy/stop-backend.ps1" -ForegroundColor Yellow
    exit 0
  }
}

$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
$process = Start-Process -FilePath $java `
  -ArgumentList @("-jar", "`"$($jar.FullName)`"") `
  -WorkingDirectory $serverDir `
  -RedirectStandardOutput $logFile `
  -RedirectStandardError "$logFile.err" `
  -WindowStyle Hidden `
  -PassThru

$process.Id | Set-Content -Path $pidFile -Encoding ASCII
Write-Host "后端已启动：PID $($process.Id)" -ForegroundColor Green
Write-Host "日志：$logFile"
Write-Host "健康检查：http://127.0.0.1:$($env:SERVER_PORT)/actuator/health"
Start-Sleep -Seconds 8
try {
  $health = Invoke-RestMethod -Uri "http://127.0.0.1:$($env:SERVER_PORT)/actuator/health" -TimeoutSec 10
  Write-Host "健康状态：$($health.status)" -ForegroundColor Green
} catch {
  Write-Host "启动后 8 秒内健康检查未通过，请查看日志" -ForegroundColor Yellow
}
