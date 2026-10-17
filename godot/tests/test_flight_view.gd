extends SceneTree
const View = preload("res://games/flight/flight_view.gd")
var checks: int = 0
var failed: int = 0
func check(value: bool, text: String) -> void:
    checks += 1
    if not value: failed += 1; push_error(text)
func _initialize() -> void:
    call_deferred("run")
func run() -> void:
    root.size = Vector2i(720,1280)
    var game = View.new()
    root.add_child(game)
    await process_frame
    check(not quit_on_go_back and not auto_accept_quit,"真正的SceneTree必须禁用自动退出，让暂停通知生效")
    game.set_process(false)
    game.start_round()
    game.players = 3
    game.mode = 1
    game._begin()
    check(game.stage == "consent", "同屏必须先各方准备")
    game._action("ready0")
    game._action("ready0")
    game._action("ready2")
    check(game.stage == "consent" and game.ready_seats.size() == 2,"重复点击不能冒充其他玩家")
    game._action("ready1")
    check(game.stage == "play", "三方准备后才开局")
    var before: Array = game.model.positions.duplicate(true)
    game._layout()
    check(game.model.positions == before and game.stage == "play","尺寸变化保留对局")
    game.model.reset(4)
    for seat in [0,1,2,3]:
        game.model.turn = seat
        game.queue_redraw()
        await process_frame
        var enabled: Dictionary = {}
        for candidate in game.buttons:
            if candidate.action == "roll": enabled = candidate
        if seat in game.model.seats:
            check(not enabled.is_empty() and enabled.top == (seat in [1,2]), "黄绿在上，红蓝在下掷骰")
    var old_size := root.size
    root.size = Vector2i(720,1600)
    await process_frame
    game._layout()
    var above: float = game.board_rect.position.y-game.top_panel.end.y
    var below: float = game.bottom_panel.position.y-game.board_rect.end.y
    check(absf(above-below) < 2, "棋盘在上下操作区之间居中，留白均衡")
    root.content_scale_size = Vector2i(1280,720)
    var landscape_before: Array = game.model.positions.duplicate(true)
    for screen in [Vector2i(1280,720),Vector2i(1280,960),Vector2i(1600,1000)]:
        root.size = screen
        await process_frame
        game._layout()
        check(game.wide_layout and game.board_rect.size.x >= screen.y*0.65,"横屏手机和平板使用大棋盘")
        check(not game.board_rect.intersects(game.top_panel) and not game.board_rect.intersects(game.bottom_panel),"横屏棋盘与上下操作区互不覆盖")
        check(Rect2(Vector2.ZERO,Vector2(screen)).encloses(game.board_rect) and game.bottom_panel.end.y <= screen.y,"横屏棋盘和操作区均在屏内")
        check(game.model.positions == landscape_before,"横屏布局计算完成")
    game.model.turn = 1
    game.queue_redraw()
    await process_frame
    var landscape_roll: Dictionary = {}
    for candidate in game.buttons:
        if candidate.action == "roll": landscape_roll = candidate
    if not landscape_roll.is_empty():
        var touch := InputEventScreenTouch.new()
        touch.pressed = true
        touch.position = landscape_roll.panel.end-landscape_roll.rect.get_center()
        game._input(touch)
    check(game.dice_time > 0 and game.roll_seat == 1,"横屏黄色倒置操作区触摸正确")
    game.dice_time = 0
    game.result_time = 0
    game.model.reset(4)
    root.content_scale_size = Vector2i(720,1280)
    root.size = old_size
    await process_frame
    game._layout()
    game.model.turn = 0
    game.last_faces[0] = 2
    game.face = 2
    game._roll(6)
    check(game.model.phase == game.Model.ROLL and game.model.last_dice == 0 and game.face == 2, "翻滚期间不提前揭晓点数或改变回合")
    game._process(0.3)
    await process_frame
    game._process(0.8)
    await process_frame
    check(game.dice_time > 0 and game.model.last_dice == 0, "骰子有完整翻滚过程")
    game.set_game_paused(true)
    var roll_at: float = game.roll_elapsed
    game._process(10)
    check(game.roll_elapsed == roll_at, "暂停冻结骰子翻滚")
    game.set_game_paused(false)
    game._process(2)
    await process_frame
    check(game.face == 6 and game.model.last_dice == 6 and game.model.sixes == 1 and game.result_time > 0, "落稳后揭晓6且规则只执行一次")
    game._action("piece0")
    check(game.model.positions[0][0] == -1, "停骰反馈期间避免误触走子")
    game._process(2)
    check(game.model.sixes == 1 and not game._busy(), "结果展示结束后解锁，不重复掷骰")
    game.queue_redraw()
    await process_frame
    var button: Dictionary = {}
    for candidate in game.buttons:
        if candidate.action == "piece0": button = candidate
    check(not button.is_empty(), "起飞操作有大号编号按钮")
    if not button.is_empty():
        var event := InputEventScreenTouch.new()
        event.pressed = true
        event.position = button.panel.position+button.rect.get_center()
        game._input(event)
        game._input(event)
    check(game.model.positions[0][0] == 0 and game.model.positions[0].count(0) == 1,"点击编号起飞且重复触摸不重复走子")
    game.set_game_paused(true)
    var at: Vector2 = game.moving_at
    game._process(10)
    check(game.moving_at == at and not game.move_data.is_empty(),"暂停冻结移动动画")
    game.set_game_paused(false)
    game._process(0.18)
    await process_frame
    check(game.moving_lift > 0 and not game.trail.is_empty(),"飞机起飞有抬升和尾迹，不瞬移到终点")
    game._process(10)
    check(game.move_data.is_empty(),"恢复后继续完成动画")
    check(game.moving_at.is_equal_approx(game.board_rect.position+Vector2(4.6,16.45)*game.cell),"触摸起飞动画实际停在机场旁起字圆点")
    game.model.reset(4)
    game.model.positions[0][0] = 4
    game.model.positions[2][0] = 31
    game.model.roll(1)
    game._move(0)
    check(game.model.positions[2][0] == -1 and game.visual_positions[2][0] == 31, "撞机先保留画面，等待本机实际落地")
    game._process(10)
    await process_frame
    check(not game.return_flights.is_empty() and game._busy(), "落地后对方飞机沿返航动画回机场")
    game._process(1)
    check(game.return_flights.is_empty(), "返航结束释放输入")
    await process_frame
    var next_roll: Dictionary = {}
    for candidate in game.buttons:
        if candidate.action == "roll": next_roll = candidate
    check(not game._busy() and not next_roll.is_empty(),"撞机返航最后一帧刷新按钮，下一方可继续掷骰")
    if not next_roll.is_empty():
        var next_touch := InputEventScreenTouch.new()
        next_touch.pressed = true
        next_touch.position = next_roll.panel.end-next_roll.rect.get_center() if next_roll.top else next_roll.panel.position+next_roll.rect.get_center()
        game._input(next_touch)
    check(game.dice_time > 0 and game.roll_seat == game.model.turn,"撞回机场后经真实触摸入口进入下一回合")
    game.dice_time = 0
    game.result_time = 0
    game.model.reset(4)
    game.model.turn = 1
    game.queue_redraw()
    await process_frame
    var yellow: Dictionary = {}
    for candidate in game.buttons:
        if candidate.action == "roll": yellow = candidate
    if not yellow.is_empty():
        var upper_touch := InputEventScreenTouch.new()
        upper_touch.pressed = true
        upper_touch.position = yellow.panel.end-yellow.rect.get_center()
        game._input(upper_touch)
    check(game.dice_time > 0 and game.roll_seat == 1, "黄色上方180度面板按物理触点正确掷骰")
    game.start_round()
    check(game.stage == "setup" and not game.paused and game.move_data.is_empty(),"重开返回选局并清除旧动画")
    game.mode = 2
    game.players = 4
    game._begin()
    var results: Array = []
    game.round_finished.connect(func(score, reason): results.append([score,reason]))
    for i in range(25000):
        if game.stage == "ended": break
        game._process(1)
    check(game.stage == "ended" and results.size() == 1,"自动演示完成四人对局且只结算一次")
    check(game.model.home_count(game.model.winner) == 4,"演示胜方四架飞机确实全部到家")
    game.queue_free()
    await process_frame
    var shell_probe = load("res://tests/back_shell_probe.gd").new()
    root.add_child(shell_probe)
    shell_probe._notification(Node.NOTIFICATION_WM_GO_BACK_REQUEST)
    shell_probe._notification(Node.NOTIFICATION_WM_GO_BACK_REQUEST)
    check(shell_probe.exit_count == 0 and not shell_probe.session.is_running(), "一次返回键的重复通知只能暂停，不能退出对局")
    shell_probe.clock_ms += 200
    shell_probe._notification(Node.NOTIFICATION_WM_GO_BACK_REQUEST)
    check(shell_probe.exit_count == 1, "稍后再次返回才退出")
    shell_probe.queue_free()
    await process_frame
    print("FLIGHT_VIEW_TESTS: %d checks, %d failures"%[checks,failed])
    quit(0 if failed == 0 else 1)