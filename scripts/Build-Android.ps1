param([switch]$Release, [switch]$Test, [switch]$Instrumented, [switch]$Install, [string]$Device, [ValidateSet("arm64-v8a","armeabi-v7a","x86_64","x86","all")][string]$Abi)
. "$PSScriptRoot/Common.ps1"
if (-not $Abi) {
    $Abi = if ($Release) { 'arm64-v8a' } else { 'x86_64' }
    if (($Install -or $Instrumented) -and $Device -and (@(& $Adb devices) -match ('^' + [regex]::Escape($Device) + '\s+device\s*$'))) {
        $deviceAbi = (@(& $Adb -s $Device shell getprop ro.product.cpu.abi) -join '').Trim()
        if ($deviceAbi -in @('arm64-v8a','armeabi-v7a','x86_64','x86')) { $Abi = $deviceAbi }
    }
}
$abiList = if ($Abi -eq 'all') { 'arm64-v8a,armeabi-v7a,x86_64,x86' } else { $Abi }
Write-Output "本次 APK 架构：$Abi"
$variant = if ($Release) { 'Release' } else { 'Debug' }
$tasks = @(':app:assemble' + $variant)
if ($Test) {
    & "$PSScriptRoot/Test-Godot.ps1"
    $tasks += ':app:testDebugUnitTest'
}
if ($Instrumented) { $tasks += ':app:connectedDebugAndroidTest' }
if ($Device) { $env:ANDROID_SERIAL = $Device }
Push-Location $ProjectRoot
try {
    & $Gradle @tasks "-PtargetAbis=$abiList" --console=plain
    Assert-NativeExit '安卓构建'
    if ($Release) {
        $output = Join-Path $ProjectRoot 'app/build/outputs/apk/release/app-release.apk'
        $releaseDirectory = Join-Path $ProjectRoot 'releases'
        New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
        $versionName = [regex]::Match([IO.File]::ReadAllText((Join-Path $ProjectRoot 'app/build.gradle.kts')), 'versionName = "([^"]+)"').Groups[1].Value
        $namedApk = Join-Path $releaseDirectory "迷你游戏合集-v$versionName-$Abi.apk"
        Copy-Item -LiteralPath $output -Destination $namedApk -Force
        Write-Output "已生成：$namedApk"
    }
    if ($Install) {
        $apk = Join-Path $ProjectRoot "app/build/outputs/apk/$($variant.ToLower())/app-$($variant.ToLower()).apk"
        $deviceArgs = @()
        if ($Device) { $deviceArgs = @('-s', $Device) }
        # ADB 冷启动可能暂时显示 offline，最多等待 30 秒。
        $ready = $false
        for ($attempt = 0; $attempt -lt 30; $attempt++) {
            $devices = @(& $Adb devices)
            if ($Device) {
                $ready = [bool]($devices -match ('^' + [regex]::Escape($Device) + '\s+device\s*$'))
            } else {
                $ready = @($devices | Where-Object { $_ -match '\s+device\s*$' }).Count -eq 1
            }
            if ($ready) { break }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw '设备未就绪；请启动模拟器或连接真机，多设备时指定 -Device。' }
        & $Adb @deviceArgs install -r $apk
        Assert-NativeExit '安装新版 APK'
    }
} finally { Pop-Location }




