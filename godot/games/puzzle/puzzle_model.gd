extends RefCounted
var memory_size: int = 4
var kind: String = "game2048"
var cols: int = 4
var rows: int = 4
var cells := PackedInt32Array()
var revealed: Array = []
var flagged: Array = []
var matched: Array = []
var score: int = 0
var moves: int = 0
var done: bool = false
var result: String = ""
var turn: int = 0
var scores: Array = [0,0]
var first: int = -1
var second: int = -1
var lock_time: float = 0
var elapsed: float = 0
var placed: bool = false
var level: int = 0
const LEVELS = [4,6,8]
var rng := RandomNumberGenerator.new()
var sequence: Array = []
var showing: bool = true
var show_index: int = 0
var lit: int = -1
var show_time: float = 0
var input_index: int = 0
var flash: float = 0
func reset(id: String) -> void:
    kind = id
    cols = 9 if id == "mines" else (2 if id == "simon" else 4)
    rows = 12 if id == "mines" else cols
    cells.resize(cols*rows); cells.fill(0)
    revealed.resize(cells.size()); revealed.fill(false)
    flagged.resize(cells.size()); flagged.fill(false)
    matched.resize(cells.size()); matched.fill(false)
    score = 0; moves = 0; done = false; result = ""
    turn = 0; scores = [0,0]; first = -1; second = -1
    lock_time = 0; elapsed = 0; placed = false; level = 0
    if id == "game2048": spawn(); spawn()
    elif id == "sliding":
        for i in range(15): cells[i] = i+1
        var blank: int = 15
        var previous: int = -1
        for step in range(260):
            var choices: Array = neighbors(blank,false)
            choices.erase(previous)
            var next: int = choices[rng.randi_range(0,choices.size()-1)]
            cells[blank] = cells[next]; cells[next] = 0
            previous = blank; blank = next
        if solved_sliding(): cells[14] = 0; cells[15] = 15
    elif id in ["memory","memory_duel"]: deal()
    elif id == "simon":
        sequence = [rng.randi_range(0,3)]
        input_index = 0
        start_show()
func neighbors(index: int, diagonals: bool) -> Array:
    var out: Array = []
    var p := Vector2i(index%cols,index/cols)
    for y in range(-1,2):
        for x in range(-1,2):
            if x == 0 and y == 0: continue
            if not diagonals and absi(x)+absi(y) != 1: continue
            var at: Vector2i = p+Vector2i(x,y)
            if at.x >= 0 and at.x < cols and at.y >= 0 and at.y < rows: out.append(at.y*cols+at.x)
    return out
func spawn() -> void:
    var free: Array = []
    for i in range(cells.size()):
        if cells[i] == 0: free.append(i)
    if not free.is_empty(): cells[free[rng.randi_range(0,free.size()-1)]] = 4 if rng.randf() < 0.1 else 2
func slide(direction: Vector2i, add_tile: bool = true) -> bool:
    if done or kind != "game2048" or direction not in [Vector2i.LEFT,Vector2i.RIGHT,Vector2i.UP,Vector2i.DOWN]: return false
    var before := cells.duplicate()
    for line in range(4):
        var ids: Array = []
        for step in range(4):
            var x: int = (3-step if direction.x > 0 else step) if direction.x != 0 else line
            var y: int = (3-step if direction.y > 0 else step) if direction.y != 0 else line
            ids.append(y*4+x)
        var values: Array = []
        for i in ids:
            if cells[i] != 0: values.append(cells[i])
        var merged: Array = []
        var at: int = 0
        while at < values.size():
            if at+1 < values.size() and values[at] == values[at+1]:
                merged.append(values[at]*2); score += values[at]*2; at += 2
            else: merged.append(values[at]); at += 1
        for i in range(4): cells[ids[i]] = merged[i] if i < merged.size() else 0
    var changed: bool = cells != before
    if changed:
        moves += 1
        if add_tile: spawn()
        if cells.has(2048): done = true; result = "合成2048，挑战成功！"
    if not cells.has(0):
        var possible: bool = false
        for i in range(16):
            for next in neighbors(i,false):
                if cells[i] == cells[next]: possible = true
        if not possible: done = true; result = "无空格且无法合并"
    return changed
func solved_sliding() -> bool:
    for i in range(15):
        if cells[i] != i+1: return false
    return cells[15] == 0
func flag(index: int) -> bool:
    if done or revealed[index]: return false
    flagged[index] = not flagged[index]
    return true
func place_mines(safe: int) -> void:
    var choices: Array = []
    var protected: Array = neighbors(safe,true)+[safe]
    for i in range(cells.size()):
        if i not in protected: choices.append(i)
    for count in range(15):
        var slot: int = rng.randi_range(0,choices.size()-1)
        cells[choices[slot]] = -1
        choices.remove_at(slot)
    for i in range(cells.size()):
        if cells[i] == -1: continue
        for next in neighbors(i,true):
            if cells[next] == -1: cells[i] += 1
    placed = true
func reveal(index: int) -> bool:
    if done or revealed[index] or flagged[index]: return false
    if not placed: place_mines(index)
    if cells[index] == -1:
        revealed[index] = true
        score = 0; done = true; result = "踩到地雷 · 用时%d秒"%int(elapsed)
        return true
    var stack: Array = [index]
    while not stack.is_empty():
        var current: int = stack.pop_back()
        if revealed[current] or flagged[current]: continue
        revealed[current] = true
        if cells[current] == 0:
            for next in neighbors(current,true):
                if not revealed[next] and cells[next] >= 0: stack.append(next)
    score = maxi(0,500-int(elapsed)*2)
    if revealed.count(true) == cells.size()-15:
        done = true; result = "排雷成功 · 用时%d秒"%int(elapsed)
    return true
func deal() -> void:
    cols = LEVELS[level] if kind == "memory_duel" else clampi(memory_size-memory_size%2,4,8)
    rows = cols
    var cards: Array = []
    for value in range(cols*rows/2): cards.append(value); cards.append(value)
    for i in range(cards.size()-1,0,-1):
        var j: int = rng.randi_range(0,i)
        var old: int = cards[i]; cards[i] = cards[j]; cards[j] = old
    cells = PackedInt32Array(cards)
    matched.resize(cells.size()); matched.fill(false)
    first = -1; second = -1; lock_time = 0
func tap(index: int) -> bool:
    if done or index < 0 or index >= cells.size(): return false
    if kind == "sliding":
        var blank: int = cells.find(0)
        if index not in neighbors(blank,false): return false
        cells[blank] = cells[index]; cells[index] = 0
        moves += 1; score = maxi(0,500-moves*8)
        if solved_sliding(): done = true; result = "拼图完成 · %d步"%moves
        return true
    if kind == "mines": return reveal(index)
    if kind in ["memory","memory_duel"]:
        if lock_time > 0 or matched[index] or index == first: return false
        if first < 0: first = index; return true
        second = index; moves += 1
        if cells[first] == cells[second]:
            matched[first] = true; matched[second] = true
            scores[turn] += 1; score += 10
            first = -1; second = -1
            if matched.count(true) == cells.size():
                if kind == "memory_duel" and level < LEVELS.size()-1:
                    level += 1; deal()
                else:
                    done = true
                    if kind == "memory": score = maxi(0,800-moves*15); result = "全部配对成功 · %d步"%moves
                    else: result = "玩家1 %d : %d 玩家2 · %s"%[scores[0],scores[1],"平局" if scores[0] == scores[1] else ("玩家1获胜" if scores[0] > scores[1] else "玩家2获胜")]
        else: lock_time = 0.8
        return true
    if kind == "simon":
        if showing: return false
        lit = index; flash = 0.2
        if index != sequence[input_index]: done = true; result = "记错了 · 完成%d轮"%(sequence.size()-1); return true
        input_index += 1
        if input_index == sequence.size():
            score += 10; sequence.append(rng.randi_range(0,3)); input_index = 0
            start_show()
        return true
    return false
func start_show() -> void:
    showing = true; show_index = -1; lit = -1; show_time = 1.25; flash = 0
func tick(delta: float) -> bool:
    if done: return false
    var changed: bool = false
    if kind == "mines":
        var before: int = int(elapsed)
        elapsed += delta
        score = maxi(0,500-int(elapsed)*2)
        changed = before != int(elapsed)
    if lock_time > 0:
        lock_time -= delta
        changed = true
        if lock_time <= 0:
            first = -1; second = -1
            if kind == "memory_duel": turn = 1-turn
    if kind == "simon":
        if showing:
            show_time -= delta
            if show_time <= 0:
                if lit >= 0:
                    lit = -1
                    var repeated: bool = show_index+1 < sequence.size() and sequence[show_index] == sequence[show_index+1]
                    show_time = 0.65 if repeated else 0.45
                else:
                    show_index += 1
                    if show_index >= sequence.size(): showing = false
                    else: lit = sequence[show_index]; show_time = 0.9
                changed = true
        elif flash > 0:
            flash -= delta
            if flash <= 0: lit = -1
            changed = true
    return changed
