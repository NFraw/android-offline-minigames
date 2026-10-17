extends SceneTree
const Art = preload("res://core/memory_art.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, label: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(label)
func _initialize() -> void: call_deferred("run")
func run() -> void:
    check(Art.texture().get_size() == Vector2(1536,768),"图集尺寸正确")
    var shared = Art.texture()
    var regions: Dictionary = {}
    for i in range(32): regions[Art.source_rect(i)] = true
    check(regions.size() == 32,"32种动物各有独立图块")
    check(Art.source_rect(31).end.x <= Art.texture().get_width(),"8×8的32对动物均有独立图片")
    check(Art.texture() == shared,"图集对象缓存复用")
    var model = load("res://games/puzzle/puzzle_model.gd").new()
    model.memory_size = 16; model.reset("memory")
    check(model.cols == 8 and model.cells.size() == 64,"即使传入旧尺寸也限制为8×8")
    check(model.LEVELS == [4,6,8],"记忆对决三关最高8×8")
    for id in ["memory","memory_duel"]:
        var game = load("res://games/puzzle/%s.tscn"%id).instantiate()
        root.add_child(game)
        await process_frame
        game.start_round(); game._action("begin")
        if id == "memory_duel": game._action("ready0"); game._action("ready1")
        check(game.memory_mode == 0,"默认动物模式 "+id)
        game._tap(0); game.queue_redraw()
        await process_frame
        check(game.model.first == 0,"翻牌仍可操作 "+id)
        if id == "memory_duel":
            game.model.turn = 1; game.queue_redraw(); await process_frame
        game.set_game_paused(true)
        game.queue_free(); await process_frame
    print("MEMORY ART TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
