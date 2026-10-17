extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    call_deferred("run")
func touch(game, at: Vector2, pressed: bool = true) -> void:
    var event := InputEventScreenTouch.new()
    event.position = at; event.pressed = pressed
    game._input(event)
func click(game, action: String) -> bool:
    for button in game.buttons:
        if button.action == action: touch(game,button.rect.get_center()); return true
    return false
func setup_cannon(game, screen: bool) -> void:
    game.model.reset()
    game.model.board.fill(0)
    game.model.board[85] = 1; game.model.board[4] = 11; game.model.board[49] = 7
    game.model.board[64] = 6; game.model.board[1] = 12
    if screen: game.model.board[37] = 7
    game.selected = -1
    game.animation.clear()
func run() -> void:
    root.content_scale_size = Vector2i(720,1280)
    root.size = Vector2i(720,1280)
    for id in ["xiangqi_duel","darkchess"]:
        var game = load("res://games/chinese/%s.tscn"%id).instantiate()
        root.add_child(game)
        await process_frame
        game.set_process(false)
        game.start_round()
        game._action("begin"); game._action("ready0"); game._action("ready1")
        await process_frame
        check(game.stage == "play","双方准备后开局 "+id)
        var board: PackedInt32Array = game.model.board.duplicate()
        game.set_game_paused(true)
        touch(game,game._center(0))
        check(game.model.board == board and (game.model.revealed.count(true) == 0 if id == "darkchess" else true),"暂停拒绝翻子落子 "+id)
        game.set_game_paused(false)
        root.size = Vector2i(1000,1600)
        await process_frame
        check(game.model.board == board,"尺寸变化保留象棋暗棋对局")
        root.size = Vector2i(720,1280)
        await process_frame
        if id == "xiangqi_duel":
            setup_cannon(game,true)
            touch(game,game._center(64)); touch(game,game._center(1))
            check(not game.animation.is_empty() and game.model.board[1] == 6 and game.animation.captured == 12,"经UI入口炮隔架能吃士")
            game.set_game_paused(true)
            game._game_process(0) # Direct update with zero time leaves the interpolation intact.
            var time: float = game.animation_time
            game._process(10)
            check(game.animation_time == time,"暂停冻结象棋走子动画")
            game.set_game_paused(false)
            game._game_process(0.3)
            check(game.animation.is_empty() and game.model.turn == 1,"走子结束释放输入")
            game._action("undo1")
            await process_frame
            check(game.confirmation == "undo" and game.requester == 1,"上方申请悔棋，交由下方确认")
            check(click(game,"deny") and game.model.undo_left[1] == 3 and game.model.board[1] == 6,"拒绝不撤销不扣次数")
            game._action("undo1")
            await process_frame
            check(click(game,"confirm") and game.model.board[64] == 6 and game.model.board[1] == 12 and game.model.undo_left[1] == 2,"对方确认按钮真实点击执行一次悔棋")
            setup_cannon(game,false)
            touch(game,game._center(64)); touch(game,game._center(1))
            check(game.animation.is_empty() and game.model.board[64] == 6 and game.model.board[1] == 12,"经UI入口无炮架不可吃士")
            game._action("resign0")
            await process_frame
            check(click(game,"confirm") and game.finished and game.model.winner == 1,"确认认输真实结算")
        else:
            var count: int = game.model.revealed.count(true)
            touch(game,Vector2(game.rect.position.x-1,game.rect.position.y+5))
            check(game.model.revealed.count(true) == count,"棋盘左侧留白不误翻第0列")
            touch(game,game._center(0))
            check(game.animation.flip and game.model.revealed[0] and game.model.player_sides[0] >= 0,"点击暗子首翻定阵营并有动效")
            game._game_process(0.3)
            touch(game,game._center(31))
            check(game.animation.flip and game.model.revealed[31],"翻子结束后另一方可继续操作")
        game.queue_free()
        await process_frame
    print("CHINESE_VIEW_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
