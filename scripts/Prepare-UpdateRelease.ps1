param([string]$NotesFile, [switch]$Build)
. "$PSScriptRoot/Common.ps1"
$repo = 'NFraw/android-offline-minigames'
if ($Build) { & "$PSScriptRoot/Build-Android.ps1" -Release -Abi arm64-v8a }
$source = [IO.File]::ReadAllText((Join-Path $ProjectRoot 'app/build.gradle.kts'))
$version = [regex]::Match($source,'versionName = "([^"]+)"').Groups[1].Value
$code = [long][regex]::Match($source,'versionCode = ([0-9]+)').Groups[1].Value
$tag = "v$version"
$apk = Join-Path $ProjectRoot "releases/迷你游戏合集-v$version-arm64-v8a.apk"
if (-not (Test-Path -LiteralPath $apk)) { throw '未找到本版本真机 APK，请先加 -Build 打包。' }
# Stable ASCII asset name, avoiding URL encoding ambiguity on Android downloads.
$folder = Join-Path $ProjectRoot "releases/github-$version"
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$name = "minigames-$version-arm64-v8a.apk"
$destination = Join-Path $folder $name
Copy-Item -LiteralPath $apk -Destination $destination -Force
$notes = if ($NotesFile) { [IO.File]::ReadAllText((Resolve-Path -LiteralPath $NotesFile)) } else { "新增应用内检查更新；游戏仍可离线游玩。" }
if ($notes.Length -gt 3000) { throw '更新说明最多3000字。' }
$manifest = [ordered]@{
    schema = 1
    applicationId = 'com.minigames.collection.next'
    versionCode = $code
    versionName = $version
    notes = $notes
    assets = @([ordered]@{
        abi = 'arm64-v8a'
        url = "https://github.com/$repo/releases/download/$tag/$name"
        size = (Get-Item -LiteralPath $destination).Length
        sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    })
}
$encoding = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $folder 'update.json'),($manifest | ConvertTo-Json -Depth 5),$encoding)
[IO.File]::WriteAllText((Join-Path $folder 'release-notes.md'),$notes,$encoding)
Write-Output "发布素材已准备：$folder"
Write-Output "GitHub Release 标签：$tag；目标分支：godot-engine。"
Write-Output '将 APK 和 update.json 放在同一个正式 Release，发布后设为 Latest；不要设为预发布。'
