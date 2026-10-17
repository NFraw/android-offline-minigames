param([switch]$Run)
. "$PSScriptRoot/Common.ps1"
if ($Run) {
    & $GodotConsole --path $GodotProject
    Assert-NativeExit '运行 Godot 场景'
} else {
    Start-Process -FilePath $GodotEditor -ArgumentList @('--editor','--path',('"' + $GodotProject + '"'))
}
