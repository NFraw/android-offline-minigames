extends SceneTree
const Catalog = preload("res://games/suite/catalog.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, label: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(label)
func _initialize() -> void: call_deferred("run")
func run() -> void:
    var timed = load("res://games/arcade/timed_model.gd").new()
    timed.reset("piano")
    var bottom: int = timed.lowest()
    timed.hit_key(bottom,(timed.rows[bottom].column+1)%4)
    check(timed.done and timed.score == 0,"点白块判负")
    timed.reset("piano"); bottom = timed.lowest()
    check(timed.hit_key(bottom,timed.rows[bottom].column) and not timed.done,"点黑块正常")
    timed.tick(10)
    check(timed.done,"黑块漏到底部判负")
    var puzzle = load("res://games/puzzle/puzzle_model.gd").new()
    puzzle.reset("simon")
    check(puzzle.lit == -1,"西蒙开始先留观察时间")
    puzzle.tick(1)
    check(puzzle.lit == -1,"第一盏灯不立刻闪过")
    puzzle.tick(0.3)
    check(puzzle.lit == puzzle.sequence[0],"延迟后第一盏灯亮起")
    puzzle.tick(0.65)
    check(puzzle.lit >= 0,"亮灯足够明显")
    for n in [4,6,8]:
        puzzle.memory_size = n; puzzle.reset("memory")
        check(puzzle.cells.size() == n*n and puzzle.cells.count(0) == 2,"记忆卡规模%d"%n)
    var chess = load("res://games/chinese/chinese_model.gd").new()
    chess.reset("darkchess"); chess.board.fill(0); chess.revealed.fill(true)
    chess.board[24] = 6; chess.board[12] = 7; chess.board[0] = 12
    check(0 in chess.dark_targets(24),"暗棋炮隔一子可远吃")
    chess.board[8] = 7
    check(0 not in chess.dark_targets(24),"隔两子不可吃")
    chess.board[8] = 0; chess.revealed[0] = false
    check(0 not in chess.dark_targets(24),"不可直接吃暗子")
    var ice = load("res://games/physics/curling_model.gd")
    check(not ice.aim(Vector2(500,1500),Vector2(500,1700)).is_empty(),"投壶框内有效瞄准")
    check(ice.aim(Vector2(500,400),Vector2(500,600)).is_empty(),"框外不可选支点")
    var platform = root.get_node("GamePlatform")
    root.size = Vector2i(720,1280)
    for id in Catalog.GAMES:
        platform.open_game(id)
        for i in range(4): await process_frame
        var game = current_scene.content
        check(is_instance_valid(game),"场景可启动 "+id)
        if game.has_method("_draw_rules"):
            game.rules_open = true
            for page in range(3):
                game.guide_page = page
                game.queue_redraw(); await process_frame
            game.rules_open = false
        elif id == "animal":
            game.rules_open = true
            for page in range(3):
                game.rules_page = page; game.queue_redraw(); await process_frame
            game.rules_open = false
        platform.exit_to_hall()
        for i in range(3): await process_frame
    var guide = load("res://core/guide_overlay.gd").new()
    guide.game_id = "mines"
    current_scene.add_child(guide)
    await process_frame
    guide._turn(1)
    await process_frame
    guide.queue_free()
    print("KIDS TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
