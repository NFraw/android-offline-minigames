extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    call_deferred("run")
func snapshot(model) -> String:
    var values: Dictionary = {}
    for property in model.get_property_list():
        if property.usage & PROPERTY_USAGE_SCRIPT_VARIABLE and property.name != "rng": values[property.name] = model.get(property.name)
    return var_to_str(values)
func touch(game, at: Vector2, finger: int = 0) -> void:
    var event := InputEventScreenTouch.new()
    event.position = at; event.pressed = true; event.index = finger
    game._input(event)
func click(game, action: String, finger: int = 0) -> bool:
    for button in game.buttons:
        if button.action == action: touch(game,button.rect.get_center(),finger); return true
    return false
func run() -> void:
    root.content_scale_size = Vector2i(720,1280)
    root.size = Vector2i(720,1280)
    for id in ["snake","snakebattle","tetris","whack","piano","reaction","reaction4","math_duel"]:
        var game = load("res://games/arcade/%s.tscn"%id).instantiate()
        root.add_child(game)
        if id == "tetris": check(game.model.board.size() == 200,"第一次绘制前方块棋盘已初始化")
        await process_frame
        game.set_process(false)
        game.start_round()
        game._action("begin")
        for owner in range(game.players):
            if game.players > 1: game._action("ready%d"%owner)
        game.queue_redraw()
        await process_frame
        check(game.stage == "play","即时游戏真实开局 "+id)
        var state: String = snapshot(game.model)
        game.set_game_paused(true)
        game._process(20)
        touch(game,game.arena.get_center(),6)
        check(snapshot(game.model) == state,"暂停拒绝触摸并冻结规则 "+id)
        game.set_game_paused(false)
        root.size = Vector2i(720,1600)
        await process_frame
        check(snapshot(game.model) == state,"改变屏幕尺寸不清空即时对局 "+id)
        root.size = Vector2i(720,1280)
        await process_frame
        if id == "snakebattle":
            check(click(game,"snake0_1",0) and click(game,"snake1_1",7),"同一帧两方方向键都能点击")
            check(game.model.pending == [Vector2i.UP,Vector2i.DOWN],"上方方向键按玩家视角旋转")
        elif id == "snake":
            click(game,"snake0_1")
            game._game_process(0.2)
            check(game.model.bodies[0][0].y < 12,"按键实际驱动蛇移动")
        elif id == "tetris":
            check(click(game,"tetris4"),"可点击硬降按钮")
            check(game.model.board.count(0) == 196,"硬降实际锁定4个方格")
        elif id == "whack":
            game.model.moles = {0:{"age":0.3,"remaining":1.0},8:{"age":0.3,"remaining":1.0}}
            touch(game,game.rect.position+Vector2.ONE*game.cell/2,0)
            touch(game,game.rect.position+Vector2.ONE*game.cell*2.5,6)
            check(game.score == 20,"多指交替打中地鼠不漏判")
        elif id == "piano":
            var row: Dictionary = game.model.rows[game.model.lowest()]
            var at: Vector2 = game.rect.position+Vector2((row.column+0.5)*game.cell,(row.y+0.5)*game.rect.size.y/6.5)
            touch(game,at,3)
            check(game.score == 1 and not game.finished,"真实点击最下黑块得分")
        elif id == "reaction4":
            game.model.state = 1; game.model.timer = 3
            for owner in range(4): touch(game,game._zone(owner).get_center(),owner)
            check(game.model.scores == [1,1,1,1],"四根手指同帧四区均计分")
        elif id == "reaction":
            game.model.state = 1; game.model.timer = 3
            touch(game,game._zone(1).get_center(),5)
            check(game.model.scores[1] == 1,"上方玩家真实触摸抢答")
        else:
            check(click(game,"math0_digit1",0) and click(game,"math1_digit2",7),"两套键盘同帧接收不同手指")
            check(game.model.inputs == ["1","2"],"两位玩家输入独立")
            check(click(game,"math0_digit0") and game.model.inputs[0] == "10","0键真实追加数字")
            check(click(game,"math0_delete") and game.model.inputs[0] == "1","删除键真实退格")
        game.queue_free()
        await process_frame
    print("ARCADE_VIEW_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
