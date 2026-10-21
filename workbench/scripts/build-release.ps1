# 构建可部署的单 jar：三个前端打进后端 static，一个端口提供全部页面
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$web = Join-Path $root 'web'
$server = Join-Path $root 'server'
$staticDir = Join-Path $server 'workbench-app\src\main\resources\static'

Write-Host '==> 1/4 构建三个前端（带 base 路径）' -ForegroundColor Cyan
Push-Location $web
try {
    pnpm build
} finally {
    Pop-Location
}

Write-Host '==> 2/4 拷贝 dist 到后端 static' -ForegroundColor Cyan
if (Test-Path $staticDir) { Remove-Item -Recurse -Force $staticDir }
New-Item -ItemType Directory -Force -Path $staticDir | Out-Null

Copy-Item -Recurse -Force (Join-Path $web 'apps\staff\dist\*') $staticDir
New-Item -ItemType Directory -Force -Path (Join-Path $staticDir 'admin') | Out-Null
Copy-Item -Recurse -Force (Join-Path $web 'apps\admin\dist\*') (Join-Path $staticDir 'admin')
New-Item -ItemType Directory -Force -Path (Join-Path $staticDir 'm') | Out-Null
Copy-Item -Recurse -Force (Join-Path $web 'apps\mobile\dist\*') (Join-Path $staticDir 'm')

Write-Host '==> 3/4 打包后端 jar' -ForegroundColor Cyan
Push-Location $server
try {
    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot'
    }
    $settings = Join-Path $env:TEMP 'mvn-aliyun.xml'
    if (Test-Path $settings) {
        mvn -s $settings -q -DskipTests package
    } else {
        mvn -q -DskipTests package
    }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $server 'workbench-app\target\workbench-app-*.jar') | Select-Object -First 1
Write-Host ("==> 4/4 完成：{0} ({1:N1} MB)" -f $jar.FullName, ($jar.Length / 1MB)) -ForegroundColor Green
Write-Host '部署：把 jar 拷到服务器，配置 deploy/workbench.env 后运行 deploy/run-backend.ps1'
