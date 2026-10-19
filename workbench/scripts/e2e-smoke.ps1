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

Write-Host "== Workbench E2E: $BaseUrl ==" -ForegroundColor Cyan

# 1. 登录
$login = Invoke-Api -Method Post -Path "/api/v1/auth/login" -Body @{ username = $Username; password = $Password }
Assert-True "登录返回 code=0" ($login.code -eq 0) "code=$($login.code) msg=$($login.message)"
Assert-True "登录返回 token" ([bool]($login.data.token)) ""
$script:token = $login.data.token

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

# 7. 清理：删任务、删项目
Invoke-Api -Method Delete -Path "/api/v1/tasks/$taskId" | Out-Null
$tasksAfterDelete = (Invoke-Api -Method Get -Path "/api/v1/projects/$projectId/tasks").data.total
Assert-True "删除任务后 total=0" ($tasksAfterDelete -eq 0) "actual=$tasksAfterDelete"

Invoke-Api -Method Delete -Path "/api/v1/projects/$projectId" | Out-Null
$projectsAfterDelete = (Invoke-Api -Method Get -Path "/api/v1/projects?page=1&size=50").data.list
Assert-True "删除项目后不可见" (-not ($projectsAfterDelete | Where-Object { $_.id -eq $projectId })) ""

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
