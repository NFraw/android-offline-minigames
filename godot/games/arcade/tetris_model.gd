extends RefCounted
const SHAPES = [
    [Vector2i(0,1),Vector2i(1,1),Vector2i(2,1),Vector2i(3,1)],
    [Vector2i(1,0),Vector2i(2,0),Vector2i(1,1),Vector2i(2,1)],
    [Vector2i(1,0),Vector2i(0,1),Vector2i(1,1),Vector2i(2,1)],
    [Vector2i(1,0),Vector2i(2,0),Vector2i(0,1),Vector2i(1,1)],
    [Vector2i(0,0),Vector2i(1,0),Vector2i(1,1),Vector2i(2,1)],
    [Vector2i(0,0),Vector2i(0,1),Vector2i(1,1),Vector2i(2,1)],
    [Vector2i(2,0),Vector2i(0,1),Vector2i(1,1),Vector2i(2,1)]
]
var board := PackedInt32Array()
var shape: Array = []
var position := Vector2i(3,0)
var kind: int = 0
var next_kind: int = 0
var bag: Array = []
var score: int = 0
var lines: int = 0
var done: bool = false
var timer: float = 0
var rng := RandomNumberGenerator.new()
func draw_kind() -> int:
    if bag.is_empty():
        bag = [0,1,2,3,4,5,6]
        for i in range(6,0,-1):
            var j: int = rng.randi_range(0,i)
            var old: int = bag[i]; bag[i] = bag[j]; bag[j] = old
    return bag.pop_back()
func reset() -> void:
    board.resize(200); board.fill(0)
    bag.clear(); score = 0; lines = 0; done = false; timer = 0
    next_kind = draw_kind(); spawn()
func spawn() -> void:
    kind = next_kind; next_kind = draw_kind()
    shape = SHAPES[kind].duplicate()
    position = Vector2i(3,0)
    if not valid(shape,position): done = true
func valid(points: Array, pos: Vector2i) -> bool:
    for part in points:
        var p: Vector2i = pos+part
        if p.x < 0 or p.x >= 10 or p.y >= 20: return false
        if p.y >= 0 and board[p.y*10+p.x] != 0: return false
    return true
func shift(direction: Vector2i) -> bool:
    if done: return false
    if valid(shape,position+direction): position += direction; return true
    return false
func rotate() -> bool:
    if done or kind == 1: return false
    var rotated: Array = []
    var edge: int = 3 if kind == 0 else 2
    for p in shape: rotated.append(Vector2i(edge-p.y,p.x))
    for offset in [Vector2i.ZERO,Vector2i.LEFT,Vector2i.RIGHT,Vector2i(-2,0),Vector2i(2,0),Vector2i.UP,Vector2i(0,-2)]:
        if valid(rotated,position+offset):
            shape = rotated; position += offset; return true
    return false
func lock() -> void:
    if done: return
    for part in shape:
        var p: Vector2i = position+part
        if p.y < 0: done = true; return
        board[p.y*10+p.x] = kind+1
    var kept: Array = []
    var cleared: int = 0
    for y in range(20):
        var full: bool = true
        for x in range(10):
            if board[y*10+x] == 0: full = false
        if full: cleared += 1
        else:
            for x in range(10): kept.append(board[y*10+x])
    if cleared > 0:
        var rebuilt: Array = []
        rebuilt.resize(cleared*10); rebuilt.fill(0)
        rebuilt.append_array(kept); board = PackedInt32Array(rebuilt)
        lines += cleared; score += [0,100,300,500,800][cleared]
    timer = 0; spawn()
func hard_drop() -> void:
    if done: return
    while shift(Vector2i.DOWN): score += 1
    lock()
func tick(delta: float) -> bool:
    if done: return false
    timer += delta
    if timer < maxf(0.08,0.7/pow(1.1,int(lines/10))): return false
    timer = 0
    if not shift(Vector2i.DOWN): lock()
    return true
