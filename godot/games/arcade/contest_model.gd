extends RefCounted
var kind: String = "reaction"
var rng := RandomNumberGenerator.new()
var scores: Array = [0,0,0,0]
var used: Array = [false,false,false,false]
var early: Array = [false,false,false,false]
var state: int = 0
var timer: float = 0
var ready_elapsed: float = 0
var response_ms: Array = [0,0,0,0]
var done: bool = false
var result: String = ""
var difficulty: int = 0
var question: String = ""
var answer: int = 0
var inputs: Array = ["",""]
var locked: Array = [false,false]
const LIMITS = [10,20,50,100,100,1000]
const LABELS = ["10以内加减","20以内加减","50以内加减","100以内加减","100以内四则","1000以内四则"]
func reset(id: String, mode: int = 0) -> void:
    kind = id; difficulty = clampi(mode,0,5)
    scores = [0,0,0,0]; done = false; result = ""
    if kind == "math_duel": new_question()
    else: next_round()
func next_round() -> void:
    state = 0; timer = rng.randf_range(1.5,3.5); ready_elapsed = 0
    used = [false,false,false,false]; early = [false,false,false,false]
    response_ms = [0,0,0,0]
func finish_if_needed() -> void:
    var target: int = 10 if kind == "reaction" else 5
    var maximum: int = 0
    var winners: Array = []
    for owner in range(4 if kind == "reaction4" else 2): maximum = maxi(maximum,scores[owner])
    if maximum < target: return
    for owner in range(4 if kind == "reaction4" else 2):
        if scores[owner] == maximum: winners.append("玩家%d"%(owner+1))
    done = true; result = "、".join(winners)+("并列获胜" if winners.size() > 1 else "获胜")
func react(owner: int) -> bool:
    if done or state == 2 or used[owner] or early[owner]: return false
    if state == 0:
        early[owner] = true
        if kind == "reaction": scores[1-owner] += 1; state = 2; timer = 0.75; finish_if_needed()
        elif early.count(true) == 4: state = 2; timer = 0.75
    else:
        used[owner] = true; scores[owner] += 1; response_ms[owner] = int(ready_elapsed*1000)
        if kind == "reaction":
            state = 2; timer = 0.75; finish_if_needed()
        elif used.count(true)+early.count(true) == 4:
            state = 2; timer = 0.75; finish_if_needed()
    return true
func new_question() -> void:
    var limit: int = LIMITS[difficulty]
    var op: int = rng.randi_range(0,3 if difficulty >= 4 else 1)
    var a: int = rng.randi_range(0,limit)
    var b: int = 0
    if op == 0:
        b = rng.randi_range(0,limit-a); answer = a+b; question = "%d + %d = ?"%[a,b]
    elif op == 1:
        b = rng.randi_range(0,a); answer = a-b; question = "%d - %d = ?"%[a,b]
    elif op == 2:
        a = rng.randi_range(1,maxi(1,int(sqrt(limit))))
        b = rng.randi_range(0,int(limit/a)); answer = a*b; question = "%d × %d = ?"%[a,b]
    else:
        var divisors: Array = []
        for value in range(1,mini(limit,30)+1):
            if a%value == 0: divisors.append(value)
        b = divisors[rng.randi_range(0,divisors.size()-1)]
        answer = int(a/b); question = "%d ÷ %d = ?"%[a,b]
    inputs = ["",""]; locked = [false,false]; timer = 0
func key(owner: int, action: String) -> bool:
    if done or locked[owner]: return false
    if action == "delete":
        inputs[owner] = inputs[owner].substr(0,maxi(0,inputs[owner].length()-1))
    elif action == "submit":
        if inputs[owner].is_empty(): return false
        if int(inputs[owner]) == answer:
            scores[owner] += 1; finish_if_needed()
            if not done: new_question()
        else:
            locked[owner] = true
            if locked[0] and locked[1]: timer = 0.8
    elif action.begins_with("digit"):
        var digit: String = action.trim_prefix("digit")
        if digit.length() != 1 or not digit.is_valid_int(): return false
        if inputs[owner] == "0": inputs[owner] = ""
        if inputs[owner].length() < str(LIMITS[difficulty]).length(): inputs[owner] += digit
    else: return false
    return true
func tick(delta: float) -> bool:
    if done: return false
    if kind == "math_duel":
        if locked[0] and locked[1]:
            timer -= delta
            if timer <= 0: new_question(); return true
        return false
    if state == 1: ready_elapsed += delta
    timer -= delta
    if timer > 0: return state == 1
    if state == 0:
        state = 1; timer = 3; ready_elapsed = 0
    elif state == 1:
        state = 2; timer = 0.75; finish_if_needed()
    else: next_round()
    return true
