extends "res://core/game_content.gd"
const DialogButtons = preload("res://core/dialog_buttons.gd")
const Book = preload("res://core/kids_book.gd")
var guide_page: int = 0
const Model = preload("res://games/flight/flight_model.gd")
const Art = preload("res://games/flight/flight_board_art.gd")
const Visual = preload("res://games/flight/flight_visual.gd")
const COLORS = [Color("df5659"),Color("e2ba37"),Color("4ca775"),Color("398fc8")]
const NAMES = ["红方","黄方","绿方","蓝方"]
const INK = Color("283a49")
const PAPER = Color("f5f3eb")
var model = Model.new()
var font: Font
var board_rect := Rect2()
var wide_layout: bool = false
var cell: float
var buttons: Array = []
var players: int = 2
var mode: int = 0
var stage: String = "setup"
var ready_seats: Array = []
var paused: bool = false
var message: String = "选择人数与模式，开始一局飞行棋"
var wait: float = 0.0
var dice_time: float = 0.0
var face: int = 1
var move_data: Dictionary = {}
var move_path: Array = []
var move_step: int = 0
var move_time: float = 0.0
var moving_at := Vector2.ZERO
var from_point := Vector2.ZERO
var top_panel := Rect2()
var bottom_panel := Rect2()
var rules_open: bool = false
var style_cache: Dictionary = {}
var pending_end: bool = false
var roll_duration: float = 1.22
var roll_elapsed: float = 0
var roll_seat: int = 0
var pending_face: int = 1
var result_time: float = 0
var result_elapsed: float = 0
var reveal_caption: String = ""
var roll_penalty: bool = false
var last_faces: Array = [1,1,1,1]
var last_side_seats: Array = [0,2]
var fx_clock: float = 0
var moving_lift: float = 0
var moving_angle: float = 0
var headings: Dictionary = {}
var trail: Array = []
var landing_rings: Array = []
var visual_positions: Array = []
var return_flights: Array = []
var roll_ticks: int = 0
func _ready() -> void:
    model.reset(players)
    font = ThemeDB.fallback_font
    game_title = "飞行棋"
    game_rules = "掷出6起飞并再掷；同色格跳4格，飞行格飞12格，可先跳后飞。\n三连6撤回本轮移动和撞机；终点必须恰好到达。\n自己的起飞格保护本方，友机可叠放，其余外圈落点可撞机。\n先让4架飞机全部到达终点的一方获胜。"
    custom_chrome = true
    show_score = false
    category_caption = "同屏 / 人机 · 2—4人"
    end_caption = "本局结束"
    get_viewport().size_changed.connect(_layout)
    _layout()
func _layout() -> void:
    var old: Rect2 = board_rect
    var old_cell: float = cell
    var size: Vector2 = get_viewport_rect().size
    wide_layout = size.x > size.y
    if wide_layout:
        var panel_width: float = minf(640,size.x*0.43)
        var side: float = minf(size.y-130,size.x-panel_width-60)
        var panel_x: float = size.x-panel_width-20
        top_panel = Rect2(panel_x,108,panel_width,202)
        bottom_panel = Rect2(panel_x,size.y-222,panel_width,202)
        board_rect = Rect2((panel_x-20-side)/2+10,(size.y+90-side)/2,side,side)
    else:
        var side: float = minf(size.x-40,size.y-600)
        top_panel = Rect2(20,108,size.x-40,202)
        bottom_panel = Rect2(20,size.y-222,size.x-40,202)
        board_rect = Rect2((size.x-side)/2,(top_panel.end.y+bottom_panel.position.y-side)/2,side,side)
    cell = board_rect.size.x/Model.Board.UNITS
    if old_cell > 0:
        moving_lift *= cell/old_cell
        moving_at = board_rect.position+(moving_at-old.position)*(cell/old_cell)
        from_point = board_rect.position+(from_point-old.position)*(cell/old_cell)
        for item in trail: item.point = board_rect.position+(item.point-old.position)*(cell/old_cell)
        for item in landing_rings: item.point = board_rect.position+(item.point-old.position)*(cell/old_cell)
        for item in return_flights:
            item.from = board_rect.position+(item.from-old.position)*(cell/old_cell)
            item.to = board_rect.position+(item.to-old.position)*(cell/old_cell)
    queue_redraw()
func prepare() -> void:
    model.reset(players)
func start_round() -> void:
    paused = false
    stage = "setup"
    rules_open = false
    move_data.clear()
    move_path.clear()
    dice_time = 0
    result_time = 0
    roll_elapsed = 0
    moving_lift = 0
    trail.clear()
    landing_rings.clear()
    return_flights.clear()
    visual_positions.clear()
    headings.clear()
    last_faces = [1,1,1,1]
    pending_end = false
    message = "选择人数与模式，开始一局飞行棋"
    queue_redraw()
func set_game_paused(value: bool) -> void:
    paused = value
    queue_redraw()
func _human(seat: int) -> bool:
    return mode == 1 or (mode == 0 and seat == 0)
func _begin() -> void:
    model.reset(players)
    ready_seats.clear()
    for seat in model.seats:
        if not _human(seat): ready_seats.append(seat)
    stage = "consent" if mode == 1 else "play"
    message = "各位玩家请点击自己的机场准备" if stage == "consent" else "红方先行：点击掷骰子"
    wait = 0.6
    print("FLIGHT_MATCH: players=",players," mode=",mode)
func _point(seat: int, piece: int, progress: int) -> Vector2:
    return board_rect.position + model.board.point_for(seat,piece,progress)*cell
func _text(text: String, center: Vector2, size: int = 22, color: Color = INK) -> void:
    var width: float = font.get_string_size(text,HORIZONTAL_ALIGNMENT_LEFT,-1,size).x
    draw_string(font,center-Vector2(width/2.0,-size*0.34),text,HORIZONTAL_ALIGNMENT_LEFT,-1,size,color)
func _box(rect: Rect2, color: Color, radius: int = 14) -> void:
    var key: String = color.to_html()+str(radius)
    if not style_cache.has(key):
        var style := StyleBoxFlat.new()
        style.bg_color = color
        style.set_corner_radius_all(radius)
        style_cache[key] = style
    draw_style_box(style_cache[key],rect)
func _button(rect: Rect2, text: String, action: String, enabled: bool = true, tint: Color = INK) -> void:
    if enabled and not text.is_empty() and (stage in ["setup","consent"] or rules_open):
        DialogButtons.draw(self,rect,text,Color(Book.info("flight")[1]),DialogButtons.primary(action,text))
        buttons.append({"rect":rect,"action":action,"top":false})
        return
    _box(rect,tint if enabled else Color("dfe3e1"))
    _text(text,rect.get_center(),27,Color.WHITE if enabled else Color("85918f"))
    if enabled: buttons.append({"rect":rect,"action":action,"top":false})
func _draw() -> void:
    buttons.clear()
    draw_rect(get_viewport_rect(),PAPER)
    _button(Rect2(20,22,125,58),"返回大厅","exit")
    _text("飞行棋",Vector2(get_viewport_rect().size.x/2,51),30)
    _button(Rect2(get_viewport_rect().size.x-270,22,118,58),"规则","rules")
    _button(Rect2(get_viewport_rect().size.x-140,22,120,58),"暂停","pause",stage != "setup")
    _draw_board()
    _draw_panel(top_panel,true)
    _draw_panel(bottom_panel,false)
    var info_x: float = bottom_panel.get_center().x if wide_layout else get_viewport_rect().size.x/2
    var gap: float = bottom_panel.position.y-top_panel.end.y
    var info_y: float = bottom_panel.position.y-gap*0.22 if wide_layout else (board_rect.end.y+bottom_panel.position.y)/2
    _text(message,Vector2(info_x,info_y),20)
    var upper := Vector2(info_x,top_panel.end.y+gap*0.22 if wide_layout else (top_panel.end.y+board_rect.position.y)/2)
    draw_set_transform(upper,PI)
    _text(message,Vector2.ZERO,20)
    draw_set_transform(Vector2.ZERO)
    Visual.dice_effect(self)
    if stage == "setup": _draw_setup()
    if rules_open: _draw_rules()
func _draw_board() -> void:
    Art.paint(self)
    for item in trail:
        var color: Color = COLORS[item.seat]
        color.a = (1.0-item.age/0.28)*0.26
        draw_circle(item.point,cell*0.13*(1.0-item.age/0.28),color)
    for item in landing_rings:
        var t: float = item.age/0.45
        var color: Color = COLORS[item.seat]
        color.a = (1-t)*(0.75 if item.strong else 0.24)
        draw_arc(item.point,cell*(0.25+t*0.5),0,TAU,40,color,2,true)
    var displayed: Array = visual_positions if not visual_positions.is_empty() else model.positions
    for seat in model.seats:
        for piece in range(4):
            if _returning(seat,piece): continue
            var progress: int = displayed[seat][piece]
            if progress == Model.FINISH: continue
            var ground: Vector2 = _point(seat,piece,progress)
            var lift: float = 0
            var angle: float = headings.get(Vector2i(seat,piece),seat*PI/2)
            if not move_data.is_empty() and move_data.seat == seat and move_data.piece == piece:
                ground = moving_at
                lift = moving_lift
                angle = moving_angle
            else:
                var count: int = 0
                for earlier in range(piece):
                    if progress >= 0 and displayed[seat][earlier] == progress: count += 1
                ground += Vector2(count*cell*0.13,-count*cell*0.13)
            var legal: bool = stage == "play" and model.phase == Model.MOVE and seat == model.turn and piece in model.legal_moves() and not _busy() and _human(seat)
            Visual.plane(self,ground-Vector2(0,lift),ground,seat,piece,angle,lift,legal)
        if stage == "consent" and _human(seat):
            var r: Rect2 = model.board.hangar_rect(seat)
            var center: Vector2 = board_rect.position+(r.position+r.size*0.5)*cell
            draw_set_transform(center,PI if _top_seat(seat) else 0.0)
            _box(Rect2(-cell*2,-cell*0.6,cell*4,cell*1.2),COLORS[seat])
            _text("已准备 ✓" if seat in ready_seats else "看懂了，准备",Vector2.ZERO,25,Color.WHITE)
            _text("掷6出发 · 四机回家",Vector2(0,-cell*0.95),maxi(16,int(cell*0.30)),INK)
            draw_set_transform(Vector2.ZERO)
            buttons.append({"rect":Rect2(center-Vector2(cell*2,cell*0.6),Vector2(cell*4,cell*1.2)),"action":"ready%d"%seat,"top":false})
    for item in return_flights:
        var t: float = minf(1,item.age/0.48)
        var ground: Vector2 = item.from.lerp(item.to,smoothstep(0,1,t))
        var lift: float = sin(t*PI)*cell*1.2
        var angle: float = (item.to-item.from).angle()+PI/2
        Visual.plane(self,ground-Vector2(0,lift),ground,item.seat,item.piece,angle,lift,false)
func _returning(seat: int, piece: int) -> bool:
    for item in return_flights:
        if item.seat == seat and item.piece == piece: return true
    return false
func _top_seat(seat: int) -> bool:
    return seat in [1,2]
func _focus_seat() -> int:
    if dice_time > 0 or result_time > 0: return roll_seat
    if not move_data.is_empty(): return move_data.seat
    return model.turn
func _panel_seat(top: bool) -> int:
    var focus: int = _focus_seat()
    if _top_seat(focus) == top:
        last_side_seats[1 if top else 0] = focus
        return focus
    var remembered: int = last_side_seats[1 if top else 0]
    if remembered in model.seats: return remembered
    for seat in model.seats:
        if _top_seat(seat) == top: return seat
    return 2 if top else 0
func _draw_panel(rect: Rect2, top: bool) -> void:
    Visual.panel(self,rect,top)
func _draw_setup() -> void:
    buttons.clear()
    draw_rect(get_viewport_rect(),Color(0.13,0.19,0.22,0.72))
    var width: float = get_viewport_rect().size.x
    var dialog_width: float = minf(width-80,820)
    var r := Rect2((width-dialog_width)/2,get_viewport_rect().size.y/2-300,dialog_width,600)
    _box(r,Color("fffcf5"),24)
    _text("飞行棋 · 一起出发",r.position+Vector2(r.size.x/2,45),34)
    Book.demo(self,"flight",0,Rect2(r.position+Vector2(20,70),Vector2(r.size.x-40,110)))
    _text("掷到6出发，把四架飞机都送回终点",r.position+Vector2(r.size.x/2,213),26)
    for count in range(2,5):
        _button(Rect2(r.position+Vector2(24+(count-2)*(r.size.x-48)/3,255),Vector2((r.size.x-64)/3,62)),"%d人%s"%[count," ✓" if players == count else ""],"count%d"%count,true,COLORS[0] if players == count else INK)
    for option in range(2):
        _button(Rect2(r.position+Vector2(24+option*(r.size.x-48)/2,337),Vector2((r.size.x-60)/2,64)),["和电脑玩","和朋友玩"][option]+(" ✓" if mode == option else ""),"mode%d"%option,true,COLORS[0] if mode == option else INK)
    _button(Rect2(r.position+Vector2(24,424),Vector2(r.size.x-48,55)),"图解玩法","rules")
    _button(Rect2(r.position+Vector2(24,503),Vector2(r.size.x-48,68)),"开始对局","begin",true,COLORS[0])
func _draw_rules() -> void:
    buttons.clear()
    draw_rect(get_viewport_rect(),Color(0.13,0.19,0.22,0.85))
    var width: float = minf(get_viewport_rect().size.x-60,820)
    var r := Rect2((get_viewport_rect().size.x-width)/2,get_viewport_rect().size.y/2-300,width,600)
    _box(r,Color("fffcf5"),24)
    _text("飞行棋 · 图解玩法 %d/3"%(guide_page+1),r.position+Vector2(r.size.x/2,48),31)
    Book.demo(self,"flight",guide_page,Rect2(r.position+Vector2(25,90),Vector2(r.size.x-50,170)))
    var lines: PackedStringArray = Book.page("flight",guide_page)
    for i in range(2): _text(lines[i],r.position+Vector2(r.size.x/2,318+i*46),28)
    _button(Rect2(r.position+Vector2(24,422),Vector2((r.size.x-60)/2,60)),"上一页","guide_prev")
    _button(Rect2(r.position+Vector2(r.size.x/2+6,422),Vector2((r.size.x-60)/2,60)),"下一页","guide_next")
    _button(Rect2(r.position+Vector2(24,510),Vector2(r.size.x-48,60)),"看懂了，继续","close_rules")
func _busy() -> bool:
    return dice_time > 0 or result_time > 0 or not move_data.is_empty() or not return_flights.is_empty()
func _input(event: InputEvent) -> void:
    if paused or event.device == -1: return
    var p: Vector2
    if event is InputEventScreenTouch and event.pressed: p = event.position
    elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT: p = event.position
    else: return
    for button in buttons:
        var local: Vector2 = p
        if button.has("panel"):
            local = button.panel.end-p if button.top else p-button.panel.position
        if button.rect.has_point(local):
            _action(button.action)
            queue_redraw()
            return
    if stage == "play" and not rules_open and not _busy() and model.phase == Model.MOVE and _human(model.turn):
        var hits: Array = []
        for piece in model.legal_moves():
            if p.distance_to(_point(model.turn,piece,model.positions[model.turn][piece])) < cell*0.55: hits.append(piece)
        if hits.size() == 1: _move(hits[0])
        elif hits.size() > 1: message = "这里有叠机，请点击编号选机"
        queue_redraw()
func _action(action: String) -> void:
    if action in ["exit","rules","close_rules","guide_prev","guide_next","rule_page","begin","pause","casual","competitive"] or action.begins_with("ready") or action.begins_with("count") or action.begins_with("mode"):
        get_node("/root/GamePlatform").feedback("ui_back" if action == "exit" else ("ui_confirm" if action == "begin" or action.begins_with("ready") else "ui_switch"))
    if action == "exit": get_node("/root/GamePlatform").exit_to_hall()
    elif action == "pause": pause_requested.emit()
    elif action == "rules": rules_open = true; guide_page = 0
    elif action == "guide_prev": guide_page = (guide_page+2)%3
    elif action == "guide_next": guide_page = (guide_page+1)%3
    elif action == "close_rules": rules_open = false
    elif action == "begin": _begin()
    elif action.begins_with("count"): players = int(action.trim_prefix("count")); model.reset(players)
    elif action.begins_with("mode"): mode = int(action.trim_prefix("mode"))
    elif action.begins_with("ready"):
        var seat: int = int(action.trim_prefix("ready"))
        if seat not in ready_seats: ready_seats.append(seat)
        if ready_seats.size() == model.seats.size(): stage = "play"; message = "各方准备完毕，红方先行"
    elif action == "roll" and not _busy(): _roll()
    elif action.begins_with("piece") and not _busy(): _move(int(action.trim_prefix("piece")))
func _roll(forced: int = 0) -> void:
    if model.phase != Model.ROLL or _busy(): return
    roll_seat = model.turn
    pending_face = forced if forced >= 1 and forced <= 6 else model.rng.randi_range(1,6)
    roll_duration = 0.34 if mode == 2 else 1.22
    roll_elapsed = 0
    roll_ticks = 0
    get_node("/root/GamePlatform").feedback("dice_roll")
    dice_time = roll_duration
    result_time = 0
    result_elapsed = 0
    reveal_caption = ""
    roll_penalty = false
    message = NAMES[roll_seat]+"正在掷骰…"
    queue_redraw()
func _reveal_roll() -> void:
    var result: Dictionary = model.roll(pending_face)
    if result.is_empty(): return
    face = result.dice
    last_faces[roll_seat] = face
    roll_penalty = result.penalty
    result_time = 0.18 if mode == 2 else (0.82 if face == 6 else 0.46)
    result_elapsed = 0
    wait = 0.06 if mode == 2 else (0.6 if result.no_moves else 0.25)
    message = NAMES[roll_seat]+"掷出 %d"%face
    reveal_caption = "%d点"%face
    if result.penalty:
        reveal_caption = "三连6 · 本轮撤回"
        message += "，三连6，本轮撤回"
    elif face == 6:
        reveal_caption = "6！可以起飞" if model.positions[roll_seat].has(-1) else "6！再掷一次"
        message += "，走完可再掷"
    elif result.no_moves:
        reveal_caption = "%d点 · 暂不能出动"%face
        message += "，无可走飞机"
    get_node("/root/GamePlatform").feedback("powerup" if face == 6 and not result.penalty else "board_place",face == 6)
func _move(piece: int) -> void:
    var before: Array = model.positions.duplicate(true)
    var result: Dictionary = model.move_piece(piece)
    if result.is_empty(): return
    visual_positions = before
    move_data = result
    move_path = result.path.duplicate()
    move_step = 0
    move_time = 0
    moving_lift = 0
    moving_angle = headings.get(Vector2i(result.seat,piece),result.seat*PI/2)
    from_point = _point(result.seat,piece,result.from)
    moving_at = from_point
    trail.clear()
    message = NAMES[result.seat]+" %d号"%(piece+1)
    if result.from < 0: message += " 起飞！"
    elif result.flew: message += " 跨区飞行！"
    elif result.jumped: message += " 同色跳跃"
    pending_end = model.phase == Model.OVER
    wait = 0.08 if mode == 2 else 0.4
    queue_redraw()
func _advance_effects(delta: float) -> void:
    # Even the frame removing the final effect must rebuild status and touch targets.
    if not return_flights.is_empty() or not landing_rings.is_empty() or not trail.is_empty():
        queue_redraw()
    fx_clock += delta
    for item in trail: item.age += delta
    trail = trail.filter(func(item): return item.age < 0.28)
    for item in landing_rings: item.age += delta
    landing_rings = landing_rings.filter(func(item): return item.age < 0.45)
    for item in return_flights: item.age += delta
    return_flights = return_flights.filter(func(item): return item.age < 0.48)
func _finish_move() -> void:
    var seat: int = move_data.seat
    var piece: int = move_data.piece
    headings[Vector2i(seat,piece)] = moving_angle
    for victim in move_data.captures:
        return_flights.append({"seat":victim.x,"piece":victim.y,"from":_point(victim.x,victim.y,visual_positions[victim.x][victim.y]),"to":_point(victim.x,victim.y,-1),"age":0.0})
    if not move_data.captures.is_empty():
        message += "，撞回 %d 架飞机"%move_data.captures.size()
        get_node("/root/GamePlatform").feedback("eat",true)
    elif move_data.to == Model.FINISH:
        message += "，到达终点！"
        get_node("/root/GamePlatform").feedback("win",true)
    else: get_node("/root/GamePlatform").feedback("move")
    move_data.clear()
    visual_positions.clear()
    if pending_end:
        stage = "ended"
        round_finished.emit(0,NAMES[model.winner]+"获胜！四架飞机已全部到达终点。")
func _process(delta: float) -> void:
    if paused or rules_open or stage != "play": return
    _advance_effects(delta)
    if dice_time > 0:
        roll_elapsed = minf(roll_duration,roll_elapsed+delta)
        var tick: int = int(roll_elapsed/roll_duration*4)
        if tick > roll_ticks and tick < 4: pass
        roll_ticks = tick
        dice_time = maxf(0,roll_duration-roll_elapsed)
        if dice_time <= 0: _reveal_roll()
        queue_redraw()
        return
    if result_time > 0:
        result_time = maxf(0,result_time-delta)
        result_elapsed += delta
        queue_redraw()
        return
    if not move_data.is_empty():
        move_time += delta
        var target: Vector2 = _point(move_data.seat,move_data.piece,move_path[move_step])
        var takeoff: bool = move_data.from < 0 and move_step == 0
        var long_step: bool = from_point.distance_to(target) > cell*1.65
        var duration: float = (0.62 if takeoff else (0.52 if long_step else 0.18)) if mode != 2 else 0.045
        var t: float = minf(1,move_time/duration)
        moving_at = from_point.lerp(target,smoothstep(0,1,t))
        moving_lift = sin(t*PI)*cell*(0.95 if takeoff else (1.2 if long_step else 0.22))
        var angle: float = (target-from_point).angle()+PI/2
        moving_angle = lerp_angle(moving_angle,angle,minf(1,delta*12))
        trail.append({"point":moving_at-Vector2(0,moving_lift),"seat":move_data.seat,"age":0.0})
        if move_time >= duration:
            from_point = target
            moving_at = target
            moving_lift = 0
            move_step += 1
            move_time = 0
            landing_rings.append({"point":target,"seat":move_data.seat,"age":0.0,"strong":move_step == move_path.size()})
            if move_step == move_path.size(): _finish_move()
        queue_redraw()
        return
    if not return_flights.is_empty() or not landing_rings.is_empty() or not trail.is_empty(): queue_redraw()
    if not return_flights.is_empty(): return
    if wait > 0: wait -= delta; return
    if not _human(model.turn):
        if model.phase == Model.ROLL: _roll()
        elif model.phase == Model.MOVE: _move(model.best_move())
        queue_redraw()
    elif model.phase == Model.MOVE: queue_redraw()