param([string]$Name = 'Medium_Phone_API_36.1')
. "$PSScriptRoot/Common.ps1"
function Find-PreviewDevice {
    foreach ($line in @(& $Adb devices)) {
        if ($line -match '^(emulator-\d+)\s+device\s*$') {
            $serial = $Matches[1]
            $avd = @(& $Adb -s $serial emu avd name)
            if ($avd.Count -gt 0 -and $avd[0].Trim() -eq $Name) { return $serial }
        }
    }
    return $null
}
$device = Find-PreviewDevice
if (-not $device) {
    $emulator = Join-Path $DevConfig.sdkPath 'emulator/emulator.exe'
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $Name, '-gpu', 'host', '-no-snapshot', '-scale', '0.35') -WindowStyle Normal
}
$deadline = [DateTime]::UtcNow.AddSeconds(60)
$booted = $false
while ([DateTime]::UtcNow -lt $deadline) {
    $device = Find-PreviewDevice
    if ($device) {
        $boot = @(& $Adb -s $device shell getprop sys.boot_completed)
        if ($boot -contains '1') { $booted = $true; break }
    }
    Start-Sleep -Seconds 2
}
if (-not $booted) { throw '安卓尚未启动完成，请等模拟器进入桌面后再次双击。' }
$installed = @(& $Adb -s $device shell pm path com.minigames.collection.next)
if (-not ($installed -match '^package:')) {
    $apk = Join-Path $ProjectRoot 'app/build/outputs/apk/debug/app-debug.apk'
    if (-not (Test-Path $apk)) { throw '请先运行 Build-Android.ps1 生成调试 APK。' }
    & $Adb -s $device install -r $apk
    Assert-NativeExit '安装新版合集'
}
& $Adb -s $device shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n 'com.minigames.collection.next/com.minigames.collection.MainActivity' -f 0x10200000
Assert-NativeExit '打开新版合集'
Write-Output '已打开 Godot 大厅；37款游戏均可在电脑模拟器体验。'

