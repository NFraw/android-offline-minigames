param([string]$Name = 'Medium_Phone_API_36.1')
. "$PSScriptRoot/Common.ps1"
$emulator = Join-Path $DevConfig.sdkPath 'emulator/emulator.exe'
& $Adb devices
Start-Process -FilePath $emulator -ArgumentList @('-avd', $Name, '-no-snapshot', '-gpu', 'host') -WindowStyle Normal
Write-Output '模拟器已启动；启动完成后运行 Build-Android.ps1 -Install。'



