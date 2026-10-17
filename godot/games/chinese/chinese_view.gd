extends "res://games/suite/suite_game.gd"
const PIECES = preload("res://games/chinese/assets/pieces-atlas.png")
const Model = preload("res://games/chinese/chinese_model.gd")
var model = Model.new()
var rect := Rect2()
var cell: float = 0
var selected: int = -1
var animation: Dictionary = {}
var animation_time: float = 0
var confirmation: String = ""
var requester: int = 0
func _rules_text() -> String:
    if game_id == "darkchess": return "4×8半盘暗棋，第一枚翻开的颜色决定双方阵营。\n每回合翻一枚暗子，或移动己方明子一步。\n兵卒吃帅将；同级互吃；炮沿直线隔一子，可吃任意远处明子。\n吃光对方或令对方无动作获胜，120步无吃子和棋。"
    return "红先黑后，按标准象棋规则走子。\n马蹩腿、象塞眼、象不过河，炮必须隔一子吃。\n不能自将或让将帅照面，将死及困毙判负。\n每方3次悔棋须对方同意，支持认输和中文语音。"
func _new_game() -> void:
    model.reset(game_id)
    selected = -1; animation.clear(); confirmation = ""
    message = "先翻开一枚暗子" if game_id == "darkchess" else "红方先走"
    _game_layout()
func _game_layout() -> void:
    var reserve: float = 110 if game_id == "xiangqi_duel" else 0
    cell = minf(arena.size.x/model.cols,(arena.size.y-reserve)/model.rows)
    rect = Rect2(arena.get_center().x-cell*model.cols/2,arena.get_center().y-cell*model.rows/2,cell*model.cols,cell*model.rows)
func _center(index: int) -> Vector2:
    return rect.position+(Vector2(model.point(index))+Vector2.ONE*0.5)*cell
func _draw_play() -> void:
    BoardArt.draw_wood_slab(self,rect.grow(9))
    if game_id == "xiangqi_duel":
        for y in range(10): draw_line(_center(y*9),_center(y*9+8),P.WOOD_LINE,1.5)
        for x in range(9):
            if x in [0,8]: draw_line(_center(x),_center(81+x),P.WOOD_LINE,1.5)
            else:
                draw_line(_center(x),_center(36+x),P.WOOD_LINE,1.5)
                draw_line(_center(45+x),_center(81+x),P.WOOD_LINE,1.5)
        for y in [0,7]:
            draw_line(_center(y*9+3),_center((y+2)*9+5),P.WOOD_LINE,1.5)
            draw_line(_center(y*9+5),_center((y+2)*9+3),P.WOOD_LINE,1.5)
        _text("楚 河",rect.position+Vector2(cell*2.5,cell*5),int(cell*0.37),P.WOOD_FRAME)
        _text("汉 界",rect.position+Vector2(cell*6.5,cell*5),int(cell*0.37),P.WOOD_FRAME,true)
    else:
        for x in range(5): draw_line(rect.position+Vector2(x*cell,0),rect.position+Vector2(x*cell,rect.size.y),P.WOOD_LINE,1.5)
        for y in range(9): draw_line(rect.position+Vector2(0,y*cell),rect.position+Vector2(rect.size.x,y*cell),P.WOOD_LINE,1.5)
    if not model.last_move.is_empty():
        for i in [model.last_move.from,model.last_move.to]: draw_circle(_center(i),cell*0.10,P.GREEN)
    if selected >= 0:
        draw_arc(_center(selected),cell*0.47,0,TAU,32,P.GREEN,3,true)
        for to in model.targets(selected):
            if model.board[to] == 0: draw_circle(_center(to),cell*0.12,P.GREEN)
            else: draw_arc(_center(to),cell*0.46,0,TAU,32,P.RED,3,true)
    for i in range(model.board.size()):
        var piece: int = model.board[i]
        if piece == 0 or (not animation.is_empty() and i == animation.to): continue
        _token(_center(i),piece,not model.revealed[i],1)
    if not animation.is_empty():
        var t: float = clampf(animation_time/0.22,0,1)
        if animation.flip:
            _token(_center(animation.to),animation.piece,t < 0.5,maxf(0.05,absf(cos(t*PI))))
        else:
            if animation.captured != 0 and t < 0.9: _token(_center(animation.to),animation.captured,false,1-clampf((t-0.7)/0.2,0,1))
            _token(_center(animation.from).lerp(_center(animation.to),smoothstep(0,1,t)),animation.piece,false,1.04)
    if game_id == "xiangqi_duel":
        if model.in_check(model.turn):
            var king: int = model.board.find(1 if model.turn == 0 else 11)
            if king >= 0: draw_arc(_center(king),cell*0.49,0,TAU,36,P.RED,3,true)
        for owner in range(2):
            var y: float = arena.position.y if owner == 1 else arena.end.y-46
            _button(Rect2(arena.position.x,y,150,40),"悔棋%d次"%model.undo_left[owner],"undo%d"%owner,P.BLUE if owner == 1 else P.RED,owner == 1)
            _button(Rect2(arena.end.x-150,y,150,40),"认输","resign%d"%owner,P.BLUE if owner == 1 else P.RED,owner == 1)
    if not confirmation.is_empty(): _draw_confirmation()
func _token(at: Vector2, piece: int, hidden: bool, factor: float) -> void:
    if factor <= 0: return
    var radius: float = cell*0.41*factor
    if hidden:
        draw_circle(at+Vector2(0,3),radius,P.SHADOW)
        Polish.orb(self,at,radius,Color("846650"))
        draw_arc(at,radius*0.77,0,TAU,28,P.PIECE_HI,1.5,true)
        _text("暗",at,int(cell*0.4*factor),P.PIECE_HI)
        draw_line(at-Vector2(radius*0.53,0),at-Vector2(radius*0.40,0),P.PIECE_HI,2)
        draw_line(at+Vector2(radius*0.40,0),at+Vector2(radius*0.53,0),P.PIECE_HI,2)
    else:
        var owner: int = Model.side(piece)
        var top: bool = owner == model.player_sides[1]
        var source: Rect2 = Rect2(31,37,839,826) if owner == 0 else Rect2(942,21,768,805)
        draw_texture_rect_region(PIECES,Rect2(at-Vector2.ONE*radius,Vector2.ONE*radius*2),source,Color.WHITE,false,true)
        _text(Model.name_of(piece),at,int(radius*1.2),Color("9d251e") if owner == 0 else Color("28221e"),top)
        if game_id == "darkchess": _text(str(Model.rank(piece)),at+Vector2(radius*0.53,radius*0.48),maxi(10,int(radius*0.38)),P.TEXT,top)
func _player_status(seat: int) -> String:
    var owner: int = model.player_sides[seat]
    var label: String = "阵营未定" if owner < 0 else ("红方" if owner == 0 else "黑方")
    var status: String = "轮到你" if model.turn == seat else "等待"
    if game_id == "xiangqi_duel" and model.turn == seat and model.in_check(owner): status += " · 将军！"
    return "玩家%d · %s · %s"%[seat+1,label,status]
func _game_down(at: Vector2, _finger: int) -> void:
    if not animation.is_empty() or not confirmation.is_empty() or not rect.has_point(at): return
    var p := Vector2i((at-rect.position)/cell)
    _tap(p.y*model.cols+p.x)
func _tap(index: int) -> void:
    if not animation.is_empty() or not confirmation.is_empty(): return
    var move: Dictionary = {}
    if game_id == "darkchess" and model.board[index] != 0 and not model.revealed[index]:
        move = model.flip(index)
    elif model.board[index] != 0 and Model.side(model.board[index]) == model.player_sides[model.turn]:
        selected = -1 if selected == index else index
        _feedback("click")
    elif selected >= 0:
        move = model.play(selected,index)
        if move.is_empty(): message = "此处不能走，不能自将或违反走法"; _feedback("lose")
    if not move.is_empty():
        selected = -1; animation = move; animation_time = 0
    queue_redraw()
func _finish_move() -> void:
    var captured: int = animation.captured
    var flipped: bool = animation.flip
    animation.clear()
    _feedback("click" if flipped else ("eat" if captured != 0 else "move"),captured != 0)
    if game_id == "xiangqi_duel":
        if model.done: _voice("jiangsi")
        elif model.in_check(model.turn): _voice("jiangjun")
        elif captured != 0: _voice("chi")
    if model.done: _end(model.result)
    queue_redraw()
func _game_action(action: String) -> void:
    if action == "deny": confirmation = ""; return
    if action == "confirm":
        if confirmation == "undo":
            if model.undo(requester,true): _voice("huiqi")
        elif confirmation == "resign":
            model.resign(requester); _voice("renshu"); _end(model.result)
        confirmation = ""; selected = -1; return
    if not animation.is_empty() or game_id != "xiangqi_duel" or not confirmation.is_empty(): return
    if action.begins_with("undo"):
        requester = int(action.trim_prefix("undo"))
        if model.history.is_empty() or model.undo_left[requester] <= 0:
            message = "暂无可撤销步骤或次数已用完"; _feedback("lose")
        else: confirmation = "undo"
    elif action.begins_with("resign"): requester = int(action.trim_prefix("resign")); confirmation = "resign"
func _draw_confirmation() -> void:
    var r: Rect2 = _modal(330)
    var top: bool = requester == 0 if confirmation == "undo" else requester == 1
    var points: Array = [Vector2(r.size.x/2,45),Vector2(r.size.x/2,110),Vector2(r.size.x/2,160)]
    var captions: Array = ["悔棋申请" if confirmation == "undo" else "确认认输",("黑方" if requester == 1 else "红方")+("请求撤销上一步" if confirmation == "undo" else "认输后将结束对局"),"需另一方同意，拒绝不会扣次数" if confirmation == "undo" else "确定结束本局？"]
    for i in range(3): _text(captions[i],r.end-points[i] if top else r.position+points[i],25 if i == 0 else 20,P.TEXT,top)
    for i in range(2):
        var local := Rect2(24+i*(r.size.x-36)/2,230,(r.size.x-60)/2,60)
        var physical := Rect2(r.end-local.end if top else r.position+local.position,local.size)
        _button(physical,"拒绝" if i == 0 else "同意","deny" if i == 0 else "confirm",P.BLUE if i == 0 else P.RED,top)
func _voice(cue: String) -> void:
    var platform = get_node("/root/GamePlatform")
    if platform.has_method("voice"): platform.voice(cue)
func _game_process(delta: float) -> bool:
    if not animation.is_empty():
        animation_time += delta
        if animation_time >= 0.22: _finish_move()
        return true
    return false
