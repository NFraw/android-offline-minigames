extends SceneTree
const Catalog = preload("res://games/suite/catalog.gd")
const Polish = preload("res://core/game_polish.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, label: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(label)
func _initialize() -> void: call_deferred("run")
func settle() -> void:
    for i in range(4): await process_frame
func run() -> void:
    var platform = root.get_node("GamePlatform")
    for id in Catalog.GAMES:
        platform.open_game(id); await settle()
        var game = current_scene.content
        if id == "plane": current_scene._primary()
        else:
            game._action("begin")
            if id == "animal": game._action("ready0"); game._action("ready1")
            elif game.get("players") != null:
                for seat in range(game.players): game._action("ready%d"%seat)
        game.set_process(false)
        if id in ["gomoku","weiqi","reversi"]: game.model.board[0] = 1; game.model.board[1] = 2
        if id == "whack": game.model.moles = {0:{"age":0.3,"remaining":0.6},4:{"age":0.3,"remaining":0.6},8:{"age":0.3,"remaining":0.6}}
        if id == "fruit":
            game.model.fruits.clear()
            for i in range(9): game.model.fruits.append({"p":Vector2(100+(i%5)*180,180+int(i/5)*230),"radius":40.0,"type":i%8,"bomb":i == 8})
        if id in ["memory","memory_duel"]:
            game.model.first = 0
            check(game.MEMORY_SIZES == [4,6,8],"记忆最大8×8")
        game.queue_redraw(); await settle()
        check(is_instance_valid(game),"基础开局与美术加载 "+id)
        platform.exit_to_hall(); await settle()
    check(Polish.textures.has("mole") and Polish.textures.has("fruit0") and Polish.textures.has("bomb"),"角色和水果素材可生成")
    check(Polish.textures.has("tile-neutral"),"彩块复用中性纹理")
    var bytes: int = 0
    for texture in Polish.textures.values(): bytes += int(texture.get_width()*texture.get_height()*4*1.34)
    print("POLISH_CACHE: %d textures, %.2f MiB"%[Polish.textures.size(),bytes/1048576.0])
    check(bytes < 12*1024*1024,"美术缓存不超过12MiB")
    platform.launch.game = "flight"
    check(platform.audio_cue("eat") == "board_capture","飞行棋撞机音")
    platform.launch.game = "memory"
    check(platform.audio_cue("eat") == "combo","配对音")
    check(platform.audio_cue("ui_click") == "ui_click","UI音不受游戏映射影响")
    print("POLISH TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
