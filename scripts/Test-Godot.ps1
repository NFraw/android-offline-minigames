. "$PSScriptRoot/Common.ps1"
& $GodotConsole --headless --path $GodotProject --editor --import
Assert-NativeExit 'Godot 资源导入'
foreach ($test in @('test_fruit_audio','test_fruit_classic','test_listening_duel_audio','test_learning_bank','test_whack_pace','test_plane_balance','test_learning','test_learning_view','run_tests','test_plane_campaign','test_flight','test_flight_view','test_animal','test_animal_view','test_stone','test_stone_view','test_puzzle','test_puzzle_view','test_arcade','test_arcade_view','test_physics','test_physics_view','test_chinese','test_chinese_view','test_hall')) {
    $testOutput = @(& $GodotConsole --headless --path $GodotProject --script "res://tests/$test.gd" --quit-after 300 2>&1)
    $testExit = $LASTEXITCODE
    $testOutput | ForEach-Object { Write-Output $_ }
    if ($testExit -ne 0 -or ($testOutput -match 'SCRIPT ERROR|ERROR:|[1-9][0-9]* failures')) { throw "Godot 测试 $test 失败" }
    if (-not ($testOutput -match 'TESTS:.*0 failures')) { throw "Godot 测试 $test 未完成" }
}

