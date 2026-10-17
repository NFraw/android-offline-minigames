extends "res://games/suite/suite_game.gd"
const Model = preload("res://games/stone/stone_model.gd")
var model = Model.new()
var rect := Rect2()
var cell: float = 0
var ai_wait: float = 0
func _rules_text() -> String:
    match game_id:
        "tictactoe": return "轮流在空格落子，横竖斜三子连线获胜。\n每局交换先手，九格落满未分胜负为平局。"
        "gomoku": return "黑方先手，横竖斜任意方向连成五子获胜。\n支持双人同屏与人机，电脑思考时不可落子。"
        "reversi": return "落子必须夹住对方棋子，并翻转整条夹线。\n无合法落点自动跳过；双方无子可下才结算。\n支持双人同屏与人机；棋子较多一方获胜。"
        _: return "黑白轮流落子，围住无气的棋群即可提子。\n禁止自杀及立即回提打劫；虚手可解除劫争。\n一方虚手只换手，连续两次虚手才结算。\n按棋子与围空之和比较面积，无贴目。"
func _new_game() -> void:
    model.reset(game_id,1+(maxi(0,round_number-1)%2) if game_id == "tictactoe" else 1)
    ai_wait = 0.35
    message = "点击空位落子"
    _game_layout()
func _game_layout() -> void:
    rect = _square(model.n,model.n)
    if game_id == "weiqi":
        rect.size *= 0.9
        rect.position = arena.get_center()-rect.size/2
    cell = rect.size.x/model.n
func _draw_play() -> void:
    _board_surface(rect.grow(10))
    var intersections: bool = game_id in ["gomoku","weiqi"]
    for i in range(model.n+1):
        if intersections and i == model.n: continue
        var offset: float = (i+0.5)*cell if intersections else i*cell
        var lo: float = cell*0.5 if intersections else 0
        var hi: float = rect.size.x-cell*0.5 if intersections else rect.size.x
        draw_line(rect.position+Vector2(offset,lo),rect.position+Vector2(offset,hi),P.WOOD_LINE,1.5,true)
        draw_line(rect.position+Vector2(lo,offset),rect.position+Vector2(hi,offset),P.WOOD_LINE,1.5,true)
    if intersections:
        for x in [3,7,11]:
            for y in [3,7,11]: draw_circle(rect.position+Vector2(x+0.5,y+0.5)*cell,cell*0.075,P.WOOD_FRAME)
    var legal: Array = model.legal_moves() if game_id == "reversi" else []
    for i in range(model.board.size()):
        var center: Vector2 = rect.position+(Vector2(model.point(i))+Vector2.ONE*0.5)*cell
        var piece: int = model.board[i]
        if piece == 0:
            if i in legal: draw_arc(center,cell*0.11,0,TAU,24,P.GREEN,2,true)
            continue
        if game_id == "tictactoe":
            if piece == 1:
                draw_line(center-Vector2.ONE*cell*0.25,center+Vector2.ONE*cell*0.25,P.RED,7,true)
                draw_line(center+Vector2(-1,1)*cell*0.25,center+Vector2(1,-1)*cell*0.25,P.RED,7,true)
            else: draw_arc(center,cell*0.27,0,TAU,48,P.BLUE,7,true)
        else:
            Polish.orb(self,center,cell*0.43,Color("283242") if piece == 1 else Color("faf8ed"))
        if i == model.last and game_id != "tictactoe": draw_circle(center,cell*0.08,P.RED if piece == 1 else P.BLUE)
    if game_id == "weiqi":
        var y: float = minf(arena.end.y+8,get_viewport_rect().size.y-103)
        _button(Rect2(rect.get_center().x-85,y,170,48),"虚手 / 跳过","pass",P.BLUE)
func _player_status(seat: int) -> String:
    var player: int = seat+1
    var active: bool = model.turn == player
    var label: String = ("X方" if player == 1 else "O方") if game_id == "tictactoe" else ("黑方" if player == 1 else "白方")
    if ai_mode and player == 2: label += " · 电脑"
    var status: String = "等待" if not active else ("电脑思考中" if ai_mode and player == 2 else "轮到你")
    if game_id in ["reversi","weiqi"]: label += " · %d子"%model.board.count(player)
    if game_id == "weiqi": label += " · 提%d"%model.captures[seat]
    return label+" · "+status
func _game_down(at: Vector2, _finger: int) -> void:
    if ai_mode and model.turn == 2: return
    if not rect.has_point(at): return
    var p := Vector2i((at-rect.position)/cell)
    _place(p.y*model.n+p.x)
func _place(index: int) -> bool:
    var result: Dictionary = model.place(index)
    if result.is_empty():
        message = "此处不能落子"
        _feedback("lose")
        return false
    _feedback("eat" if not result.changed.is_empty() else "move")
    ai_wait = 0.35
    if model.winner >= 0:
        _end(("平局" if model.winner == 0 else (("X方" if model.winner == 1 else "O方") if game_id == "tictactoe" else ("黑方" if model.winner == 1 else "白方"))+"获胜")+" · "+model.reason)
    else: message = "对方无子可下，自动跳过" if model.passes > 0 else "点击空位落子"
    queue_redraw()
    return true
func _game_action(action: String) -> void:
    if action == "pass" and not (ai_mode and model.turn == 2):
        if model.pass_turn():
            _feedback("click")
            if model.winner >= 0: _end(("平局" if model.winner == 0 else (("X方" if model.winner == 1 else "O方") if game_id == "tictactoe" else ("黑方" if model.winner == 1 else "白方"))+"获胜")+" · "+model.reason)
func _game_process(delta: float) -> bool:
    if ai_mode and model.turn == 2 and model.winner < 0:
        ai_wait -= delta
        if ai_wait <= 0:
            var index: int = model.best_move()
            if index >= 0: _place(index)
            return true
    return false
