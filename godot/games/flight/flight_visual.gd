extends RefCounted
const Polish = preload("res://core/game_polish.gd")
const Dice = preload("res://games/flight/flight_dice_art.gd")
static func panel(v, rect: Rect2, top: bool) -> void:
    var seat: int = v._panel_seat(top)
    var focus: int = v._focus_seat()
    var active: bool = v.stage == "play" and focus == seat
    Polish.tile(v,rect,Color("fffdf4"),3)
    var previous: int = v.buttons.size()
    v.draw_set_transform(rect.end if top else rect.position,PI if top else 0.0)
    var group: Array = [2,1] if top else [0,3]
    var badge_width: float = (rect.size.x-44)/2
    for index in range(2):
        var player: int = group[index]
        var x: float = 14+index*(badge_width+16)
        var present: bool = player in v.model.seats
        var selected: bool = player == focus and v.stage == "play"
        v._box(Rect2(x,12,badge_width,40),v.COLORS[player].lightened(0.76) if selected else Color("eef1ec"),12)
        var caption: String = v.NAMES[player]+(" · %d/4"%v.model.home_count(player) if present else " · 未参战")
        v._text(caption,Vector2(x+badge_width/2-12,31),21,v.COLORS[player].darkened(0.25) if present else Color("9ba49e"))
        if present: Dice.draw(v,Vector2(x+badge_width-29,31),21,v.last_faces[player],false,1,v.COLORS[player])
    var status: String = "等待另一侧" if not active else "点击骰子，试试手气"
    if active and v.model.phase == v.Model.MOVE: status = "选择一架高亮飞机"
    if active and v._busy(): status = "骰子翻滚中…" if v.dice_time > 0 else (v.reveal_caption if v.result_time > 0 else "飞机正在移动…")
    elif active and not v._human(seat): status = "电脑的回合"
    if v.stage == "consent": status = "请先在各自机场准备"
    v._text(status,Vector2(rect.size.x/2,72),20,v.INK)
    var enable: bool = active and not v._busy() and v._human(seat) and seat == v.model.turn
    var can_roll: bool = enable and v.model.phase == v.Model.ROLL
    v._button(Rect2(14,94,148,92),"","roll",can_roll,v.COLORS[seat])
    Dice.draw(v,Vector2(65,137),49,v.last_faces[seat],false,1,v.COLORS[seat])
    v._text("掷骰",Vector2(127,139),21,Color.WHITE if can_roll else Color("88958d"))
    for piece in range(4):
        var width: float = (rect.size.x-210)/4
        var x: float = 178+piece*(rect.size.x-190)/4
        var location: int = v.model.positions[seat][piece]
        var allowed: bool = enable and v.model.phase == v.Model.MOVE and piece in v.model.legal_moves()
        v._button(Rect2(x,94,width,92),"","piece%d"%piece,allowed,v.COLORS[seat])
        var color: Color = Color.WHITE if allowed else Color("85948c")
        v._text("%d号"%(piece+1),Vector2(x+width/2,120),25,color)
        var label: String = "待起飞" if location < 0 else ("已到达" if location == v.Model.FINISH else ("起飞坪" if location == 0 else ("冲刺中" if location >= v.Model.Board.HOME_START else "飞行中")))
        v._text(label,Vector2(x+width/2,157),16,color)
    v.draw_set_transform(Vector2.ZERO)
    for i in range(previous,v.buttons.size()):
        v.buttons[i].top = top
        v.buttons[i].panel = rect
static func dice_effect(v) -> void:
    if v.dice_time <= 0 and v.result_time <= 0: return
    var top: bool = v._top_seat(v.roll_seat)
    var center: Vector2 = v.board_rect.get_center()
    var p: float = clampf(v.roll_elapsed/v.roll_duration,0,1)
    var lift: float = sin(p*PI)*34+absf(sin(p*PI*4))*(1-p)*20 if v.dice_time > 0 else 0.0
    var accent: Color = v.COLORS[v.roll_seat]
    var six: bool = v.dice_time <= 0 and v.face == 6 and not v.roll_penalty
    v._box(Rect2(center-Vector2(132,146),Vector2(264,292)),Color(0.99,0.98,0.94,0.97),30)
    v.draw_set_transform(center,PI if top else 0.0)
    v._text(v.NAMES[v.roll_seat]+"的骰子",Vector2(0,-113),24,accent.darkened(0.15))
    v.draw_set_transform(center+Vector2(0,63).rotated(PI if top else 0.0),0,Vector2(1,0.24))
    v.draw_circle(Vector2.ZERO,55-lift*0.20,Color(0.10,0.16,0.20,0.16))
    v.draw_set_transform(center,PI if top else 0.0)
    if six:
        var t: float = v.result_elapsed
        for i in range(16):
            var angle: float = TAU*i/16+t*0.8
            var at := Vector2(cos(angle),sin(angle))*(86+t*24)
            var color: Color = accent if i%2 == 0 else Color("e6b83e")
            color.a = maxf(0,1-t/1.1)
            v.draw_line(at,at.normalized()*(at.length()+9),color,3,true)
    var bounce: float = sin(minf(1,v.result_elapsed/0.22)*PI)*0.08 if v.result_time > 0 else 0
    Dice.draw(v,Vector2(0,-lift),92*(1+bounce),v.pending_face if v.dice_time > 0 else v.face,v.dice_time > 0,p,accent)
    var caption: String = "能掷出6吗？" if v.dice_time > 0 else v.reveal_caption
    v._text(caption,Vector2(0,104),22,accent.darkened(0.2) if six else v.INK)
    v.draw_set_transform(Vector2.ZERO)
static func plane(v, p: Vector2, ground: Vector2, seat: int, piece: int, angle: float, lift: float, highlighted: bool) -> void:
    var radius: float = v.cell*0.42*(1+minf(lift/150,0.16))
    v.draw_set_transform(ground+Vector2(0,4),0,Vector2(1,0.63))
    v.draw_circle(Vector2.ZERO,radius*(0.84-minf(lift/220,0.24)),Color(0.10,0.18,0.22,0.18))
    v.draw_set_transform(Vector2.ZERO)
    if highlighted:
        var pulse: float = 0.48+sin(v.fx_clock*4)*0.04
        v.draw_circle(p,v.cell*pulse,Color.WHITE)
        v.draw_arc(p,v.cell*pulse,0,TAU,32,v.COLORS[seat],1.8,true)
    var silhouette := PackedVector2Array()
    for vector in [Vector2(0,-1),Vector2(0.20,-0.22),Vector2(0.88,0.17),Vector2(0.88,0.47),Vector2(0.23,0.22),Vector2(0.20,0.65),Vector2(0.43,0.9),Vector2(-0.43,0.9),Vector2(-0.20,0.65),Vector2(-0.23,0.22),Vector2(-0.88,0.47),Vector2(-0.88,0.17),Vector2(-0.20,-0.22)]: silhouette.append(p+vector.rotated(angle)*radius)
    v.draw_colored_polygon(silhouette,v.COLORS[seat].darkened(0.06))
    var outline: PackedVector2Array = silhouette.duplicate()
    outline.append(outline[0])
    v.draw_polyline(outline,v.COLORS[seat].darkened(0.35),1.2,true)
    v.draw_line(p+Vector2(0,-0.70).rotated(angle)*radius,p+Vector2(0,-0.22).rotated(angle)*radius,Color("ffffffa0"),radius*0.12,true)
    v.draw_circle(p,radius*0.48,v.COLORS[seat])
    v.draw_set_transform(p,PI if v._top_seat(seat) else 0.0)
    v._text(str(piece+1),Vector2.ZERO,20,Color.WHITE)
    v.draw_set_transform(Vector2.ZERO)