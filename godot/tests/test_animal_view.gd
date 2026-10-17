extends SceneTree
const View = preload("res://games/animal/animal_view.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    call_deferred("run")
func touch(game, at: Vector2) -> void:
    var event := InputEventScreenTouch.new()
    event.pressed = true
    event.position = at
    game._input(event)
func click_action(game, action: String) -> bool:
    for button in game.buttons:
        if button.action == action:
            var at: Vector2 = button.rect.get_center()
            if button.panel.size != Vector2.ZERO:
                at = button.panel.end-at if button.top else button.panel.position+at
            touch(game,at)
            return true
    return false
func run() -> void:
    root.content_scale_size = Vector2i(1280,720)
    root.size = Vector2i(1280,720)
    var game = View.new()
    root.add_child(game)
    await process_frame
    game.set_process(false)
    game.start_round()
    await process_frame
    check(click_action(game,"competitive") and game.competitive,"通过真实按钮选择比赛模式")
    await process_frame
    check(click_action(game,"begin") and game.stage == "consent","开始后进入双方独立准备")
    await process_frame
    check(click_action(game,"ready1") and game.ready_sides == [1],"倒置蓝方面板按物理触点准备")
    game._action("ready1")
    check(game.stage == "consent","重复点击同一方不能代替另一方准备")
    await process_frame
    check(click_action(game,"ready0") and game.stage == "play","双方准备后开局")
    touch(game,game._center(0))
    check(game.selected == -1,"红方不能选择蓝方棋子")
    touch(game,game._center(48))
    check(game.selected == 48,"触摸格子选中红鼠")
    touch(game,game._center(40))
    check(game.animation.is_empty() and game.selected == 48,"非法位置明确拒绝且保留选子")
    touch(game,game._center(47))
    check(not game.animation.is_empty() and game.model.board[47] == 1,"点高亮位置后走棋")
    var remaining: float = game.model.remaining
    game._process(0.1)
    check(game.model.remaining == remaining,"走棋动画不吞掉对方思考时间")
    var at: float = game.animation_time
    game.set_game_paused(true)
    game._process(10)
    check(game.animation_time == at,"暂停冻结动画")
    game.set_game_paused(false)
    game._process(1)
    await process_frame
    check(game.animation.is_empty() and game.model.turn == 1,"移动结束刷新并解锁下一方")
    touch(game,game._center(14))
    check(game.selected == 14,"动画结束后蓝方可立即触摸选子")
    game.selected = -1
    game.rules_open = true
    game._process(50)
    check(game.model.winner == -1 and game.model.remaining == remaining,"查看规则暂停比赛计时")
    game.rules_open = false
    var snapshot: PackedInt32Array = game.model.board.duplicate()
    for screen in [Vector2i(1280,720),Vector2i(1280,960),Vector2i(1600,1000)]:
        root.size = screen
        await process_frame
        game._layout()
        check(Rect2(Vector2.ZERO,Vector2(screen)).encloses(game.board_rect.grow(12)),"手机及平板棋盘完整入屏")
        check(not game.board_rect.intersects(game.top_panel) and not game.board_rect.intersects(game.bottom_panel),"棋盘不压住玩家面板")
        check(is_equal_approx(game.board_rect.size.x/game.board_rect.size.y,7.0/9.0),"棋盘维持7列9行比例")
        check(game.model.board == snapshot,"改变尺寸不重置对局")
    game.model.reset(false)
    game.model.board.fill(0)
    game.model.board[7] = 1
    game.model.board[8] = -8
    game.model.board[62] = -2
    game._tap(7)
    game._tap(8)
    check(game.animation.captured == -8,"吃子动画保留被吃兽")
    game._process(1)
    await process_frame
    check(game.animation.is_empty() and game.model.board[8] == 1,"吃子结束清除移动状态")
    game._tap(62)
    check(game.selected == 62,"吃子后下一方仍可操作")
    game.model.reset(false)
    game.model.board.fill(0)
    game.model.board[21] = 6
    game.model.board[0] = -8
    game.selected = -1
    game._tap(21)
    game._tap(24)
    check(game.animation.jump and game.animation_duration > 0.5,"跳河采用独立腾跃动画")
    game._process(1)
    check(game.animation.is_empty() and game.model.board[24] == 6,"跳河落地恢复输入")
    game.model.reset(true)
    game.model.board.fill(0)
    game.model.board[10] = 1
    game.model.board[62] = -8
    game.selected = -1
    var ends: Array = []
    game.round_finished.connect(func(score: int, reason: String): ends.append([score,reason]))
    game._tap(10)
    game._tap(3)
    check(game.model.winner == 0 and ends.is_empty(),"进穴后等落子动画完成再结算")
    game._process(1)
    game._process(100)
    check(ends.size() == 1 and game.stage == "ended","胜利只结算一次")
    game.start_round()
    game._action("begin")
    game._action("ready0")
    game._action("ready1")
    game._process(31)
    check(ends.size() == 2 and game.model.winner == 1,"比赛超时经界面层真实结算")
    game.queue_free()
    await process_frame
    print("ANIMAL_VIEW_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
