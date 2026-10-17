extends RefCounted
const COLS := 7
const ROWS := 9
const NAMES = ["","鼠","猫","狗","狼","豹","虎","狮","象"]
const DIRS = [Vector2i.LEFT,Vector2i.RIGHT,Vector2i.UP,Vector2i.DOWN]
var board := PackedInt32Array()
var turn: int = 0
var winner: int = -1
var reason: String = ""
var competitive: bool = false
var remaining: float = 30
var history: Array = [[],[]]
var last_move: Dictionary = {}
func reset(match_rules: bool = false) -> void:
    board.resize(63)
    board.fill(0)
    for entry in [[0,0,7],[6,0,6],[1,1,3],[5,1,2],[0,2,1],[2,2,5],[4,2,4],[6,2,8]]:
        board[entry[1]*7+entry[0]] = -entry[2]
        board[(8-entry[1])*7+6-entry[0]] = entry[2]
    turn = 0
    winner = -1
    reason = ""
    competitive = match_rules
    remaining = 30
    history = [[],[]]
    last_move = {}
static func side(piece: int) -> int:
    return 0 if piece > 0 else 1
static func point(index: int) -> Vector2i:
    return Vector2i(index%7,index/7)
static func inside(p: Vector2i) -> bool:
    return p.x >= 0 and p.x < 7 and p.y >= 0 and p.y < 9
static func water(index: int) -> bool:
    var p := point(index)
    return p.y >= 3 and p.y <= 5 and p.x in [1,2,4,5]
static func den(index: int) -> int:
    return 1 if index == 3 else (0 if index == 59 else -1)
static func trap(index: int) -> int:
    return 1 if index in [2,4,10] else (0 if index in [58,60,52] else -1)
func strength(index: int) -> int:
    var piece: int = board[index]
    if piece == 0: return 0
    return 0 if trap(index) == 1-side(piece) else absi(piece)
func can_capture(from: int, to: int) -> bool:
    var a: int = board[from]
    var b: int = board[to]
    if a == 0 or b == 0 or side(a) == side(b): return false
    if water(from) != water(to): return false
    var attack: int = strength(from)
    var defend: int = strength(to)
    if defend == 0: return true
    if attack == 0: return false
    if attack == 8 and defend == 1: return false
    if attack == 1 and defend == 8: return true
    return attack >= defend
func raw_moves(from: int) -> Array:
    var result: Array = []
    if from < 0 or from >= 63 or board[from] == 0: return result
    var piece: int = board[from]
    var owner: int = side(piece)
    for direction in DIRS:
        var p: Vector2i = point(from)+direction
        if not inside(p): continue
        var to: int = p.y*7+p.x
        var jumped: bool = false
        if water(to) and absi(piece) != 1:
            if absi(piece) not in [6,7]: continue
            jumped = true
            var blocked: bool = false
            while inside(p) and water(p.y*7+p.x):
                var crossing: int = board[p.y*7+p.x]
                # User supplied rules: only the opponent's rat obstructs a jump.
                if crossing != 0 and side(crossing) != owner: blocked = true
                p += direction
            if blocked or not inside(p): continue
            to = p.y*7+p.x
        if den(to) == owner: continue
        if board[to] != 0 and not can_capture(from,to): continue
        result.append({"from":from,"to":to,"piece":piece,"captured":board[to],"jump":jumped})
    return result
func threatened(index: int) -> bool:
    var victim: int = board[index]
    if victim == 0: return false
    for source in range(63):
        if board[source] == 0 or side(board[source]) == side(victim): continue
        for move in raw_moves(source):
            if move.to == index: return true
    return false
func threats_after(move: Dictionary) -> Array:
    var previous: int = board[move.to]
    board[move.from] = 0
    board[move.to] = move.piece
    var targets: Array = []
    for next in raw_moves(move.to):
        if next.captured != 0: targets.append(next.captured)
    board[move.from] = move.piece
    board[move.to] = previous
    return targets
func restriction(move: Dictionary) -> String:
    if not competitive or trap(move.to) != -1: return ""
    var owner: int = side(move.piece)
    var records: Array = history[owner]
    # A pursued animal is exempt; entering a trap breaks the restricted sequence.

    if records.size() >= 17:
        var same: bool = true
        var cells: Dictionary = {}
        for entry in records.slice(-17):
            if entry.piece != move.piece or entry.trap: same = false
            cells[entry.from] = true
            cells[entry.to] = true
        if same and cells.size() <= 5 and cells.has(move.to):
            return "17步内同一兽仅在5格内活动，请走出该区域或换一只兽"
    if threatened(move.from): return ""
    var recent: Array = records.slice(maxi(0,records.size()-6))
    for entry in recent:
        if entry.trap: return ""
    var targets: Array = threats_after(move)
    for target in targets:
        var visits: int = 0
        for entry in recent:
            if entry.piece == move.piece and entry.to == move.to and target in entry.threats:
                visits += 1
        if visits >= 2: return "7步内不能第三次进入同格追杀同一只兽"
    return ""
func moves(from: int) -> Array:
    var result: Array = []
    if winner >= 0 or from < 0 or from >= 63 or board[from] == 0 or side(board[from]) != turn: return result
    for move in raw_moves(from):
        if restriction(move).is_empty(): result.append(move)
    return result
func all_moves() -> Array:
    var result: Array = []
    for index in range(63): result.append_array(moves(index))
    return result
func explain(from: int, to: int) -> String:
    for move in raw_moves(from):
        if move.to == to: return restriction(move)
    return "这里不能走：请点击高亮落点"
func play(from: int, to: int) -> Dictionary:
    var chosen: Dictionary = {}
    for move in moves(from):
        if move.to == to: chosen = move; break
    if chosen.is_empty(): return {}
    var owner: int = turn
    var targets: Array = threats_after(chosen)
    board[from] = 0
    board[to] = chosen.piece
    history[owner].append({"piece":chosen.piece,"from":from,"to":to,"trap":trap(from) >= 0 or trap(to) >= 0,"threats":targets})
    if history[owner].size() > 17: history[owner].pop_front()
    last_move = chosen.duplicate()
    remaining = 30
    if den(to) == 1-owner:
        winner = owner
        reason = "进入对方兽穴"
    turn = 1-owner
    if winner < 0 and all_moves().is_empty():
        winner = owner
        reason = "对方已无棋子" if not has_animals(turn) else "对方所有棋子均无法移动"
    return chosen
func has_animals(owner: int) -> bool:
    for piece in board:
        if piece != 0 and side(piece) == owner: return true
    return false
func tick(delta: float) -> bool:
    if not competitive or winner >= 0: return false
    remaining = maxf(0,remaining-delta)
    if remaining > 0: return false
    winner = 1-turn
    reason = "对方超过30秒未落子"
    return true
