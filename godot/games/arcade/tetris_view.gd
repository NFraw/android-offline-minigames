extends "res://games/suite/suite_game.gd"
const Model = preload("res://games/arcade/tetris_model.gd")
var model = Model.new()
var rect := Rect2()
var cell: float = 0
var touches: Dictionary = {}
func _rules_text() -> String:
    return "左右移动、旋转方块，填满一行消除。\n旋转遇墙会尝试踢墙；每消10行速度提升。\n点方块旋转，拖动移动，也可用下方按钮。\n向下滑很远或点落到底可立即落下。"
func _new_game() -> void:
    model.reset()
    touches.clear()
    _game_layout()
func _game_layout() -> void:
    cell = minf((arena.size.x-100)/10,(arena.size.y-100)/20)
    rect = Rect2(arena.get_center().x-5*cell,arena.position.y,cell*10,cell*20)
func _color(value: int) -> Color:
    return [P.BLUE,P.WOOD_FRAME,P.RED,P.GREEN,P.BLUE.lightened(0.25),P.RED.lightened(0.3),P.WOOD_LO][(value-1)%7]
func _block(p: Vector2i, value: int, outline: bool = false) -> void:
    if p.y < 0: return
    var r := Rect2(rect.position+Vector2(p)*cell,Vector2.ONE*cell).grow(-1)
    if outline: draw_rect(r,_color(value),false,1.5)
    else: Polish.tile(self,r,_color(value),0)
func _draw_play() -> void:
    _box(rect.grow(8),Color("58658b"),12)
    draw_rect(rect,Color("242c43"))
    for x in range(11): draw_line(rect.position+Vector2(x*cell,0),rect.position+Vector2(x*cell,rect.size.y),Color("39455d"),1)
    for y in range(21): draw_line(rect.position+Vector2(0,y*cell),rect.position+Vector2(rect.size.x,y*cell),Color("39455d"),1)
    for i in range(200):
        if model.board[i] != 0: _block(Vector2i(i%10,i/10),model.board[i])
    if not model.done:
        var ghost: Vector2i = model.position
        while model.valid(model.shape,ghost+Vector2i.DOWN): ghost += Vector2i.DOWN
        for p in model.shape: _block(ghost+p,model.kind+1,true)
        for p in model.shape: _block(model.position+p,model.kind+1)
    var preview := Vector2(rect.end.x+12,rect.position.y+42)
    _text("下一块",preview+Vector2(35,-20),16,P.MUTED)
    for p in Model.SHAPES[model.next_kind]: Polish.tile(self,Rect2(preview+Vector2(p)*17,Vector2(16,16)),_color(model.next_kind+1),1)
    for i in range(5):
        var width: float = arena.size.x/5
        _button(Rect2(arena.position.x+i*width+2,rect.end.y+18,width-4,54),["←","旋转","→","↓","落到底"][i],"tetris%d"%i,P.BLUE)
func _game_action(action: String) -> void:
    if not action.begins_with("tetris"): return
    match int(action.trim_prefix("tetris")):
        0: model.shift(Vector2i.LEFT)
        1: model.rotate()
        2: model.shift(Vector2i.RIGHT)
        3:
            if not model.shift(Vector2i.DOWN): model.lock()
        4: model.hard_drop()
    _sync()
func _sync() -> void:
    if score != model.score: _set_score(model.score); _feedback("eat")
    message = "已消%d行 · 等级%d"%[model.lines,int(model.lines/10)+1]
    if model.done: _end("方块堆到顶部 · "+message,false)
func _game_down(at: Vector2, finger: int) -> void:
    touches[finger] = {"start":at,"last":at}
func _game_drag(at: Vector2, finger: int) -> void:
    if not touches.has(finger): return
    var delta: Vector2 = at-touches[finger].last
    if absf(delta.x) >= cell: model.shift(Vector2i(signi(int(delta.x)),0)); touches[finger].last = at
    elif delta.y >= cell: model.shift(Vector2i.DOWN); touches[finger].last = at
    _sync()
func _game_up(at: Vector2, finger: int) -> void:
    if not touches.has(finger): return
    var delta: Vector2 = at-touches[finger].start
    if delta.length() < 15: model.rotate()
    elif delta.y > cell*4: model.hard_drop()
    touches.erase(finger)
    _sync()
func _game_key(key: int) -> void:
    var actions: Dictionary = {KEY_LEFT:"tetris0",KEY_UP:"tetris1",KEY_RIGHT:"tetris2",KEY_DOWN:"tetris3",KEY_SPACE:"tetris4"}
    if actions.has(key): _game_action(actions[key])
func _game_process(delta: float) -> bool:
    var changed: bool = model.tick(delta)
    _sync()
    return changed
func _clear_controls() -> void:
    touches.clear()
