extends RefCounted
const Physics = preload("res://core/physics2d.gd")
const SIZE = Vector2(1000,500)
const RADIUS: float = 15
const POCKETS = [Vector2(0,0),Vector2(500,0),Vector2(1000,0),Vector2(0,500),Vector2(500,500),Vector2(1000,500)]
var balls: Array = []
var turn: int = 0
var groups: Array = [0,0]
var break_shot: bool = true
var ball_in_hand: bool = false
var kitchen_only: bool = false
var phase: String = "aim"
var first_contact: int = -1
var shot_events: Array = []
var need_eight: bool = false
var call_pocket: int = -1
var winner: int = -1
var done: bool = false
var result: String = ""
var message: String = "向后拖动蓄力，松手击球"
var last_impact: float = 0
func reset() -> void:
    balls = [{"p":Vector2(200,250),"v":Vector2.ZERO,"num":0,"active":true}]
    var order: Array = [1,9,2,10,8,3,11,4,12,5,6,13,7,14,15]
    var index: int = 0
    for row in range(5):
        for column in range(row+1):
            var p := Vector2(740+row*RADIUS*sqrt(3.0)*1.03,250+(column-row/2.0)*RADIUS*2.06)
            balls.append({"p":p,"v":Vector2.ZERO,"num":order[index],"active":true}); index += 1
    turn = 0; groups = [0,0]; break_shot = true; ball_in_hand = false; kitchen_only = false
    phase = "aim"; first_contact = -1; shot_events = []; need_eight = false
    call_pocket = -1; winner = -1; done = false; result = ""; message = "向后拖动蓄力，松手击球"
static func ball_group(num: int) -> int:
    return 1 if num in range(1,8) else (2 if num in range(9,16) else 0)
func get_ball(num: int) -> Dictionary:
    for ball in balls:
        if ball.num == num: return ball
    return {}
func cleared(owner: int) -> bool:
    if groups[owner] == 0: return false
    for ball in balls:
        if ball.active and ball_group(ball.num) == groups[owner]: return false
    return true
func group_label(owner: int) -> String:
    return ["待定","实球","花球"][groups[owner]]
func start_turn() -> void:
    call_pocket = -1
    if ball_in_hand: phase = "place"; message = "自由球：点击发球区放置母球" if kitchen_only else "自由球：点击空位放置母球"
    elif cleared(turn): phase = "call"; message = "打8号前，请点击一个袋口叫袋"
    else: phase = "aim"; message = "向后拖动蓄力，松手击球"
func legal_placement(at: Vector2, ignore: int = 0) -> bool:
    if at.x < RADIUS or at.y < RADIUS or at.x > SIZE.x-RADIUS or at.y > SIZE.y-RADIUS: return false
    if kitchen_only and at.x > 220-RADIUS: return false
    for pocket in POCKETS:
        if at.distance_to(pocket) < RADIUS*2.85+1: return false
    for ball in balls:
        if ball.active and ball.num != ignore and ball.p.distance_to(at) < RADIUS*2+0.5: return false
    return true
func place_cue(at: Vector2) -> bool:
    if phase != "place" or not legal_placement(at): return false
    var cue: Dictionary = get_ball(0)
    cue.p = at; cue.v = Vector2.ZERO; cue.active = true
    ball_in_hand = false; kitchen_only = false; start_turn()
    return true
func choose_pocket(index: int) -> bool:
    if phase != "call" or index < 0 or index >= 6: return false
    call_pocket = index; phase = "aim"; message = "已叫%d号袋，向后拖动击球"%(index+1)
    return true
func shoot(aim: Vector2) -> bool:
    if done or phase != "aim": return false
    var cue: Dictionary = get_ball(0)
    var direction: Vector2 = cue.p-aim
    if not cue.active or direction.length() < 12: return false
    var power: float = clampf(direction.length()/500,0.15,1)
    cue.v = direction.normalized()*1400*power
    need_eight = cleared(turn)
    first_contact = -1; shot_events.clear(); phase = "moving"
    return true
func foul() -> bool:
    if not get_ball(0).active or first_contact < 0: return true
    if groups[turn] == 0: return first_contact == 8
    return first_contact != 8 if need_eight else ball_group(first_contact) != groups[turn]
func respot_eight() -> void:
    var ball: Dictionary = get_ball(8)
    for radius in range(0,400,31):
        for angle in range(0,360,15):
            var at: Vector2 = Vector2(740,250)+Vector2.from_angle(deg_to_rad(angle))*radius
            var free: bool = at.x > 30 and at.x < 970 and at.y > 30 and at.y < 470
            for other in balls:
                if other.num != 8 and other.active and other.p.distance_to(at) < RADIUS*2+0.5: free = false
            if free: ball.p = at; ball.v = Vector2.ZERO; ball.active = true; return
func resolve_shot() -> void:
    var fouled: bool = foul()
    var eight_pocket: int = -1
    for event in shot_events:
        if event.x == 8: eight_pocket = event.y
    if eight_pocket >= 0 and break_shot:
        respot_eight()
        shot_events = shot_events.filter(func(event): return event.x != 8)
        eight_pocket = -1
    if eight_pocket >= 0:
        var legal: bool = not fouled and need_eight and cleared(turn) and call_pocket == eight_pocket
        winner = turn if legal else 1-turn
        done = true; phase = "ended"
        result = "玩家%d获胜 · %s"%[winner+1,"合法打进叫袋8号" if legal else "对方违例打进8号"]
        return
    if break_shot:
        break_shot = false
        if fouled:
            turn = 1-turn; ball_in_hand = true; kitchen_only = true
        elif shot_events.is_empty(): turn = 1-turn
        start_turn(); return
    if not fouled and groups[turn] == 0:
        for event in shot_events:
            var group: int = ball_group(event.x)
            if group != 0:
                groups[turn] = group; groups[1-turn] = 3-group; break
    if fouled:
        turn = 1-turn; ball_in_hand = true; kitchen_only = false
    else:
        var own: bool = false
        for event in shot_events:
            if ball_group(event.x) == groups[turn] and groups[turn] > 0: own = true
        if not own: turn = 1-turn
    start_turn()
func tick(delta: float) -> bool:
    if done or phase != "moving": return false
    var step: Dictionary = Physics.balls_step(balls,SIZE,RADIUS,delta,0.96,1.3,8,POCKETS)
    last_impact = step.impact
    for contact in step.contacts:
        if first_contact >= 0: break
        if contact.x == 0: first_contact = contact.y
        elif contact.y == 0: first_contact = contact.x
    shot_events.append_array(step.pockets)
    if not step.moving: resolve_shot()
    return true
