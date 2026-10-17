extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    call_deferred("run")
func touch(game, at: Vector2, pressed: bool = true, finger: int = 0) -> void:
    var event := InputEventScreenTouch.new()
    event.position = at; event.pressed = pressed; event.index = finger
    game._input(event)
func run() -> void:
    root.content_scale_size = Vector2i(720,1280)
    root.size = Vector2i(720,1280)
    for id in ["game2048","sliding","mines","memory","memory_duel","simon"]:
        var game = load("res://games/puzzle/%s.tscn"%id).instantiate()
        root.add_child(game)
        await process_frame
        game.set_process(false)
        game.start_round()
        game._action("begin")
        if id == "memory_duel":
            check(game.stage == "consent","双人翻牌独立准备")
            game._action("ready0"); game._action("ready1")
        check(game.stage == "play","益智场景真实开局 "+id)
        var before: PackedInt32Array = game.model.cells.duplicate()
        game.set_game_paused(true)
        touch(game,game.rect.get_center())
        game._process(20)
        check(game.model.cells == before,"暂停冻结输入和计时 "+id)
        game.set_game_paused(false)
        if id == "game2048":
            game.model.cells = PackedInt32Array([2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0])
            var start: Vector2 = game.rect.get_center()
            touch(game,start,true,3); touch(game,start-Vector2(100,0),false,3)
            check(game.model.cells[0] == 4 and game.score == 4,"真实手指滑动合并2048")
        elif id == "sliding":
            game.model.cells = PackedInt32Array([1,2,3,4,5,6,7,8,9,10,11,12,13,14,0,15])
            touch(game,game.rect.position+Vector2(3.5,3.5)*game.cell)
            check(game.finished and game.score == 492,"拼图触摸归位并结算")
        elif id == "mines":
            var at: Vector2 = game.rect.position+Vector2.ONE*game.cell/2
            touch(game,at,true,4)
            game._game_process(0.6)
            touch(game,at,false,4)
            check(game.model.flagged[0] and not game.model.placed,"长按只插旗，不误翻开")
            game._game_action("flag_mode")
            touch(game,at,true,5); touch(game,at,false,5)
            check(not game.model.flagged[0],"插旗模式可移除旗子")
            touch(game,at,true,6)
            game.set_game_paused(true)
            game.set_game_paused(false)
            touch(game,at,false,6)
            check(not game.model.placed,"暂停取消未完成长按，恢复不偷翻牌")
        elif id in ["memory","memory_duel"]:
            game.model.cells = PackedInt32Array([0,1,0,1,2,2,3,3,4,4,5,5,6,6,7,7])
            game._tap(0); game._tap(1)
            check(game.model.lock_time > 0,"配错后展示记忆")
            game._game_process(0.81)
            check(game.model.first == -1,"展示结束最后一帧重新盖牌")
            if id == "memory_duel": check(game.model.turn == 1,"双人配错换手")
            game._tap(0); game._tap(2)
            check(game.model.matched[0] and game.model.score == 10,"配对继续")
        else:
            game.model.sequence = [0,0]
            game.model.start_show()
            game._game_process(0.51)
            check(game.model.lit == -1,"连续闪灯保留灭灯阶段")
        game.queue_free()
        await process_frame
    print("PUZZLE_VIEW_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
