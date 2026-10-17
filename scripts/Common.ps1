$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$configFile = Join-Path $ProjectRoot 'dev.local.json'
if (-not (Test-Path $configFile)) { throw '请先运行 scripts/Setup-Tools.ps1，生成本机配置。' }
$DevConfig = Get-Content $configFile -Raw | ConvertFrom-Json
$GodotConsole = Join-Path $ProjectRoot ".tools/godot/Godot_v$($DevConfig.godotVersion)-stable_win64_console.exe"
$GodotEditor = Join-Path $ProjectRoot ".tools/godot/Godot_v$($DevConfig.godotVersion)-stable_win64.exe"
$GodotProject = Join-Path $ProjectRoot 'godot'
$Gradle = Join-Path $ProjectRoot '.tools/gradle-8.14.3/bin/gradle.bat'
$Adb = Join-Path $DevConfig.sdkPath 'platform-tools/adb.exe'
$env:JAVA_HOME = $DevConfig.javaPath
$env:ANDROID_HOME = $DevConfig.sdkPath
$env:PATH = "$($DevConfig.javaPath)\bin;$($DevConfig.sdkPath)\platform-tools;$env:PATH"
function Assert-NativeExit([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step 失败，退出码 $LASTEXITCODE" }
}
