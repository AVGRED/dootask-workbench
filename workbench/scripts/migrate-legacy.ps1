#requires -Version 5.1
<#
.SYNOPSIS
    旧 dootask 数据 → 星图 v2 迁移。

.DESCRIPTION
    调用已打包的 workbench-app jar，以「无 Web 模式」运行迁移器：
    先 dry-run 统计，再按需实际写入。旧库缺少新列时自动填默认值。

.EXAMPLE
    powershell -File workbench/scripts/migrate-legacy.ps1 -SourceUrl "jdbc:mysql://127.0.0.1:3306/dootask" -DryRun
#>
param(
    [Parameter(Mandatory = $true)][string]$SourceUrl,
    [string]$SourceUser = "root",
    [string]$SourcePassword = "",
    [string]$TargetUrl = "jdbc:mysql://127.0.0.1:3306/workbench?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC",
    [string]$TargetUser = "wb",
    [string]$TargetPassword = "wb",
    [switch]$DryRun,
    [string]$Jar = "workbench/server/workbench-app/target/workbench-app-0.1.0-SNAPSHOT.jar"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Jar)) {
    throw "找不到 jar：$Jar，请先执行 mvn -DskipTests package -pl workbench-app -am"
}

$dryValue = if ($DryRun) { "true" } else { "false" }
Write-Host "== 旧库迁移（dry-run=$dryValue） ==" -ForegroundColor Cyan
Write-Host "source: $SourceUrl"
Write-Host "target: $TargetUrl"

$arguments = @(
    "-jar", $Jar,
    "--spring.main.web-application-type=none",
    "--spring.datasource.url=$TargetUrl",
    "--spring.datasource.username=$TargetUser",
    "--spring.datasource.password=$TargetPassword",
    "--workbench.migrate.enabled=true",
    "--workbench.migrate.source-url=$SourceUrl",
    "--workbench.migrate.source-username=$SourceUser",
    "--workbench.migrate.source-password=$SourcePassword",
    "--workbench.migrate.dry-run=$dryValue",
    "--workbench.migrate.exit=true"
)

& java @arguments
if ($LASTEXITCODE -ne 0) {
    throw "迁移失败，退出码 $LASTEXITCODE"
}

if ($DryRun) {
    Write-Host "dry-run 完成，未写入任何数据；去掉 -DryRun 再执行一次即实际迁移。" -ForegroundColor Yellow
} else {
    Write-Host "迁移完成。" -ForegroundColor Green
}
