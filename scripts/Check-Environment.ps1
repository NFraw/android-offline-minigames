$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Common.ps1"
$required = @($GodotConsole, $Gradle, $Adb, (Join-Path $DevConfig.javaPath 'bin/java.exe'), (Join-Path $DevConfig.sdkPath 'platforms/android-36/android.jar'))
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path)) { throw "缺少开发组件：$path" }
    Write-Output "就绪：$path"
}
& $GodotConsole --version
Assert-NativeExit 'Godot 版本检查'
& java -version
Assert-NativeExit 'Java 检查'
& $Adb devices -l
Assert-NativeExit 'ADB 检查'
$templatePath = Join-Path $ProjectRoot ".tools/godot/editor_data/export_templates/$($DevConfig.godotVersion).stable"
foreach ($name in @('android_debug.apk','android_release.apk','android_source.zip')) {
    if (-not (Test-Path (Join-Path $templatePath $name))) { throw "缺少安卓导出模板：$name" }
}
Write-Output '开发环境检查通过。'
