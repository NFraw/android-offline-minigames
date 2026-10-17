extends "res://games/suite/suite_game.gd"
const MemoryArt = preload("res://core/memory_art.gd")
const Model = preload("res://games/puzzle/puzzle_model.gd")
var model = Model.new()
var memory_mode: int = 0
var memory_size: int = 4
const MEMORY_SIZES = [4,6,8]
const MEMORY_NAMES = ["动物","数字","颜色"]
var rect := Rect2()
var cell: float = 0
var finger: int = -1
var press_at := Vector2.ZERO
var press_index: int = -1
var hold_time: float = 0
var hold_fired: bool = false
var flag_mode: bool = false
var pulse: float = 0
func _rules_text() -> String:
    match game_id:
        "game2048": return "上下左右滑动，让同数方块合并。\n每个方块一次滑动只合并一次，合成2048获胜。\n不能移动时不会生成新块；也可点方向按钮。"
        "sliding": return "点击空格相邻的数字块移动。\n按1到15顺序排好，右下角留空即可获胜。\n打乱通过合法移动生成，保证可解。"
        "mines": return "9×12棋盘，15颗雷，首次点击保证安全。\n点击翻开，长按插旗；也可切换插旗模式。\n翻开全部安全格获胜，空白区自动展开。"
        "memory": return "翻开两张相同的牌进行配对。\n不同牌显示0.8秒后盖回，全部配齐就获胜，可选动物、数字、颜色。"
        "memory_duel": return "双人轮流翻牌，配对成功得分并继续。\n配错0.8秒后盖回并换手，三关累计比分。\n关卡依次4×4、6×6、8×8。"
        _: return "先观察四种颜色的闪灯顺序，再照着点击。\n每轮追加一种颜色，点错即结束。\n连续同色会先熄灭再亮起，播放时不可输入。"
func _new_game() -> void:
    texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
    model.memory_size = memory_size
    model.reset(game_id)
    flag_mode = false
    pulse = 0
    _clear_controls()
    message = "观察闪灯顺序" if game_id == "simon" else "点击棋盘开始"
    _game_layout()
func _game_layout() -> void:
    rect = _square(model.cols,model.rows)
    if game_id in ["game2048","mines"]:
        rect.size *= 0.91
        rect.position = arena.get_center()-rect.size/2
    cell = rect.size.x/model.cols
func _extra_setup(r: Rect2) -> void:
    if game_id not in ["memory","memory_duel"]: return
    for i in range(3):
        var width: float = (r.size.x-60)/3
        _button(Rect2(r.position+Vector2(24+i*(width+6),295),Vector2(width,60)),MEMORY_NAMES[i]+(" ✓" if memory_mode == i else ""),"memory_mode%d"%i,P.GREEN if memory_mode == i else P.BLUE)
    if game_id == "memory":
        _button(Rect2(r.position+Vector2(24,375),Vector2(r.size.x-48,60)),"棋盘 %d×%d · 点此调整"%[memory_size,memory_size],"memory_size",P.BLUE)
func _player_status(seat: int) -> String:
    return "玩家%d · %d对 · 第%d/3关 %d×%d · %s"%[seat+1,model.scores[seat],model.level+1,model.cols,model.rows,"轮到你" if model.turn == seat else "等待"]
func _draw_play() -> void:
    if game_id == "mines":
        _classic_bevel(rect.grow(10),true)
        _box(Rect2(rect.position.x,rect.position.y-70,rect.size.x,52),Color("c0c0c0"),0)
        _text("%03d"%maxi(0,15-model.flagged.count(true)),Vector2(rect.position.x+52,rect.position.y-44),32,Color("c52b24"))
        _text("%03d"%mini(999,int(model.elapsed)),Vector2(rect.end.x-55,rect.position.y-44),32,Color("c52b24"))
        draw_circle(Vector2(rect.get_center().x,rect.position.y-44),20,Color("f3da4b"))
        _text("☺",Vector2(rect.get_center().x,rect.position.y-44),31,P.TEXT)
    elif game_id == "simon":
        _box(rect.grow(15),Color("45485c"),28)
        _box(rect.grow(7),Color("292e42"),23)
    else: _board_surface(rect.grow(8))
    var colors: Array = [P.RED,P.BLUE,P.GREEN,P.WOOD_FRAME]
    for index in range(model.cells.size()):
        var p := Vector2i(index%model.cols,index/model.cols)
        var r := Rect2(rect.position+Vector2(p)*cell,Vector2.ONE*cell).grow(-3 if cell > 45 else -1.5)
        var value: int = model.cells[index]
        var tint: Color = P.WOOD_LO
        var caption: String = ""
        var ink: Color = P.TEXT
        if game_id == "game2048":
            tint = P.WOOD_LO if value == 0 else P.PIECE_HI.lerp(P.RED,clampf(log(maxi(2,value))/log(2)/12,0,1))
            caption = str(value) if value > 0 else ""
        elif game_id == "sliding":
            tint = P.PIECE_HI if value > 0 else P.WOOD_LO
            caption = str(value) if value > 0 else ""
        elif game_id == "mines":
            var show: bool = model.revealed[index] or (model.done and value == -1)
            tint = Color("c0c0c0") if show else Color("bdbdbd")
            caption = (str(value) if value > 0 else "") if show else ""
            ink = [Color.BLUE,Color("187126"),Color.RED,Color("151573"),Color("7f1d1d"),Color("178b8b"),Color.BLACK,Color("686868")][clampi(value-1,0,7)]
        elif game_id in ["memory","memory_duel"]:
            var show: bool = model.matched[index] or index in [model.first,model.second]
            tint = P.GREEN.lightened(0.55) if model.matched[index] else (P.PIECE_HI if show else P.BLUE)
            caption = str(value+1) if show and memory_mode == 1 else ("?" if not show else "")
            ink = P.TEXT if show else P.PIECE_HI
        else:
            tint = colors[index].lightened(0.04) if model.lit == index else colors[index].lightened(0.78)
            caption = ["红","蓝","绿","黄"][index]
            ink = P.PIECE_HI if model.lit == index else P.MUTED
        if game_id == "mines":
            _classic_bevel(r,model.revealed[index])
            if (model.done or model.revealed[index]) and value < 0: _mine(r)
            elif model.flagged[index] and not model.revealed[index]: _flag(r)
        elif game_id in ["game2048","sliding","simon"]:
            Polish.tile(self,r,tint,3 if game_id != "simon" else 5)
        else: _box(r,tint,mini(16,int(cell/7)))
        if game_id in ["memory","memory_duel"] and (model.matched[index] or index in [model.first,model.second]):
            if memory_mode == 0:
                MemoryArt.draw_animal(self,r.get_center(),r.size.x*0.44,value,game_id == "memory_duel" and model.turn == 1)
            elif memory_mode == 2:
                var tint_card := Color.from_hsv(fmod(value*0.6180339,1),0.48+0.12*(int(value/8)%3),0.77+0.08*(int(value/24)%3))
                draw_circle(r.get_center(),r.size.x*0.34,tint_card)
                for dot in range(int(value/12)+1):
                    var angle: float = TAU*dot/(int(value/12)+1)
                    draw_circle(r.get_center()+Vector2(cos(angle),sin(angle))*r.size.x*0.22,maxf(1,r.size.x*0.035),P.PIECE_HI)
        if game_id == "sliding" and value > 0 and value == index+1:
            draw_circle(r.end-Vector2(12,12),3,P.GREEN)
        _text(caption,r.get_center(),int(clampf(cell*0.39,15,48)),ink,game_id == "memory_duel" and model.turn == 1)
        if game_id == "simon" and model.lit == index:
            draw_rect(r.grow(-5),P.PIECE_HI,false,7)
            _text("亮",r.get_center()+Vector2(0,cell*0.28),int(cell*0.18),P.PIECE_HI)
    var y: float = minf(rect.end.y+16,get_viewport_rect().size.y-105)
    if game_id == "mines":
        _button(Rect2(rect.get_center().x-170,y,340,48),"操作："+("插旗" if flag_mode else "翻开")+" · 已插%d旗"%model.flagged.count(true),"flag_mode",P.BLUE)
    elif game_id == "game2048":
        for index in range(4):
            _button(Rect2(rect.get_center().x-218+index*112,y,100,48),["←","↑","↓","→"][index],"direction%d"%index,P.BLUE)
    if pulse > 0: draw_rect(rect.grow(3),P.GREEN*Color(1,1,1,pulse/0.18),false,3)
func _index(at: Vector2) -> int:
    if not rect.has_point(at): return -1
    var p := Vector2i((at-rect.position)/cell)
    return p.y*model.cols+p.x
func _game_down(at: Vector2, id: int) -> void:
    var index: int = _index(at)
    if index < 0: return
    if game_id in ["game2048","mines"]:
        if finger >= 0: return
        finger = id; press_at = at; press_index = index; hold_time = 0; hold_fired = false
        return
    _tap(index)
func _game_drag(at: Vector2, id: int) -> void:
    if id == finger and game_id == "mines" and at.distance_to(press_at) > 15:
        hold_fired = true
func _game_up(at: Vector2, id: int) -> void:
    if id != finger: return
    if game_id == "game2048":
        var delta: Vector2 = at-press_at
        if delta.length() >= 24:
            var direction := Vector2i(signi(int(delta.x)),0) if absf(delta.x) > absf(delta.y) else Vector2i(0,signi(int(delta.y)))
            if model.slide(direction): pulse = 0.18; _feedback("move")
    elif game_id == "mines" and not hold_fired and _index(at) == press_index:
        _tap(press_index)
    _clear_controls()
    _sync()
func _tap(index: int) -> void:
    var before: int = model.score
    var old_cols: int = model.cols
    var changed: bool = model.flag(index) if game_id == "mines" and flag_mode else model.tap(index)
    if changed:
        pulse = 0.18
        _feedback("simon%d"%index if game_id == "simon" and not model.done else ("eat" if model.score > before else "click"))
    else: _feedback("lose")
    if model.cols != old_cols: _game_layout()
    _sync()
func _sync() -> bool:
    var changed: bool = score != model.score
    if changed: _set_score(model.score)
    match game_id:
        "mines": message = "用时%d秒 · 长按插旗"%int(model.elapsed)
        "sliding": message = "已走%d步"%model.moves
        "memory":
            message = "%s · 已配对%d/%d · %d步"%[MEMORY_NAMES[memory_mode],model.matched.count(true)/2,model.cells.size()/2,model.moves]
            if memory_mode == 0 and model.first >= 0: message = MemoryArt.name_of(model.cells[model.first])+" · 记住它的位置，再找另一张"
        "simon": message = ("准备观察…" if model.show_index < 0 else "仔细看：第%d盏"%(model.show_index+1)) if model.showing else "轮到你 · 第%d步/%d"%[model.input_index+1,model.sequence.size()]
    if model.done:
        var success: bool = true
        if game_id == "simon": success = false
        elif game_id == "game2048": success = model.cells.has(2048)
        elif game_id == "mines":
            for i in range(model.cells.size()):
                if model.cells[i] == -1 and model.revealed[i]: success = false
        _end(model.result,success)
        changed = true
    return changed
func _game_action(action: String) -> void:
    if action.begins_with("memory_mode"):
        memory_mode = int(action.trim_prefix("memory_mode")); return
    if action == "memory_size":
        memory_size = MEMORY_SIZES[(MEMORY_SIZES.find(memory_size)+1)%MEMORY_SIZES.size()]; return
    if action == "flag_mode": flag_mode = not flag_mode
    elif action.begins_with("direction"):
        var direction: Vector2i = [Vector2i.LEFT,Vector2i.UP,Vector2i.DOWN,Vector2i.RIGHT][int(action.trim_prefix("direction"))]
        if model.slide(direction): _feedback("move"); pulse = 0.18
        _sync()
func _game_key(key: int) -> void:
    if game_id == "game2048":
        var actions: Dictionary = {KEY_LEFT:"direction0",KEY_UP:"direction1",KEY_DOWN:"direction2",KEY_RIGHT:"direction3"}
        if actions.has(key): _game_action(actions[key]); queue_redraw()
func _game_process(delta: float) -> bool:
    var old_lit: int = model.lit
    var changed: bool = model.tick(delta)
    if game_id == "simon" and model.lit >= 0 and model.lit != old_lit: _feedback("simon%d"%model.lit)
    if pulse > 0: pulse = maxf(0,pulse-delta); changed = true
    if game_id == "mines" and finger >= 0 and not hold_fired:
        hold_time += delta
        if hold_time >= 0.5:
            model.flag(press_index); hold_fired = true; _feedback("click",true); changed = true
    return _sync() or changed
func _clear_controls() -> void:
    finger = -1; press_index = -1; hold_time = 0; hold_fired = false

func _classic_bevel(r: Rect2, pressed: bool) -> void:
    draw_rect(r,Color("c0c0c0"))
    var light := Color("808080") if pressed else Color.WHITE
    var dark := Color("808080") if not pressed else Color.WHITE
    draw_line(r.position,r.position+Vector2(r.size.x,0),light,3)
    draw_line(r.position,r.position+Vector2(0,r.size.y),light,3)
    draw_line(r.end,r.end-Vector2(r.size.x,0),dark,3)
    draw_line(r.end,r.end-Vector2(0,r.size.y),dark,3)
func _mine(r: Rect2) -> void:
    var center: Vector2 = r.get_center()
    for i in range(4):
        var vector := Vector2(cos(i*PI/4),sin(i*PI/4))*r.size.x*0.33
        draw_line(center-vector,center+vector,Color.BLACK,2,true)
    draw_circle(center,r.size.x*0.22,Color.BLACK)
    draw_circle(center-Vector2.ONE*r.size.x*0.07,r.size.x*0.055,Color.WHITE)
func _flag(r: Rect2) -> void:
    var center: Vector2 = r.get_center()
    draw_line(center+Vector2(0,-r.size.y*0.32),center+Vector2(0,r.size.y*0.3),Color.BLACK,3)
    draw_colored_polygon(PackedVector2Array([center+Vector2(0,-r.size.y*0.32),center+Vector2(-r.size.x*0.3,-r.size.y*0.12),center+Vector2(0,r.size.y*0.02)]),Color("d22c22"))
    draw_line(center+Vector2(-r.size.x*0.21,r.size.y*0.3),center+Vector2(r.size.x*0.21,r.size.y*0.3),Color.BLACK,3)
