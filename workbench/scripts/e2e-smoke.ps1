#requires -Version 5.1
<#
.SYNOPSIS
    工作台 v2 端到端冒烟脚本（需要已启动的 workbench-app 与可用的 MySQL）。

.DESCRIPTION
    覆盖：登录（旧 md5 密码自动升级）、项目创建/改名/归档/恢复/删除、
    任务创建/更新/完成/取消完成/删除、成员添加/移除、分页 total、中文 UTF-8。
    所有请求体以 UTF-8 字节发送，避免 PowerShell 5.1 默认编码导致中文变问号。

.EXAMPLE
    powershell -File workbench/scripts/e2e-smoke.ps1 -BaseUrl http://127.0.0.1:9002
#>
param(
    [string]$BaseUrl = "http://127.0.0.1:9002",
    [string]$Username = "demo@workbench.local",
    [string]$Password = "secret123"
)

$ErrorActionPreference = "Stop"
$script:token = ""
$script:failures = 0
$script:checks = 0

function Assert-True {
    param([string]$Name, [bool]$Condition, [string]$Detail = "")
    $script:checks++
    if ($Condition) {
        Write-Host ("  [PASS] " + $Name)
    } else {
        $script:failures++
        Write-Host ("  [FAIL] " + $Name + " " + $Detail) -ForegroundColor Red
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null
    )
    $headers = @{ Accept = "application/json" }
    if ($script:token) { $headers.Authorization = "Bearer $($script:token)" }
    $params = @{
        Uri         = "$BaseUrl$Path"
        Method      = $Method
        Headers     = $headers
        ContentType = "application/json; charset=utf-8"
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Compress -Depth 6
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    # Invoke-RestMethod 在 PowerShell 5.1 里按 Content-Type 的 charset 解码，
    # Spring 返回的 application/json 不带 charset，会被当成 ISO-8859-1 导致中文乱码；
    # 因此这里自己按 UTF-8 解码响应体。
    $response = Invoke-WebRequest @params
    $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}

function New-Suffix { return (Get-Date -Format "HHmmss") }

$port = ([uri]$BaseUrl).Port
Write-Host "== Workbench E2E: $BaseUrl ==" -ForegroundColor Cyan

# 0. 健康检查（actuator）
$healthResponse = Invoke-WebRequest -Uri "$BaseUrl/actuator/health"
$healthBody = [System.Text.Encoding]::UTF8.GetString($healthResponse.RawContentStream.ToArray())
Assert-True "健康检查 status=UP" ($healthBody -like '*"status":"UP"*') "body=$healthBody"
Assert-True "响应头带 traceId" ([bool]$healthResponse.Headers['X-Trace-Id']) "header=$($healthResponse.Headers['X-Trace-Id'])"
$traceEcho = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -Headers @{ 'X-Trace-Id' = 'e2e-trace-1' }
Assert-True "透传上游 traceId" ($traceEcho.Headers['X-Trace-Id'] -eq 'e2e-trace-1') "header=$($traceEcho.Headers['X-Trace-Id'])"

# 1. 登录
$login = Invoke-Api -Method Post -Path "/api/v1/auth/login" -Body @{ username = $Username; password = $Password }
Assert-True "登录返回 code=0" ($login.code -eq 0) "code=$($login.code) msg=$($login.message)"
Assert-True "登录返回 token" ([bool]($login.data.token)) ""
$script:token = $login.data.token

# bootstrap-admin-token：首个用户自举为管理员，后续用户管理用管理员态
$bootstrapResp = Invoke-Api -Method Put -Path "/api/v1/users/$($login.data.userid)/role" -Body @{ role = "admin" }
Assert-True "首个用户自举为管理员" ($bootstrapResp.data.role -eq "admin") "role=$($bootstrapResp.data.role)"
$adminLogin = Invoke-Api -Method Post -Path "/api/v1/auth/login" -Body @{ username = $Username; password = $Password }
$script:token = $adminLogin.data.token
Assert-True "管理员登录角色为 admin" ($adminLogin.data.role -eq "admin") "role=$($adminLogin.data.role)"

# 2. 项目创建（中文）
$suffix = New-Suffix
$projectName = "E2E 项目 $suffix"
$project = Invoke-Api -Method Post -Path "/api/v1/projects" -Body @{ name = $projectName; desc = "端到端中文描述" }
Assert-True "创建项目 code=0" ($project.code -eq 0) "code=$($project.code)"
Assert-True "项目名中文未被破坏" ($project.data.name -eq $projectName) "actual=$($project.data.name)"
Assert-True "创建者自动成为成员" ($project.data.memberCount -eq 1) "memberCount=$($project.data.memberCount)"
$projectId = $project.data.id

# 3. 项目改名
$renamed = "E2E 改名 $suffix"
$updated = Invoke-Api -Method Put -Path "/api/v1/projects/$projectId" -Body @{ name = $renamed; desc = "改名后" }
Assert-True "项目改名生效" ($updated.data.name -eq $renamed) "actual=$($updated.data.name)"

# 4. 归档 / 恢复
Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/archive" | Out-Null
$archivedList = Invoke-Api -Method Get -Path "/api/v1/projects?page=1&size=50"
$archivedFlag = ($archivedList.data.list | Where-Object { $_.id -eq $projectId }).archived
Assert-True "归档后 archived=true" ($archivedFlag -eq $true) "actual=$archivedFlag"

Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/restore" | Out-Null
$restoredList = Invoke-Api -Method Get -Path "/api/v1/projects?page=1&size=50"
$restoredFlag = ($restoredList.data.list | Where-Object { $_.id -eq $projectId }).archived
Assert-True "恢复后 archived=false" ($restoredFlag -eq $false) "actual=$restoredFlag"

# 5. 任务：创建 / 更新 / 完成 / 取消完成
$task = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "E2E 任务"; desc = "任务描述"; pLevel = 1 }
Assert-True "创建任务 code=0" ($task.code -eq 0) "code=$($task.code)"
$taskId = $task.data.id

$taskUpdated = Invoke-Api -Method Put -Path "/api/v1/tasks/$taskId" -Body @{ name = "E2E 任务改名"; pLevel = 3 }
Assert-True "任务改名与优先级" (($taskUpdated.data.name -eq "E2E 任务改名") -and ($taskUpdated.data.pLevel -eq 3)) "actual=$($taskUpdated.data.name)/$($taskUpdated.data.pLevel)"

$openBefore = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks").data.total
Assert-True "任务列表 total=1" ($openBefore -eq 1) "actual=$openBefore"

Invoke-Api -Method Post -Path "/api/v1/tasks/$taskId/complete" | Out-Null
$afterComplete = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks").data.total
Assert-True "完成后列表 total=0" ($afterComplete -eq 0) "actual=$afterComplete"

Invoke-Api -Method Post -Path "/api/v1/tasks/$taskId/reopen" | Out-Null
$afterReopen = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks").data.total
Assert-True "取消完成后列表 total=1" ($afterReopen -eq 1) "actual=$afterReopen"

# 6. 成员：创建用户 -> 加入 -> 移除
$memberEmail = "e2e-$suffix@workbench.local"
$member = Invoke-Api -Method Post -Path "/api/v1/users" -Body @{ email = $memberEmail; nickname = "E2E 成员"; password = "member123" }
Assert-True "创建成员用户 code=0" ($member.code -eq 0) "code=$($member.code)"
$memberId = $member.data.userid

$added = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/members" -Body @{ userid = $memberId }
Assert-True "添加成员 code=0" ($added.code -eq 0) "code=$($added.code)"

$members = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/members").data
Assert-True "成员列表包含新成员" ([bool]($members | Where-Object { $_.userid -eq $memberId })) ""

$dup = $null
try {
    $dup = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/members" -Body @{ userid = $memberId }
} catch {
    $dup = $null
}
$dupCode = if ($dup) { $dup.code } else { -1 }
Assert-True "重复添加被拒绝（code=400 或 HTTP 错误）" (($dupCode -eq 400) -or ($dupCode -eq -1)) "code=$dupCode"

Invoke-Api -Method Delete -Path "/api/v1/projects/$projectId/members/$memberId" | Out-Null
$membersAfter = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/members").data
Assert-True "移除成员生效" (-not ($membersAfter | Where-Object { $_.userid -eq $memberId })) ""

# 6.5 规划：里程碑 / 依赖 / 排期预演
$milestone = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/milestones" -Body @{ name = "E2E 里程碑"; targetAt = "2026-12-31"; reminderDays = 3 }
Assert-True "创建里程碑 code=0" ($milestone.code -eq 0) "code=$($milestone.code)"
$milestoneId = $milestone.data.id
$milestones = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/milestones").data
$mine = $milestones | Where-Object { $_.id -eq $milestoneId }
Assert-True "里程碑列表包含新建项" ([bool]$mine) ""
Assert-True "里程碑提醒天数=3" ($mine.reminderDays -eq 3) "actual=$($mine.reminderDays)"

$task2 = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "E2E 后继任务"; startAt = "2026-10-06T09:00:00"; endAt = "2026-10-06T18:00:00" }
$task2Id = $task2.data.id
Assert-True "创建后继任务" ([bool]$task2Id) ""

$dep = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/dependencies" -Body @{ predecessorTaskId = $taskId; successorTaskId = $task2Id }
Assert-True "添加依赖 code=0" ($dep.code -eq 0) "code=$($dep.code)"

$dupDep = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/dependencies" -Body @{ predecessorTaskId = $taskId; successorTaskId = $task2Id }
Assert-True "重复依赖被拒绝" ($dupDep.code -eq 400) "code=$($dupDep.code)"

$cycleDep = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/dependencies" -Body @{ predecessorTaskId = $task2Id; successorTaskId = $taskId }
Assert-True "成环依赖被拒绝" ($cycleDep.code -eq 400) "code=$($cycleDep.code)"

$selfDep = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/dependencies" -Body @{ predecessorTaskId = $taskId; successorTaskId = $taskId }
Assert-True "自依赖被拒绝" ($selfDep.code -eq 400) "code=$($selfDep.code)"

$edges = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/dependencies").data
Assert-True "依赖列表只有一条边" ($edges.Count -eq 1) "count=$($edges.Count)"

$risk = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/dependencies/risk-summary").data
Assert-True "风险汇总 edgeCount=1" ($risk.edgeCount -eq 1) "edgeCount=$($risk.edgeCount)"

$preview = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/schedule/preview" -Body @{ anchorTaskId = $taskId; anchorStart = "2026-10-05T09:00:00"; anchorEnd = "2026-10-07T18:00:00" }
Assert-True "排期预演返回变更" ($preview.data.changeCount -ge 1) "changeCount=$($preview.data.changeCount)"
$shifted = $preview.data.changes | Where-Object { $_.taskId -eq $task2Id }
Assert-True "后置任务被顺延" ([bool]$shifted) ""
if ($shifted) {
    Assert-True "顺延后为工作日 09:00" ($shifted.newStart -like "*-10-08T09:00:00") "actual=$($shifted.newStart)"
}

Invoke-Api -Method Delete -Path "/api/v1/projects/$projectId/dependencies/$($dep.data.id)" | Out-Null
$edgesAfter = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/dependencies").data
Assert-True "删除依赖后为空" ($edgesAfter.Count -eq 0) "count=$($edgesAfter.Count)"

Invoke-Api -Method Delete -Path "/api/v1/tasks/$task2Id" | Out-Null
Invoke-Api -Method Delete -Path "/api/v1/projects/$projectId/milestones/$milestoneId" | Out-Null
$milestonesAfter = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/milestones").data
Assert-True "删除里程碑后为空" ($milestonesAfter.Count -eq 0) "count=$($milestonesAfter.Count)"

# 6.6 工时与容量
$today = (Get-Date).ToString("yyyy-MM-dd")
$worklogBody = @{ requestId = "e2e-work-1"; minutes = 120; workDate = $today; note = "E2E 工时" }
$worklog = Invoke-Api -Method Post -Path "/api/v1/tasks/$taskId/worklogs" -Body $worklogBody
Assert-True "登记工时 code=0" ($worklog.code -eq 0) "code=$($worklog.code)"
$worklogId = $worklog.data.id

$duplicate = Invoke-Api -Method Post -Path "/api/v1/tasks/$taskId/worklogs" -Body $worklogBody
Assert-True "重复 requestId 幂等（同一条）" ($duplicate.data.id -eq $worklogId) "id=$($duplicate.data.id) vs $worklogId"

$worklogList = (Invoke-Api -Method Get -Path "/api/v1/tasks/$taskId/worklogs").data
Assert-True "任务工时列表只有一条" ($worklogList.Count -eq 1) "count=$($worklogList.Count)"
Assert-True "工时分钟数=120" ($worklogList[0].minutes -eq 120) "minutes=$($worklogList[0].minutes)"

$from = (Get-Date).AddDays(-3).ToString("yyyy-MM-dd")
$to = (Get-Date).AddDays(3).ToString("yyyy-MM-dd")
$capacity = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/capacity?from=$from&to=$to").data
Assert-True "容量汇总 workdays>0" ($capacity.workdays -gt 0) "workdays=$($capacity.workdays)"
Assert-True "容量汇总已登记>=120" ($capacity.members[0].loggedMinutes -ge 120) "logged=$($capacity.members[0].loggedMinutes)"

$longFrom = (Get-Date).AddDays(-100).ToString("yyyy-MM-dd")
$tooLong = Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/capacity?from=$longFrom&to=$today"
Assert-True "超 93 天区间被拒绝" ($tooLong.code -eq 400) "code=$($tooLong.code)"

Invoke-Api -Method Delete -Path "/api/v1/worklogs/$worklogId" | Out-Null
$worklogAfter = (Invoke-Api -Method Get -Path "/api/v1/tasks/$taskId/worklogs").data
Assert-True "删除工时后为空" ($worklogAfter.Count -eq 0) "count=$($worklogAfter.Count)"

# 6.7 日报与周报
$report = Invoke-Api -Method Post -Path "/api/v1/daily-reports?date=$today" -Body @{ contentDone = "E2E 第一天日报"; contentPlan = "继续"; minutes = 60 }
Assert-True "提交日报 code=0" ($report.code -eq 0) "code=$($report.code)"
$reportId = $report.data.id
Assert-True "日报状态有文案" ([bool]$report.data.statusText) "status=$($report.data.statusText)"

$overwrite = Invoke-Api -Method Post -Path "/api/v1/daily-reports?date=$today" -Body @{ contentDone = "E2E 覆盖后的日报"; minutes = 90 }
Assert-True "同日重复提交覆盖同一条" ($overwrite.data.id -eq $reportId) "id=$($overwrite.data.id) vs $reportId"
Assert-True "覆盖后内容更新" ($overwrite.data.contentDone -eq "E2E 覆盖后的日报") "actual=$($overwrite.data.contentDone)"

$byDate = Invoke-Api -Method Get -Path "/api/v1/daily-reports/$today"
Assert-True "按日期查询到日报" ([bool]$byDate.data) ""
Assert-True "按日期内容正确" ($byDate.data.contentDone -eq "E2E 覆盖后的日报") "actual=$($byDate.data.contentDone)"

$reports = (Invoke-Api -Method Get -Path "/api/v1/daily-reports").data
Assert-True "日报列表非空" ($reports.Count -ge 1) "count=$($reports.Count)"

$monday = (Get-Date).AddDays(-(((Get-Date).DayOfWeek.value__ + 6) % 7)).ToString("yyyy-MM-dd")
$weekly = Invoke-Api -Method Post -Path "/api/v1/weekly-summaries/regenerate" -Body @{ weekStart = $monday }
Assert-True "生成周报 code=0" ($weekly.code -eq 0) "code=$($weekly.code)"
Assert-True "周报统计到日报" ($weekly.data.reportCount -ge 1) "reportCount=$($weekly.data.reportCount)"

$weeklies = (Invoke-Api -Method Get -Path "/api/v1/weekly-summaries").data
Assert-True "周报列表非空" ($weeklies.Count -ge 1) "count=$($weeklies.Count)"

Invoke-Api -Method Delete -Path "/api/v1/daily-reports/$reportId" | Out-Null
$reportsAfter = (Invoke-Api -Method Get -Path "/api/v1/daily-reports").data
Assert-True "删除日报后列表为空" ($reportsAfter.Count -eq 0) "count=$($reportsAfter.Count)"

# 6.8 评论与通知
$me = $login.data.userid
$comment = Invoke-Api -Method Post -Path "/api/v1/tasks/$taskId/comments" -Body @{ content = "E2E 评论"; mentionUserids = @($me) }
Assert-True "发表评论 code=0" ($comment.code -eq 0) "code=$($comment.code)"
$commentId = $comment.data.id
Assert-True "评论带 @ 列表" ($comment.data.mentionUserids.Count -eq 1) "count=$($comment.data.mentionUserids.Count)"

$commentList = (Invoke-Api -Method Get -Path "/api/v1/tasks/$taskId/comments").data
Assert-True "评论列表只有一条" ($commentList.Count -eq 1) "count=$($commentList.Count)"

$unreadBefore = (Invoke-Api -Method Get -Path "/api/v1/notifications/unread-count").data.unread
Assert-True "@ 自己产生未读通知" ($unreadBefore -ge 1) "unread=$unreadBefore"

$unreadList = (Invoke-Api -Method Get -Path "/api/v1/notifications?unreadOnly=true").data
Assert-True "未读通知类型为 mention" ([bool]($unreadList | Where-Object { $_.type -eq "mention" })) ""
$notifyId = ($unreadList | Where-Object { $_.type -eq "mention" } | Select-Object -First 1).id

Invoke-Api -Method Post -Path "/api/v1/notifications/$notifyId/read" | Out-Null
$unreadAfterOne = (Invoke-Api -Method Get -Path "/api/v1/notifications/unread-count").data.unread
Assert-True "标记单条已读后未读减少" ($unreadAfterOne -lt $unreadBefore) "before=$unreadBefore after=$unreadAfterOne"

$readAll = Invoke-Api -Method Post -Path "/api/v1/notifications/read-all"
Assert-True "全部已读接口 code=0" ($readAll.code -eq 0) "code=$($readAll.code)"
$unreadFinal = (Invoke-Api -Method Get -Path "/api/v1/notifications/unread-count").data.unread
Assert-True "全部已读后未读为 0" ($unreadFinal -eq 0) "unread=$unreadFinal"

Invoke-Api -Method Delete -Path "/api/v1/comments/$commentId" | Out-Null
$commentsAfter = (Invoke-Api -Method Get -Path "/api/v1/tasks/$taskId/comments").data
Assert-True "删除评论后列表为空" ($commentsAfter.Count -eq 0) "count=$($commentsAfter.Count)"

# 6.9 附件（multipart 用 curl，PS5.1 的 Invoke-WebRequest 不支持 -Form）
$uploadFile = Join-Path $env:TEMP "e2e-note.txt"
[System.IO.File]::WriteAllText($uploadFile, "attached-by-e2e", (New-Object System.Text.UTF8Encoding($false)))

$uploadRaw = & curl.exe -s -X POST "http://127.0.0.1:$port/api/v1/tasks/$taskId/attachments" -H "Authorization: Bearer $script:token" -F "file=@$uploadFile;type=text/plain"
$upload = $uploadRaw | ConvertFrom-Json
Assert-True "上传附件 code=0" ($upload.code -eq 0) "code=$($upload.code)"
$attachmentId = $upload.data.id
Assert-True "附件大小为 15" ($upload.data.sizeBytes -eq 15) "size=$($upload.data.sizeBytes)"

$attachmentList = (Invoke-Api -Method Get -Path "/api/v1/tasks/$taskId/attachments").data
Assert-True "附件列表只有一条" ($attachmentList.Count -eq 1) "count=$($attachmentList.Count)"

$downloadFile = Join-Path $env:TEMP "e2e-note-downloaded.txt"
& curl.exe -s -H "Authorization: Bearer $script:token" -o $downloadFile "http://127.0.0.1:$port/api/v1/attachments/$attachmentId/download"
$downloaded = [System.IO.File]::ReadAllText($downloadFile)
Assert-True "下载内容一致" ($downloaded -eq "attached-by-e2e") "actual=$downloaded"

$badFile = Join-Path $env:TEMP "e2e-bad.exe"
[System.IO.File]::WriteAllText($badFile, "MZ", (New-Object System.Text.UTF8Encoding($false)))
$badRaw = & curl.exe -s -X POST "http://127.0.0.1:$port/api/v1/tasks/$taskId/attachments" -H "Authorization: Bearer $script:token" -F "file=@$badFile"
$bad = $badRaw | ConvertFrom-Json
Assert-True "非法扩展名被拒绝" ($bad.code -eq 400) "code=$($bad.code)"

Invoke-Api -Method Delete -Path "/api/v1/attachments/$attachmentId" | Out-Null
$attachmentsAfter = (Invoke-Api -Method Get -Path "/api/v1/tasks/$taskId/attachments").data
Assert-True "删除附件后列表为空" ($attachmentsAfter.Count -eq 0) "count=$($attachmentsAfter.Count)"

# 6.10 任务模板与版本
$template = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/templates" -Body @{ name = "E2E 模板"; content = "步骤一" }
Assert-True "创建模板 code=0" ($template.code -eq 0) "code=$($template.code)"
$templateId = $template.data.id
Assert-True "模板初始版本为 1" ($template.data.revisionNo -eq 1) "rev=$($template.data.revisionNo)"

$templateV2 = Invoke-Api -Method Put -Path "/api/v1/templates/$templateId" -Body @{ name = "E2E 模板 v2"; content = "步骤二" }
Assert-True "更新模板版本递增到 2" ($templateV2.data.revisionNo -eq 2) "rev=$($templateV2.data.revisionNo)"

$revisions = (Invoke-Api -Method Get -Path "/api/v1/templates/$templateId/revisions").data
Assert-True "版本快照共 2 条" ($revisions.Count -eq 2) "count=$($revisions.Count)"
Assert-True "最新版本在最前" ($revisions[0].revisionNo -eq 2) "first=$($revisions[0].revisionNo)"

$applyResult = Invoke-Api -Method Post -Path "/api/v1/templates/$templateId/apply"
Assert-True "应用模板生成任务" ($applyResult.data.taskId -gt 0) "taskId=$($applyResult.data.taskId)"
$generated = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks?keyword=E2E%20模板").data
Assert-True "生成的任务可查到" ([bool]($generated.list | Where-Object { $_.id -eq $applyResult.data.taskId })) ""

Invoke-Api -Method Delete -Path "/api/v1/tasks/$($applyResult.data.taskId)" | Out-Null
Invoke-Api -Method Delete -Path "/api/v1/templates/$templateId" | Out-Null
$templatesAfter = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/templates").data
Assert-True "删除模板后列表为空" ($templatesAfter.Count -eq 0) "count=$($templatesAfter.Count)"

# 6.11 排期应用与撤销
$applyBody = @{
    requestId = "e2e-apply-1"
    anchorTaskId = $taskId
    anchorStart = "2026-10-05T09:00:00"
    anchorEnd = "2026-10-07T18:00:00"
}
$applied = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/schedule/apply" -Body $applyBody
Assert-True "应用排期 code=0" ($applied.code -eq 0) "code=$($applied.code)"
$batchId = $applied.data.id
Assert-True "应用后有变更" ($applied.data.changeCount -ge 1) "changeCount=$($applied.data.changeCount)"

$moved = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks").data.list | Where-Object { $_.id -eq $taskId }
Assert-True "任务开始时间已更新" ($moved.startAt -like "2026-10-05T09:00*") "startAt=$($moved.startAt)"

$replay = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/schedule/apply" -Body $applyBody
Assert-True "重复 requestId 幂等（同一批次）" ($replay.data.id -eq $batchId) "id=$($replay.data.id) vs $batchId"

$undone = Invoke-Api -Method Post -Path "/api/v1/schedule/batches/$batchId/undo"
Assert-True "撤销批次 code=0" ($undone.code -eq 0) "code=$($undone.code)"
Assert-True "撤销后标记 undone" ($undone.data.undone -eq $true) "undone=$($undone.data.undone)"

$restored = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks").data.list | Where-Object { $_.id -eq $taskId }
Assert-True "撤销后日期恢复为空" ([string]::IsNullOrEmpty($restored.startAt)) "startAt=$($restored.startAt)"

$undoAgain = Invoke-Api -Method Post -Path "/api/v1/schedule/batches/$batchId/undo"
Assert-True "重复撤销被拒绝" ($undoAgain.code -eq 400) "code=$($undoAgain.code)"

$batches = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/schedule/batches").data
Assert-True "批次列表包含该批次" ([bool]($batches | Where-Object { $_.id -eq $batchId })) ""

# 6.12 任务编辑与里程碑关联
$m2 = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/milestones" -Body @{ name = "E2E 里程碑任务组"; targetAt = "2026-11-30" }
Assert-True "创建里程碑用于关联 code=0" ($m2.code -eq 0) "code=$($m2.code)"
$m2Id = $m2.data.id

$linked = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "E2E 里程碑任务"; milestoneId = $m2Id }
Assert-True "创建带里程碑的任务" ($linked.data.milestoneId -eq $m2Id) "milestoneId=$($linked.data.milestoneId)"
$linkedId = $linked.data.id

$filtered = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks?milestoneId=$m2Id").data
Assert-True "按里程碑过滤只返回该任务" (($filtered.total -eq 1) -and ($filtered.list[0].id -eq $linkedId)) "total=$($filtered.total)"

$cleared = Invoke-Api -Method Put -Path "/api/v1/tasks/$linkedId" -Body @{ name = "E2E 里程碑任务"; milestoneId = 0 }
Assert-True "清除里程碑后返回空" ([string]::IsNullOrEmpty([string]$cleared.data.milestoneId)) "milestoneId=$($cleared.data.milestoneId)"

$filteredAfter = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks?milestoneId=$m2Id").data
Assert-True "清除后过滤结果为空" ($filteredAfter.total -eq 0) "total=$($filteredAfter.total)"

$badMilestone = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "E2E 非法里程碑"; milestoneId = 999999 }
Assert-True "非法里程碑被拒绝" ($badMilestone.code -eq 400) "code=$($badMilestone.code)"

Invoke-Api -Method Delete -Path "/api/v1/tasks/$linkedId" | Out-Null
Invoke-Api -Method Delete -Path "/api/v1/projects/$projectId/milestones/$m2Id" | Out-Null

# 6.13 项目动态与审计
$actTask = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "E2E 动态任务" }
$actTaskId = $actTask.data.id
Invoke-Api -Method Post -Path "/api/v1/tasks/$actTaskId/complete" | Out-Null

$acts = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/activities?taskId=$actTaskId").data
Assert-True "任务动态至少两条" ($acts.total -ge 2) "total=$($acts.total)"
$actions = ($acts.list | ForEach-Object { $_.action }) -join ","
Assert-True "动态含 task.create" ($actions -like "*task.create*") "actions=$actions"
Assert-True "动态含 task.complete" ($actions -like "*task.complete*") "actions=$actions"
Assert-True "动态带中文文案" ([bool]($acts.list | Where-Object { $_.actionText -eq "创建任务" })) ""

$allActs = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/activities?size=5").data
Assert-True "动态分页 size 生效" ($allActs.size -eq 5) "size=$($allActs.size)"
Assert-True "动态列表非空" ($allActs.total -ge 1) "total=$($allActs.total)"

Invoke-Api -Method Delete -Path "/api/v1/tasks/$actTaskId" | Out-Null

# 6.15 全局搜索
$search = Invoke-Api -Method Get -Path "/api/v1/search?keyword=$suffix"
Assert-True "搜索命中项目" ([bool]($search.data.projects | Where-Object { $_.id -eq $projectId })) "hits=$($search.data.projects.Count)"
$taskKeyword = [uri]::EscapeDataString("E2E 任务改名")
$searchTask = Invoke-Api -Method Get -Path "/api/v1/search?keyword=$taskKeyword"
Assert-True "搜索命中任务" ([bool]($searchTask.data.tasks | Where-Object { $_.id -eq $taskId })) "hits=$($searchTask.data.tasks.Count)"
$searchBlank = Invoke-Api -Method Get -Path "/api/v1/search?keyword="
Assert-True "空关键词返回空结果" ($searchBlank.data.projects.Count -eq 0) "count=$($searchBlank.data.projects.Count)"
$searchNone = Invoke-Api -Method Get -Path "/api/v1/search?keyword=zzz-not-exists-zzz"
Assert-True "无匹配时为空" (($searchNone.data.projects.Count -eq 0) -and ($searchNone.data.tasks.Count -eq 0)) ""

# 6.16 看板
$board = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/board").data
Assert-True "看板默认至少三列" ($board.columns.Count -ge 3) "columns=$($board.columns.Count)"
$secondColumnId = $board.columns[1].column.id
Invoke-Api -Method Put -Path "/api/v1/tasks/$taskId/move" -Body @{ columnId = $secondColumnId } | Out-Null
$boardMoved = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/board").data
$movedCards = ($boardMoved.columns | Where-Object { $_.column.id -eq $secondColumnId }).cards
Assert-True "任务已移动到第二列" ([bool]($movedCards | Where-Object { $_.id -eq $taskId })) "cards=$($movedCards.Count)"
$newColumn = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/columns" -Body @{ name = "E2E 新列" }
Assert-True "新增看板列成功" ($newColumn.data.name -eq "E2E 新列") "name=$($newColumn.data.name)"
Invoke-Api -Method Delete -Path "/api/v1/projects/$projectId/columns/$($newColumn.data.id)" | Out-Null

# 6.17 甘特图
$ganttA = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "甘特任务A"; startAt = "2026-10-05T09:00:00"; endAt = "2026-10-07T18:00:00" }
$ganttB = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "甘特任务B"; startAt = "2026-10-08T09:00:00"; endAt = "2026-10-10T18:00:00" }
Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/dependencies" -Body @{ predecessorTaskId = $ganttA.data.id; successorTaskId = $ganttB.data.id } | Out-Null
$gantt = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/gantt").data
Assert-True "甘特图返回任务" ($gantt.tasks.Count -ge 2) "tasks=$($gantt.tasks.Count)"
Assert-True "甘特图返回依赖边" ([bool]($gantt.edges | Where-Object { $_.predecessorTaskId -eq $ganttA.data.id -and $_.successorTaskId -eq $ganttB.data.id })) ""
Assert-True "甘特图范围覆盖任务" ($gantt.rangeStart -le "2026-10-05" -and $gantt.rangeEnd -ge "2026-10-10") "range=$($gantt.rangeStart)~$($gantt.rangeEnd)"
Invoke-Api -Method Delete -Path "/api/v1/tasks/$($ganttA.data.id)" | Out-Null
Invoke-Api -Method Delete -Path "/api/v1/tasks/$($ganttB.data.id)" | Out-Null

# 6.18 标签与子任务
$tag = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tags" -Body @{ name = "E2E 标签"; color = "#409eff" }
Assert-True "创建标签成功" ($tag.data.name -eq "E2E 标签") "name=$($tag.data.name)"
$tagId = $tag.data.id
Invoke-Api -Method Post -Path "/api/v1/tasks/$taskId/tags" -Body @{ tagId = $tagId } | Out-Null
$taskTags = (Invoke-Api -Method Get -Path "/api/v1/tasks/$taskId/tags").data
Assert-True "任务打上标签" ([bool]($taskTags | Where-Object { $_.id -eq $tagId })) "count=$($taskTags.Count)"
$byTag = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks?tagId=$tagId").data
Assert-True "按标签筛选命中任务" ([bool]($byTag.list | Where-Object { $_.id -eq $taskId })) "total=$($byTag.total)"

$sub = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "E2E 子任务"; parentId = $taskId }
Assert-True "创建子任务" ($sub.data.parentId -eq $taskId) "parentId=$($sub.data.parentId)"
$children = (Invoke-Api -Method Get -Path "/api/v1/tasks/$taskId/children").data
Assert-True "父任务能查到子任务" ([bool]($children | Where-Object { $_.id -eq $sub.data.id })) "count=$($children.Count)"

Invoke-Api -Method Delete -Path "/api/v1/tasks/$taskId/tags/$tagId" | Out-Null
$taskTagsAfter = (Invoke-Api -Method Get -Path "/api/v1/tasks/$taskId/tags").data
Assert-True "取消标签后为空" ($taskTagsAfter.Count -eq 0) "count=$($taskTagsAfter.Count)"
Invoke-Api -Method Delete -Path "/api/v1/tasks/$($sub.data.id)" | Out-Null
Invoke-Api -Method Delete -Path "/api/v1/projects/$projectId/tags/$tagId" | Out-Null

# 6.19 批量操作与导出
$batchA = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "批量任务A" }
$batchB = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks" -Body @{ name = "批量任务B" }
$batchResult = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks/batch" -Body @{ action = "complete"; taskIds = @($batchA.data.id, $batchB.data.id) }
Assert-True "批量完成 2 个任务" ($batchResult.data.affected -eq 2) "affected=$($batchResult.data.affected)"
$batchAssign = Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks/batch" -Body @{ action = "assign"; taskIds = @($batchA.data.id, $batchB.data.id); ownerUserid = $me }
Assert-True "批量指派受影响 2 个" ($batchAssign.data.affected -eq 2) "affected=$($batchAssign.data.affected)"
Invoke-Api -Method Post -Path "/api/v1/projects/$projectId/tasks/batch" -Body @{ action = "delete"; taskIds = @($batchA.data.id, $batchB.data.id) } | Out-Null
$afterBatch = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks").data
Assert-True "批量删除后不可见" (-not ($afterBatch.list | Where-Object { $_.id -eq $batchA.data.id })) ""

$tasksCsvRaw = & curl.exe -s -H "Authorization: Bearer $script:token" "http://127.0.0.1:$port/api/v1/projects/$projectId/tasks/export"
$tasksCsvText = ($tasksCsvRaw -join "`n")
Assert-True "任务导出含表头" ($tasksCsvText -like "*id,name*") ""
Assert-True "任务导出含任务名" ($tasksCsvText -like "*E2E*") ""

$worklogsCsvRaw = & curl.exe -s -H "Authorization: Bearer $script:token" "http://127.0.0.1:$port/api/v1/projects/$projectId/worklogs/export"
$worklogsCsvText = ($worklogsCsvRaw -join "`n")
Assert-True "工时导出含表头" ($worklogsCsvText -like "*id,task_id,userid,work_date,minutes*") ""

# 7. 清理：删任务、删项目
Invoke-Api -Method Delete -Path "/api/v1/tasks/$taskId" | Out-Null
$tasksAfterDelete = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks").data.total
Assert-True "删除任务后 total=0" ($tasksAfterDelete -eq 0) "actual=$tasksAfterDelete"

Invoke-Api -Method Delete -Path "/api/v1/projects/$projectId" | Out-Null
$projectsAfterDelete = (Invoke-Api -Method Get -Path "/api/v1/projects?page=1&size=50").data.list
Assert-True "删除项目后不可见" (-not ($projectsAfterDelete | Where-Object { $_.id -eq $projectId })) ""

# 6.13.5 项目角色：viewer 只读
$proj2 = Invoke-Api -Method Post -Path "/api/v1/projects" -Body @{ name = "E2E 权限项目" }
$proj2Id = $proj2.data.id
$memberUser = Invoke-Api -Method Post -Path "/api/v1/users" -Body @{ email = "viewer-$((New-Suffix))@workbench.local"; nickname = "只读成员"; password = "viewer123" }
$viewerId = $memberUser.data.userid
$added = Invoke-Api -Method Post -Path "/api/v1/projects/$proj2Id/members" -Body @{ userid = $viewerId; role = "viewer" }
Assert-True "添加 viewer 成员成功" ($added.data.role -eq "viewer") "role=$($added.data.role)"

$viewerLogin = Invoke-Api -Method Post -Path "/api/v1/auth/login" -Body @{ username = $memberUser.data.email; password = "viewer123" }
$viewerToken = $viewerLogin.data.token
$script:token = $viewerToken
$viewerWrite = Invoke-Api -Method Post -Path "/api/v1/projects/$proj2Id/tasks" -Body @{ name = "viewer 不该能建" }
Assert-True "viewer 建任务被拒（403）" ($viewerWrite.code -eq 403) "code=$($viewerWrite.code)"
$viewerRead = Invoke-Api -Method Get -Path "/api/v1/projects/$proj2Id/tasks"
Assert-True "viewer 仍可读任务列表" ($viewerRead.code -eq 0) "code=$($viewerRead.code)"

$script:token = $adminLogin.data.token
$adminTaskIn2 = Invoke-Api -Method Post -Path "/api/v1/projects/$proj2Id/tasks" -Body @{ name = "权限验证任务" }
$taskIn2 = $adminTaskIn2.data.id
$script:token = $viewerToken
$viewerComment = Invoke-Api -Method Post -Path "/api/v1/tasks/$taskIn2/comments" -Body @{ content = "viewer 不该能评论" }
Assert-True "viewer 发评论被拒（403）" ($viewerComment.code -eq 403) "code=$($viewerComment.code)"
$viewerWorklog = Invoke-Api -Method Post -Path "/api/v1/tasks/$taskIn2/worklogs" -Body @{ requestId = "viewer-work-1"; minutes = 30; workDate = $today }
Assert-True "viewer 登记工时被拒（403）" ($viewerWorklog.code -eq 403) "code=$($viewerWorklog.code)"
$viewerCommentsRead = Invoke-Api -Method Get -Path "/api/v1/tasks/$taskIn2/comments"
Assert-True "viewer 仍可读评论" ($viewerCommentsRead.code -eq 0) "code=$($viewerCommentsRead.code)"
$viewerWorklogsRead = Invoke-Api -Method Get -Path "/api/v1/tasks/$taskIn2/worklogs"
Assert-True "viewer 仍可读工时" ($viewerWorklogsRead.code -eq 0) "code=$($viewerWorklogsRead.code)"

# viewer 上传附件应 403（multipart 用 curl），读附件列表应 200
$viewerUploadRaw = & curl.exe -s -X POST "http://127.0.0.1:$port/api/v1/tasks/$taskIn2/attachments" -H "Authorization: Bearer $viewerToken" -F "file=@$uploadFile;type=text/plain"
$viewerUpload = $viewerUploadRaw | ConvertFrom-Json
Assert-True "viewer 上传附件被拒（403）" ($viewerUpload.code -eq 403) "code=$($viewerUpload.code)"
$viewerMove = Invoke-Api -Method Put -Path "/api/v1/tasks/$taskIn2/move" -Body @{ columnId = 0 }
Assert-True "viewer 移动任务被拒（403）" ($viewerMove.code -eq 403) "code=$($viewerMove.code)"
$viewerAttachments = Invoke-Api -Method Get -Path "/api/v1/tasks/$taskIn2/attachments"
Assert-True "viewer 仍可读附件列表" ($viewerAttachments.code -eq 0) "code=$($viewerAttachments.code)"

$script:token = $adminLogin.data.token
Invoke-Api -Method Delete -Path "/api/v1/projects/$proj2Id" | Out-Null
$staffCreate = Invoke-Api -Method Post -Path "/api/v1/users" -Body @{ email = "x$((New-Suffix))@workbench.local"; nickname = "x"; password = "secret123" }
Assert-True "管理员可创建用户" ($staffCreate.code -eq 0) "code=$($staffCreate.code)"

# 6.14 系统管理：设置、字典、角色

$settingsList = (Invoke-Api -Method Get -Path "/api/v1/admin/settings").data
Assert-True "管理员可读设置" ([bool]($settingsList | Where-Object { $_.key -eq "app.name" })) ""

$savedSetting = Invoke-Api -Method Put -Path "/api/v1/admin/settings/app.name" -Body @{ value = "工作台E2E"; description = "E2E 修改" }
Assert-True "设置写入成功" ($savedSetting.data.value -eq "工作台E2E") "value=$($savedSetting.data.value)"

$publicRaw = Invoke-WebRequest -Uri "$BaseUrl/api/v1/system/public/settings"
$publicBody = [System.Text.Encoding]::UTF8.GetString($publicRaw.RawContentStream.ToArray())
Assert-True "公开设置可匿名读" ($publicBody -like '*app.name*') "body=$publicBody"

$dictCreated = Invoke-Api -Method Post -Path "/api/v1/admin/dicts" -Body @{ type = "e2e_dict"; code = "a"; label = "甲"; sort = 1 }
Assert-True "创建字典项" ($dictCreated.data.code -eq "a") "code=$($dictCreated.data.code)"
$dictList = (Invoke-Api -Method Get -Path "/api/v1/dicts/e2e_dict").data
Assert-True "按类型读取字典" ([bool]($dictList | Where-Object { $_.code -eq "a" })) ""
Invoke-Api -Method Delete -Path "/api/v1/admin/dicts/$($dictCreated.data.id)" | Out-Null
$dictAfter = (Invoke-Api -Method Get -Path "/api/v1/dicts/e2e_dict").data
Assert-True "删除字典项后为空" ($dictAfter.Count -eq 0) "count=$($dictAfter.Count)"

$legacyLogin = Invoke-Api -Method Post -Path "/api/v1/auth/login" -Body @{ username = "legacy@example.com"; password = "secret123" }
$legacyToken = $legacyLogin.data.token
$script:token = $legacyToken
$legacyChange = Invoke-Api -Method Put -Path "/api/v1/users/$memberId/role" -Body @{ role = "admin" }
Assert-True "普通用户改他人角色被拒（管理员已存在）" ($legacyChange.code -eq 403) "code=$($legacyChange.code)"

$script:token = $adminLogin.data.token
$reverted = Invoke-Api -Method Put -Path "/api/v1/users/$me/role" -Body @{ role = "staff" }
Assert-True "管理员可把角色改回 staff" ($reverted.data.role -eq "staff") "role=$($reverted.data.role)"

$staffLogin = Invoke-Api -Method Post -Path "/api/v1/auth/login" -Body @{ username = $Username; password = $Password }
Assert-True "改回后登录角色为 staff" ($staffLogin.data.role -eq "staff") "role=$($staffLogin.data.role)"
$script:token = $staffLogin.data.token

# 8. 分页与中文
$users = Invoke-Api -Method Get -Path "/api/v1/users?page=1&size=5"
Assert-True "用户分页 total>0" ($users.data.total -gt 0) "total=$($users.data.total)"
Assert-True "用户分页 size 生效" ($users.data.size -eq 5) "size=$($users.data.size)"

Write-Host ""
if ($script:failures -eq 0) {
    Write-Host ("RESULT: PASS  ($script:checks checks)") -ForegroundColor Green
    exit 0
} else {
    Write-Host ("RESULT: FAIL  ($script:failures/$script:checks checks failed)") -ForegroundColor Red
    exit 1
}
