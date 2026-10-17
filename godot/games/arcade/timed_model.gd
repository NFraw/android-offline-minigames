extends RefCounted
var kind: String = "whack"
var rng := RandomNumberGenerator.new()
var score: int = 0
var done: bool = false
var result: String = ""
var time_left: float = 30
var moles: Dictionary = {}
var spawn_time: float = 0.5
var flash: float = 0
var flash_index: int = -1
var rows: Array = []
var speed: float = 1
var started: bool = false
func reset(id: String) -> void:
    kind = id; score = 0; done = false; result = ""
    time_left = 30; moles.clear(); spawn_time = 0.5
    flash = 0; flash_index = -1; speed = 1; started = false
    rows = []
    if kind == "piano":
        for i in range(7): rows.append({"column":rng.randi_range(0,3),"y":float(i-1),"hit":false})
func hit_hole(index: int) -> bool:
    if done or index < 0 or index > 8: return false
    flash_index = index; flash = 0.18
    if moles.has(index):
        score += 10; moles.erase(index); return true
    score = maxi(0,score-2)
    return false
func lowest() -> int:
    var index: int = -1
    for i in range(rows.size()):
        if not rows[i].hit and (index < 0 or rows[i].y > rows[index].y): index = i
    return index
func hit_key(index: int, column: int) -> bool:
    if done: return false
    started = true
    if index < 0 or index >= rows.size() or rows[index].hit or rows[index].column != column:
        done = true; result = "点到白块了 · 得分%d"%score; return false
    if index != lowest(): return false
    rows[index].hit = true; score += 1; speed = minf(3,speed+0.05)
    return true
func tick(delta: float) -> bool:
    if done: return false
    if kind == "whack":
        time_left = maxf(0,time_left-delta)
        flash = maxf(0,flash-delta)
        if time_left <= 0:
            done = true; result = "时间到 · 得分%d"%score; moles.clear(); return true
        for hole in moles.keys():
            moles[hole].age += delta
            moles[hole].remaining -= delta
            if moles[hole].remaining <= 0: moles.erase(hole)
        spawn_time -= delta
        if spawn_time <= 0:
            var phase: int = 0 if time_left > 20 else (1 if time_left > 10 else 2)
            var empty: Array = []
            for hole in range(9):
                if not moles.has(hole): empty.append(hole)
            # 随机抽取空洞，已有地鼠不被覆盖；中后期同一波可同时补足2/3只。
            for i in range(maxi(0,phase+1-moles.size())):
                var at: int = rng.randi_range(0,empty.size()-1)
                var hole: int = empty.pop_at(at)
                moles[hole] = {"age":0.0,"remaining":rng.randf_range([1.2,1.0,0.85][phase],[1.5,1.25,1.1][phase])}
            spawn_time = [0.5,0.38,0.25][phase]
        return true
    if not started: return false
    for row in rows: row.y += delta*speed*0.9
    for i in range(rows.size()-1,-1,-1):
        if rows[i].y >= 6.5:
            if not rows[i].hit: done = true; result = "漏掉了黑块 · 得分%d"%score
            rows.remove_at(i)
    if not done:
        var top: float = rows[0].y if not rows.is_empty() else 0
        while top >= 0:
            top -= 1
            rows.push_front({"column":rng.randi_range(0,3),"y":top,"hit":false})
    return true
