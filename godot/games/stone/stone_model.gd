extends RefCounted
const AXES = [Vector2i.RIGHT,Vector2i.DOWN,Vector2i(1,1),Vector2i(1,-1)]
const FOUR = [Vector2i.LEFT,Vector2i.RIGHT,Vector2i.UP,Vector2i.DOWN]
const EIGHT = [Vector2i.LEFT,Vector2i.RIGHT,Vector2i.UP,Vector2i.DOWN,Vector2i(1,1),Vector2i(-1,1),Vector2i(1,-1),Vector2i(-1,-1)]
var kind: String = "gomoku"
var n: int = 15
var board := PackedInt32Array()
var turn: int = 1
var winner: int = -1
var reason: String = ""
var last: int = -1
var captures: Array = [0,0]
var passes: int = 0
var ko_board := PackedInt32Array()
var rng := RandomNumberGenerator.new()
func reset(id: String = "gomoku", first: int = 1) -> void:
    kind = id
    n = 3 if id == "tictactoe" else (8 if id == "reversi" else 15)
    board.resize(n*n)
    board.fill(0)
    turn = first
    winner = -1
    reason = ""
    last = -1
    captures = [0,0]
    passes = 0
    ko_board.clear()
    if kind == "reversi":
        board[3*n+3] = 2; board[4*n+4] = 2
        board[3*n+4] = 1; board[4*n+3] = 1
func point(i: int) -> Vector2i:
    return Vector2i(i%n,i/n)
func inside(p: Vector2i) -> bool:
    return p.x >= 0 and p.x < n and p.y >= 0 and p.y < n
func neighbors(i: int) -> Array:
    var result: Array = []
    for d in FOUR:
        var p: Vector2i = point(i)+d
        if inside(p): result.append(p.y*n+p.x)
    return result
func flips(index: int, player: int) -> Array:
    var result: Array = []
    if index < 0 or index >= board.size() or board[index] != 0: return result
    for d in EIGHT:
        var p: Vector2i = point(index)+d
        var run: Array = []
        while inside(p) and board[p.y*n+p.x] == 3-player:
            run.append(p.y*n+p.x)
            p += d
        if not run.is_empty() and inside(p) and board[p.y*n+p.x] == player: result.append_array(run)
    return result
func group(index: int) -> Dictionary:
    var stones: Array = [index]
    var seen: Dictionary = {index:true}
    var liberties: Dictionary = {}
    var cursor: int = 0
    while cursor < stones.size():
        for next in neighbors(stones[cursor]):
            if board[next] == 0: liberties[next] = true
            elif board[next] == board[index] and not seen.has(next):
                seen[next] = true
                stones.append(next)
        cursor += 1
    return {"stones":stones,"liberties":liberties}
func go_candidate(index: int) -> Dictionary:
    if board[index] != 0: return {}
    var before := board.duplicate()
    board[index] = turn
    var removed: Array = []
    for next in neighbors(index):
        if board[next] == 3-turn:
            var enemy: Dictionary = group(next)
            if enemy.liberties.is_empty():
                for stone in enemy.stones: board[stone] = 0; removed.append(stone)
    var valid: bool = not group(index).liberties.is_empty() and (ko_board.is_empty() or board != ko_board)
    var after := board.duplicate()
    board = before
    return {"board":after,"captured":removed} if valid else {}
func legal(index: int) -> bool:
    if winner >= 0 or index < 0 or index >= board.size() or board[index] != 0: return false
    if kind == "reversi": return not flips(index,turn).is_empty()
    if kind == "weiqi": return not go_candidate(index).is_empty()
    return true
func legal_moves(player: int = 0) -> Array:
    var result: Array = []
    var previous: int = turn
    if player > 0: turn = player
    for i in range(board.size()):
        if legal(i): result.append(i)
    turn = previous
    return result
func line_count(index: int, player: int, axis: Vector2i) -> int:
    var count: int = 1
    for sign in [-1,1]:
        var p: Vector2i = point(index)+axis*sign
        while inside(p) and board[p.y*n+p.x] == player:
            count += 1
            p += axis*sign
    return count
func place(index: int) -> Dictionary:
    if not legal(index): return {}
    var player: int = turn
    var changed: Array = []
    if kind == "reversi":
        changed = flips(index,turn)
        for i in changed: board[i] = player
        board[index] = player
    elif kind == "weiqi":
        var before := board.duplicate()
        var candidate: Dictionary = go_candidate(index)
        board = candidate.board
        changed = candidate.captured
        captures[player-1] += changed.size()
        ko_board = before
    else: board[index] = player
    last = index
    passes = 0
    if kind in ["gomoku","tictactoe"]:
        for axis in AXES:
            if line_count(index,player,axis) >= (3 if kind == "tictactoe" else 5):
                winner = player
                reason = "三子连线" if kind == "tictactoe" else "五子连珠"
        if winner < 0 and not board.has(0): winner = 0; reason = "棋盘已满，平局"
    turn = 3-player
    if kind == "reversi" and legal_moves().is_empty():
        turn = player
        if legal_moves().is_empty(): finish_counts()
        else: passes = 1
    return {"at":index,"player":player,"changed":changed}
func area_score() -> Array:
    var totals: Array = [board.count(1),board.count(2)]
    var seen: Dictionary = {}
    for i in range(board.size()):
        if board[i] != 0 or seen.has(i): continue
        var empty: Array = [i]
        var borders: Dictionary = {}
        seen[i] = true
        var cursor: int = 0
        while cursor < empty.size():
            for next in neighbors(empty[cursor]):
                if board[next] == 0:
                    if not seen.has(next): seen[next] = true; empty.append(next)
                else: borders[board[next]] = true
            cursor += 1
        if borders.size() == 1:
            var owner: int = borders.keys()[0]
            totals[owner-1] += empty.size()
    return totals
func finish_counts() -> void:
    var totals: Array = area_score() if kind == "weiqi" else [board.count(1),board.count(2)]
    winner = 0 if totals[0] == totals[1] else (1 if totals[0] > totals[1] else 2)
    reason = "%s %d : %d"%["面积" if kind == "weiqi" else "棋子",totals[0],totals[1]]
func pass_turn() -> bool:
    if kind != "weiqi" or winner >= 0: return false
    turn = 3-turn
    passes += 1
    ko_board.clear()
    if passes >= 2: finish_counts()
    return true
func best_move() -> int:
    var candidates: Array = legal_moves()
    if candidates.is_empty(): return -1
    if kind == "gomoku":
        if board.count(0) == board.size(): return (n*n)/2
        for i in candidates:
            board[i] = turn
            var wins_now: bool = false
            for axis in AXES:
                if line_count(i,turn,axis) >= 5: wins_now = true
            board[i] = 0
            if wins_now: return i
        var best: int = candidates[0]
        var best_score: float = -INF
        for i in candidates:
            var near: bool = false
            for d in EIGHT:
                var p: Vector2i = point(i)+d
                if inside(p) and board[p.y*n+p.x] != 0: near = true
            if not near: continue
            var value: float = 0
            for player in [turn,3-turn]:
                board[i] = player
                for axis in AXES:
                    var length: int = line_count(i,player,axis)
                    var open_ends: int = 0
                    for sign in [-1,1]:
                        var p: Vector2i = point(i)+axis*sign
                        while inside(p) and board[p.y*n+p.x] == player: p += axis*sign
                        if inside(p) and board[p.y*n+p.x] == 0: open_ends += 1
                    value += (1000000 if length >= 5 else pow(12,length)*open_ends)*(1.1 if player == turn else 1.0)
                board[i] = 0
            value -= Vector2(point(i)).distance_to(Vector2(n/2,n/2))*0.01
            if value > best_score: best_score = value; best = i
        return best
    if kind == "reversi":
        var best: int = candidates[0]
        var best_score: float = -INF
        for i in candidates:
            var p: Vector2i = point(i)
            var corner: bool = p.x in [0,n-1] and p.y in [0,n-1]
            var value: float = flips(i,turn).size()+1000*int(corner)
            if p.x in [1,n-2] and p.y in [1,n-2] and board[(0 if p.y == 1 else n-1)*n+(0 if p.x == 1 else n-1)] == 0: value -= 100
            if value > best_score: best_score = value; best = i
        return best
    return candidates[rng.randi_range(0,candidates.size()-1)]
