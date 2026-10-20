$base = "http://127.0.0.1:9020"
$body = '{"username":"demo@workbench.local","password":"secret123"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
$r = Invoke-WebRequest -Uri "$base/api/v1/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $bytes
$login = ([System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())) | ConvertFrom-Json
$h = @{ Authorization = "Bearer $($login.data.token)" }

$list = (Invoke-RestMethod -Uri "$base/api/v1/projects?page=1&size=100" -Headers $h).data
"projects_before=$($list.total)"
$deleted = 0
foreach ($p in $list.list) {
  if ($p.name -match '^(E2E|PW 项目|产品迭代 · 看板演示|中文项目|\?\?\?)' -or $p.name -match 'E2E') {
    try {
      $res = Invoke-RestMethod -Uri "$base/api/v1/projects/$($p.id)" -Method Delete -Headers $h
      if ($res.code -eq 0) { $deleted++ }
    } catch { }
  }
}
$after = (Invoke-RestMethod -Uri "$base/api/v1/projects?page=1&size=100" -Headers $h).data
"deleted=$deleted projects_after=$($after.total)"
$after.list | ForEach-Object { "$($_.id) $($_.name)" }

$avatar = (Invoke-RestMethod -Uri "$base/api/v1/me/avatar" -Headers $h).data.avatar
"avatar_file=$avatar"
if ($avatar) {
  try {
    $a = Invoke-WebRequest -Uri "$base/api/v1/avatars/$avatar" -UseBasicParsing
    "avatar_http=$($a.StatusCode)"
  } catch { "avatar_http=fail" }
}
