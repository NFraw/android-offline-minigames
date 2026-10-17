param([string]$SdkPath, [string]$JavaPath)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$lock = Get-Content (Join-Path $projectRoot 'tools.lock.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (-not $SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not $JavaPath) { $JavaPath = $env:JAVA_HOME }
if (-not $JavaPath) { $JavaPath = Split-Path (Split-Path (Get-Command java.exe -ErrorAction Stop).Source -Parent) -Parent }
foreach ($path in @((Join-Path $SdkPath 'platform-tools/adb.exe'), (Join-Path $SdkPath 'platforms/android-36/android.jar'), (Join-Path $JavaPath 'bin/java.exe'))) {
    if (-not (Test-Path $path)) { throw "请先配置本机安卓 SDK/API 36 和 JDK 17 或 21，缺失：$path" }
}
$downloadRoot = Join-Path $projectRoot '.tools/downloads'
$editorRoot = Join-Path $projectRoot '.tools/godot'
New-Item -ItemType Directory -Force -Path $downloadRoot, $editorRoot | Out-Null
function Get-VerifiedFile([string]$Uri, [string]$Destination, [string]$Algorithm, [string]$Hash) {
    if ((Test-Path $Destination) -and (Get-FileHash $Destination -Algorithm $Algorithm).Hash -eq $Hash) { return }
    Invoke-WebRequest -UseBasicParsing -Uri $Uri -OutFile $Destination -TimeoutSec 1200
    if ((Get-FileHash $Destination -Algorithm $Algorithm).Hash -ne $Hash) { throw "下载校验失败：$Destination" }
}
$editorZip = Join-Path $downloadRoot 'godot-editor.zip'
Get-VerifiedFile "https://downloads.godotengine.org/?version=$($lock.godotVersion)&flavor=stable&slug=win64.exe.zip" $editorZip SHA512 $lock.editorSha512
$editor = Join-Path $editorRoot "Godot_v$($lock.godotVersion)-stable_win64_console.exe"
if (-not (Test-Path $editor)) { Expand-Archive $editorZip $editorRoot -Force }
New-Item -ItemType File -Path (Join-Path $editorRoot '_sc_') -Force | Out-Null
$templateZip = Join-Path $downloadRoot 'godot-templates.tpz'
Get-VerifiedFile "https://downloads.godotengine.org/?version=$($lock.godotVersion)&flavor=stable&slug=export_templates.tpz" $templateZip SHA512 $lock.templatesSha512
Add-Type -AssemblyName System.IO.Compression.FileSystem
$destination = Join-Path $editorRoot "editor_data/export_templates/$($lock.godotVersion).stable"
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$zip = [System.IO.Compression.ZipFile]::OpenRead($templateZip)
try {
    foreach ($entry in $zip.Entries | Where-Object { $_.Name -match '^android_|^version.txt$' }) {
        $output = Join-Path $destination $entry.Name
        if (-not (Test-Path $output)) { [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $output, $false) }
    }
} finally { $zip.Dispose() }
$gradle = Join-Path $projectRoot ".tools/gradle-$($lock.gradleVersion)/bin/gradle.bat"
if (-not (Test-Path $gradle)) {
    $gradleZip = Join-Path $downloadRoot 'gradle.zip'
    Get-VerifiedFile "https://services.gradle.org/distributions/gradle-$($lock.gradleVersion)-bin.zip" $gradleZip SHA256 $lock.gradleSha256
    Expand-Archive $gradleZip (Join-Path $projectRoot '.tools') -Force
}
$config = @{sdkPath=$SdkPath; javaPath=$JavaPath; godotVersion=$lock.godotVersion} | ConvertTo-Json
[System.IO.File]::WriteAllText((Join-Path $projectRoot 'dev.local.json'), $config, [System.Text.UTF8Encoding]::new($true))
$sdkEscaped = $SdkPath.Replace('\','\\').Replace(':','\:')
[System.IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$sdkEscaped`n", [System.Text.UTF8Encoding]::new($false))
& "$PSScriptRoot/Check-Environment.ps1"
