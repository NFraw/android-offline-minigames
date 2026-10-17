extends "res://core/game_content.gd"
const DialogButtons = preload("res://core/dialog_buttons.gd")
const Book = preload("res://core/kids_book.gd")
const Model = preload("res://games/animal/animal_model.gd")
const Art = preload("res://games/animal/animal_art.gd")
const P = Art.P
var model = Model.new()
var font: Font
var stage: String = "setup"
var competitive: bool = false
var paused: bool = false
var rules_open: bool = false
var rules_page: int = 0
var ready_sides: Array = []
var selected: int = -1
var board_rect := Rect2()
var cell: float = 0
var top_panel := Rect2()
var bottom_panel := Rect2()
var buttons: Array = []
var styles: Dictionary = {}
var animation: Dictionary = {}
var animation_time: float = 0
var animation_duration: float = 0.32
var message: String = "点击己方棋子，再点高亮落点"
var last_clock: int = 30
func _ready() -> void:
    font = ThemeDB.fallback_font
    game_title = "斗兽棋"
    game_rules = "象狮虎豹狼狗猫鼠；红方先走。鼠吃象，狮虎跳河，进入敌方兽穴获胜。"
    custom_chrome = true
    show_score = false
    category_caption = "双人同屏 · 传统7×9棋盘"
    end_caption = "本局结束"
    model.reset()
    get_viewport().size_changed.connect(_layout)
    _layout()
func prepare() -> void:
    model.reset()
func start_round() -> void:
    model.reset(competitive)
    stage = "setup"
    paused = false
    rules_open = false
    selected = -1
    ready_sides.clear()
    animation.clear()
    message = "点击己方棋子，再点高亮落点"
    queue_redraw()
func set_game_paused(value: bool) -> void:
    paused = value
    queue_redraw()
func _layout() -> void:
    var size: Vector2 = get_viewport_rect().size
    if size.x > size.y:
        var panel_width: float = minf(640,size.x*0.43)
        var board_height: float = minf(size.y-140,(size.x-panel_width-80)*9/7)
        cell = board_height/9
        var x: float = size.x-panel_width-20
        board_rect = Rect2((x-20-cell*7)/2+10,(size.y+90-board_height)/2,cell*7,board_height)
        top_panel = Rect2(x,108,panel_width,202)
        bottom_panel = Rect2(x,size.y-222,panel_width,202)
    else:
        cell = minf((size.x-56)/7,(size.y-410)/9)
        board_rect = Rect2((size.x-cell*7)/2,(size.y+70-cell*9)/2,cell*7,cell*9)
        top_panel = Rect2(20,100,size.x-40,120)
        bottom_panel = Rect2(20,size.y-140,size.x-40,120)
    queue_redraw()
func _center(index: int) -> Vector2:
    return board_rect.position+(Vector2(Model.point(index))+Vector2.ONE*0.5)*cell
func _text(text: String, center: Vector2, size: int = 22, tint: Color = P.TEXT) -> void:
    var width: float = font.get_string_size(text,HORIZONTAL_ALIGNMENT_LEFT,-1,size).x
    draw_string(font,center-Vector2(width/2,-size*0.34),text,HORIZONTAL_ALIGNMENT_LEFT,-1,size,tint)
func _box(rect: Rect2, tint: Color, radius: int = 14) -> void:
    var key: String = tint.to_html()+str(radius)
    if not styles.has(key):
        var box := StyleBoxFlat.new()
        box.bg_color = tint
        box.corner_radius_top_left = radius
        box.corner_radius_top_right = radius
        box.corner_radius_bottom_left = radius
        box.corner_radius_bottom_right = radius
        styles[key] = box
    draw_style_box(styles[key],rect)
func _button(rect: Rect2, caption: String, action: String, tint: Color = P.TEXT, top: bool = false, panel: Rect2 = Rect2()) -> void:
    if stage in ["setup","consent"] or rules_open:
        var strong: bool = DialogButtons.primary(action,caption)
        _box(rect,DialogButtons.fill(Color(Book.info("animal")[1]),strong),10)
        _text(caption,rect.get_center(),DialogButtons.font_size(font,caption,rect),Color.WHITE if strong else DialogButtons.TEXT)
    else:
        _box(rect,tint)
        _text(caption,rect.get_center(),23,P.PIECE_HI)
    buttons.append({"rect":rect,"action":action,"top":top,"panel":panel})
func _draw() -> void:
    buttons.clear()
    draw_rect(get_viewport_rect(),P.BG)
    var width: float = get_viewport_rect().size.x
    _button(Rect2(20,22,130,58),"返回大厅","exit")
    _text("斗兽棋",Vector2(width/2,50),30)
    _button(Rect2(width-270,22,118,58),"规则","rules")
    if stage != "setup": _button(Rect2(width-140,22,120,58),"暂停","pause")
    Art.paint(self)
    _player_panel(1,top_panel)
    _player_panel(0,bottom_panel)
    if stage == "setup": _setup()
    if rules_open: _rules()
func _player_panel(owner: int, rect: Rect2) -> void:
    var top: bool = owner == 1
    var color: Color = P.BLUE if top else P.RED
    var name: String = "蓝方" if top else "红方"
    _box(rect,P.CARD,22)
    draw_set_transform(rect.end if top else rect.position,PI if top else 0)
    var compact: bool = rect.size.y < 150
    var alive: int = 0
    var lost: String = ""
    for rank in range(8,0,-1):
        if model.board.has(-rank if top else rank): alive += 1
        else: lost += Model.NAMES[rank]
    _text(name+" · %d只兽"%alive,Vector2(rect.size.x*0.24,27),25,color)
    var status: String = "等待对方"
    if stage == "consent": status = "已准备" if owner in ready_sides else "请准备"
    elif stage == "play" and not animation.is_empty(): status = "棋子正在移动…"
    elif stage == "play" and model.turn == owner: status = "轮到你走棋"
    elif stage == "ended": status = "本局结束"
    _text(status,Vector2(rect.size.x*0.73,27),23,color if stage == "play" and model.turn == owner else P.MUTED)
    if stage == "consent" and owner not in ready_sides:
        _text("走一格 · 进敌方兽穴获胜",Vector2(rect.size.x/2,97),17,P.MUTED)
        _button(Rect2(20,47,rect.size.x-40,42),"看懂了，准备","ready%d"%owner,color,top,rect)
    elif compact:
        _text("%s · %s"%["30秒比赛" if competitive else "休闲无计时",message if model.turn == owner else "等待另一方走棋"],Vector2(rect.size.x/2,75),19)
    else:
        var clock: String = "%d秒"%ceili(model.remaining) if competitive else "无时间限制"
        _text(clock+" · 红方先手",Vector2(rect.size.x/2,66),20,P.MUTED)
        var status_message: String = message if model.turn == owner else "看清对手走法，守好自己的兽穴"
        _text(status_message,Vector2(rect.size.x/2,105),20)
        _text("已失去："+(lost if not lost.is_empty() else "暂无"),Vector2(rect.size.x/2,145),20,P.MUTED)
        _text("象 8 > 狮 7 > 虎 6 > 豹 5 > 狼 4 > 狗 3 > 猫 2 > 鼠 1",Vector2(rect.size.x/2,179),16,P.MUTED)
    draw_set_transform(Vector2.ZERO)
func _dialog(height: float) -> Rect2:
    var size: Vector2 = get_viewport_rect().size
    draw_rect(get_viewport_rect(),P.SHADE)
    var width: float = minf(size.x-60,860)
    var rect := Rect2((size.x-width)/2,(size.y-height)/2,width,height)
    _box(rect,P.PIECE_HI,24)
    buttons.clear()
    return rect
func _setup() -> void:
    var rect: Rect2 = _dialog(560)
    _text("斗兽棋 · 丛林棋阵",rect.position+Vector2(rect.size.x/2,45),32)
    Book.demo(self,"animal",0,Rect2(rect.position+Vector2(25,70),Vector2(rect.size.x-50,130)))
    _text("轮流走一只动物，先到对方兽穴",rect.position+Vector2(rect.size.x/2,235),27)
    _button(Rect2(rect.position+Vector2(24,284),Vector2(rect.size.x-48,60)),"计时挑战："+("开 · 每步30秒" if competitive else "关 · 慢慢想"),"casual" if competitive else "competitive",P.BLUE)
    _button(Rect2(rect.position+Vector2(24,370),Vector2(rect.size.x-48,60)),"图解玩法","rules",P.BLUE)
    _button(Rect2(rect.position+Vector2(24,461),Vector2(rect.size.x-48,68)),"开始准备","begin",P.RED)
func _rules() -> void:
    var rect: Rect2 = _dialog(600)
    _text("斗兽棋 · 图解玩法 %d/3"%(rules_page+1),rect.position+Vector2(rect.size.x/2,44),30)
    Book.demo(self,"animal",rules_page,Rect2(rect.position+Vector2(25,80),Vector2(rect.size.x-50,170)))
    var lines: PackedStringArray = Book.page("animal",rules_page)
    for i in range(2): _text(lines[i],rect.position+Vector2(rect.size.x/2,305+i*45),27)
    _button(Rect2(rect.position+Vector2(24,425),Vector2(rect.size.x-48,56)),"下一页","rule_page",P.BLUE)
    _button(Rect2(rect.position+Vector2(24,515),Vector2(rect.size.x-48,56)),"看懂了，继续","close_rules",P.RED)
func _action(action: String) -> void:
    if action in ["exit","rules","close_rules","guide_prev","guide_next","rule_page","begin","pause","casual","competitive"] or action.begins_with("ready") or action.begins_with("count") or action.begins_with("mode"):
        get_node("/root/GamePlatform").feedback("ui_back" if action == "exit" else ("ui_confirm" if action == "begin" or action.begins_with("ready") else "ui_switch"))
    if action == "exit": get_node("/root/GamePlatform").exit_to_hall()
    elif action == "pause": pause_requested.emit()
    elif action == "rules": rules_open = true; rules_page = 0
    elif action == "rule_page": rules_page = (rules_page+1)%3
    elif action == "close_rules": rules_open = false
    elif action == "casual": competitive = false
    elif action == "competitive": competitive = true
    elif action == "begin":
        model.reset(competitive)
        stage = "consent"
        ready_sides.clear()
        selected = -1
    elif action.begins_with("ready"):
        var owner: int = int(action.trim_prefix("ready"))
        if owner not in ready_sides: ready_sides.append(owner)
        if ready_sides.size() == 2: stage = "play"
    queue_redraw()
func _input(event: InputEvent) -> void:
    if paused or event.device == -1: return
    var at: Vector2
    if event is InputEventScreenTouch and event.pressed: at = event.position
    elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT: at = event.position
    else: return
    for button in buttons:
        var local: Vector2 = at
        if button.panel.size != Vector2.ZERO:
            local = button.panel.end-at if button.top else at-button.panel.position
        if button.rect.has_point(local):
            _action(button.action)
            return
    if stage != "play" or rules_open or not animation.is_empty(): return
    if not board_rect.has_point(at): return
    var p: Vector2i = Vector2i((at-board_rect.position)/cell)
    _tap(p.y*7+p.x)
func _tap(index: int) -> void:
    if index < 0 or index >= 63 or stage != "play" or paused or rules_open or not animation.is_empty(): return
    var piece: int = model.board[index]
    if piece != 0 and Model.side(piece) == model.turn:
        selected = -1 if selected == index else index
        message = "已选"+Model.NAMES[absi(piece)]+" · 点击高亮落点" if selected >= 0 else "已取消选择"
        _feedback("click")
    elif selected >= 0:
        var invalid: String = model.explain(selected,index)
        var move: Dictionary = model.play(selected,index)
        if move.is_empty():
            message = invalid
            _feedback("lose",true)
        else:
            animation = move
            animation_time = 0
            animation_duration = 0.62 if move.jump else 0.28
            selected = -1
            message = ("蓝方" if move.piece < 0 else "红方")+Model.NAMES[absi(move.piece)]+("跳河" if move.jump else "走棋")
    else:
        message = "请先选择自己的一只兽"
        _feedback("click")
    queue_redraw()
func _feedback(cue: String, haptic: bool = false) -> void:
    get_node("/root/GamePlatform").feedback(cue,haptic)
func _finish_animation() -> void:
    var captured: int = animation.captured
    if captured != 0:
        message = "吃掉"+Model.NAMES[absi(captured)]+"，轮到你了"
    else: message = "轮到你了 · 点棋子，再点高亮格"
    animation.clear()
    _feedback("eat" if captured != 0 else "move",captured != 0)
    # The final frame must refresh the panel and restore input even with no remaining effects.
    queue_redraw()
    _finish_round_if_needed()
func _finish_round_if_needed() -> void:
    if model.winner < 0 or stage != "play": return
    stage = "ended"
    selected = -1
    _feedback("win",true)
    round_finished.emit(0,("红方" if model.winner == 0 else "蓝方")+"获胜："+model.reason)
func _process(delta: float) -> void:
    if paused or rules_open or stage != "play": return
    if not animation.is_empty():
        animation_time += delta
        if animation_time >= animation_duration: _finish_animation()
        queue_redraw()
        return
    if model.tick(delta):
        _finish_round_if_needed()
        queue_redraw()
    elif competitive and ceili(model.remaining) != last_clock:
        last_clock = ceili(model.remaining)
        queue_redraw()
