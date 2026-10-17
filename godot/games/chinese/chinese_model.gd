extends RefCounted
const DIRS = [Vector2i.LEFT,Vector2i.RIGHT,Vector2i.UP,Vector2i.DOWN]
const RED_SET = [1,2,2,3,3,5,5,4,4,6,6,7,7,7,7,7]
const RED_NAMES = ["","帥","仕","相","馬","車","炮","兵"]
const BLACK_NAMES = ["","將","士","象","馬","車","砲","卒"]
var kind: String = "xiangqi_duel"
var cols: int = 9
var rows: int = 10
var board := PackedInt32Array()
var revealed: Array = []
var player_sides: Array = [0,1]
var turn: int = 0
var winner: int = -1
var done: bool = false
var result: String = ""
var quiet: int = 0
var history: Array = []
var undo_left: Array = [3,3]
var last_move: Dictionary = {}
var rng := RandomNumberGenerator.new()
static func side(piece: int) -> int:
    return 0 if piece < 10 else 1
static func name_of(piece: int) -> String:
    return RED_NAMES[piece%10] if side(piece) == 0 else BLACK_NAMES[piece%10]
static func rank(piece: int) -> int:
    return [0,7,6,5,3,4,2,1][piece%10]
func reset(id: String = "xiangqi_duel") -> void:
    kind = id; cols = 4 if id == "darkchess" else 9; rows = 8 if id == "darkchess" else 10
    board.resize(cols*rows); board.fill(0)
    revealed.resize(board.size()); revealed.fill(id != "darkchess")
    turn = 0; winner = -1; done = false; result = ""; quiet = 0
    history.clear(); undo_left = [3,3]; last_move = {}
    player_sides = [-1,-1] if id == "darkchess" else [0,1]
    if id == "darkchess":
        var deck: Array = RED_SET.duplicate()
        for piece in RED_SET: deck.append(piece+10)
        for i in range(deck.size()-1,0,-1):
            var j: int = rng.randi_range(0,i)
            var old: int = deck[i]; deck[i] = deck[j]; deck[j] = old
        board = PackedInt32Array(deck)
    else:
        var back: Array = [5,4,3,2,1,2,3,4,5]
        for x in range(9): board[x] = back[x]+10; board[81+x] = back[x]
        for x in range(0,9,2): board[27+x] = 17; board[54+x] = 7
        board[19] = 16; board[25] = 16; board[64] = 6; board[70] = 6
func point(index: int) -> Vector2i:
    return Vector2i(index%cols,index/cols)
func inside(p: Vector2i) -> bool:
    return p.x >= 0 and p.x < cols and p.y >= 0 and p.y < rows
func xiangqi_targets(from: int) -> Array:
    var out: Array = []
    var piece: int = board[from]
    if piece == 0: return out
    var type: int = piece%10
    var owner: int = side(piece)
    var origin: Vector2i = point(from)
    if type in [5,6]:
        for d in DIRS:
            var p: Vector2i = origin+d
            var screen: bool = false
            while inside(p):
                var index: int = p.y*cols+p.x
                if type == 5:
                    out.append(index)
                    if board[index] != 0: break
                elif not screen:
                    if board[index] == 0: out.append(index)
                    else: screen = true
                elif board[index] != 0:
                    out.append(index); break
                p += d
    elif type == 4:
        for step in [Vector2i(-2,-1),Vector2i(-2,1),Vector2i(2,-1),Vector2i(2,1),Vector2i(-1,-2),Vector2i(1,-2),Vector2i(-1,2),Vector2i(1,2)]:
            var p: Vector2i = origin+step
            var leg := Vector2i(signi(step.x),0) if absi(step.x) == 2 else Vector2i(0,signi(step.y))
            var middle: Vector2i = origin+leg
            if inside(p) and board[middle.y*cols+middle.x] == 0: out.append(p.y*cols+p.x)
    elif type == 3:
        for d in [Vector2i(-2,-2),Vector2i(2,-2),Vector2i(-2,2),Vector2i(2,2)]:
            var p: Vector2i = origin+d
            if not inside(p) or (owner == 0 and p.y < 5) or (owner == 1 and p.y > 4): continue
            var middle := Vector2i(origin.x+int(d.x/2),origin.y+int(d.y/2))
            if board[middle.y*cols+middle.x] == 0: out.append(p.y*cols+p.x)
    elif type in [1,2]:
        var directions: Array = DIRS if type == 1 else [Vector2i(-1,-1),Vector2i(1,-1),Vector2i(-1,1),Vector2i(1,1)]
        for d in directions:
            var p: Vector2i = origin+d
            if p.x >= 3 and p.x <= 5 and p.y >= (7 if owner == 0 else 0) and p.y <= (9 if owner == 0 else 2): out.append(p.y*cols+p.x)
        if type == 1:
            for d in [Vector2i.UP,Vector2i.DOWN]:
                var p: Vector2i = origin+d
                while inside(p):
                    var target: int = board[p.y*cols+p.x]
                    if target != 0:
                        if target%10 == 1 and side(target) != owner: out.append(p.y*cols+p.x)
                        break
                    p += d
    else:
        var directions: Array = [Vector2i.UP if owner == 0 else Vector2i.DOWN]
        if (owner == 0 and origin.y <= 4) or (owner == 1 and origin.y >= 5): directions += [Vector2i.LEFT,Vector2i.RIGHT]
        for d in directions:
            var p: Vector2i = origin+d
            if inside(p): out.append(p.y*cols+p.x)
    return out
func in_check(owner: int) -> bool:
    var king: int = board.find(1 if owner == 0 else 11)
    if king < 0: return false
    for from in range(board.size()):
        if board[from] != 0 and side(board[from]) != owner and king in xiangqi_targets(from): return true
    return false
func legal_xiangqi(from: int, to: int) -> bool:
    if from < 0 or from >= 90 or to < 0 or to >= 90 or board[from] == 0: return false
    var piece: int = board[from]
    var target: int = board[to]
    if target != 0 and side(piece) == side(target): return false
    if to not in xiangqi_targets(from): return false
    board[from] = 0; board[to] = piece
    var legal: bool = not in_check(side(piece))
    board[from] = piece; board[to] = target
    return legal
func dark_capture(a: int, b: int) -> bool:
    if rank(a) == 1 and rank(b) == 7: return true
    if rank(a) == 7 and rank(b) == 1: return false
    return rank(a) >= rank(b)
func dark_targets(from: int) -> Array:
    var out: Array = []
    var piece: int = board[from]
    if piece == 0 or not revealed[from]: return out
    for d in DIRS:
        var p: Vector2i = point(from)+d
        if not inside(p): continue
        var to: int = p.y*cols+p.x
        var target: int = board[to]
        if target == 0: out.append(to)
        elif revealed[to] and side(target) != side(piece) and piece%10 != 6 and dark_capture(piece,target): out.append(to)
        # 炮可以沿整条直线隔恰好一个棋子，吃已翻开的敌子。
        if piece%10 == 6:
            var screens: int = 0
            p = point(from)+d
            while inside(p):
                to = p.y*cols+p.x; target = board[to]
                if target != 0:
                    if screens == 1:
                        if revealed[to] and side(target) != side(piece): out.append(to)
                        break
                    screens += 1
                p += d
    return out
func targets(from: int) -> Array:
    var out: Array = []
    if done or from < 0 or from >= board.size() or board[from] == 0 or side(board[from]) != player_sides[turn]: return out
    if kind == "darkchess": return dark_targets(from)
    for to in xiangqi_targets(from):
        if legal_xiangqi(from,to): out.append(to)
    return out
func has_action() -> bool:
    if kind == "darkchess":
        for i in range(board.size()):
            if board[i] != 0 and not revealed[i]: return true
    for i in range(board.size()):
        if not targets(i).is_empty(): return true
    return false
func judge() -> void:
    var counts: Array = [0,0]
    for piece in board:
        if piece != 0: counts[side(piece)] += 1
    if kind == "xiangqi_duel":
        if not board.has(1) or not board.has(11): winner = 1 if not board.has(1) else 0
        elif not has_action(): winner = 1-turn
        if winner >= 0: done = true; result = ("红方" if winner == 0 else "黑方")+"获胜 · 将死或对方无子可走"
    else:
        if counts[0] == 0 or counts[1] == 0:
            var surviving: int = 0 if counts[0] > 0 else 1
            winner = player_sides.find(surviving)
        elif not has_action(): winner = 1-turn
        if winner >= 0: done = true; result = "玩家%d获胜 · 对方无子或无合法动作"%(winner+1)
        elif quiet >= 120: done = true; winner = 2; result = "连续120步无吃子，和棋"
func flip(index: int) -> Dictionary:
    if kind != "darkchess" or done or index < 0 or index >= 32 or board[index] == 0 or revealed[index]: return {}
    revealed[index] = true
    if player_sides[0] < 0: player_sides = [side(board[index]),1-side(board[index])]
    turn = 1-turn; quiet += 1
    last_move = {"from":index,"to":index,"piece":board[index],"captured":0,"flip":true}
    judge()
    return last_move.duplicate()
func play(from: int, to: int) -> Dictionary:
    if to not in targets(from): return {}
    if kind == "xiangqi_duel":
        history.append({"board":board.duplicate(),"turn":turn,"last":last_move.duplicate()})
    var move: Dictionary = {"from":from,"to":to,"piece":board[from],"captured":board[to],"flip":false}
    board[to] = board[from]; board[from] = 0
    revealed[to] = true; revealed[from] = true
    quiet = 0 if move.captured != 0 else quiet+1
    turn = 1-turn; last_move = move.duplicate()
    judge()
    return move
func undo(requester: int, agreed: bool) -> bool:
    if kind != "xiangqi_duel" or done or history.is_empty() or undo_left[requester] <= 0 or not agreed: return false
    var previous: Dictionary = history.pop_back()
    board = previous.board; turn = previous.turn; last_move = previous.last
    undo_left[requester] -= 1
    return true
func resign(owner: int) -> void:
    if done: return
    winner = 1-owner; done = true
    result = ("红方" if winner == 0 else "黑方")+"获胜 · 对方认输"
