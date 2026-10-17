extends RefCounted
const Physics = preload("res://core/physics2d.gd")
const COLORS = [Color("dc3026"),Color("e3bb65"),Color("f6a729"),Color("f2cf39"),Color("8fb736"),Color("bbd87c"),Color("df3545")]
const NAMES = ["西瓜","苹果","橙子","菠萝","猕猴桃","梨","草莓"]
const GRAVITY: float = 1120
var size := Vector2(1280,720)
var fruits: Array = []
var halves: Array = []
var drops: Array = []
var stains: Array = []
var events: Array = []
var combos: Dictionary = {}
var rng := RandomNumberGenerator.new()
var score: int = 0
var lives: int = 3
var missed: int = 0
var bomb_hits: int = 0
var elapsed: float = 0
var spawn_time: float = 0.6
var wave: int = 0
var done: bool = false
var won: bool = false
var result: String = ""
var critical_chance: float = 0.04
func configure(value: Vector2) -> void:
    size = value
func reset(_id: String = "fruit") -> void:
    for a in [fruits,halves,drops,stains,events]: a.clear()
    combos.clear(); score = 0; lives = 3; missed = 0; bomb_hits = 0
    elapsed = 0; spawn_time = 0.75; wave = 0; done = false; won = false; result = ""
func make_fruit(at: Vector2, velocity: Vector2, type: int = 0, bomb: bool = false) -> Dictionary:
    return {"p":at,"v":velocity,"radius":34.0 if bomb else (44.0 if type == 0 else 38.0),"type":type,"bomb":bomb,"angle":rng.randf_range(-PI,PI),"spin":rng.randf_range(-2.0,2.0)}
func spawn_fruit() -> void:
    if fruits.size() >= 18: return
    wave += 1
    var difficulty: int = mini(5,int(score/25))
    var count: int = 1 if wave <= 2 else rng.randi_range(2,mini(6,3+difficulty))
    var center: float = rng.randf_range(size.x*0.2,size.x*0.8)
    for i in range(count):
        if fruits.size() >= 18: break
        var vy: float = -rng.randf_range(950,1120)
        var vx: float = rng.randf_range(-160,160)
        var target: float = clampf(center+(i-(count-1)*0.5)*rng.randf_range(65,100),90,size.x-90)
        var x: float = clampf(target-vx*(-vy/GRAVITY),80,size.x-80)
        fruits.append(make_fruit(Vector2(x,size.y+65+i*7),Vector2(vx,vy),rng.randi_range(0,6)))
    if elapsed > 7 and rng.randf() < minf(0.5,0.22+difficulty*0.045) and fruits.size() < 18:
        var x: float = clampf(center+rng.randf_range(-120,120),100,size.x-100)
        fruits.append(make_fruit(Vector2(x,size.y+95),Vector2(rng.randf_range(-70,70),-rng.randf_range(980,1100)),0,true))
func _add_score(value: int) -> void:
    var previous: int = int(score/100)
    score += value
    if int(score/100) > previous and lives < 3:
        lives = mini(3,lives+int(score/100)-previous)
        events.append({"kind":"restore","p":Vector2(size.x*0.5,100)})
func _finish(bomb: bool) -> void:
    if done: return
    done = true; combos.clear()
    result = ("切中炸弹！" if bomb else "漏掉三次水果！")+"\n本局%d分 · 漏果%d个"%[score,missed]
    events.append({"kind":"bomb" if bomb else "end","p":Vector2(size.x*0.5,size.y*0.5)})
func cut(a: Vector2, b: Vector2, finger: int = 0) -> int:
    if done or a.distance_to(b) < 7: return 0
    var candidates: Array = []
    var direction: Vector2 = b-a
    for fruit in fruits:
        if Physics.segment_distance(fruit.p,a,b) <= fruit.radius:
            candidates.append({"fruit":fruit,"t":clampf((Vector2(fruit.p)-a).dot(direction)/direction.length_squared(),0,1)})
    candidates.sort_custom(func(x,y): return x.t < y.t)
    var hits: int = 0
    for hit in candidates:
        var fruit: Dictionary = hit.fruit
        fruits.erase(fruit); hits += 1
        if fruit.bomb:
            bomb_hits += 1; lives = 0
            events.append({"kind":"blast","p":fruit.p})
            _finish(true); break
        var critical: bool = rng.randf() < critical_chance
        _add_score(10 if critical else 1)
        events.append({"kind":"critical" if critical else "slice","p":fruit.p,"type":fruit.type})
        _split(fruit,direction.normalized())
        if not combos.has(finger) or elapsed-float(combos[finger].time) > 0.16: combos[finger] = {"count":0,"time":elapsed}
        combos[finger].count += 1; combos[finger].time = elapsed
        var n: int = combos[finger].count
        if n >= 3:
            _add_score(3 if n == 3 else 1)
            events.append({"kind":"combo","p":fruit.p,"count":n,"bonus":n})
    return hits
func release(finger: int) -> void:
    combos.erase(finger)
func _split(fruit: Dictionary, direction: Vector2) -> void:
    var normal := Vector2(-direction.y,direction.x)
    var tint: Color = COLORS[fruit.type]
    for sign in [-1,1]:
        if halves.size() >= 56: halves.pop_front()
        halves.append({"p":Vector2(fruit.p)+normal*sign*9,"v":Vector2(fruit.v)*0.2+normal*sign*175+Vector2(0,-85),"type":fruit.type,"angle":direction.angle()+sign*0.18,"spin":sign*rng.randf_range(2.0,4.0),"radius":float(fruit.radius)*0.90,"life":1.3})
    if stains.size() >= 42: stains.pop_front()
    stains.append({"p":fruit.p,"radius":rng.randf_range(24,48),"color":tint,"life":12.0,"angle":rng.randf_range(0,TAU),"seed":rng.randi_range(0,100)})
    for i in range(10):
        if drops.size() >= 180: drops.pop_front()
        drops.append({"p":fruit.p,"v":Vector2.from_angle(rng.randf_range(0,TAU))*rng.randf_range(70,300),"radius":rng.randf_range(2,7),"color":tint,"life":rng.randf_range(0.35,0.8)})
func tick(delta: float) -> bool:
    var dt: float = clampf(delta,0,0.05)
    events.clear()
    for half in halves:
        half.life -= dt; half.v.y += GRAVITY*dt; half.p += half.v*dt; half.angle += half.spin*dt
    halves = halves.filter(func(h): return h.life > 0 and h.p.y < size.y+120)
    for drop in drops:
        drop.life -= dt; drop.v.y += GRAVITY*0.65*dt; drop.p += drop.v*dt
    drops = drops.filter(func(d): return d.life > 0)
    for stain in stains: stain.life -= dt
    stains = stains.filter(func(s): return s.life > 0)
    if done: return true
    elapsed += dt; spawn_time -= dt
    if spawn_time <= 0:
        spawn_fruit()
        spawn_time = rng.randf_range(maxf(0.85,1.6-score*0.003),maxf(1.25,2.15-score*0.003))
    for i in range(fruits.size()-1,-1,-1):
        var fruit: Dictionary = fruits[i]
        fruit.v.y += GRAVITY*dt; fruit.p += fruit.v*dt
        fruit.angle = float(fruit.get("angle",0))+float(fruit.get("spin",0))*dt
        if fruit.p.y > size.y+float(fruit.radius)+100 and fruit.v.y > 0:
            fruits.remove_at(i)
            if not fruit.bomb:
                lives = maxi(0,lives-1); missed += 1
                events.append({"kind":"miss","p":Vector2(clampf(fruit.p.x,40,size.x-40),size.y-42)})
                if lives <= 0: _finish(false); break
    return true
