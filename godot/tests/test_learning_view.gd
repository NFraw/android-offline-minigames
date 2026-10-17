extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, label: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(label)
func _initialize() -> void: call_deferred("run")
func run() -> void:
    var catalog = load("res://games/suite/catalog.gd")
    var platform = root.get_node("GamePlatform")
    var count: int = 0
    for id in catalog.GAMES:
        if not catalog.GAMES[id].get("educational",false): continue
        count += 1
        var view = load(catalog.GAMES[id].scene).instantiate()
        root.add_child(view)
        await process_frame
        view.start_round()
        check(not view.game_rules.contains("年级"),"游戏不显示年级分类")
        view._action("begin")
        if id.ends_with("duel"):
            check(view.stage == "consent","双人先进入准备页")
            view._action("ready0"); view._action("ready1")
        check(view.stage == "play" and view.model.questions.size() == 10,"进入正式答题")
        await process_frame
        if id.begins_with("listening"):
            var play_action: String = "duel_listen0" if view.players == 2 else "note_question"
            platform.launch.sound = false
            view._game_action(play_action)
            check(not view.model.heard,"静音时不能标记已播放")
            platform.launch.sound = true
            view._game_action(play_action)
            check(not view.model.heard,"音序播放完之前不能作答")
            for attempt in range(110):
                if view.model.heard: break
                await create_timer(0.05).timeout
            check(view.model.heard,"钢琴音序完整播放后可以作答")
        var correct: int = view.model.question().choices.find(view.model.question().answer)
        view._game_action("answer0_%d"%correct)
        if view.players == 2:
            check(view.model.revealing and view.model.scores == [1,0],"先答对立即得分，无需等对方")
            view._game_action("answer1_%d"%correct)
            check(view.model.scores == [1,0],"揭晓后另一方不能重复得分")
        check(view.model.revealing,"答题后显示讲解")
        view.set_game_paused(true)
        var remaining: float = view.model.reveal_time
        view._process(1)
        check(view.model.reveal_time == remaining,"暂停后计时冻结")
        view.set_game_paused(false)
        view._process(0.05)
        view.model.reveal_time = 1
        view._game_action("next")
        check(view.model.index == 1,"手动下一题")
        view.queue_free()
        await process_frame
    check(count == 8,"益智共8款且全部拥有Godot场景")
    await create_timer(0.15).timeout
    print("LEARNING VIEW TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
