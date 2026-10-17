extends "res://games/suite/suite_game.gd"
const Model = preload("res://games/arcade/timed_model.gd")
var model = Model.new()
var rect := Rect2()
var cell: float = 0
func _rules_text() -> String:
    if game_id == "whack": return "30秒内点击冒出的地鼠，每次命中加10分。\n前10秒1只，中间10秒最多2只，最后10秒最多3只同时出现。\n误点空洞扣2分，支持多根手指快速交替点按。"
    return "按从下往上的顺序点击每行黑块。\n点到白块立即结束；必须按从下往上的顺序。\n漏掉滑出底部的黑块才失败，速度逐步增加。"
func _new_game() -> void:
    model.reset(game_id)
    _game_layout()
    message = "点击最下方黑块开始" if game_id == "piano" else "30秒挑战"
func _game_layout() -> void:
    rect = _square(3,3) if game_id == "whack" else arena
    cell = rect.size.x/(3 if game_id == "whack" else 4)
func _draw_play() -> void:
    _box(rect.grow(5),P.WOOD_LO)
    if game_id == "whack":
        Polish.surface(self,rect,"garden")
        for index in range(9):
            var center: Vector2 = rect.position+(Vector2(index%3,index/3)+Vector2.ONE*0.5)*cell
            draw_set_transform(center+Vector2(0,cell*0.18),0,Vector2(1,0.42))
            draw_circle(Vector2.ZERO,cell*0.38,Color("8e704d"))
            draw_circle(Vector2(0,-4),cell*0.32,Color("5c4935"))
            draw_set_transform(Vector2.ZERO)
            if model.moles.has(index):
                var emerge: float = minf(smoothstep(0,0.18,model.moles[index].age),smoothstep(0,0.18,model.moles[index].remaining))
                var head: Vector2 = center+Vector2(0,cell*(0.3-emerge*0.36))
                draw_set_transform(head,0,Vector2(1,maxf(0.03,emerge)))
                Polish.mole(self,Vector2.ZERO,cell*0.36)
                draw_set_transform(Vector2.ZERO)
            draw_set_transform(center+Vector2(0,cell*0.18),0,Vector2(1,0.42))
            draw_arc(Vector2.ZERO,cell*0.37,0,PI,24,Color("a48559"),cell*0.09,true)
            draw_set_transform(Vector2.ZERO)
            if model.flash > 0 and index == model.flash_index:
                var t: float = 1-model.flash/0.18
                draw_arc(center,cell*(0.32+t*0.2),0,TAU,32,Color("e6c66a",1-t),4,true)
                _text("啪",center-Vector2(0,cell*(0.32+t*0.12)),int(cell*0.21),Color("8c573c"))
    else:
        draw_rect(rect,Color("fffefa"))
        var row_h: float = rect.size.y/6.5
        for row in model.rows:
            var r := Rect2(rect.position+Vector2(row.column*cell,row.y*row_h),Vector2(cell,row_h))
            if not r.intersects(rect): continue
            r = r.intersection(rect)
            if row.hit:
                draw_rect(r,Color("d3e5de"))
                draw_line(r.position+Vector2(5,r.size.y-2),r.end-Vector2(5,2),Color("78aa97"),2)
            else:
                Polish.tile(self,r.grow(-1),Color("252b39"),0)
                var center: Vector2 = r.get_center()
                draw_circle(center+Vector2(-3,5),5,Color("d2d7e2"))
                draw_line(center+Vector2(1,5),center-Vector2(-1,15),Color("d2d7e2"),2)
        for i in range(5): draw_line(rect.position+Vector2(i*cell,0),rect.position+Vector2(i*cell,rect.size.y),Color("b8bbc8"),2)
func _game_down(at: Vector2, _finger: int) -> void:
    if not rect.has_point(at): return
    var ok: bool = false
    if game_id == "whack":
        var p := Vector2i((at-rect.position)/cell)
        ok = model.hit_hole(p.y*3+p.x)
    else:
        var local_y: float = (at.y-rect.position.y)/(rect.size.y/6.5)
        var index: int = -1
        for i in range(model.rows.size()):
            if local_y >= model.rows[i].y and local_y < model.rows[i].y+1: index = i; break
        ok = model.hit_key(index,int((at.x-rect.position.x)/cell))
    if not model.done:
        _feedback(("piano%d"%(model.score%8) if game_id == "piano" else "hit_heavy") if ok else "ui_error",ok and game_id == "whack")
    _sync()
func _sync() -> void:
    if score != model.score: _set_score(model.score)
    if game_id == "whack": message = "剩余%d秒 · %s"%[ceili(model.time_left),"看准再点" if model.time_left > 20 else ("双只接力" if model.time_left > 10 else "三只连击！")]
    else: message = "速度 %.2f · 按从下往上顺序"%model.speed
    if model.done: _end(model.result,game_id == "whack")
func _game_process(delta: float) -> bool:
    var changed: bool = model.tick(delta)
    _sync()
    return changed
