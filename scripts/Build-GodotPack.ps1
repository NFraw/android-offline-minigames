$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Common.ps1"
& "$PSScriptRoot/Sync-GodotCatalog.ps1"
$outputDirectory = Join-Path $ProjectRoot 'app/build/generated/godotAssets'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& $GodotConsole --headless --path $GodotProject --editor --import
Assert-NativeExit 'Godot 资源导入'
& $GodotConsole --headless --path $GodotProject --export-pack Android (Join-Path $outputDirectory 'games.pck')
Assert-NativeExit 'Godot PCK 打包'
if (-not (Test-Path (Join-Path $outputDirectory 'games.pck'))) { throw '未生成 games.pck' }
