extends "res://games/suite/suite_game.gd"
const Model = preload("res://games/physics/rally_model.gd")
var model = Model.new()
var rect := Rect2()
var world_scale: float = 1
var touches: Dictionary = {}
var trails: Array = []
func _rules_text() -> String:
    if game_id == "pong": return "双方各自在自己的半屏拖动球拍。\n球拍碰撞后球速加快，先到5分获胜。\n支持两人同时拖动，球拍不会越过边界。"
    if game_id == "fruit": return "滑动切水果得分，切中炸弹或漏水果扣一条命。\n共3条命；每30秒增加出现速度与数量。\n水果不会飞出左右和顶部边界，刀痕会渐渐消失。"
    return "拖动下方挡板反弹小球，清完所有砖块获胜。\n漏球即失败，方块标记代表剩余耐久。\n碰撞使用圆与矩形精确检测，避免角落误反弹。"
func _new_game() -> void:
    model.reset(game_id)
    touches.clear(); trails.clear()
    _game_layout()
func _game_layout() -> void:
    if game_id == "pong":
        var size: Vector2 = get_viewport_rect().size
        arena = Rect2(28,155,size.x-56,size.y-235)
    world_scale = minf(arena.size.x/model.size.x,arena.size.y/model.size.y)
    rect = Rect2(arena.get_center()-model.size*world_scale/2,model.size*world_scale)
    if game_id == "pong": rect = arena
func _screen(p: Vector2) -> Vector2:
    return rect.position+p*Vector2(rect.size.x/model.size.x,rect.size.y/model.size.y)
func _world(p: Vector2) -> Vector2:
    return (p-rect.position)/Vector2(rect.size.x/model.size.x,rect.size.y/model.size.y)
func _draw_play() -> void:
    _board_surface(rect.grow(8))
    draw_rect(rect,Color("36725f") if game_id == "pong" else (Color("f5e4c7") if game_id == "fruit" else Color("e2e9f0")))
    if game_id == "fruit": Polish.surface(self,rect,"kitchen")
    elif game_id == "pong": Polish.surface(self,rect,"felt")
    if game_id == "pong":
        draw_rect(rect.grow(-12),Color("d5e7da"),false,3)
        draw_line(Vector2(rect.get_center().x,rect.position.y+12),Vector2(rect.get_center().x,rect.end.y-12),Color("b3d4bf"),2)
        draw_arc(rect.get_center(),rect.size.x*0.18,0,TAU,50,Color("c9e0d2"),2,true)
    if game_id == "fruit":
        var colors: Array = [P.GREEN,P.WOOD_FRAME,P.RED,P.WOOD_LO,P.BLUE,P.GREEN,P.RED,P.RED]
        for fruit in model.fruits:
            if fruit.p.y > model.size.y+fruit.radius: continue
            var at: Vector2 = _screen(fruit.p)
            Polish.fruit(self,at,fruit.radius*world_scale,fruit.type,fruit.bomb)
        for particle in model.particles:
            var color: Color = P.TEXT if particle.bomb else colors[particle.type]
            color.a = particle.life/0.6
            draw_circle(_screen(particle.p),15*world_scale,color)
        for trail in trails:
            var color: Color = P.BLUE
            color.a = maxf(0,1-trail.age/0.32)
            draw_line(_screen(trail.from),_screen(trail.to),color,4*world_scale,true)
    else:
        if game_id == "pong": draw_dashed_line(_screen(Vector2(0,700)),_screen(Vector2(1000,700)),P.WOOD_LINE,2,10)
        else:
            for brick in model.bricks:
                if brick.hp <= 0: continue
                var r := Rect2(_screen(brick.rect.position),brick.rect.size*world_scale)
                Polish.tile(self,r,[P.GREEN,P.BLUE,P.RED][brick.hp-1],3)
                if game_id == "breakout": _text(str(brick.hp),r.get_center(),int(22*world_scale),P.PIECE_HI)
        for owner in range(2 if game_id == "pong" else 1):
            var r: Rect2 = model.paddle(owner)
            Polish.tile(self,Rect2(_screen(r.position),r.size*Vector2(rect.size.x/model.size.x,rect.size.y/model.size.y)),P.RED if owner == 0 else P.BLUE,3)
        Polish.orb(self,_screen(model.ball),model.radius*world_scale,Color("fff8e6") if game_id == "pong" else Color("6c9eaf"))
func _player_status(seat: int) -> String:
    return "玩家%d · %d分 · 先到5分胜"%[seat+1,model.scores[seat]]
func _game_down(at: Vector2, finger: int) -> void:
    if not rect.has_point(at): return
    var world: Vector2 = _world(at)
    var owner: int = 1 if game_id == "pong" and world.y < model.size.y/2 else 0
    touches[finger] = {"owner":owner,"at":world}
    if game_id != "fruit": model.set_paddle(owner,world.x)
func _game_drag(at: Vector2, finger: int) -> void:
    if not touches.has(finger): return
    var world: Vector2 = _world(at)
    if game_id == "fruit":
        var before: int = model.lives
        var hits: int = model.cut(touches[finger].at,world)
        if trails.size() >= 36: trails.pop_front()
        trails.append({"from":touches[finger].at,"to":world,"age":0.0})
        if hits > 0: _feedback("lose" if model.lives < before else "eat",model.lives < before)
        _sync()
    else: model.set_paddle(touches[finger].owner,world.x)
    touches[finger].at = world
func _game_up(at: Vector2, finger: int) -> void:
    _game_drag(at,finger)
    touches.erase(finger)
func _sync() -> void:
    if score != model.score: _set_score(model.score)
    if game_id == "fruit": message = "生命%d/3 · 第%d阶段"%[maxi(0,model.lives),int(model.elapsed/30)+1]
    elif game_id != "pong": message = "拖动挡板，别让球漏出底部"
    if model.done: _end(model.result,model.won or game_id == "pong")
func _game_process(delta: float) -> bool:
    var before: int = model.lives
    var old_velocity: Vector2 = model.velocity
    var old_score: int = model.score
    var changed: bool = model.tick(delta)
    if game_id != "fruit":
        if model.score > old_score: _feedback("hit_heavy")
        elif model.velocity != old_velocity and model.serve_time <= 0 and not model.done: _feedback("hit_soft" if game_id == "pong" else "hit_light")
    if model.lives < before: _feedback("lose",true)
    var had_trails: bool = not trails.is_empty()
    for trail in trails: trail.age += delta
    trails = trails.filter(func(trail): return trail.age < 0.32)
    _sync()
    return changed or had_trails
func _clear_controls() -> void:
    touches.clear()
