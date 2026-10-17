extends "res://games/suite/suite_game.gd"
const Model = preload("res://games/arcade/snake_model.gd")
const VECTORS = [Vector2i.LEFT,Vector2i.UP,Vector2i.DOWN,Vector2i.RIGHT]
var model = Model.new()
var rect := Rect2()
var cell: float = 0
var touches: Dictionary = {}
func _rules_text() -> String:
    if game_id == "snakebattle": return "双方各自准备后开局；上下各有四个方向按钮。\n不能反向掉头；撞墙、撞自己或对方蛇身会死亡。\n一条蛇死亡后另一条继续，都死后按长度决胜。"
    return "滑动或点击方向键控制蛇，吃食物变长。\n不能反向掉头，撞墙或身体结束。\n每50分加速一次，支持键盘方向键。"
func _new_game() -> void:
    model.reset(game_id == "snakebattle")
    touches.clear()
    _game_layout()
func _game_layout() -> void:
    cell = minf(arena.size.x/model.cols,(arena.size.y-160)/model.rows)
    rect = Rect2(arena.get_center().x-model.cols*cell/2,arena.get_center().y-model.rows*cell/2,model.cols*cell,model.rows*cell)
func _draw_play() -> void:
    _box(rect.grow(9),Color("4d7058"),14)
    Polish.checker(self,rect,model.cols,model.rows)
    for food in model.foods: Polish.fruit(self,rect.position+(Vector2(food)+Vector2.ONE*0.5)*cell,cell*0.40,2)
    for owner in range(2 if model.dual else 1):
        var color: Color = P.RED if owner == 0 else P.BLUE
        if not model.alive[owner]: color = color.lightened(0.55)
        for i in range(model.bodies[owner].size()):
            var p: Vector2i = model.bodies[owner][i]
            var r := Rect2(rect.position+Vector2(p)*cell,Vector2.ONE*cell).grow(-1)
            Polish.tile(self,r,color.lightened(0.12 if i == 0 else 0),0)
            if i == 0:
                var direction := Vector2(model.dirs[owner])
                var side := Vector2(-direction.y,direction.x)
                for sign in [-1,1]:
                    var eye: Vector2 = r.get_center()+direction*cell*0.19+side*sign*cell*0.18
                    draw_circle(eye,cell*0.105,P.PIECE_HI)
                    draw_circle(eye+direction*cell*0.035,cell*0.052,P.TEXT)
    for owner in range(2 if model.dual else 1):
        var y: float = rect.position.y-88 if owner == 1 else rect.end.y+18
        var width: float = minf(110,(arena.size.x-18)/4)
        for direction in range(4):
            var r := Rect2(arena.get_center().x-width*2+direction*width+3,y,width-8,70)
            var tint: Color = Color("4b8298") if owner == 1 else Color("67854c")
            _box(Rect2(r.position+Vector2(0,4),r.size),tint.darkened(0.25),16)
            _box(r,tint,16)
            var d := Vector2(VECTORS[direction])
            if owner == 1: d = -d
            var center: Vector2 = r.get_center()
            draw_line(center-d*19,center+d*18,P.PIECE_HI,6,true)
            var side := Vector2(-d.y,d.x)
            draw_line(center+d*18,center+d*4+side*13,P.PIECE_HI,6,true)
            draw_line(center+d*18,center+d*4-side*13,P.PIECE_HI,6,true)
            buttons.append({"rect":r,"action":"snake%d_%d"%[owner,direction]})
func _player_status(seat: int) -> String:
    return "玩家%d · 长度%d · %s"%[seat+1,model.bodies[seat].size(),"存活" if model.alive[seat] else "已出局，等待对方"]
func _game_action(action: String) -> void:
    if action.begins_with("snake"):
        var parts: PackedStringArray = action.trim_prefix("snake").split("_")
        var owner: int = int(parts[0])
        var direction: Vector2i = VECTORS[int(parts[1])]
        if owner == 1: direction = -direction
        model.set_direction(owner,direction)
func _game_down(at: Vector2, finger: int) -> void:
    var owner: int = 1 if model.dual and at.y < get_viewport_rect().size.y/2 else 0
    touches[finger] = {"owner":owner,"at":at}
func _game_drag(at: Vector2, finger: int) -> void:
    if not touches.has(finger): return
    var delta: Vector2 = at-touches[finger].at
    if delta.length() < 24: return
    var direction := Vector2i(signi(int(delta.x)),0) if absf(delta.x) > absf(delta.y) else Vector2i(0,signi(int(delta.y)))
    model.set_direction(touches[finger].owner,direction)
    touches[finger].at = at
func _game_up(at: Vector2, finger: int) -> void:
    _game_drag(at,finger)
    touches.erase(finger)
func _game_key(key: int) -> void:
    var map: Dictionary = {KEY_LEFT:Vector2i.LEFT,KEY_RIGHT:Vector2i.RIGHT,KEY_UP:Vector2i.UP,KEY_DOWN:Vector2i.DOWN}
    if map.has(key): model.set_direction(0,map[key])
func _game_process(delta: float) -> bool:
    var before: int = model.score
    var old_alive: Array = model.alive.duplicate()
    var changed: bool = model.tick(delta)
    if model.score != before: _set_score(model.score); _feedback("eat")
    if model.alive != old_alive: _feedback("lose",true)
    if model.done: _end(model.result,model.dual or model.alive[0])
    return changed
func _clear_controls() -> void:
    touches.clear()
