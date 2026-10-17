extends RefCounted
const Physics = preload("res://core/physics2d.gd")
var kind: String = "bounce"
var size := Vector2(1000,1400)
var ball := Vector2(500,1100)
var velocity := Vector2(210,-620)
var radius: float = 15
var paddles: Array = [500.0,500.0]
var scores: Array = [0,0]
var score: int = 0
var speed: float = 650
var bricks: Array = []
var fruits: Array = []
var particles: Array = []
var lives: int = 3
var bomb_hits: int = 0
var missed: int = 0
var spawn_time: float = 0.6
var elapsed: float = 0
var serve_time: float = 0.8
var done: bool = false
var result: String = ""
var won: bool = false
var rng := RandomNumberGenerator.new()
const VALUES = [20,15,15,10,10,10,5,5]
const NAMES = ["瓜","橙","苹","蕉","葡","猕","莓","樱"]
func reset(id: String) -> void:
    kind = id; size = Vector2(1000,600) if id == "fruit" else Vector2(1000,1400)
    ball = Vector2(500,700 if id == "pong" else 1100)
    velocity = Vector2(210,-620); paddles = [500.0,500.0]; scores = [0,0]
    score = 0; speed = 650; lives = 3; bomb_hits = 0; missed = 0
    bricks = []; fruits = []; particles = []; spawn_time = 0.6; elapsed = 0; serve_time = 0.8
    done = false; result = ""; won = false
    if id in ["bounce","breakout"]:
        for y in range(5):
            for x in range(8): bricks.append({"rect":Rect2(35+x*117,120+y*67,105,51),"hp":rng.randi_range(1,3) if id == "breakout" else 1})
func paddle(owner: int) -> Rect2:
    return Rect2(paddles[owner]-100,1320 if owner == 0 else 60,200,24)
func set_paddle(owner: int, x: float) -> void:
    paddles[owner] = clampf(x,100,900)
func rally_step(delta: float) -> void:
    var steps: int = clampi(ceili(velocity.length()*delta/(radius*0.5)),1,32)
    for step in range(steps):
        ball += velocity*delta/steps
        if ball.x < radius: ball.x = radius; velocity.x = absf(velocity.x)
        elif ball.x > size.x-radius: ball.x = size.x-radius; velocity.x = -absf(velocity.x)
        if kind != "pong" and ball.y < radius: ball.y = radius; velocity.y = absf(velocity.y)
        for owner in range(2 if kind == "pong" else 1):
            if (owner == 0 and velocity.y <= 0) or (owner == 1 and velocity.y >= 0): continue
            var hit: Dictionary = Physics.circle_rect(ball,radius,paddle(owner))
            if not hit.is_empty():
                ball.y = paddle(owner).position.y-radius if owner == 0 else paddle(owner).end.y+radius
                if kind == "pong": speed = minf(2400,speed*1.07)
                var vx: float = clampf((ball.x-paddles[owner])/100,-1,1)*speed*0.78
                velocity = Vector2(vx,(-1 if owner == 0 else 1)*sqrt(maxf(1,speed*speed-vx*vx)))
        if kind != "pong":
            for brick in bricks:
                if brick.hp <= 0: continue
                var hit: Dictionary = Physics.circle_rect(ball,radius,brick.rect)
                if hit.is_empty(): continue
                ball += hit.normal*hit.depth
                if velocity.dot(hit.normal) < 0: velocity -= 2*velocity.dot(hit.normal)*hit.normal
                brick.hp -= 1; score += 10; break
            var any: bool = false
            for brick in bricks:
                if brick.hp > 0: any = true
            if not any: done = true; won = true; result = "全部砖块已清除"; return
        if ball.y > size.y+radius or (kind == "pong" and ball.y < -radius):
            if kind == "pong":
                var owner: int = 1 if ball.y > size.y else 0
                scores[owner] += 1
                if scores[owner] >= 5:
                    done = true; result = "玩家%d以%d : %d获胜"%[owner+1,scores[0],scores[1]]; return
                ball = size/2; serve_time = 0.8
                velocity = Vector2(210,-620 if owner == 0 else 620); speed = velocity.length()
                return
            done = true; result = "小球漏出底部 · 得分%d"%score; return
func spawn_fruit() -> void:
    var stage: int = int(elapsed/30)
    var count: int = rng.randi_range(1,mini(5,2+stage))
    for i in range(count):
        var bomb: bool = rng.randf() < minf(0.35,0.12+stage*0.025)
        fruits.append({"p":Vector2(rng.randf_range(70,930),650),"v":Vector2(rng.randf_range(-160,160),-rng.randf_range(650,850)),
            "radius":30.0 if bomb else rng.randf_range(28,42),"type":rng.randi_range(0,7),"bomb":bomb})
func cut(a: Vector2, b: Vector2) -> int:
    if done or kind != "fruit": return 0
    var hits: int = 0
    for i in range(fruits.size()-1,-1,-1):
        var fruit: Dictionary = fruits[i]
        if Physics.segment_distance(fruit.p,a,b) > fruit.radius: continue
        hits += 1
        if fruit.bomb: lives -= 1; bomb_hits += 1
        else: score += VALUES[fruit.type]
        for sign in [-1,1]:
            particles.append({"p":fruit.p,"v":Vector2(sign*150,-150),"life":0.6,"type":fruit.type,"bomb":fruit.bomb})
        fruits.remove_at(i)
    if lives <= 0: done = true; result = "得分%d · 切中%d枚炸弹，漏掉%d个水果"%[score,bomb_hits,missed]
    return hits
func fruit_step(delta: float) -> void:
    elapsed += delta; spawn_time -= delta
    if spawn_time <= 0: spawn_fruit(); spawn_time = maxf(0.28,0.9-int(elapsed/30)*0.08)
    for i in range(fruits.size()-1,-1,-1):
        var fruit: Dictionary = fruits[i]
        fruit.v.y += 900*delta; fruit.p += fruit.v*delta
        if fruit.p.x < fruit.radius: fruit.p.x = fruit.radius; fruit.v.x = absf(fruit.v.x)
        elif fruit.p.x > size.x-fruit.radius: fruit.p.x = size.x-fruit.radius; fruit.v.x = -absf(fruit.v.x)
        if fruit.p.y < fruit.radius: fruit.p.y = fruit.radius; fruit.v.y = absf(fruit.v.y)*0.7
        if fruit.p.y > size.y+fruit.radius and fruit.v.y > 0:
            if not fruit.bomb: lives -= 1; missed += 1
            fruits.remove_at(i)
    for particle in particles:
        particle.life -= delta; particle.v.y += 900*delta; particle.p += particle.v*delta
    particles = particles.filter(func(p): return p.life > 0)
    if lives <= 0: done = true; result = "得分%d · 切中%d枚炸弹，漏掉%d个水果"%[score,bomb_hits,missed]
func tick(delta: float) -> bool:
    if done: return false
    if kind == "fruit": fruit_step(delta); return true
    if serve_time > 0: serve_time = maxf(0,serve_time-delta); return true
    rally_step(delta)
    return true
