extends RefCounted
var cols: int = 16
var rows: int = 24
var dual: bool = false
var bodies: Array = [[],[]]
var dirs: Array = [Vector2i.RIGHT,Vector2i.LEFT]
var pending: Array = [Vector2i.RIGHT,Vector2i.LEFT]
var alive: Array = [true,false]
var foods: Array = []
var score: int = 0
var done: bool = false
var result: String = ""
var accumulator: float = 0
var speed: float = 5.5
var rng := RandomNumberGenerator.new()
func reset(two: bool = false) -> void:
    dual = two
    bodies = [[Vector2i(4,18),Vector2i(3,18),Vector2i(2,18)],[Vector2i(11,5),Vector2i(12,5),Vector2i(13,5)]] if two else [[Vector2i(6,12),Vector2i(5,12),Vector2i(4,12)],[]]
    dirs = [Vector2i.RIGHT,Vector2i.LEFT]; pending = dirs.duplicate()
    alive = [true,two]; foods = []; score = 0; done = false
    result = ""; accumulator = 0; speed = 5.5 if two else 6
    refill()
func set_direction(owner: int, value: Vector2i) -> bool:
    if done or not alive[owner] or value == -dirs[owner]: return false
    pending[owner] = value
    return true
func refill() -> void:
    var free: Array = []
    for y in range(rows):
        for x in range(cols):
            var p := Vector2i(x,y)
            if p not in bodies[0] and p not in bodies[1] and p not in foods: free.append(p)
    while foods.size() < (4 if dual else 1) and not free.is_empty():
        var slot: int = rng.randi_range(0,free.size()-1)
        foods.append(free[slot]); free.remove_at(slot)
    if free.is_empty() and foods.is_empty():
        done = true; result = "棋盘已填满，挑战成功"
func step() -> void:
    if done: return
    var next: Array = [Vector2i.ZERO,Vector2i.ZERO]
    var grows: Array = [false,false]
    var died: Array = [false,false]
    for owner in range(2):
        if not alive[owner]: continue
        dirs[owner] = pending[owner]
        next[owner] = bodies[owner][0]+dirs[owner]
        grows[owner] = next[owner] in foods
        var p: Vector2i = next[owner]
        if p.x < 0 or p.x >= cols or p.y < 0 or p.y >= rows: died[owner] = true
    for owner in range(2):
        if not alive[owner]: continue
        for other in range(2):
            var occupied: Array = bodies[other].duplicate()
            if alive[other] and not grows[other] and not occupied.is_empty(): occupied.pop_back()
            if next[owner] in occupied: died[owner] = true
        if dual and alive[1-owner] and next[owner] == next[1-owner]: died[owner] = true
    for owner in range(2):
        if not alive[owner]: continue
        if died[owner]: alive[owner] = false; continue
        bodies[owner].push_front(next[owner])
        if grows[owner]:
            foods.erase(next[owner]); score += 10
            if not dual: speed = minf(13,6+int(score/50)*0.6)
        else: bodies[owner].pop_back()
    if not alive.has(true):
        done = true
        result = "撞到边界或蛇身 · 长度%d"%bodies[0].size()
        if dual:
            result = "长度 %d : %d · %s"%[bodies[0].size(),bodies[1].size(),"平局" if bodies[0].size() == bodies[1].size() else ("玩家1获胜" if bodies[0].size() > bodies[1].size() else "玩家2获胜")]
    else: refill()
func tick(delta: float) -> bool:
    if done: return false
    accumulator += delta*speed
    var changed: bool = false
    while accumulator >= 1 and not done:
        accumulator -= 1; step(); changed = true
    return changed
