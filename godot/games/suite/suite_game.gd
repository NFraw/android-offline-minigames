extends "res://core/game_content.gd"
const BoardArt = preload("res://core/board_art.gd")
const Catalog = preload("res://games/suite/catalog.gd")
const P = BoardArt.Palette
const DialogButtons = preload("res://core/dialog_buttons.gd")
const Polish = preload("res://core/game_polish.gd")
var press_rect := Rect2()
var press_time: float = 0
var last_touch := Vector2.ZERO
var sparks: Array = []
const Book = preload("res://core/kids_book.gd")
var guide_page: int = 0
var same_direction: bool = false
@export var game_id: String = ""
var font: Font
var arena := Rect2()
var stage: String = "setup"
var paused: bool = false
var rules_open: bool = false
var players: int = 1
var ai_mode: bool = false
var supports_ai: bool = false
var ready_seats: Array = []
var buttons: Array = []
var styles: Dictionary = {}
var score: int = 0
var message: String = ""
var round_number: int = 0
var finished: bool = false
func _ready() -> void:
    font = ThemeDB.fallback_font
    var spec: Dictionary = Catalog.GAMES.get(game_id,{})
    game_title = spec.get("title",game_id)
    players = 1 if spec.get("category","SOLO") == "SOLO" else (4 if game_id == "reaction4" else 2)
    supports_ai = game_id in ["gomoku","reversi"]
    same_direction = game_id == "curling"
    custom_chrome = true
    show_score = players == 1
    category_caption = "双人同屏" if players > 1 else "离线单人"
    game_rules = _rules_text()
    get_viewport().size_changed.connect(_layout)
    _layout()
    _new_game()
func start_round() -> void:
    stage = "setup"
    paused = false
    rules_open = false
    finished = false
    round_success = true
    score = 0
    ready_seats.clear()
    _clear_controls()
    sparks.clear(); press_time = 0
    _new_game()
    _layout()
func set_game_paused(value: bool) -> void:
    paused = value
    if value: _clear_controls()
    queue_redraw()
func _layout() -> void:
    var size: Vector2 = get_viewport_rect().size
    arena = Rect2(30,190,size.x-60,maxf(100,size.y-300))
    if size.x > size.y: arena = Rect2(30,150,size.x-60,maxf(100,size.y-250))
    _game_layout()
    queue_redraw()
func _square(cols: int, rows: int) -> Rect2:
    var c: float = minf(arena.size.x/cols,arena.size.y/rows)
    return Rect2(arena.get_center()-Vector2(cols,rows)*c/2,Vector2(cols,rows)*c)
func _text(text: String, center: Vector2, size: int = 22, tint: Color = P.TEXT, top: bool = false) -> void:
    if top: draw_set_transform(center,PI)
    var width: float = font.get_string_size(text,HORIZONTAL_ALIGNMENT_LEFT,-1,size).x
    draw_string(font,(Vector2.ZERO if top else center)-Vector2(width/2,-size*0.34),text,HORIZONTAL_ALIGNMENT_LEFT,-1,size,tint)
    if top: draw_set_transform(Vector2.ZERO)
func _box(rect: Rect2, tint: Color, radius: int = 14) -> void:
    var key: String = tint.to_html()+str(radius)
    if not styles.has(key):
        var box := StyleBoxFlat.new()
        box.bg_color = tint
        box.corner_radius_top_left = radius; box.corner_radius_top_right = radius
        box.corner_radius_bottom_left = radius; box.corner_radius_bottom_right = radius
        styles[key] = box
    draw_style_box(styles[key],rect)
func _button(rect: Rect2, caption: String, action: String, tint: Color = P.TEXT, top: bool = false) -> void:
    var pressed: bool = press_time > 0 and press_rect == rect
    if stage in ["setup","consent"] or rules_open:
        DialogButtons.draw(self,rect,caption,Color(Book.info(game_id)[1]),DialogButtons.primary(action,caption),top,pressed)
        buttons.append({"rect":rect,"action":action})
        return
    Polish.tile(self,rect,tint.darkened(0.12) if pressed else tint,1 if pressed else 3)
    var size: int = int(minf(rect.size.y*0.56,36 if caption.length() <= 2 else 28))
    size = mini(size,int((rect.size.x-12)/maxf(1,caption.length())))
    _text(caption,rect.get_center(),maxi(18,size),P.PIECE_HI,top)
    buttons.append({"rect":rect,"action":action})
func _draw() -> void:
    buttons.clear()
    var size: Vector2 = get_viewport_rect().size
    _draw_theme()
    _button(Rect2(20,22,125,58),"返回大厅","exit")
    var title_width: float = maxf(80,size.x-435)
    var title_size: int = mini(28,int(28*title_width/maxf(title_width,font.get_string_size(game_title,HORIZONTAL_ALIGNMENT_LEFT,-1,28).x)))
    _text(game_title,Vector2((size.x-125)/2,50),title_size)
    _button(Rect2(size.x-270,22,118,58),"规则","rules")
    _button(Rect2(size.x-140,22,120,58),"暂停","pause")
    if same_direction:
        _text(message,Vector2(size.x/2,123),24,Color(Book.info(game_id)[1]))
        _text("观察上一壶，再调整力度和方向",Vector2(size.x/2,size.y-46),23,P.MUTED)
    elif players == 2:
        _text(_player_status(1),Vector2(size.x/2,123),22,P.BLUE,true)
        _text(_player_status(0),Vector2(size.x/2,size.y-46),22,P.RED)
    elif players > 2:
        _text("%d人同屏对战"%players,Vector2(size.x/2,123),22,P.MUTED)
        _text(message,Vector2(size.x/2,size.y-46),20,P.MUTED)
    else:
        _text("得分 %d   ·   最高 %d"%[score,int(get_node("/root/GamePlatform").launch.get("best",0))],Vector2(size.x/2,123),22,P.MUTED)
        _text(message,Vector2(size.x/2,size.y-46),20,P.MUTED)
    _draw_play()
    for spark in sparks:
        var color: Color = spark.color
        color.a = clampf(spark.life/0.35,0,0.8)
        draw_circle(spark.p,maxf(1,spark.life*9),color)
    if stage == "setup": _draw_setup()
    elif stage == "consent": _draw_consent()
    if rules_open: _draw_rules()
func _modal(height: float) -> Rect2:
    var size: Vector2 = get_viewport_rect().size
    draw_rect(get_viewport_rect(),P.SHADE)
    var width: float = minf(size.x-50,860)
    var rect := Rect2((size.x-width)/2,(size.y-height)/2,width,height)
    _box(rect,P.PIECE_HI,22)
    buttons.clear()
    return rect
func _draw_setup() -> void:
    var r: Rect2 = _modal(minf(670,get_viewport_rect().size.y-36))
    _text(game_title,r.position+Vector2(r.size.x/2,40),32)
    Book.demo(self,game_id,0,Rect2(r.position+Vector2(30,72),Vector2(r.size.x-60,135)))
    var lines: PackedStringArray = Book.page(game_id,0)
    for i in range(2): _text(lines[i],r.position+Vector2(r.size.x/2,226+i*35),26)
    if supports_ai:
        for i in range(2):
            _button(Rect2(r.position+Vector2(24+i*(r.size.x-48)/2,292),Vector2((r.size.x-60)/2,58)),["两人一起玩","和电脑玩"][i]+(" ✓" if ai_mode == (i == 1) else ""),"ai%d"%i,Color(Book.info(game_id)[1]))
    _extra_setup(r)
    var bottom: float = r.end.y
    _button(Rect2(r.position.x+24,bottom-198,r.size.x-48,50),"图解玩法 · 3小页","rules",Color(Book.info(game_id)[1]))
    _button(Rect2(r.position.x+24,bottom-132,r.size.x-48,64),"开始准备" if players > 1 and not ai_mode else "开始游戏","begin",P.RED)
    _button(Rect2(r.position.x+24,bottom-60,r.size.x-48,48),"返回大厅","exit")
func _seat_rect(seat: int) -> Rect2:
    var size: Vector2 = get_viewport_rect().size
    if players == 2:
        return Rect2(35,100 if seat == 1 else size.y/2+20,size.x-70,size.y/2-140)
    var w: float = size.x/2-40
    var h: float = size.y/2-170
    return Rect2(25+(seat%2)*(size.x/2),135 if seat >= 2 else size.y/2+15,w,h)
func _draw_consent() -> void:
    draw_rect(get_viewport_rect(),P.SHADE)
    buttons.clear()
    if same_direction:
        var r: Rect2 = _modal(minf(620,get_viewport_rect().size.y-40))
        _text("一起向前投壶",r.position+Vector2(r.size.x/2,42),32)
        Book.demo(self,game_id,1,Rect2(r.position+Vector2(30,75),Vector2(r.size.x-60,130)))
        _text("底部虚线框按住 → 向后拉 → 松手",r.position+Vector2(r.size.x/2,240),25)
        _text("大家从同一侧轮流玩，越靠近圆心越好",r.position+Vector2(r.size.x/2,280),24)
        for seat in range(players):
            _button(Rect2(r.position.x+24,r.position.y+320+seat*60,r.size.x-48,52),"玩家%d · %s"%[seat+1,"准备好了 ✓" if seat in ready_seats else "看懂了，准备"],"ready%d"%seat,Color(Book.info(game_id)[1]))
        return
    for seat in range(players):
        var r: Rect2 = _seat_rect(seat)
        var top: bool = seat == 1 if players == 2 else seat >= 2
        var tint: Color = P.RED if seat == 0 else P.BLUE
        _box(r,P.PIECE_HI,22)
        # 在各自朝向的小窗内先看两条关键规则，再准备。
        var short_rules: PackedStringArray = Book.page(game_id,0)
        var center: Vector2 = r.get_center()
        for i in range(2):
            var offset := Vector2(0,-82+i*38)
            _text(short_rules[i],center-offset if top else center+offset,24 if players == 2 else 20,P.TEXT,top)
        _button(Rect2(center-Vector2(r.size.x*0.43,74 if top else -10),Vector2(r.size.x*0.86,64)),"玩家%d · %s"%[seat+1,"已准备 ✓" if seat in ready_seats else "看懂了，准备"],"ready%d"%seat,tint,top)
    _button(Rect2(get_viewport_rect().size.x/2-130,get_viewport_rect().size.y/2-30,260,60),"图解玩法","rules",Color(Book.info(game_id)[1]))
func _draw_rules() -> void:
    var r: Rect2 = _modal(minf(600,get_viewport_rect().size.y-40))
    _text(game_title+" · "+["怎么玩","再看一步","小小挑战"][guide_page],r.position+Vector2(r.size.x/2,42),30)
    Book.demo(self,game_id,guide_page,Rect2(r.position+Vector2(30,82),Vector2(r.size.x-60,165)))
    var lines: PackedStringArray = Book.page(game_id,guide_page)
    for i in range(2): _text(lines[i],r.position+Vector2(r.size.x/2,295+i*42),27)
    if guide_page == 2: _text(str(Book.info(game_id)[3]).split("：")[-1],r.position+Vector2(r.size.x/2,390),23,Color(Book.info(game_id)[1]))
    var y: float = r.end.y-152
    _button(Rect2(r.position.x+24,y,160,64),"上一页","guide_prev",P.BLUE)
    _text("%d / 3"%(guide_page+1),Vector2(r.get_center().x,y+32),25)
    _button(Rect2(r.end.x-184,y,160,64),"下一页","guide_next",P.BLUE)
    _button(Rect2(r.position.x+24,r.end.y-78,r.size.x-48,64),"看懂了，继续","close_rules",P.RED)
func _draw_theme() -> void:
    var theme: Array = Book.info(game_id)
    var bg := Color(theme[0])
    var accent := Color(theme[1])
    draw_rect(get_viewport_rect(),bg)
    var size: Vector2 = get_viewport_rect().size
    # 用轻量线稿为每个游戏作专属背景，不遮挡操作区。
    for i in range(7):
        var at := Vector2(25+i*119,95+(i%3)*31)
        if game_id in ["piano","simon"]:
            draw_circle(at,6,Color(accent,0.12)); draw_line(at,at-Vector2(0,24),Color(accent,0.12),2)
        elif game_id in ["snake","snakebattle","whack","memory","memory_duel","fruit"]:
            draw_arc(at,18,0,PI,16,Color(accent,0.12),5,true)
        else: draw_rect(Rect2(at,Vector2(20,12)),Color(accent,0.10),false,2)
    draw_line(Vector2(20,92),Vector2(size.x-20,92),Color(accent,0.2),2)
func _board_surface(rect: Rect2) -> void:
    if game_id in ["gomoku","weiqi","xiangqi_duel","darkchess","sliding"]:
        BoardArt.draw_wood_slab(self,rect)
    elif game_id == "tictactoe":
        _box(rect,P.CARD,18)
        for y in range(int(rect.size.y/24)): draw_line(rect.position+Vector2(6,24*y),rect.position+Vector2(rect.size.x-6,24*y),Color("dce5ef"),1)
    elif game_id == "reversi":
        _box(rect,Color("375c48"),18); _box(rect.grow(-7),Color("b5c8a0"),12)
    else:
        _box(Rect2(rect.position+Vector2(0,5),rect.size),P.SHADOW,18)
        _box(rect,Color(Book.info(game_id)[1]),18)
        _box(rect.grow(-6),Color(Book.info(game_id)[0]).lightened(0.3),12)
func _action(action: String) -> void:
    # A state transition invalidates the previous screen's cached touch targets.
    if action in ["exit","rules","close_rules","pause","ai","ai0","ai1","guide_prev","guide_next","begin"] or action.begins_with("ready"): buttons.clear()
    if action in ["exit","begin","rules","close_rules","guide_prev","guide_next","pause","ai","ai0","ai1"] or action.begins_with("ready"):
        _feedback("ui_back" if action == "exit" else ("ui_confirm" if action == "begin" or action.begins_with("ready") else "ui_click"))
    if action == "exit": get_node("/root/GamePlatform").exit_to_hall()
    elif action == "rules": rules_open = true; guide_page = 0; _clear_controls()
    elif action == "close_rules": rules_open = false
    elif action == "pause": pause_requested.emit()
    elif action == "ai": ai_mode = not ai_mode
    elif action in ["ai0","ai1"]: ai_mode = action == "ai1"
    elif action == "guide_prev": guide_page = (guide_page+2)%3
    elif action == "guide_next": guide_page = (guide_page+1)%3
    elif action == "begin":
        round_number += 1
        _new_game()
        stage = "consent" if players > 1 and not ai_mode else "play"
        ready_seats.clear()
    elif action.begins_with("ready"):
        var seat: int = int(action.trim_prefix("ready"))
        if seat not in ready_seats: ready_seats.append(seat)
        if ready_seats.size() == players: stage = "play"
    else: _game_action(action)
    queue_redraw()
func _input(event: InputEvent) -> void:
    if paused or event.device == -1: return
    var at: Vector2
    var finger: int = 0
    var down: bool = false
    var up: bool = false
    var drag: bool = false
    if event is InputEventScreenTouch:
        at = event.position; finger = event.index
        down = event.pressed; up = not event.pressed
    elif event is InputEventScreenDrag:
        at = event.position; finger = event.index; drag = true
    elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
        at = event.position; down = event.pressed; up = not event.pressed
    elif event is InputEventMouseMotion and event.button_mask & MOUSE_BUTTON_MASK_LEFT:
        at = event.position; drag = true
    elif event is InputEventKey and event.pressed:
        if stage == "play" and not rules_open: _game_key(event.keycode)
        return
    else: return
    if down:
        last_touch = at
        for button in buttons:
            if button.rect.has_point(at):
                press_rect = button.rect; press_time = 0.14
                _action(button.action); return
    if stage != "play" or rules_open or finished: return
    if down: _game_down(at,finger)
    elif drag: _game_drag(at,finger)
    elif up: _game_up(at,finger)
    queue_redraw()
func _process(delta: float) -> void:
    if paused: return
    var effects: bool = press_time > 0 or not sparks.is_empty()
    if press_time > 0: press_time = maxf(0,press_time-delta)
    for spark in sparks:
        spark.life -= delta
        spark.p += spark.v*delta
        spark.v.y += 100*delta
    sparks = sparks.filter(func(s): return s.life > 0)
    if effects: queue_redraw()
    if rules_open or stage != "play" or finished: return
    if _game_process(minf(delta,0.05)): queue_redraw()
func _set_score(value: int) -> void:
    if value > score and game_id != "plane":
        var at: Vector2 = last_touch if arena.has_point(last_touch) else arena.get_center()
        for i in range(6):
            if sparks.size() >= 24: break
            var velocity := Vector2.from_angle(TAU*i/6)*65
            sparks.append({"p":at,"v":velocity,"life":0.32,"color":Color(Book.info(game_id)[1])})
    score = value
    score_changed.emit(value)
func _end(caption: String, success: bool = true) -> void:
    if finished: return
    finished = true
    stage = "ended"
    _clear_controls()
    round_success = success
    queue_redraw()
    round_finished.emit(score,caption)
func _feedback(cue: String, vibrate: bool = false) -> void:
    get_node("/root/GamePlatform").feedback(cue,vibrate)
func _player_status(seat: int) -> String:
    return "玩家%d · %s"%[seat+1,message]
func _rules_text() -> String:
    return ""
func _new_game() -> void:
    pass
func _game_layout() -> void:
    pass
func _draw_play() -> void:
    pass
func _extra_setup(_rect: Rect2) -> void:
    pass
func _game_action(_action_name: String) -> void:
    pass
func _game_down(_at: Vector2, _finger: int) -> void:
    pass
func _game_drag(_at: Vector2, _finger: int) -> void:
    pass
func _game_up(_at: Vector2, _finger: int) -> void:
    pass
func _game_key(_key: int) -> void:
    pass
func _game_process(_delta: float) -> bool:
    return false
func _clear_controls() -> void:
    pass
