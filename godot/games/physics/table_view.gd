extends "res://games/suite/suite_game.gd"
const Pool = preload("res://games/physics/pool_model.gd")
const Curling = preload("res://games/physics/curling_model.gd")
const CURLING_ART = preload("res://games/physics/assets/curling-atlas.png")
var model = Pool.new()
var rect := Rect2()
var world_scale: float = 1
var vertical: bool = false
var player_choice: int = 2
var finger: int = -1
var pivot := Vector2.ZERO
var aim_at := Vector2.ZERO
var charge: float = 0
var power: float = 0
func _rules_text() -> String:
    if game_id == "pool": return "8球规则：开球进球不定组，之后首次合法进球定组。\n拖向母球的后方蓄力，松手出杆；犯规对方自由球。\n实球1到7、花球9到15；清组后打8号前先点袋口。\n合法叫袋进8获胜，违例进8判负；开球进8摆回。"
    return "2至4人轮流投壶，每人每局两壶。\n按下点为固定支点，拖动瞄准线，按住蓄力后松手。\n线与发射底线的交点为投出点，预览与实际一致。\n只有营垒圈内的壶计分，下一局由上局赢家先投。"
func _new_game() -> void:
    if game_id == "curling":
        model = Curling.new(); players = player_choice; model.reset(players)
    else: model = Pool.new(); model.reset()
    _clear_controls()
    _game_layout()
func _game_layout() -> void:
    vertical = game_id == "pool" and arena.size.y > arena.size.x
    var size: Vector2 = Vector2(500,1000) if vertical else model.SIZE
    world_scale = minf(arena.size.x/size.x,(arena.size.y-70)/size.y)
    rect = Rect2(arena.get_center().x-size.x*world_scale/2,arena.get_center().y-size.y*world_scale/2-20,size.x*world_scale,size.y*world_scale)
func _screen(p: Vector2) -> Vector2:
    var at: Vector2 = Vector2(p.y,1000-p.x) if vertical else p
    return rect.position+at*world_scale
func _world(p: Vector2) -> Vector2:
    var at: Vector2 = (p-rect.position)/world_scale
    return Vector2(1000-at.y,at.x) if vertical else at
func _extra_setup(r: Rect2) -> void:
    if game_id == "curling":
        _button(Rect2(r.position+Vector2(24,300),Vector2(r.size.x-48,52)),"人数：%d人 · 点击切换"%player_choice,"players",P.BLUE)
func _draw_play() -> void:
    BoardArt.draw_wood_slab(self,rect.grow(12))
    if game_id == "pool": _draw_pool()
    else: _draw_curling()
func _draw_pool() -> void:
    Polish.surface(self,rect,"felt")
    draw_rect(rect.grow(-3),Color("d9c393"),false,3)
    for i in range(6):
        var at: Vector2 = _screen(Pool.POCKETS[i])
        draw_circle(at,Pool.RADIUS*2.05*world_scale,Color("9d7c4e"))
        draw_circle(at,Pool.RADIUS*1.85*world_scale,Color("142c29"))
        draw_circle(at+Vector2(0,2),Pool.RADIUS*1.45*world_scale,Color("0b1618"))
        _text(str(i+1),at,15,P.PIECE_HI)
        if model.phase == "call" or model.call_pocket == i: draw_arc(at,Pool.RADIUS*2.7*world_scale,0,TAU,32,P.RED,2,true)
    var colors: Array = [P.WOOD_FRAME,P.BLUE,P.RED,P.BLUE.darkened(0.2),P.RED.lightened(0.2),P.GREEN,P.WOOD_FRAME.darkened(0.2)]
    for ball in model.balls:
        if not ball.active: continue
        var at: Vector2 = _screen(ball.p)
        var radius: float = Pool.RADIUS*world_scale
        var tint: Color = P.PIECE_HI if ball.num == 0 else (P.TEXT if ball.num == 8 else colors[(ball.num-1)%7])
        Polish.orb(self,at,radius*1.05,P.PIECE_HI if ball.num > 8 else tint)
        if ball.num > 8: draw_rect(Rect2(at-Vector2(radius*0.9,radius*0.3),Vector2(radius*1.8,radius*0.6)),tint)
        if ball.num > 0:
            draw_circle(at,radius*0.47,P.PIECE_HI)
            _text(str(ball.num),at,maxi(10,int(radius*0.65)),P.TEXT)
    if finger >= 0 and model.phase == "aim":
        var cue: Dictionary = model.get_ball(0)
        var direction: Vector2 = cue.p-aim_at
        if direction.length() > 1:
            draw_dashed_line(_screen(cue.p),_screen(cue.p+direction.normalized()*300),P.PIECE_HI,2,8)
            draw_line(_screen(cue.p),_screen(aim_at),P.WOOD_LO,3,true)
    _text(model.message,Vector2(rect.get_center().x,rect.end.y+35),19,P.MUTED)
func _draw_curling() -> void:
    Polish.surface(self,rect,"ice")
    draw_rect(rect.grow(-2),Color("7aa5b8"),false,2)
    var colors: Array = [P.BLUE,P.PIECE_HI,P.RED,P.PIECE_HI]
    for i in range(4): draw_circle(_screen(Curling.CENTER),Curling.HOUSE*(4-i)/4*world_scale,colors[i])
    draw_line(_screen(Vector2(0,Curling.CENTER.y)),_screen(Vector2(1000,Curling.CENTER.y)),P.BLUE,1)
    draw_line(_screen(Vector2(Curling.RADIUS,1800-Curling.RADIUS)),_screen(Vector2(1000-Curling.RADIUS,1800-Curling.RADIUS)),P.BLUE,2)
    var aim_zone := Rect2(_screen(Curling.AIM_ZONE.position),Curling.AIM_ZONE.size*world_scale)
    draw_rect(aim_zone,Color("97bacd30"))
    draw_rect(aim_zone,Color("7297a8"),false,2)
    _text("在框内按住，再向后拉",aim_zone.get_center(),maxi(18,int(32*world_scale)),Color("547584"))
    for ball in model.balls:
        var radius: float = Curling.RADIUS*world_scale
        var at: Vector2 = _screen(ball.p)
        var cell: Vector2 = CURLING_ART.get_size()/2.0
        var source := Rect2(Vector2(ball.owner%2,int(ball.owner/2))*cell,cell)
        draw_texture_rect_region(CURLING_ART,Rect2(at-Vector2.ONE*radius*1.2,Vector2.ONE*radius*2.4),source,Color.WHITE,false,true)
        _text(str(ball.owner+1),at+Vector2(0,radius*0.48),maxi(11,int(radius*0.8)),P.PIECE_HI)
    if finger >= 0 and model.phase == "throw":
        var solution: Dictionary = Curling.aim(pivot,aim_at)
        draw_circle(_screen(pivot),5,P.RED)
        draw_line(_screen(pivot),_screen(aim_at),P.TEXT,2,true)
        if not solution.is_empty():
            draw_circle(_screen(solution.launch),Curling.RADIUS*world_scale,Color("a87736"))
            draw_dashed_line(_screen(solution.launch),_screen(solution.launch+solution.direction*300),P.BLUE,2,8)
        _box(Rect2(rect.position.x,rect.end.y+9,rect.size.x*power,12),P.RED,5)
    _text(model.round_result if model.phase == "between" else "框内按住 → 向后拉，拉得越远力度越大",Vector2(rect.get_center().x,rect.end.y+37),19,P.MUTED)
func _player_status(seat: int) -> String:
    if game_id == "pool": return "玩家%d · %s · %s"%[seat+1,model.group_label(seat),"轮到你" if model.turn == seat else "等待"]
    return "玩家%d · %d分 · 第%d/%d局 · %s"%[seat+1,model.scores[seat],model.round_index,model.rounds,"当前投壶" if model.turn == seat else "等待"]
func _game_action(action: String) -> void:
    if action == "players": player_choice = 2+(player_choice-1)%3; players = player_choice
func _game_down(at: Vector2, id: int) -> void:
    if not rect.grow(15).has_point(at) or finger >= 0: return
    var world: Vector2 = _world(at)
    if game_id == "pool":
        if model.phase == "place":
            if not model.place_cue(world): model.message = "此处不能放球：避开其他球与袋口"
            return
        if model.phase == "call":
            for i in range(6):
                if world.distance_to(Pool.POCKETS[i]) < Pool.RADIUS*3.5: model.choose_pocket(i); return
            return
        if model.phase != "aim": return
    elif model.phase != "throw": return
    elif not Curling.AIM_ZONE.has_point(world):
        message = "请在底部的投壶框内按住"; return
    finger = id; pivot = world; aim_at = world; charge = 0; power = 0
func _game_drag(at: Vector2, id: int) -> void:
    if id == finger:
        aim_at = _world(at)
        if game_id == "curling": aim_at = pivot+(aim_at-pivot).limit_length(Curling.MAX_PULL)
func _game_up(at: Vector2, id: int) -> void:
    if id != finger: return
    aim_at = _world(at)
    if game_id == "curling": aim_at = pivot+(aim_at-pivot).limit_length(Curling.MAX_PULL)
    var valid: bool = false
    if game_id == "pool": valid = model.shoot(aim_at)
    else:
        power = clampf(pivot.distance_to(aim_at)/Curling.MAX_PULL,0,1)
        valid = model.throw_stone(Curling.aim(pivot,aim_at),power)
    _feedback("move" if valid else "lose",valid)
    _clear_controls()
func _game_process(delta: float) -> bool:
    var changed: bool = model.tick(delta)
    if game_id == "curling":
        if finger >= 0 and model.phase == "throw":
            charge += delta; power = clampf(pivot.distance_to(aim_at)/Curling.MAX_PULL,0,1); changed = true
        message = "第%d/%d局 · 玩家%d投壶 · %s"%[model.round_index,model.rounds,model.turn+1," / ".join(PackedStringArray(model.scores.slice(0,players).map(func(s): return str(s))))]
    if changed and model.last_impact > 250: _feedback("click")
    if model.done: _end(model.result)
    return changed
func _clear_controls() -> void:
    finger = -1; power = 0; charge = 0
