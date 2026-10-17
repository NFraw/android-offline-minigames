extends RefCounted
const Board = preload("res://games/flight/flight_board.gd")
const ROLL: int = 0
const MOVE: int = 1
const OVER: int = 2
const FLY: int = Board.FLY_PROGRESS
const FINISH: int = Board.FINISH
var board = Board.new()
var seats: Array = [0,2]
var positions: Array = []
var turn: int = 0
var phase: int = ROLL
var last_dice: int = 0
var sixes: int = 0
var winner: int = -1
var turn_snapshot: Array = []
var rng := RandomNumberGenerator.new()
func reset(players: int = 2) -> void:
    assert(players >= 2 and players <= 4)
    seats = [0,2,1,3].slice(0, players)
    positions = [[-1,-1,-1,-1],[-1,-1,-1,-1],[-1,-1,-1,-1],[-1,-1,-1,-1]]
    turn = 0
    phase = ROLL
    last_dice = 0
    sixes = 0
    winner = -1
    turn_snapshot = positions.duplicate(true)
    rng.randomize()
func absolute_cell(seat: int, progress: int) -> int:
    return board.absolute(seat, progress)
func home_count(seat: int) -> int:
    return positions[seat].count(FINISH)
func compute_move(seat: int, piece: int, dice: int) -> Dictionary:
    if seat not in seats or piece < 0 or piece >= 4 or dice < 1 or dice > 6:
        return {}
    var previous: int = positions[seat][piece]
    if previous == FINISH:
        return {}
    if previous < 0:
        if dice != 6:
            return {}
        return {"from":-1,"to":0,"path":[0],"landings":[0],"jumped":false,"flew":false}
    var target: int = previous + dice
    if target > FINISH:
        return {}
    var path: Array = []
    for progress in range(previous + 1, target + 1):
        path.append(progress)
    var landings: Array = [target]
    var jumped: bool = false
    var flew: bool = false
    if target <= Board.OUTER_STEPS:
        if target == FLY:
            target += 12
            path.append(target)
            landings.append(target)
            flew = true
        elif board.color_of(absolute_cell(seat, target)) == seat and target + 4 <= Board.OUTER_STEPS:
            target += 4
            path.append(target)
            landings.append(target)
            jumped = true
            if target == FLY:
                target += 12
                path.append(target)
                landings.append(target)
                flew = true
    return {"from":previous,"to":target,"path":path,"landings":landings,"jumped":jumped,"flew":flew}
func legal_moves(dice: int = -1) -> Dictionary:
    var moves: Dictionary = {}
    if phase == OVER:
        return moves
    var value: int = last_dice if dice < 0 else dice
    for piece in range(4):
        var move: Dictionary = compute_move(turn, piece, value)
        if not move.is_empty():
            moves[piece] = move
    return moves
func roll(value: int = 0) -> Dictionary:
    if phase != ROLL:
        return {}
    var dice: int = rng.randi_range(1,6) if value == 0 else value
    if dice < 1 or dice > 6:
        return {}
    if sixes == 0:
        turn_snapshot = positions.duplicate(true)
    var player: int = turn
    last_dice = dice
    if dice == 6:
        sixes += 1
        if sixes == 3:
            positions = turn_snapshot.duplicate(true)
            _next_turn()
            return {"dice":6,"seat":player,"penalty":true,"no_moves":true}
    var moves: Dictionary = legal_moves(dice)
    if moves.is_empty():
        if dice != 6:
            _next_turn()
        return {"dice":dice,"seat":player,"penalty":false,"no_moves":true}
    phase = MOVE
    return {"dice":dice,"seat":player,"penalty":false,"no_moves":false}
func _captures(seat: int, progress: int) -> Array:
    var victims: Array = []
    if progress <= 0 or progress > Board.OUTER_STEPS:
        return victims
    var cell: int = absolute_cell(seat, progress)
    for other in seats:
        if other == seat:
            continue
        for piece in range(4):
            var location: int = positions[other][piece]
            if location > 0 and location <= Board.OUTER_STEPS and absolute_cell(other, location) == cell:
                victims.append(Vector2i(other, piece))
    return victims
func move_piece(piece: int) -> Dictionary:
    if phase != MOVE:
        return {}
    var move: Dictionary = compute_move(turn, piece, last_dice)
    if move.is_empty():
        return {}
    var player: int = turn
    var victims: Array = []
    for landing in move.landings:
        for victim in _captures(player, landing):
            positions[victim.x][victim.y] = -1
            victims.append(victim)
    positions[player][piece] = move.to
    move["captures"] = victims
    move["seat"] = player
    move["piece"] = piece
    if home_count(player) == 4:
        winner = player
        phase = OVER
    elif last_dice == 6:
        phase = ROLL
    else:
        _next_turn()
    return move
func _next_turn() -> void:
    turn = int(seats[(seats.find(turn) + 1) % seats.size()])
    last_dice = 0
    sixes = 0
    phase = ROLL
    turn_snapshot = positions.duplicate(true)
func best_move() -> int:
    var best: int = -1
    var best_score: float = -INF
    for piece in legal_moves():
        var move: Dictionary = compute_move(turn, piece, last_dice)
        var score: float = float(move.to) + (35 if move.from < 0 else 0)
        if move.to == FINISH:
            score += 10000
        for landing in move.landings:
            score += _captures(turn, landing).size() * 120
        if score > best_score:
            best_score = score
            best = piece
    return best
