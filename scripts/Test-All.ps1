<#
.SYNOPSIS
  安卓离线小游戏合集（新版）· 独立自动化测试程序

.DESCRIPTION
  一条命令跑完全部测试，并自动生成逐游戏测试报告：

    1. 开发环境检查（Godot / Gradle / JDK / ADB / 安卓导出模板）
    2. Godot 资源导入（--import）
    3. Godot 引擎测试（自动发现 godot/tests 下全部测试脚本）
    4. Kotlin 单元测试（:app:testDebugUnitTest）
    5. 构建校验（debug / x86_64，可选 release / arm64-v8a）
    6. 设备逐款冒烟（模拟器或真机，逐款启动 29 款游戏并返回大厅）
    7. 仪器化测试（可选，:app:connectedDebugAndroidTest）
    8. 生成报告：Markdown + JSON，并打印控制台汇总

  本程序为只读运行：不修改任何游戏代码、场景或资源。
  任一阶段失败都不会中断后续阶段，最终统一汇总（退出码 0 = 全绿）。

.PARAMETER Quick
  快速模式：只跑「引擎测试 + 单元测试」，跳过构建与设备阶段。

.PARAMETER Instrumented
  额外运行 Gradle 仪器化测试（:app:connectedDebugAndroidTest）。

.PARAMETER SkipBuild
  跳过构建阶段。

.PARAMETER SkipDevice
  跳过设备逐款冒烟阶段（无设备时自动跳过）。

.PARAMETER SkipUnit
  跳过 Kotlin 单元测试阶段。

.PARAMETER SkipImport
  跳过 Godot 资源导入阶段。

.PARAMETER Device
  指定设备序列号（多设备时必填），例如 -Device emulator-5554。

.PARAMETER OnlyGames
  只测指定游戏（逗号分隔），例如 -OnlyGames flight,piano。

.PARAMETER QuitAfter
  Godot 测试脚本的帧数上限（默认 20000），用于兜底终止不主动 quit 的脚本。
  含真实计时器（create_timer）的脚本需要较多帧，帧数给得太小会被误判为「未完成」。

.PARAMETER ReportPath
  指定报告输出路径（Markdown）。默认 artifacts/autotest/报告-<时间戳>.md。

.PARAMETER NoReport
  不生成报告文件，只在控制台输出。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/Test-All.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/Test-All.ps1 -Quick

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/Test-All.ps1 -OnlyGames flight,piano -SkipBuild
#>
param(
    [switch]$Quick,
    [switch]$Instrumented,
    [switch]$SkipBuild,
    [switch]$SkipDevice,
    [switch]$SkipUnit,
    [switch]$SkipImport,
    [string]$Device,
    [string]$OnlyGames,
    [int]$QuitAfter = 20000,
    [string]$ReportPath,
    [switch]$NoReport
)

. "$PSScriptRoot/Common.ps1"

# 本程序要「跑完全部再汇总」，不能被单点错误中断，因此覆盖 Common.ps1 的 Stop 策略。
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

$PackageName = 'com.minigames.collection.next'
$EngineActivity = "$PackageName/com.minigames.collection.engine.EngineGameActivity"
$HallActivity = "$PackageName/com.minigames.collection.MainActivity"
$EngineProcess = "$PackageName:godot"

$TestDir = Join-Path $GodotProject 'tests'
$CatalogFile = Join-Path $GodotProject 'games/suite/catalog.gd'
$OutDir = Join-Path $ProjectRoot 'artifacts/autotest'
$LogDir = Join-Path $OutDir 'logs'
if (-not (Test-Path -LiteralPath $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

# 不是测试脚本的辅助/工具脚本，需从自动发现中排除。
$NonTestScripts = @('back_shell_probe', 'export_licenses', 'prepare_memory_art')

# 益智答题（education）8 款游戏。
$EducationIds = @(
    'poetry_adventure', 'poetry_duel',
    'pinyin_adventure', 'pinyin_duel',
    'listening_adventure', 'listening_duel',
    'english_adventure', 'english_duel'
)

# 测试脚本 -> 覆盖的游戏 id。用于把脚本级结果折算成逐游戏结果。
$ScriptGameMap = @{
    'run_tests'                 = @('plane')
    'test_flight'               = @('flight')
    'test_flight_view'          = @('flight')
    'test_animal'               = @('animal')
    'test_animal_view'          = @('animal')
    'test_stone'                = @('tictactoe', 'gomoku', 'reversi', 'weiqi')
    'test_stone_view'           = @('tictactoe', 'gomoku', 'reversi', 'weiqi')
    'test_puzzle'               = @('game2048', 'sliding', 'mines', 'memory_duel', 'simon')
    'test_puzzle_view'          = @('game2048', 'sliding', 'mines', 'memory', 'memory_duel', 'simon')
    'test_arcade'               = @('snake', 'snakebattle', 'tetris', 'whack', 'piano', 'reaction', 'reaction4', 'math_duel')
    'test_arcade_view'          = @('snake', 'snakebattle', 'tetris', 'whack', 'piano', 'reaction', 'reaction4', 'math_duel')
    'test_physics'              = @('bounce', 'breakout', 'fruit', 'pong', 'curling', 'pool')
    'test_physics_view'         = @('bounce', 'breakout', 'fruit', 'pong', 'curling', 'pool')
    'test_chinese'              = @('xiangqi_duel', 'darkchess')
    'test_chinese_view'         = @('xiangqi_duel', 'darkchess')
    'test_memory_art'           = @('memory', 'memory_duel')
    'test_whack_pace'           = @('whack')
    'test_fruit_classic'        = @('fruit')
    'test_fruit_audio'          = @('fruit')
    'test_plane_balance'        = @('plane')
    'test_plane_campaign'       = @('plane')
    'test_learning'             = $EducationIds
    'test_learning_view'        = $EducationIds
    'test_learning_bank'        = $EducationIds
    'test_listening_duel_audio' = @('listening_duel')
}
# 遍历整个目录、覆盖全部游戏的脚本。
$AllGamesScripts = @('test_hall', 'test_kids_smoke', 'test_polish_smoke')

# Kotlin 单元测试类 -> 覆盖的游戏 id。
$UnitGameMap = @{
    'FlightBoardTest'    = @('flight')
    'CurlingPhysicsTest' = @('curling')
    'XiangqiRulesTest'   = @('xiangqi_duel')
}

# 已知问题：只做标注，仍然计入失败，避免掩盖真实回归。
# 例：@{ 'test_xxx' = '原因说明与修复建议' }
$KnownIssues = @{}

# ────────────────────────────────────────────────────────────── 基础设施

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$Exe,
        [string[]]$Arguments = @(),
        [string]$LogPath
    )
    $raw = & $Exe @Arguments 2>&1
    $code = $LASTEXITCODE
    $text = ($raw | ForEach-Object { $_.ToString() }) -join "`n"
    if ($LogPath) { $text | Set-Content -LiteralPath $LogPath -Encoding UTF8 }
    return @{ Code = $code; Text = $text }
}

function Write-Stage {
    param([string]$Title)
    Write-Host ''
    Write-Host ("═" * 62) -ForegroundColor DarkGray
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host ("═" * 62) -ForegroundColor DarkGray
}

function Write-Line {
    param([string]$Text, [string]$Color = 'Gray')
    Write-Host "  $Text" -ForegroundColor $Color
}

function Format-Size {
    param([long]$Bytes)
    if ($Bytes -ge 1048576) { return ('{0:N1} MiB' -f ($Bytes / 1048576.0)) }
    if ($Bytes -ge 1024) { return ('{0:N1} KiB' -f ($Bytes / 1024.0)) }
    return "$Bytes B"
}

# 中文按 2 个字符宽计算，保证控制台表格对齐。
function Get-DisplayWidth {
    param([string]$Text)
    $w = 0
    foreach ($ch in $Text.ToCharArray()) {
        $code = [int]$ch
        if (($code -ge 0x1100 -and $code -le 0x115F) -or
            ($code -ge 0x2E80 -and $code -le 0xA4CF) -or
            ($code -ge 0xAC00 -and $code -le 0xD7A3) -or
            ($code -ge 0xF900 -and $code -le 0xFAFF) -or
            ($code -ge 0xFE30 -and $code -le 0xFE6F) -or
            ($code -ge 0xFF00 -and $code -le 0xFF60) -or
            ($code -ge 0xFFE0 -and $code -le 0xFFE6)) { $w += 2 } else { $w += 1 }
    }
    return $w
}

function Format-Cell {
    param([string]$Text, [int]$Width)
    if ($null -eq $Text) { $Text = '' }
    $d = Get-DisplayWidth -Text $Text
    if ($d -ge $Width) { return $Text }
    return $Text + (' ' * ($Width - $d))
}

# 读取游戏目录（id / 标题 / 分类），保持 catalog.gd 中的声明顺序。
function Get-GameCatalog {
    $games = New-Object System.Collections.ArrayList
    if (-not (Test-Path -LiteralPath $CatalogFile)) { return $games }
    $pattern = '^\s*"([a-z0-9_]+)"\s*:\s*\{\s*"title"\s*:\s*"([^"]+)"\s*,\s*"category"\s*:\s*"([A-Z]+)"'
    foreach ($line in [IO.File]::ReadAllLines($CatalogFile)) {
        $m = [regex]::Match($line, $pattern)
        if ($m.Success) {
            [void]$games.Add([pscustomobject]@{
                Id       = $m.Groups[1].Value
                Title    = $m.Groups[2].Value
                Category = $m.Groups[3].Value
            })
        }
    }
    return $games
}

# 自动发现 Godot 测试脚本。
function Get-GodotTestScripts {
    if (-not (Test-Path -LiteralPath $TestDir)) { return @() }
    $found = Get-ChildItem -LiteralPath $TestDir -Filter '*.gd' -File |
        Where-Object { $_.BaseName -like 'test_*' -or $_.BaseName -eq 'run_tests' } |
        Where-Object { $NonTestScripts -notcontains $_.BaseName } |
        Sort-Object Name
    return @($found | ForEach-Object { $_.BaseName })
}

# 从 Godot 测试输出里提取 "XXX_TESTS: N checks, M failures"。
function Get-TestSummary {
    param([string]$Text)
    $m = [regex]::Match($Text, '([A-Z][A-Z_ ]*TESTS):\s*(\d+)\s+checks?,\s*(\d+)\s+failures?')
    if ($m.Success) {
        return @{
            Label    = $m.Groups[1].Value
            Checks   = [int]$m.Groups[2].Value
            Failures = [int]$m.Groups[3].Value
        }
    }
    return $null
}

# 读取 Kotlin 单元测试 XML 结果。
function Get-UnitTestResults {
    $results = New-Object System.Collections.ArrayList
    $dir = Join-Path $ProjectRoot 'app/build/test-results/testDebugUnitTest'
    if (-not (Test-Path -LiteralPath $dir)) { return $results }
    foreach ($file in Get-ChildItem -LiteralPath $dir -Filter 'TEST-*.xml' -File) {
        $xml = [xml](Get-Content -LiteralPath $file.FullName -Raw)
        $suite = $xml.testsuite
        if ($null -eq $suite) { continue }
        [void]$results.Add([pscustomobject]@{
            Class    = $suite.name
            Short    = ($suite.name -split '\.')[-1]
            Tests    = [int]$suite.tests
            Failures = [int]$suite.failures
            Errors   = [int]$suite.errors
            Skipped  = [int]$suite.skipped
        })
    }
    return $results
}

# 读取仪器化测试 XML 结果。
function Get-InstrumentedResults {
    $dir = Join-Path $ProjectRoot 'app/build/outputs/androidTest-results/connected/debug'
    if (-not (Test-Path -LiteralPath $dir)) { return $null }
    $file = Get-ChildItem -LiteralPath $dir -Filter 'TEST-*.xml' -File | Select-Object -First 1
    if (-not $file) { return $null }
    $xml = [xml](Get-Content -LiteralPath $file.FullName -Raw)
    $suite = $xml.testsuite
    if ($null -eq $suite) { return $null }
    return [pscustomobject]@{
        Tests    = [int]$suite.tests
        Failures = [int]$suite.failures
        Errors   = [int]$suite.errors
        Skipped  = [int]$suite.skipped
        Time     = $suite.time
    }
}

# ────────────────────────────────────────────────────────────── 设备辅助

function Get-AdbArgs {
    if ($Device) { return @('-s', $Device) }
    return @()
}

function Get-OnlineDevice {
    $lines = @(& $Adb devices)
    $ready = @($lines | Where-Object { $_ -match '^(\S+)\s+device\s*$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($Device) {
        if ($ready -contains $Device) { return $Device }
        return $null
    }
    if ($ready.Count -ge 1) { return $ready[0] }
    return $null
}

function Get-DeviceLog {
    param([string[]]$AdbArgs)
    $raw = & $Adb @AdbArgs logcat -d -s godot:V AndroidRuntime:E 2>&1
    return (($raw | ForEach-Object { $_.ToString() }) -join "`n")
}

function Wait-DeviceLog {
    param(
        [string[]]$AdbArgs,
        [string]$Pattern,
        [int]$TimeoutSec = 25
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $text = ''
    while ((Get-Date) -lt $deadline) {
        $text = Get-DeviceLog -AdbArgs $AdbArgs
        if ($text -match $Pattern) { return @{ Ok = $true; Text = $text } }
        Start-Sleep -Milliseconds 300
    }
    return @{ Ok = $false; Text = $text }
}

# ────────────────────────────────────────────────────────────── 结果容器

$StageResults = New-Object System.Collections.ArrayList
$GodotResults = New-Object System.Collections.ArrayList
$DeviceResults = New-Object System.Collections.ArrayList
$Findings = New-Object System.Collections.ArrayList

function Add-Stage {
    param([string]$Name, [bool]$Passed, [string]$Detail)
    [void]$StageResults.Add([pscustomobject]@{ Name = $Name; Passed = $Passed; Detail = $Detail })
}

$startedAt = Get-Date
Write-Host ''
Write-Host '  迷你游戏合集（新版）· 自动化测试' -ForegroundColor White
Write-Host "  开始时间 $($startedAt.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor DarkGray
if ($Quick) { Write-Host '  模式：快速（跳过构建与设备阶段）' -ForegroundColor Yellow }

# ────────────────────────────────────────────────────────────── 1. 环境检查

Write-Stage '1/8 开发环境检查'
$envScript = Join-Path $PSScriptRoot 'Check-Environment.ps1'
$envResult = Invoke-Native -Exe 'powershell' -Arguments @('-ExecutionPolicy', 'Bypass', '-File', $envScript) -LogPath (Join-Path $LogDir 'environment.log')
$envOk = $envResult.Code -eq 0
Add-Stage -Name '开发环境检查' -Passed $envOk -Detail $(if ($envOk) { '组件齐备' } else { "退出码 $($envResult.Code)" })
if ($envOk) {
    Write-Line '[通过] Godot / Gradle / JDK / ADB / 安卓导出模板 齐备' 'Green'
} else {
    Write-Line "[失败] 环境检查退出码 $($envResult.Code)，详见 $LogDir\environment.log" 'Red'
    [void]$Findings.Add('环境检查未通过，后续阶段结果可能不完整。')
}

# ────────────────────────────────────────────────────────────── 2. 资源导入

if (-not $SkipImport) {
    Write-Stage '2/8 Godot 资源导入'
    $importArgs = @('--headless', '--path', $GodotProject, '--editor', '--import')
    $importResult = Invoke-Native -Exe $GodotConsole -Arguments $importArgs -LogPath (Join-Path $LogDir 'import.log')
    $importOk = $importResult.Code -eq 0
    Add-Stage -Name 'Godot 资源导入' -Passed $importOk -Detail $(if ($importOk) { '导入完成' } else { "退出码 $($importResult.Code)" })
    Write-Line $(if ($importOk) { '[通过] 资源导入完成' } else { "[失败] 资源导入退出码 $($importResult.Code)" }) $(if ($importOk) { 'Green' } else { 'Red' })
} else {
    Add-Stage -Name 'Godot 资源导入' -Passed $true -Detail '已跳过'
    Write-Line '[跳过] 资源导入' 'DarkGray'
}

# ────────────────────────────────────────────────────────────── 3. Godot 引擎测试

Write-Stage '3/8 Godot 引擎测试'
$scripts = Get-GodotTestScripts
if ($scripts.Count -eq 0) {
    Write-Line "[失败] 未在 $TestDir 发现任何测试脚本" 'Red'
    Add-Stage -Name 'Godot 引擎测试' -Passed $false -Detail '未发现测试脚本'
} else {
    Write-Line "发现 $($scripts.Count) 个测试脚本" 'DarkGray'
    Write-Host ''
    Write-Host ('  {0,-24} {1,8} {2,10}  {3}' -f '脚本', 'checks', 'failures', '结果') -ForegroundColor DarkGray
    $godotFail = 0
    foreach ($name in $scripts) {
        $logPath = Join-Path $LogDir "$name.log"
        $args = @('--headless', '--path', $GodotProject, '--script', "res://tests/$name.gd", '--quit-after', "$QuitAfter")
        $res = Invoke-Native -Exe $GodotConsole -Arguments $args -LogPath $logPath
        $summary = Get-TestSummary -Text $res.Text
        $hasScriptError = $res.Text -match 'SCRIPT ERROR'
        $finished = $null -ne $summary
        $passed = ($res.Code -eq 0) -and $finished -and (-not $hasScriptError) -and ($summary.Failures -eq 0)
        $checks = 0; $failures = 0
        if ($finished) { $checks = $summary.Checks; $failures = $summary.Failures }
        $state = '通过'
        if (-not $finished) { $state = '未完成' }
        elseif (-not $passed) { $state = '失败' }
        if (-not $passed) { $godotFail++ }
        [void]$GodotResults.Add([pscustomobject]@{
            Script   = $name
            Checks   = $checks
            Failures = $failures
            Passed   = $passed
            Finished = $finished
            State    = $state
            ExitCode = $res.Code
            Log      = $logPath
        })
        $color = if ($passed) { 'Green' } else { 'Red' }
        Write-Host ('  {0} {1} {2}  {3}' -f (Format-Cell $name 28), (Format-Cell "$checks" 8), (Format-Cell "$failures" 10), $state) -ForegroundColor $color
        if ($KnownIssues.ContainsKey($name) -and -not $passed) {
            Write-Line "已知问题：$($KnownIssues[$name])" 'Yellow'
        }
    }
    $totalChecks = ($GodotResults | Measure-Object -Property Checks -Sum).Sum
    $totalFail = ($GodotResults | Measure-Object -Property Failures -Sum).Sum
    $godotIncomplete = @($GodotResults | Where-Object { -not $_.Finished }).Count
    Write-Host ''
    Write-Line "合计 $totalChecks checks / $totalFail failures，失败脚本 $godotFail 个$(if ($godotIncomplete -gt 0) { "（其中未完成 $godotIncomplete 个）" })" $(if ($godotFail -eq 0) { 'Green' } else { 'Red' })
    Add-Stage -Name 'Godot 引擎测试' -Passed ($godotFail -eq 0) -Detail "$totalChecks checks / $totalFail failures（$($scripts.Count) 个脚本$(if ($godotIncomplete -gt 0) { "，未完成 $godotIncomplete" })）"
    if ($godotFail -gt 0) {
        foreach ($r in ($GodotResults | Where-Object { -not $_.Passed })) {
            $note = if ($KnownIssues.ContainsKey($r.Script)) { '（已知问题）' } else { '' }
            $why = if ($r.Finished) { "失败 $($r.Failures) 项" } else { "未完成（未输出汇总行，可调大 -QuitAfter）" }
            [void]$Findings.Add("Godot 测试 $($r.Script) $why$note，日志 $($r.Log)")
        }
    }
}

# ────────────────────────────────────────────────────────────── 4. Kotlin 单元测试

$unitResults = @()
if (-not $SkipUnit) {
    Write-Stage '4/8 Kotlin 单元测试'
    $unitResult = Invoke-Native -Exe $Gradle -Arguments @(':app:testDebugUnitTest', '--console=plain') -LogPath (Join-Path $LogDir 'unit-tests.log')
    $unitResults = Get-UnitTestResults
    $unitFail = 0
    foreach ($u in $unitResults) { $unitFail += $u.Failures + $u.Errors }
    $unitOk = ($unitResult.Code -eq 0) -and ($unitFail -eq 0) -and ($unitResults.Count -gt 0)
    foreach ($u in $unitResults) {
        $mark = if (($u.Failures + $u.Errors) -eq 0) { '通过' } else { '失败' }
        Write-Host ('  {0,-42} {1,4} 项  {2}' -f $u.Short, $u.Tests, $mark) -ForegroundColor $(if (($u.Failures + $u.Errors) -eq 0) { 'Green' } else { 'Red' })
    }
    $unitTotal = ($unitResults | Measure-Object -Property Tests -Sum).Sum
    if ($null -eq $unitTotal) { $unitTotal = 0 }
    Write-Line "合计 $unitTotal 项，失败 $unitFail 项" $(if ($unitOk) { 'Green' } else { 'Red' })
    Add-Stage -Name 'Kotlin 单元测试' -Passed $unitOk -Detail "$unitTotal 项 / $unitFail 失败"
    if (-not $unitOk) { [void]$Findings.Add("Kotlin 单元测试未通过（失败 $unitFail 项），日志 $LogDir\unit-tests.log") }
} else {
    Add-Stage -Name 'Kotlin 单元测试' -Passed $true -Detail '已跳过'
    Write-Stage '4/8 Kotlin 单元测试'
    Write-Line '[跳过]' 'DarkGray'
}

# ────────────────────────────────────────────────────────────── 5. 构建校验

$apkPath = $null
if (-not $SkipBuild -and -not $Quick) {
    Write-Stage '5/8 构建校验'
    $debugArgs = @(':app:assembleDebug', '-PtargetAbis=x86_64', '--console=plain')
    $debugResult = Invoke-Native -Exe $Gradle -Arguments $debugArgs -LogPath (Join-Path $LogDir 'build-debug.log')
    $debugOk = $debugResult.Code -eq 0
    Write-Line $(if ($debugOk) { '[通过] 调试版构建（x86_64）' } else { '[失败] 调试版构建（x86_64）' }) $(if ($debugOk) { 'Green' } else { 'Red' })

    $releaseArgs = @(':app:assembleRelease', '-PtargetAbis=arm64-v8a', '--console=plain')
    $releaseResult = Invoke-Native -Exe $Gradle -Arguments $releaseArgs -LogPath (Join-Path $LogDir 'build-release.log')
    $releaseOk = $releaseResult.Code -eq 0
    Write-Line $(if ($releaseOk) { '[通过] 正式版构建（arm64-v8a）' } else { '[失败] 正式版构建（arm64-v8a）' }) $(if ($releaseOk) { 'Green' } else { 'Red' })

    $apkPath = Join-Path $ProjectRoot 'app/build/outputs/apk/debug/app-debug.apk'
    if (Test-Path -LiteralPath $apkPath) {
        $size = (Get-Item -LiteralPath $apkPath).Length
        Write-Line "调试版 APK：$(Format-Size $size)" 'DarkGray'
    }
    Add-Stage -Name '构建校验' -Passed ($debugOk -and $releaseOk) -Detail "debug=$(if ($debugOk) {'OK'} else {'FAIL'}) release=$(if ($releaseOk) {'OK'} else {'FAIL'})"
    if (-not ($debugOk -and $releaseOk)) { [void]$Findings.Add("构建未通过，日志见 $LogDir\build-*.log") }
} else {
    Add-Stage -Name '构建校验' -Passed $true -Detail '已跳过'
    Write-Stage '5/8 构建校验'
    Write-Line '[跳过]' 'DarkGray'
}

# ────────────────────────────────────────────────────────────── 6. 设备逐款冒烟

$games = Get-GameCatalog
$deviceSerial = $null
$deviceInfo = ''
if (-not $SkipDevice -and -not $Quick) {
    Write-Stage '6/8 设备逐款冒烟'
    $deviceSerial = Get-OnlineDevice
    if (-not $deviceSerial) {
        Write-Line '[跳过] 没有在线设备；启动模拟器后重跑本阶段' 'Yellow'
        Add-Stage -Name '设备逐款冒烟' -Passed $true -Detail '无在线设备，已跳过'
    } else {
        $deviceInfo = $deviceSerial
        $AdbArgs = Get-AdbArgs
        $abi = (@(& $Adb @AdbArgs shell getprop ro.product.cpu.abi) -join '').Trim()
        $sdk = (@(& $Adb @AdbArgs shell getprop ro.build.version.sdk) -join '').Trim()
        Write-Line "设备 $deviceSerial（$abi / API $sdk）" 'DarkGray'

        # 确保设备上跑的是刚构建出来的调试包（构建过就强制重装，避免残留旧版本）。
        $debugApk = Join-Path $ProjectRoot 'app/build/outputs/apk/debug/app-debug.apk'
        $installed = @(& $Adb @AdbArgs shell pm path $PackageName) -join ''
        $hasPackage = $installed -match 'package:'
        $builtNow = (-not $SkipBuild -and -not $Quick)
        if (-not (Test-Path -LiteralPath $debugApk)) {
            Write-Line '[跳过] 未找到调试包 app-debug.apk，请先构建（去掉 -SkipBuild）' 'Yellow'
            Add-Stage -Name '设备逐款冒烟' -Passed $true -Detail '缺少调试包，已跳过'
        } elseif ($builtNow -or -not $hasPackage) {
            $reason = if ($builtNow) { '安装本次构建的调试包' } else { '设备未安装，安装调试包' }
            Write-Line "$reason…" 'DarkGray'
            $installOut = @(& $Adb @AdbArgs install -r $debugApk) -join ' '
            if ($installOut -notmatch 'Success') {
                Write-Line "[失败] 安装失败：$installOut" 'Red'
                Add-Stage -Name '设备逐款冒烟' -Passed $false -Detail '安装失败'
                [void]$Findings.Add("设备安装失败：$installOut")
            }
        } else {
            Write-Line '设备已安装调试包（未构建则沿用现有版本）' 'DarkGray'
        }

        $stillMissing = -not ((@(& $Adb @AdbArgs shell pm path $PackageName) -join '') -match 'package:')
        if (-not $stillMissing) {
            # 启动大厅并等待引擎就绪
            & $Adb @AdbArgs logcat -c | Out-Null
            & $Adb @AdbArgs shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $HallActivity -f 0x10200000 | Out-Null
            $hall = Wait-DeviceLog -AdbArgs $AdbArgs -Pattern 'GAME_HALL_READY' -TimeoutSec 40
            $initialPid = (@(& $Adb @AdbArgs shell pidof $EngineProcess) -join '').Trim()
            if (-not $hall.Ok) {
                Write-Line '[失败] 大厅未就绪（未看到 GAME_HALL_READY）' 'Red'
                Add-Stage -Name '设备逐款冒烟' -Passed $false -Detail '大厅未就绪'
                [void]$Findings.Add('设备上大厅未就绪，逐款冒烟未执行。')
            } else {
                Write-Line "引擎已就绪（pid $initialPid）" 'DarkGray'
                Write-Host ''
                Write-Host ('  {0,-16} {1,-22} {2}' -f 'id', '启动', '返回大厅') -ForegroundColor DarkGray

                $targetIds = $games.Id
                if ($OnlyGames) {
                    $wanted = @($OnlyGames -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
                    $targetIds = @($targetIds | Where-Object { $wanted -contains $_ })
                }

                foreach ($gid in $targetIds) {
                    $game = $games | Where-Object { $_.Id -eq $gid } | Select-Object -First 1
                    $title = if ($game) { $game.Title } else { $gid }
                    & $Adb @AdbArgs logcat -c | Out-Null
                    & $Adb @AdbArgs shell am start -n $EngineActivity --es game $gid | Out-Null
                    $ready = Wait-DeviceLog -AdbArgs $AdbArgs -Pattern ("GAME_READY:" + [regex]::Escape($gid)) -TimeoutSec 25
                    $crashed = $ready.Text -match 'FATAL EXCEPTION|SCRIPT ERROR'
                    $pidNow = (@(& $Adb @AdbArgs shell pidof $EngineProcess) -join '').Trim()
                    $launchOk = $ready.Ok -and (-not $crashed)

                    $hallOk = $false
                    if ($launchOk) {
                        & $Adb @AdbArgs shell input keyevent KEYCODE_BACK | Out-Null
                        if ($gid -ne 'plane') {
                            $paused = Wait-DeviceLog -AdbArgs $AdbArgs -Pattern ("GAME_PAUSED:" + [regex]::Escape($gid)) -TimeoutSec 15
                            if (-not $paused.Ok) { $launchOk = $false }
                            Start-Sleep -Milliseconds 450
                            & $Adb @AdbArgs shell input keyevent KEYCODE_BACK | Out-Null
                        }
                        $back = Wait-DeviceLog -AdbArgs $AdbArgs -Pattern 'GAME_HALL_READY' -TimeoutSec 20
                        $hallOk = $back.Ok
                    }
                    if ($initialPid -and $pidNow -and $pidNow -ne $initialPid) {
                        $launchOk = $false
                        [void]$Findings.Add("$gid 触发引擎进程重建（$initialPid -> $pidNow）")
                    }

                    [void]$DeviceResults.Add([pscustomobject]@{
                        Id       = $gid
                        Title    = $title
                        Launched = $launchOk
                        Returned = $hallOk
                    })
                    $ok = $launchOk -and $hallOk
                    Write-Host ('  {0,-16} {1,-22} {2}' -f $gid, $(if ($launchOk) { '通过' } else { '失败' }), $(if ($hallOk) { '通过' } else { '失败' })) -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
                }

                $devFail = @($DeviceResults | Where-Object { -not ($_.Launched -and $_.Returned) }).Count
                Write-Host ''
                Write-Line "共 $($DeviceResults.Count) 款，失败 $devFail 款" $(if ($devFail -eq 0) { 'Green' } else { 'Red' })
                Add-Stage -Name '设备逐款冒烟' -Passed ($devFail -eq 0) -Detail "$($DeviceResults.Count) 款 / $devFail 失败"
                if ($devFail -gt 0) {
                    foreach ($d in ($DeviceResults | Where-Object { -not ($_.Launched -and $_.Returned) })) {
                        [void]$Findings.Add("设备冒烟失败：$($d.Id)")
                    }
                }
            }
        }
    }
} else {
    Add-Stage -Name '设备逐款冒烟' -Passed $true -Detail '已跳过'
    Write-Stage '6/8 设备逐款冒烟'
    Write-Line '[跳过]' 'DarkGray'
}

# ────────────────────────────────────────────────────────────── 7. 仪器化测试

$instResult = $null
if ($Instrumented -and $deviceSerial) {
    Write-Stage '7/8 仪器化测试'
    $instArgs = @(':app:connectedDebugAndroidTest', '-PtargetAbis=x86_64', '--console=plain')
    $instRun = Invoke-Native -Exe $Gradle -Arguments $instArgs -LogPath (Join-Path $LogDir 'instrumented-tests.log')
    $instResult = Get-InstrumentedResults
    $instOk = $false
    if ($instResult) {
        $instOk = ($instRun.Code -eq 0) -and ($instResult.Failures -eq 0) -and ($instResult.Errors -eq 0)
        Write-Line "$($instResult.Tests) 项 / 失败 $($instResult.Failures) / 错误 $($instResult.Errors)" $(if ($instOk) { 'Green' } else { 'Red' })
    } else {
        Write-Line '[失败] 未找到仪器化测试结果' 'Red'
    }
    Add-Stage -Name '仪器化测试' -Passed $instOk -Detail $(if ($instResult) { "$($instResult.Tests) 项 / $($instResult.Failures) 失败" } else { '无结果' })
    if (-not $instOk) { [void]$Findings.Add("仪器化测试未通过，日志 $LogDir\instrumented-tests.log") }
} else {
    Add-Stage -Name '仪器化测试' -Passed $true -Detail $(if ($Instrumented) { '无设备，已跳过' } else { '未启用' })
    Write-Stage '7/8 仪器化测试'
    Write-Line $(if ($Instrumented) { '[跳过] 无在线设备' } else { '[跳过] 未启用（加 -Instrumented 开启）' }) 'DarkGray'
}

# ────────────────────────────────────────────────────────────── 逐游戏折算

$perGame = New-Object System.Collections.ArrayList
foreach ($g in $games) {
    $covering = New-Object System.Collections.ArrayList
    $shared = New-Object System.Collections.ArrayList
    foreach ($r in $GodotResults) {
        if ($AllGamesScripts -contains $r.Script) {
            [void]$shared.Add($r)
            continue
        }
        if ($ScriptGameMap.ContainsKey($r.Script) -and ($ScriptGameMap[$r.Script] -contains $g.Id)) {
            [void]$covering.Add($r)
        }
    }
    $coveringUnit = @($unitResults | Where-Object { $UnitGameMap.ContainsKey($_.Short) -and ($UnitGameMap[$_.Short] -contains $g.Id) })
    $dev = $DeviceResults | Where-Object { $_.Id -eq $g.Id } | Select-Object -First 1

    $engineChecked = ($covering | Measure-Object -Property Checks -Sum).Sum
    if ($null -eq $engineChecked) { $engineChecked = 0 }

    $engineState = '通过'
    if ($covering.Count -eq 0) {
        $engineState = '未覆盖'
    } elseif (@($covering | Where-Object { $_.State -eq '失败' }).Count -gt 0) {
        $engineState = '失败'
    } elseif (@($covering | Where-Object { $_.State -eq '未完成' }).Count -gt 0) {
        $engineState = '未完成'
    }

    $unitState = '—'
    if ($coveringUnit.Count -gt 0) {
        if (@($coveringUnit | Where-Object { ($_.Failures + $_.Errors) -gt 0 }).Count -gt 0) { $unitState = '失败' } else { $unitState = '通过' }
    }

    # 全量遍历类脚本（大厅/儿童/美化）对每款游戏都生效，单独一列，避免一个共享用例失败把 37 款游戏全部标红。
    $sharedState = '—'
    if ($shared.Count -gt 0) {
        if (@($shared | Where-Object { $_.State -eq '失败' }).Count -gt 0) { $sharedState = '失败' }
        elseif (@($shared | Where-Object { $_.State -eq '未完成' }).Count -gt 0) { $sharedState = '未完成' }
        else { $sharedState = '通过' }
    }

    $devState = '—'
    if ($dev) {
        if ($dev.Launched -and $dev.Returned) { $devState = '通过' } else { $devState = '失败' }
    }

    $overall = '通过'
    if ($engineState -eq '失败' -or $unitState -eq '失败' -or $devState -eq '失败') { $overall = '失败' }
    elseif ($engineState -eq '未完成') { $overall = '未完成' }
    elseif ($engineState -eq '未覆盖' -and $devState -eq '—' -and $unitState -eq '—') { $overall = '未覆盖' }

    [void]$perGame.Add([pscustomobject]@{
        Id       = $g.Id
        Title    = $g.Title
        Category = $g.Category
        Engine   = $engineState
        Checks   = $engineChecked
        Unit     = $unitState
        Shared   = $sharedState
        Device   = $devState
        Overall  = $overall
        Covering = (($covering | ForEach-Object { $_.Script }) -join ', ')
    })
}

# ────────────────────────────────────────────────────────────── 8. 汇总 + 报告

Write-Stage '8/8 汇总'
$stageFail = @($StageResults | Where-Object { -not $_.Passed }).Count
$gameFail = @($perGame | Where-Object { $_.Overall -eq '失败' -or $_.Overall -eq '未完成' }).Count
$gameIncomplete = @($perGame | Where-Object { $_.Overall -eq '未完成' }).Count

Write-Host ''
Write-Host '  阶段结果' -ForegroundColor White
foreach ($s in $StageResults) {
    $mark = if ($s.Passed) { '通过' } else { '失败' }
    Write-Host ('    {0} {1} {2}' -f (Format-Cell $s.Name 20), (Format-Cell $mark 6), $s.Detail) -ForegroundColor $(if ($s.Passed) { 'Green' } else { 'Red' })
}

Write-Host ''
Write-Host '  逐游戏结果' -ForegroundColor White
Write-Host ('    {0} {1} {2} {3} {4} {5} {6}' -f (Format-Cell '游戏' 16), (Format-Cell '引擎' 8), (Format-Cell '单元' 6), (Format-Cell '共享' 6), (Format-Cell '设备' 6), (Format-Cell '结论' 8), 'checks') -ForegroundColor DarkGray
foreach ($p in $perGame) {
    $color = 'Green'
    if ($p.Overall -eq '失败') { $color = 'Red' }
    elseif ($p.Overall -eq '未完成') { $color = 'Magenta' }
    elseif ($p.Overall -eq '未覆盖') { $color = 'Yellow' }
    Write-Host ('    {0} {1} {2} {3} {4} {5} {6}' -f (Format-Cell $p.Title 16), (Format-Cell $p.Engine 8), (Format-Cell $p.Unit 6), (Format-Cell $p.Shared 6), (Format-Cell $p.Device 6), (Format-Cell $p.Overall 8), $p.Checks) -ForegroundColor $color
}

if ($Findings.Count -gt 0) {
    Write-Host ''
    Write-Host '  待关注' -ForegroundColor Yellow
    foreach ($f in $Findings) { Write-Host "    - $f" -ForegroundColor Yellow }
}

$elapsed = (Get-Date) - $startedAt
Write-Host ''
$verdict = if ($stageFail -eq 0 -and $gameFail -eq 0) { '全部通过' } else { '存在失败项' }
$verdictColor = if ($stageFail -eq 0 -and $gameFail -eq 0) { 'Green' } else { 'Red' }
Write-Host "  结论：$verdict（失败阶段 $stageFail，失败游戏 $gameFail$(if ($gameIncomplete -gt 0) { "，其中未完成 $gameIncomplete" })，用时 $([int]$elapsed.TotalSeconds) 秒）" -ForegroundColor $verdictColor
Write-Host ''

# ── 生成 Markdown 报告 ──
$reportFile = $ReportPath
if (-not $reportFile -and -not $NoReport) {
    $reportFile = Join-Path $OutDir ("报告-{0}.md" -f $startedAt.ToString('yyyyMMdd-HHmmss'))
}

if ($reportFile) {
    $sb = New-Object System.Text.StringBuilder
    function Add-Line2 { param([string]$T) ; [void]$sb.AppendLine($T) }

    Add-Line2 "# 自动化测试报告 · 安卓离线小游戏合集（新版）"
    Add-Line2 ''
    Add-Line2 "| 项 | 值 |"
    Add-Line2 "|---|---|"
    Add-Line2 "| 运行时间 | $($startedAt.ToString('yyyy-MM-dd HH:mm:ss')) |"
    Add-Line2 "| 用时 | $([int]$elapsed.TotalSeconds) 秒 |"
    Add-Line2 "| 工程 | ``$ProjectRoot`` |"
    Add-Line2 "| 引擎 | Godot（headless console） |"
    Add-Line2 "| 设备 | $(if ($deviceInfo) { $deviceInfo } else { '未使用' }) |"
    Add-Line2 "| 游戏总数 | $($games.Count) |"
    Add-Line2 "| 结论 | **$verdict**（失败阶段 $stageFail，失败游戏 $gameFail$(if ($gameIncomplete -gt 0) { "，其中未完成 $gameIncomplete" })） |"
    Add-Line2 ''
    Add-Line2 "> 本报告由 ``scripts/Test-All.ps1`` 自动生成，全程只读，不修改任何游戏代码。"
    Add-Line2 ''

    Add-Line2 '## 一、阶段结果'
    Add-Line2 ''
    Add-Line2 '| 阶段 | 结果 | 明细 |'
    Add-Line2 '|---|---|---|'
    foreach ($s in $StageResults) {
        $mark = if ($s.Passed) { '✅ 通过' } else { '❌ 失败' }
        Add-Line2 "| $($s.Name) | $mark | $($s.Detail) |"
    }
    Add-Line2 ''

    Add-Line2 '## 二、Godot 引擎测试明细'
    Add-Line2 ''
    Add-Line2 '| 脚本 | checks | failures | 结果 |'
    Add-Line2 '|---|---:|---:|---|'
    foreach ($r in $GodotResults) {
        $mark = if ($r.Passed) { '✅ 通过' } elseif (-not $r.Finished) { '⚠️ 未完成' } else { '❌ 失败' }
        Add-Line2 "| ``$($r.Script)`` | $($r.Checks) | $($r.Failures) | $mark |"
    }
    $gc = ($GodotResults | Measure-Object -Property Checks -Sum).Sum
    $gf = ($GodotResults | Measure-Object -Property Failures -Sum).Sum
    Add-Line2 "| **合计** | **$gc** | **$gf** | |"
    Add-Line2 ''

    if ($unitResults.Count -gt 0) {
        Add-Line2 '## 三、Kotlin 单元测试明细'
        Add-Line2 ''
        Add-Line2 '| 测试类 | 项 | 失败 | 错误 |'
        Add-Line2 '|---|---:|---:|---:|'
        foreach ($u in $unitResults) {
            Add-Line2 "| $($u.Short) | $($u.Tests) | $($u.Failures) | $($u.Errors) |"
        }
        Add-Line2 ''
    }

    if ($instResult) {
        Add-Line2 '## 四、仪器化测试'
        Add-Line2 ''
        Add-Line2 "共 $($instResult.Tests) 项，失败 $($instResult.Failures)，错误 $($instResult.Errors)，用时 $($instResult.Time) 秒。"
        Add-Line2 ''
    }

    Add-Line2 '## 五、逐游戏结果'
    Add-Line2 ''
    Add-Line2 '| # | 游戏 | id | 分类 | 专属引擎测试 | checks | 单元测试 | 共享用例 | 设备冒烟 | 结论 |'
    Add-Line2 '|---:|---|---|---|---|---:|---|---|---|---|'
    $i = 0
    foreach ($p in $perGame) {
        $i++
        $mark = if ($p.Overall -eq '通过') { '✅ 通过' } elseif ($p.Overall -eq '失败') { '❌ 失败' } elseif ($p.Overall -eq '未完成') { '⚠️ 未完成' } else { '➖ 未覆盖' }
        Add-Line2 "| $i | $($p.Title) | ``$($p.Id)`` | $($p.Category) | $($p.Engine) | $($p.Checks) | $($p.Unit) | $($p.Shared) | $($p.Device) | $mark |"
    }
    Add-Line2 ''
    Add-Line2 '> 「专属引擎测试」= 只覆盖该游戏的脚本；「共享用例」= 遍历全部游戏的大厅/儿童/美化脚本（' + ($AllGamesScripts -join '、') + '），单列显示以免一个共享用例失败把全部游戏标红。'
    Add-Line2 ''

    Add-Line2 '### 覆盖来源'
    Add-Line2 ''
    Add-Line2 '| 游戏 | 覆盖脚本 |'
    Add-Line2 '|---|---|'
    foreach ($p in $perGame) {
        Add-Line2 "| $($p.Title) | $(if ($p.Covering) { $p.Covering } else { '（仅大厅级）' }) |"
    }
    Add-Line2 ''

    if ($Findings.Count -gt 0) {
        Add-Line2 '## 六、待关注项'
        Add-Line2 ''
        foreach ($f in $Findings) { Add-Line2 "- $f" }
        Add-Line2 ''
    }

    if ($GodotResults.Count -gt 0) {
        $knownHit = @($GodotResults | Where-Object { (-not $_.Passed) -and $KnownIssues.ContainsKey($_.Script) })
        if ($knownHit.Count -gt 0) {
            Add-Line2 '## 七、已知问题说明'
            Add-Line2 ''
            foreach ($k in $knownHit) {
                Add-Line2 "**$($k.Script)**"
                Add-Line2 ''
                Add-Line2 $KnownIssues[$k.Script]
                Add-Line2 ''
            }
        }
    }

    Add-Line2 '## 附录 · 日志位置'
    Add-Line2 ''
    Add-Line2 "- 全部日志目录：``artifacts/autotest/logs/``"
    Add-Line2 "- 本次报告：``$reportFile``"

    $reportDir = Split-Path -Parent $reportFile
    if ($reportDir -and -not (Test-Path -LiteralPath $reportDir)) { New-Item -ItemType Directory -Force -Path $reportDir | Out-Null }
    $sb.ToString() | Set-Content -LiteralPath $reportFile -Encoding UTF8

    $latest = Join-Path $OutDir 'latest.md'
    $sb.ToString() | Set-Content -LiteralPath $latest -Encoding UTF8

    $json = [pscustomobject]@{
        StartedAt    = $startedAt.ToString('s')
        ElapsedSec   = [int]$elapsed.TotalSeconds
        Verdict      = $verdict
        FailedStages = $stageFail
        FailedGames  = $gameFail
        Device       = $deviceInfo
        Stages       = $StageResults
        Godot        = $GodotResults
        Unit         = $unitResults
        Instrumented = $instResult
        Games        = $perGame
        Findings     = $Findings
    }
    $json | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir 'result.json') -Encoding UTF8

    Write-Host "  报告：$reportFile" -ForegroundColor Cyan
    Write-Host "  最新：$latest" -ForegroundColor Cyan
    Write-Host "  日志：$LogDir" -ForegroundColor Cyan
    Write-Host ''
}

if ($stageFail -eq 0 -and $gameFail -eq 0) { exit 0 }
exit 1
