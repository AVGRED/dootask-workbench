$base = "http://127.0.0.1:9020"
$body = '{"username":"demo@workbench.local","password":"secret123"}'
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
$r = Invoke-WebRequest -Uri "$base/api/v1/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $bytes
$login = ([System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())) | ConvertFrom-Json
$h = @{ Authorization = "Bearer $($login.data.token)" }
$userId = [int]$login.data.userid

# 1) 清空所有遗留项目
$all = (Invoke-RestMethod -Uri "$base/api/v1/projects?page=1&size=100" -Headers $h).data
$deleted = 0
foreach ($p in $all.list) {
  try {
    $res = Invoke-RestMethod -Uri "$base/api/v1/projects/$($p.id)" -Method Delete -Headers $h
    if ($res.code -eq 0) { $deleted++ }
  } catch { }
}
"cleared_projects=$deleted"

# 2) 建一个演示项目
function Save-Json($obj) { [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 5)) }
$projBody = Save-Json @{ name = "产品迭代 · 工作台演示"; desc = "首页板块、看板、甘特、报表的演示数据" }
$proj = Invoke-RestMethod -Uri "$base/api/v1/projects" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body $projBody
$pid2 = $proj.data.id
"project=$pid2"

$tasks = @(
  @{ name = "梳理需求与验收标准"; startAt = "2026-10-20T09:00:00"; endAt = "2026-10-22T18:00:00"; pLevel = 1; ownerUserid = $userId },
  @{ name = "设计首页板块布局";   startAt = "2026-10-22T09:00:00"; endAt = "2026-10-27T18:00:00"; pLevel = 1; ownerUserid = $userId },
  @{ name = "实现看板拖拽";       startAt = "2026-10-26T09:00:00"; endAt = "2026-10-30T18:00:00"; pLevel = 2; ownerUserid = $userId },
  @{ name = "甘特视图联调";       startAt = "2026-10-29T09:00:00"; endAt = "2026-11-03T18:00:00"; pLevel = 2; ownerUserid = $userId },
  @{ name = "回归测试与上线";     startAt = "2026-11-03T09:00:00"; endAt = "2026-11-06T18:00:00"; pLevel = 3; ownerUserid = $userId }
)
$ids = @()
foreach ($t in $tasks) {
  $created = Invoke-RestMethod -Uri "$base/api/v1/projects/$pid2/tasks" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json $t)
  $ids += $created.data.id
}
"tasks=$($ids -join ',')"

# 3) 依赖 + 标签 + 移动任务 + 状态 + 日报
Invoke-RestMethod -Uri "$base/api/v1/projects/$pid2/dependencies" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json @{ predecessorTaskId = $ids[0]; successorTaskId = $ids[1] }) | Out-Null
Invoke-RestMethod -Uri "$base/api/v1/projects/$pid2/dependencies" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json @{ predecessorTaskId = $ids[1]; successorTaskId = $ids[2] }) | Out-Null
$tag = Invoke-RestMethod -Uri "$base/api/v1/projects/$pid2/tags" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json @{ name = "前端"; color = "#409eff" })
Invoke-RestMethod -Uri "$base/api/v1/tasks/$($ids[1])/tags" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json @{ tagId = $tag.data.id }) | Out-Null
Invoke-RestMethod -Uri "$base/api/v1/tasks/$($ids[0])/complete" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json @{}) | Out-Null
Invoke-RestMethod -Uri "$base/api/v1/tasks/$($ids[0])/worklogs" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json @{ requestId = "demo-wl-1"; minutes = 240; workDate = "2026-10-20" }) | Out-Null
Invoke-RestMethod -Uri "$base/api/v1/tasks/$($ids[1])/worklogs" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json @{ requestId = "demo-wl-2"; minutes = 180; workDate = "2026-10-20" }) | Out-Null
Invoke-RestMethod -Uri "$base/api/v1/me/status" -Method Put -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json @{ status = "focus"; note = "推进工作台改版" }) | Out-Null
$report = @{ contentDone = "完成首页可拖拽板块与项目卡片"; contentBlocker = "管理端报表还需要补导出"; contentPlan = "优化移动端看板"; minutes = 420 }
try { Invoke-RestMethod -Uri "$base/api/v1/daily-reports?date=2026-10-20" -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body (Save-Json $report) | Out-Null } catch { }

$final = (Invoke-RestMethod -Uri "$base/api/v1/projects?page=1&size=100" -Headers $h).data
"projects_after=$($final.total)"
