extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    call_deferred("run")
func touch(game, at: Vector2) -> void:
    var event := InputEventScreenTouch.new()
    event.position = at
    event.pressed = true
    game._input(event)
func run() -> void:
    root.content_scale_size = Vector2i(720,1280)
    root.size = Vector2i(720,1280)
    for id in ["tictactoe","gomoku","reversi","weiqi"]:
        var game = load("res://games/stone/%s.tscn"%id).instantiate()
        root.add_child(game)
        await process_frame
        game.set_process(false)
        game.start_round()
        game._action("begin")
        check(game.stage == "consent","双人棋类必须双方准备")
        game._action("ready0")
        game._action("ready1")
        game.queue_redraw()
        await process_frame
        var first: int = game.model.legal_moves()[0]
        var point: Vector2 = game.rect.position+(Vector2(game.model.point(first))+Vector2.ONE*0.5)*game.cell
        touch(game,point)
        check(game.model.board[first] == 1,"真实触摸入口落子 "+id)
        var before: PackedInt32Array = game.model.board.duplicate()
        game.set_game_paused(true)
        var next: int = game.model.legal_moves()[0]
        touch(game,game.rect.position+(Vector2(game.model.point(next))+Vector2.ONE*0.5)*game.cell)
        check(game.model.board == before,"暂停拒绝落子 "+id)
        game.set_game_paused(false)
        root.size = Vector2i(1000,1600)
        await process_frame
        check(game.model.board == before,"尺寸变化保留对局 "+id)
        root.size = Vector2i(720,1280)
        await process_frame
        if id in ["gomoku","reversi"]:
            game.ai_mode = true
            var count: int = game.model.board.count(0)
            next = game.model.legal_moves()[0]
            touch(game,game.rect.position+(Vector2(game.model.point(next))+Vector2.ONE*0.5)*game.cell)
            check(game.model.board.count(0) == count,"AI回合屏蔽人类操作")
            game._game_process(1)
            check(game.model.board.count(0) == count-1,"AI延迟后真实落子")
        if id == "weiqi":
            game._action("pass")
            check(game.stage == "play","一次虚手不结束")
            game._action("pass")
            check(game.finished and game.stage == "ended","两次虚手通过界面结算")
        game.queue_free()
        await process_frame
    print("STONE_VIEW_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
