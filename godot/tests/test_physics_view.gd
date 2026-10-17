extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    call_deferred("run")
func snapshot(model, resize_fruit: bool = false) -> String:
    var values: Dictionary = {}
    for property in model.get_property_list():
        if resize_fruit and property.name == "size": continue
        if property.usage & PROPERTY_USAGE_SCRIPT_VARIABLE and property.name != "rng": values[property.name] = model.get(property.name)
    return var_to_str(values)
func touch(game, at: Vector2, finger: int = 0, pressed: bool = true) -> void:
    var event := InputEventScreenTouch.new()
    event.position = at; event.index = finger; event.pressed = pressed
    game._input(event)
func drag(game, at: Vector2, finger: int = 0) -> void:
    var event := InputEventScreenDrag.new()
    event.position = at; event.index = finger
    game._input(event)
func run() -> void:
    root.content_scale_size = Vector2i(720,1280)
    root.size = Vector2i(720,1280)
    for id in ["bounce","breakout","fruit","pong","curling","pool"]:
        var game = load("res://games/physics/%s.tscn"%id).instantiate()
        root.add_child(game)
        await process_frame
        game.set_process(false)
        game.start_round()
        if id == "curling": game._action("players"); game._action("players")
        game._action("begin")
        for owner in range(game.players):
            if game.players > 1: game._action("ready%d"%owner)
        game.queue_redraw()
        await process_frame
        check(game.stage == "play","物理游戏开局 "+id)
        var before: String = snapshot(game.model,id == "fruit")
        game.set_game_paused(true); game._process(10); touch(game,game.rect.get_center())
        check(snapshot(game.model,id == "fruit") == before,"暂停冻结物理与触摸 "+id)
        game.set_game_paused(false)
        root.size = Vector2i(1000,1600)
        await process_frame
        check(snapshot(game.model,id == "fruit") == before,"尺寸变化保留已有物体和对局 "+id)
        root.size = Vector2i(720,1280)
        await process_frame
        if id == "pong":
            touch(game,game._screen(Vector2(500,1200)),0)
            touch(game,game._screen(Vector2(500,200)),7)
            drag(game,game._screen(Vector2(50,1200)),0)
            drag(game,game._screen(Vector2(950,200)),7)
            check(game.model.paddles == [100.0,900.0],"双人多指独立拖拍")
        elif id == "fruit":
            game.model.critical_chance = 0
            game.model.fruits = [{"p":Vector2(500,300),"v":Vector2.ZERO,"radius":30.0,"type":0,"bomb":false}]
            touch(game,game._screen(Vector2(450,300)),2)
            drag(game,game._screen(Vector2(550,300)),2)
            touch(game,game._screen(Vector2(550,300)),2,false)
            check(game.score == 1 and not game.trails.is_empty(),"经典模式真实滑动切割与刀痕")
            game._game_process(0.4)
            check(game.trails.is_empty(),"松手后的刀痕逐段消失")
        elif id == "curling":
            check(game.players == 4 and game.model.rounds == 5,"多人选择影响实际冰壶局制")
            var p := Vector2(500,1500)
            var f := Vector2(600,1300)
            touch(game,game._screen(p),3); game._game_process(0.4)
            game.set_game_paused(true); game.set_game_paused(false)
            touch(game,game._screen(f),3,false)
            check(game.model.balls.is_empty(),"暂停取消未完成投壶，恢复不误出手")
            touch(game,game._screen(p),4); game._game_process(0.4)
            drag(game,game._screen(f),4); touch(game,game._screen(f),4,false)
            var solution: Dictionary = game.Curling.aim(p,f)
            check(game.model.balls.size() == 1 and game.model.balls[0].v.normalized().is_equal_approx(solution.direction),"松手真实投出与预览方向相同")
        elif id == "pool":
            check(game.vertical and game._screen(Vector2(200,250)).y > game._screen(Vector2(740,250)).y,"竖屏母球靠玩家1底部，球阵在上方")
            for p in [Vector2.ZERO,Vector2(200,250),Vector2(1000,500)]:
                check(game._world(game._screen(p)).distance_to(p) < 0.01,"台球屏幕与物理坐标互逆")
            game.model.ball_in_hand = true; game.model.phase = "place"
            touch(game,game._screen(Vector2(140,350)))
            check(game.model.phase == "aim" and game.model.get_ball(0).p.distance_to(Vector2(140,350)) < 0.01,"自由球经真实触摸放置")
            game.model.groups = [1,2]
            for b in game.model.balls:
                if b.num in range(1,8): b.active = false
            game.model.start_turn()
            touch(game,game._screen(game.Pool.POCKETS[2]))
            check(game.model.call_pocket == 2 and game.model.phase == "aim","打8前真实点击叫袋")
        else:
            touch(game,game._screen(Vector2(100,1200)),5)
            drag(game,game._screen(Vector2(800,1200)),5)
            check(is_equal_approx(game.model.paddles[0],800),"拖动挡板实际更新")
        game.queue_free()
        await process_frame
    print("PHYSICS_VIEW_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
