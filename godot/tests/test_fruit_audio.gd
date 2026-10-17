extends SceneTree
var failures: int = 0
var checks: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void: call_deferred("run")
func run() -> void:
    var platform = root.get_node("GamePlatform")
    platform.launch.sound = true
    var view = load("res://games/physics/fruit.tscn").instantiate()
    root.add_child(view); await process_frame
    view.set_process(false); view.start_round(); view._action("begin")
    var audio = view.fruit_audio
    audio.swipe(0)
    check(audio.variant == 0,"点按不发出挥刀声")
    audio.swipe(110)
    check(audio.variant == 1,"空挥触发风切声")
    audio.swipe(110)
    check(audio.variant == 1,"短时间重复拖动不会堆叠挥刀声")
    view.model.critical_chance = 0
    view.model.fruits = [view.model.make_fruit(Vector2(300,300),Vector2.ZERO)]
    view.model.cut(Vector2(200,300),Vector2(400,300)); view._events()
    check(audio.variant == 2,"切中水果触发专用破皮汁水声")
    view.model.fruits = [view.model.make_fruit(Vector2(300,300),Vector2.ZERO,0,true)]
    view.model.cut(Vector2(200,300),Vector2(400,300)); view._events()
    check(audio.variant == 3,"切中炸弹触发专用爆炸声")
    var active: int = 0
    for player in audio.voices:
        if player.playing: active += 1
    check(active == 1,"爆炸停止残余切果与挥刀声")
    view.set_game_paused(true)
    active = 0
    for player in audio.voices:
        if player.playing: active += 1
    check(active == 0,"暂停停止专用音效")
    platform.launch.sound = false; audio.bomb()
    check(audio.variant == 3,"静音设置阻止专用音效")
    view.queue_free(); await process_frame
    print("FRUIT_AUDIO_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
