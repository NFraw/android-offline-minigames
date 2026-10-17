extends RefCounted
const Physics = preload("res://core/physics2d.gd")
const SIZE = Vector2(1000,1800)
const RADIUS: float = 28
const CENTER = Vector2(500,400)
const HOUSE: float = 220
const AIM_ZONE = Rect2(120,1250,760,450)
const MAX_PULL: float = 260
var players: int = 2
var rounds: int = 3
var round_index: int = 1
var turn: int = 0
var last_winner: int = 0
var thrown: Array = [0,0,0,0]
var scores: Array = [0,0,0,0]
var balls: Array = []
var phase: String = "throw"
var wait: float = 0
var done: bool = false
var result: String = ""
var round_result: String = ""
var last_impact: float = 0
func reset(count: int = 2) -> void:
    players = clampi(count,2,4); rounds = players+1
    round_index = 1; turn = 0; last_winner = 0
    thrown = [0,0,0,0]; scores = [0,0,0,0]; balls = []
    phase = "throw"; wait = 0; done = false; result = ""; round_result = ""; last_impact = 0
static func aim(pivot: Vector2, finger: Vector2) -> Dictionary:
    if not AIM_ZONE.has_point(pivot): return {}
    var baseline: float = SIZE.y-RADIUS
    var line: Vector2 = finger-pivot
    if line.length() < 12 or absf(line.y) < 1 or pivot.y >= baseline: return {}
    line = line.limit_length(MAX_PULL)
    var fraction: float = (baseline-pivot.y)/line.y
    var x: float = pivot.x+fraction*line.x
    if x < RADIUS or x > SIZE.x-RADIUS: return {}
    var launch := Vector2(x,baseline)
    var direction: Vector2 = pivot-launch
    if direction.length() < 1 or direction.y >= 0: return {}
    return {"launch":launch,"direction":direction.normalized()}
func throw_stone(solution: Dictionary, power: float) -> bool:
    if phase != "throw" or done or solution.is_empty() or power <= 0.08: return false
    if not solution.has("launch") or not solution.has("direction") or solution.direction.y >= 0: return false
    balls.append({"p":solution.launch,"v":solution.direction*clampf(power,0,1)*SIZE.y*1.35,"owner":turn,"num":balls.size()+1,"active":true})
    thrown[turn] += 1; phase = "moving"
    return true
func score_round() -> void:
    var distances: Array = [INF,INF,INF,INF]
    for ball in balls:
        var distance: float = ball.p.distance_to(CENTER)
        if distance <= HOUSE: distances[ball.owner] = minf(distances[ball.owner],distance)
    var best: int = -1
    var closest: float = INF
    for owner in range(players):
        if distances[owner] < closest: closest = distances[owner]; best = owner
    if best < 0: round_result = "无人进营垒，本轮0分"; return
    var second: float = INF
    for owner in range(players):
        if owner != best: second = minf(second,distances[owner])
    var points: int = 0
    for ball in balls:
        var distance: float = ball.p.distance_to(CENTER)
        if ball.owner == best and distance <= HOUSE and distance < second: points += 1
    if points > 0: scores[best] += points; last_winner = best
    round_result = "玩家%d本轮%d分"%[best+1,points] if points > 0 else "最近距离相同，本轮0分"
func next_round() -> void:
    if round_index >= rounds:
        done = true; phase = "ended"
        var best: int = 0
        for owner in range(players): best = maxi(best,scores[owner])
        var names: PackedStringArray = []
        var table: PackedStringArray = []
        for owner in range(players):
            table.append("玩家%d %d分"%[owner+1,scores[owner]])
            if scores[owner] == best: names.append("玩家%d"%(owner+1))
        result = "、".join(names)+"获胜 · "+" / ".join(table)
    else:
        round_index += 1; balls.clear(); thrown = [0,0,0,0]; turn = last_winner; phase = "throw"
func tick(delta: float) -> bool:
    if done: return false
    if phase == "moving":
        var step: Dictionary = Physics.balls_step(balls,SIZE,RADIUS,delta,0.4,-60*log(0.98),10)
        last_impact = step.impact
        if not step.moving:
            var total: int = 0
            for count in thrown: total += count
            if total == players*2:
                score_round(); phase = "between"; wait = 2.6
            else:
                turn = (turn+1)%players
                while thrown[turn] >= 2: turn = (turn+1)%players
                phase = "throw"
        return true
    if phase == "between":
        wait -= delta
        if wait <= 0: next_round()
        return true
    return false
