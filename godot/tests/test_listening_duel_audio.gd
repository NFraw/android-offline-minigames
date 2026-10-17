extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void: call_deferred("run")
func run() -> void:
    var v = load("res://games/education/listening_duel.tscn").instantiate()
    root.add_child(v)
    await process_frame
    v.start_round(); v._action("begin"); v._action("ready0"); v._action("ready1")
    check(v.get("reference_uses") is Array,"双人听音应分别记录每题两次播放额度")
    if not v.get("reference_uses") is Array:
        v.queue_free(); await process_frame
        print("DUEL AUDIO TESTS: %d checks, %d failures"%[checks,failures]); quit(1); return
    var short_note := AudioStreamWAV.new()
    short_note.format = AudioStreamWAV.FORMAT_16_BITS; short_note.mix_rate = 44100
    var data := PackedByteArray(); data.resize(882); short_note.data = data
    v.note_streams.fill(short_note)
    v.model.questions[0].notes = [1,2]
    var platform = root.get_node("GamePlatform")
    platform.launch.sound = false; v._game_action("duel_listen0")
    check(v.reference_uses == [0,0],"静音点击不消耗额度")
    platform.launch.sound = true
    v._game_action("duel_listen0")
    check(v.reference_uses == [1,0] and v.note_queue == [1,2],"先播放基准do，队列自动跟上本题音序")
    check(not v.model.heard,"只启动基准音不能提前作答")
    v._game_action("duel_listen1"); v._game_action("duel_listen0")
    check(v.reference_uses == [1,0],"双方同时点击只有第一份被接受，不占用另一方次数")
    for attempt in range(60):
        if v.model.heard: break
        await create_timer(0.03).timeout
    check(v.model.heard and not v._audio_busy(),"基准后自动播放一次题目，完整结束才解锁")
    v._game_action("duel_listen0")
    check(v.reference_uses == [2,0],"同一玩家每题可以播放第二次")
    for attempt in range(60):
        if not v._audio_busy(): break
        await create_timer(0.03).timeout
    v._game_action("duel_listen0")
    check(v.reference_uses == [2,0] and not v._audio_busy(),"第三次点击被拒绝")
    v._game_action("duel_listen1")
    check(v.reference_uses == [2,1] and v._audio_busy(),"另一方仍有自己的额度")
    v.set_game_paused(true)
    check(v.reference_uses == [2,0] and not v._audio_busy(),"暂停中断时退还未播完的额度")
    v.set_game_paused(false)
    var correct: int = v.model.question().choices.find(v.model.question().answer)
    v._game_action("answer0_%d"%correct); v.model.reveal_time = 1; v._game_action("next")
    check(v.reference_uses == [0,0] and not v.model.heard,"下一题恢复双方两次机会")
    v._game_action("note_ref"); v._game_action("note_question")
    check(not v._audio_busy(),"双人不能绕过组合按钮独立播放")
    v.queue_free(); await process_frame; await create_timer(0.1).timeout
    print("DUEL AUDIO TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
