$ErrorActionPreference = 'Continue'
Set-Location 'D:\repos\workbench\dootask'

"== secrets in tracked files =="
git grep -n -I -E "a1s2d3f4|Demo-2026|JWT_SECRET=" -- workbench | Select-Object -First 10

"== deploy files tracked =="
git ls-files workbench/server/deploy

"== non-white hex =="
$files = Get-ChildItem workbench\web\apps -Recurse -Include *.vue,*.css -File | Where-Object { $_.FullName -notlike '*node_modules*' -and $_.FullName -notlike '*dist*' -and $_.Name -ne 'tokens.css' }
$hits = Select-String -Path $files.FullName -Pattern '#[0-9a-fA-F]{3,8}\b' -AllMatches
foreach ($hit in $hits) {
    $vals = $hit.Matches | Where-Object { $_.Value -notmatch '^#(fff|ffffff|000|000000)$' }
    if ($vals) {
        $names = ($vals | ForEach-Object { $_.Value }) -join ','
        "{0}:{1} {2}" -f $hit.Filename, $hit.LineNumber, $names
    }
}

"== mobile public =="
Get-ChildItem workbench\web\apps\mobile\public | Select-Object -ExpandProperty Name

"== staff router imports =="
Select-String -Path workbench\web\apps\staff\src\router\index.ts -Pattern 'import' | Select-Object -First 8 | ForEach-Object { $_.Line.Trim() }
