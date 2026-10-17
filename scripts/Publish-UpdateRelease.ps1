param([string]$NotesFile, [switch]$Build)
. "$PSScriptRoot/Common.ps1"
$repo = 'NFraw/android-offline-minigames'
$branch = (& git branch --show-current).Trim()
if ($branch -ne 'godot-engine') { throw '仅允许从 godot-engine 发布新版。' }
$ghCommand = Get-Command gh -ErrorAction SilentlyContinue
$gh = if ($ghCommand) { $ghCommand.Source } else {
    Get-ChildItem (Join-Path $ProjectRoot '.tools/github-cli') -Filter gh.exe -Recurse | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $gh) { throw '请先安装 GitHub CLI 并执行 gh auth login。' }
& $gh auth status
Assert-NativeExit 'GitHub CLI 登录检查'
& "$PSScriptRoot/Prepare-UpdateRelease.ps1" -Build:$Build -NotesFile $NotesFile
$source = [IO.File]::ReadAllText((Join-Path $ProjectRoot 'app/build.gradle.kts'))
$version = [regex]::Match($source,'versionName = "([^"]+)"').Groups[1].Value
$tag = "v$version"
$folder = Join-Path $ProjectRoot "releases/github-$version"
$manifest = Get-Content (Join-Path $folder 'update.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$apk = Join-Path $folder ([IO.Path]::GetFileName($manifest.assets[0].url))
$head = (& git rev-parse HEAD).Trim()
$remoteHead = (& $gh api "repos/$repo/branches/godot-engine" --jq '.commit.sha').Trim()
Assert-NativeExit '远程新版分支检查'
if ($head -ne $remoteHead) { throw '请先将当前提交推送到远程 godot-engine。' }
$existing = & $gh release view $tag --repo $repo --json tagName 2>$null
if ($LASTEXITCODE -eq 0) { throw '该版本的 Release 已存在；不覆盖，请检查版本号。' }
& $gh release create $tag $apk (Join-Path $folder 'update.json') --repo $repo --target $head --draft --title "迷你游戏合集 v$version" --notes-file (Join-Path $folder 'release-notes.md')
Assert-NativeExit '创建完整发布草稿'
& $gh release edit $tag --repo $repo --draft=false --latest
Assert-NativeExit '发布正式最新版'
Write-Output "已发布：https://github.com/$repo/releases/tag/$tag"
