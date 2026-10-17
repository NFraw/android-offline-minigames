extends "res://games/suite/suite_game.gd"
const Model = preload("res://games/education/learning_model.gd")
var model = Model.new()
var audio: AudioStreamPlayer
var note_streams: Array = []
var pictures: Dictionary = {}
var notice: String = ""
var feedback_done: bool = false
var note_queue: Array = []
var playback_is_question: bool = false
var playback_gap: float = 0
var reference_uses: Array = [0,0]
var playback_owner: int = -1
var playback_reference: bool = false
func _ready() -> void:
    audio = AudioStreamPlayer.new()
    add_child(audio)
    audio.finished.connect(_note_finished)
    for note in Model.NOTES: note_streams.append(load("res://games/education/assets/note_%s.wav"%note))
    super._ready()
func _rules_text() -> String:
    return "每局10题，四选一。"+("先答对立即得1分；答错本题锁定，另一方继续。双方答错或30秒超时后揭晓。" if game_id.ends_with("_duel") else "慢慢想，不限时间；答对收集星星。")+"看完讲解点击下一题。"+("听音对决每人每题可播放两次，先听基准do，再自动播放题目；播放时双方一起听，不能重复启动。" if game_id == "listening_duel" else "听音题先播放再作答。")
func _new_game() -> void:
    model.reset(game_id,false)
    reference_uses = [0,0]
    notice = ""; feedback_done = false
    message = "看清题目再选择，答错也能学到知识"
func _extra_setup(r: Rect2) -> void:
    var total: int = Model.Bank.records(game_id.get_slice("_",0)).size()
    _text("题库%d题 · 每局10题 · 轮换不重复"%total,r.position+Vector2(r.size.x/2,342),24,P.MUTED)
func _action(action: String) -> void:
    super._action(action)
    if action == "begin": model.reset(game_id)
func _game_layout() -> void:
    var size: Vector2 = get_viewport_rect().size
    arena = Rect2(20,165,size.x-40,size.y-250)
func _zone(owner: int) -> Rect2:
    if players == 1: return arena
    return Rect2(arena.position+Vector2(0,arena.size.y/2 if owner == 0 else 0),Vector2(arena.size.x,arena.size.y/2)).grow(-5)
func _point(r: Rect2, p: Vector2, top: bool) -> Vector2:
    return r.end-p*r.size if top else r.position+p*r.size
func _fit_text(caption: String, at: Vector2, width: float, wanted: int, tint: Color, top: bool) -> void:
    var measured: float = font.get_string_size(caption,HORIZONTAL_ALIGNMENT_LEFT,-1,wanted).x
    _text(caption,at,mini(wanted,int(wanted*width/maxf(width,measured))),tint,top)
func _panel_button(r: Rect2, local: Rect2, caption: String, action: String, tint: Color, top: bool) -> void:
    var physical := Rect2(r.end-local.end*r.size if top else r.position+local.position*r.size,local.size*r.size)
    _button(physical,caption,action,tint,top)
func _draw_play() -> void:
    if model.questions.is_empty(): return
    for owner in range(players): _panel(owner)
func _panel(owner: int) -> void:
    var r: Rect2 = _zone(owner)
    var top: bool = owner == 1
    var accent := Color(Book.info(game_id)[1])
    var q: Dictionary = model.question()
    _box(r,P.PIECE_HI,20)
    _text("第%d/10题 · %d颗星"%[model.index+1,model.scores[owner]],_point(r,Vector2(0.5,0.06),top),25,accent,top)
    if players == 1:
        for i in range(10): _text("★" if i < model.scores[0] else "☆",_point(r,Vector2(0.08+i*0.093,0.13),top),26,Color("b88a26"),top)
    var title_y: float = 0.25 if players == 1 else 0.18
    if q.has("picture"):
        var center: Vector2 = _point(r,Vector2(0.5,0.25),top)
        var radius: float = minf(r.size.y*0.12,110)
        preload("res://core/learning_art.gd").draw(self,int(q.picture),center,radius,top)
        title_y = 0.39
    _fit_text(q.prompt,_point(r,Vector2(0.5,title_y),top),r.size.x-25,36,P.TEXT,top)
    if model.music:
        if players == 2:
            var available: bool = reference_uses[owner] < 2 and not _audio_busy() and not model.revealing and model.selections[owner] < 0
            var caption: String = "听基准＋题目 · 还可%d次"%(2-reference_uses[owner])
            if _audio_busy(): caption = "播放中 · 双方一起听"
            elif reference_uses[owner] >= 2: caption = "本题两次播放已用完"
            elif model.selections[owner] >= 0: caption = "本题已作答"
            _panel_button(r,Rect2(0.04,0.28,0.92,0.12),caption,"duel_listen%d"%owner,accent if available else P.MUTED,top)
        else:
            _panel_button(r,Rect2(0.04,0.28,0.44,0.12),"基准 do","note_ref",accent,top)
            _panel_button(r,Rect2(0.52,0.28,0.44,0.12),"播放题目","note_question",accent,top)
    elif not q.has("picture"):
        _text("请选择下一句" if game_id.begins_with("poetry") else "补上韵母（不标声调）",_point(r,Vector2(0.5,0.36),top),24,P.MUTED,top)
    for i in range(4):
        var tint: Color = accent
        if model.revealing:
            if q.choices[i] == q.answer: tint = P.GREEN
            elif model.selections[owner] == i: tint = P.RED
            else: tint = P.MUTED
        elif model.selections[owner] == i: tint = P.RED if players == 2 else accent.darkened(0.4)
        _panel_button(r,Rect2(0.035+(i%2)*0.49,0.46+int(i/2)*0.15,0.46,0.125),q.choices[i],"answer%d_%d"%[owner,i],tint,top)
    if model.revealing:
        if players == 2:
            var winner: int = -1
            for player in range(2):
                if model.selections[player] >= 0 and q.choices[model.selections[player]] == q.answer: winner = player
            _text("本题无人得分" if winner < 0 else "玩家%d答对，+1分！"%(winner+1),_point(r,Vector2(0.5,0.425),top),23,accent,top)
        for i in range(q.explanation.size()):
            _fit_text(q.explanation[i],_point(r,Vector2(0.5,0.80+i*0.047),top),r.size.x-20,25,P.TEXT,top)
        _panel_button(r,Rect2(0.22,0.90,0.56,0.085),"查看收获" if model.index == 9 else "下一题 →","next",accent,top)
    else:
        var status: String = notice
        if model.music and players == 2 and _audio_busy(): status = "基准do播放中，请一起听" if playback_reference else "题目播放中，请一起听"
        if status.is_empty():
            if model.selections[owner] >= 0: status = "答错了，本题由对方继续"
            elif players == 2 and model.selections[1-owner] >= 0: status = "对方答错了，轮到你作答！"
            elif model.music and not model.heard: status = "点听基准＋题目，双方一起听" if players == 2 else "先播放题目，再选择答案"
            elif players == 2: status = "先答对得分 · 剩余%d秒"%ceili(model.remaining)
            else: status = "慢慢想，不限时间"
        _fit_text(status,_point(r,Vector2(0.5,0.84),top),r.size.x-20,24,P.MUTED,top)
func _player_status(seat: int) -> String:
    return "玩家%d · %d颗星"%[seat+1,model.scores[seat]]
func _game_action(action: String) -> void:
    if stage != "play" or rules_open or finished: return
    if action.begins_with("duel_listen"):
        if game_id != "listening_duel" or model.revealing or _audio_busy(): return
        if action not in ["duel_listen0","duel_listen1"]: return
        var owner: int = int(action.trim_prefix("duel_listen"))
        if reference_uses[owner] >= 2 or model.selections[owner] >= 0: return
        if not bool(get_node("/root/GamePlatform").launch.sound): notice = "请点暂停，在设置中开启音效"; return
        reference_uses[owner] += 1; playback_owner = owner
        playback_is_question = true; playback_reference = true
        note_queue = [0]+model.question().notes.duplicate()
        _play_next_note(); notice = ""
    elif action.begins_with("note_"):
        if players == 2 or model.revealing or _audio_busy(): return
        if not bool(get_node("/root/GamePlatform").launch.sound): notice = "请点暂停，在设置中开启音效"; return
        playback_is_question = action == "note_question"
        playback_reference = not playback_is_question
        note_queue = model.question().notes.duplicate() if playback_is_question else [0]
        _play_next_note()
        notice = ""
    elif action.begins_with("answer"):
        var parts: PackedStringArray = action.trim_prefix("answer").split("_")
        if model.music and not model.heard: notice = "请先播放题目，再作答"; return
        model.submit(int(parts[0]),int(parts[1]))
        _reveal_feedback()
    elif action == "next" and model.revealing and model.reveal_time >= 0.8:
        if model.advance(): notice = ""; feedback_done = false; reference_uses = [0,0]
        elif model.finished:
            if players == 1: _end("答对%d/10题，收集%d颗星星！"%[model.scores[0],model.scores[0]],model.scores[0] >= 6)
            else: _end(("平局，一起进步！" if model.scores[0] == model.scores[1] else "玩家%d获胜！"%(1 if model.scores[0] > model.scores[1] else 2))+"\n玩家1：%d分 · 玩家2：%d分"%[model.scores[0],model.scores[1]])
func _reveal_feedback() -> void:
    if not model.revealing or feedback_done: return
    feedback_done = true
    _clear_controls()
    if players == 1:
        _set_score(model.scores[0]*10)
        _feedback("eat" if model.selections[0] >= 0 and model.question().choices[model.selections[0]] == model.question().answer else "lose")
    else: _feedback("ui_confirm")
func _game_process(delta: float) -> bool:
    if playback_gap > 0:
        playback_gap = maxf(0,playback_gap-delta)
        if playback_gap == 0 and not note_queue.is_empty(): _play_next_note()
    model.tick(delta)
    _reveal_feedback()
    return true
func _clear_controls() -> void:
    if is_instance_valid(audio): audio.stop()
    # An interrupted sequence did not deliver its full listening opportunity.
    if playback_owner >= 0: reference_uses[playback_owner] = maxi(0,reference_uses[playback_owner]-1)
    playback_owner = -1; playback_reference = false
    note_queue.clear(); playback_gap = 0; playback_is_question = false
func _audio_busy() -> bool:
    return (is_instance_valid(audio) and audio.playing) or not note_queue.is_empty() or playback_gap > 0
func _play_next_note() -> void:
    if note_queue.is_empty(): return
    audio.stream = note_streams[int(note_queue.pop_front())]
    audio.play()
func _note_finished() -> void:
    if paused or rules_open or stage != "play": return
    if not note_queue.is_empty():
        playback_gap = 0.45 if playback_reference else 0.13
        playback_reference = false
    elif playback_is_question:
        model.heard = true
        playback_is_question = false; playback_owner = -1; playback_reference = false
    else: playback_reference = false
