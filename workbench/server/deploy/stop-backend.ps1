# 停止工作台后端
# 用法：powershell -ExecutionPolicy Bypass -File deploy/stop-backend.ps1

$serverDir = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $serverDir "workbench.pid"

$stopped = $false
if (Test-Path $pidFile) {
  $processId = (Get-Content $pidFile | Select-Object -First 1)
  if ($processId -and (Get-Process -Id $processId -ErrorAction SilentlyContinue)) {
    Stop-Process -Id $processId -Force
    Write-Host "已停止后端（PID $processId）" -ForegroundColor Green
    $stopped = $true
  }
  Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

if (-not $stopped) {
  $conn = Get-NetTCPConnection -LocalPort 9020 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($conn) {
    Stop-Process -Id $conn.OwningProcess -Force
    Write-Host "已停止占用 9020 端口的进程（PID $($conn.OwningProcess)）" -ForegroundColor Yellow
  } else {
    Write-Host "后端未在运行"
  }
}
