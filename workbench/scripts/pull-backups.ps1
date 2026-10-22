$ErrorActionPreference = 'Stop'

$key = Join-Path $env:USERPROFILE '.ssh\xingtu_ed25519'
$dest = 'D:\backups\xingtu'
New-Item -ItemType Directory -Force -Path $dest | Out-Null

# 本机拉取服务器备份（异地副本）
$remote = 'root@47.109.88.107:/root/backup/*.gz'
scp -i $key -o StrictHostKeyChecking=accept-new -q $remote $dest
if ($LASTEXITCODE -ne 0) { throw "scp failed: $LASTEXITCODE" }

Get-ChildItem $dest | Sort-Object LastWriteTime -Descending | Select-Object -First 5 Name, Length, LastWriteTime

# 本地也保留最近 14 天
Get-ChildItem $dest -Filter '*.gz' | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-14) } | Remove-Item -Force
"total files: $((Get-ChildItem $dest -Filter '*.gz' | Measure-Object).Count)"
