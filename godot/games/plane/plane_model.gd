extends RefCounted
## 六关战役；模型独立于渲染，所有敌方弹药发射后保持固定速度方向。
const LEVEL_NAMES = ["启航训练","峡谷巡逻","导弹警戒","重装空域","要塞外围","最终防线"]
const BOSS_NAMES = ["苍隼 · 巡航长机","疾风 · 穿梭猎手","铜蛇 · 导弹巡洋舰","磐石 · 重装壁垒","极光 · 护卫航母","苍穹 · 终极旗舰"]
const BOSS_TACTICS = ["先看瞄准线，再移动","留意交叉线之间的空隙","击落橙色导弹，保持走位","观察左右发射区，提前换位","先清护卫，再攻击航母","避开轮换火力，攻击核心"]
const BOSS_HP = [60.0,105.0,170.0,250.0,350.0,500.0]
const PACK_NAMES = {"wing":"弹道 +1","rapid":"射速提升","missile":"导弹装备","homing":"追踪导弹","shield":"护盾 +1","repair":"机体维修"}
const PLAYER_RADIUS: float = 18.0
const ENEMY_RADIUS: float = 24.0
var size := Vector2(720,1280)
var player := Vector2.ZERO
var bullets: Array = []
var enemies: Array = []
var enemy_shots: Array = []
var warnings: Array = []
var pickups: Array = []
var events: Array = []
var boss: Dictionary = {}
var lives: int = 5
var shields: int = 0
var score: int = 0
var over: bool = false
var victory: bool = false
var reason: String = ""
var lanes: int = 1
var rapid: int = 0
var weapon: int = 0
var level: int = 1
var phase: String = "wave"
var wave_time: float = 0
var total_time: float = 0
var transition_time: float = 0
var fire_timer: float = 0
var missile_timer: float = 0
var spawn_timer: float = 1.2
var invulnerable: float = 0
var dropped: Dictionary = {}
var kills: int = 0
var next_id: int = 1
var random := RandomNumberGenerator.new()

func configure(view_size: Vector2) -> void:
    size = view_size
    if player != Vector2.ZERO: move_player(player)
func reset() -> void:
    random.randomize()
    for array in [bullets,enemies,enemy_shots,warnings,pickups,events]: array.clear()
    boss.clear(); dropped.clear()
    player = Vector2(size.x*0.5,size.y*0.8)
    lives = 5; shields = 0; score = 0; over = false; victory = false; reason = ""
    lanes = 1; rapid = 0; weapon = 0; level = 1; phase = "wave"
    wave_time = 0; total_time = 0; transition_time = 0; fire_timer = 0; missile_timer = 0; spawn_timer = 1.2
    invulnerable = 0; kills = 0; next_id = 1
func move_player(target: Vector2) -> void:
    player = Vector2(clampf(target.x,34,size.x-34),clampf(target.y,maxf(590,size.y*0.4),size.y-100))
func wave_duration() -> float:
    return minf(42,20+level*4)
func fire_interval() -> float:
    return [0.30,0.24,0.19][rapid]
func make_bullet(at: Vector2, velocity: Vector2, kind: int = -1) -> Dictionary:
    var type: int = weapon if kind < 0 else kind
    return {"position":at,"previous":at,"velocity":velocity,"weapon":type,"damage":[1.0,2.0,1.6][type],"radius":5.0 if type == 0 else 9.0,"target":-1,"age":0.0}
func make_enemy(kind: String, at: Vector2, speed_override: float = -1) -> Dictionary:
    var stats: Array = {"scout":[1.0,0.20,10,24.0],"weaver":[2.0,0.16,15,26.0],"armor":[5.0,0.11,25,34.0],"launcher":[4.0,0.10,30,31.0]}[kind]
    var hp: float = float(stats[0])+floorf((level-1)/2.0)*(1.0 if kind in ["scout","weaver"] else 2.0)
    var id: int = next_id; next_id += 1
    return {"id":id,"kind":kind,"position":at,"previous":at,"origin_x":at.x,"speed":size.y*float(stats[1]) if speed_override < 0 else speed_override,"hp":hp,"max_hp":hp,"points":int(stats[2]),"radius":float(stats[3]),"age":0.0,"fire":random.randf_range(1.8,3.0)}
func collect_pack(kind: String) -> void:
    match kind:
        "wing":
            if lanes < 3: lanes += 1
        "rapid":
            if rapid < 2: rapid += 1
        "missile":
            weapon = maxi(weapon,1)
        "homing":
            weapon = 2
        "shield": shields = mini(2,shields+1)
        "repair":
            if lives < 5: lives += 1
    events.append({"kind":"pack","position":player,"pack":kind,"text":PACK_NAMES.get(kind,"补给送达")})
func drop_pack(kind: String, at: Vector2) -> void:
    if pickups.size() >= 4:
        pickups.remove_at(0)
    pickups.append({"position":Vector2(clampf(at.x,60,size.x-60),at.y),"kind":kind,"age":0.0})
func damage_player(message: String) -> void:
    if over or invulnerable > 0: return
    if shields > 0:
        shields -= 1; invulnerable = 0.8
        events.append({"kind":"shield","position":player}); return
    lives = maxi(0,lives-1); invulnerable = 1.6
    events.append({"kind":"damage","position":player})
    if lives == 0: _end(message,false)
func _end(message: String, success: bool) -> void:
    if over: return
    over = true; victory = success; reason = message
    events.append({"kind":"end","position":player})
func start_boss() -> void:
    phase = "boss"; enemies.clear(); enemy_shots.clear(); warnings.clear()
    boss = {"position":Vector2(size.x*0.5,455),"previous":Vector2(size.x*0.5,455),"hp":BOSS_HP[level-1],"max_hp":BOSS_HP[level-1],"radius":68.0 if level < 6 else 90.0,"age":0.0,"fire":2.0,"escort":5.0,"stage":1,"attack":0}
    events.append({"kind":"boss","position":boss.position,"text":BOSS_NAMES[level-1]+" 来袭"})
func damage_boss(amount: float) -> void:
    if phase != "boss" or boss.is_empty() or over: return
    boss.hp = maxf(0,float(boss.hp)-amount)
    if boss.hp > 0: return
    var at: Vector2 = boss.position
    score += level*120; boss.clear(); enemies.clear(); enemy_shots.clear(); warnings.clear(); bullets.clear()
    lives = mini(5,lives+1); invulnerable = 2
    events.append({"kind":"boss_down","position":at})
    if level == 6:
        _end("六关通关！苍穹堡垒已击破",true)
    else:
        phase = "transition"; transition_time = 3
        events.append({"kind":"level","position":player,"text":"第%d关完成 · 维修 +1"%level})
func _fire_player() -> void:
    if bullets.size()+lanes > 72: return
    for i in range(lanes):
        var offset: float = (i-(lanes-1)*0.5)*22
        bullets.append(make_bullet(player+Vector2(offset,-38),Vector2(offset*2.4,-900),0))
    events.append({"kind":"fire","position":player})
func _fire_missile() -> void:
    if weapon == 0 or bullets.size() >= 72: return
    bullets.append(make_bullet(player+Vector2(0,-48),Vector2(0,-720),weapon))
    # 导弹是独立副武器，不受快射和三发数量加成。
    missile_timer = 1.1 if weapon == 2 else 0.85
func _scheduled_packs() -> void:
    var plan: Array = {1:[[6.0,"wing"],[14.0,"rapid"]],2:[[6.0,"wing"],[15.0,"missile"]],3:[[6.0,"rapid"],[15.0,"homing"]]}.get(level,[])
    for entry in plan:
        var key: String = "%d:%s"%[level,entry[1]]
        if wave_time >= float(entry[0]) and not dropped.has(key):
            dropped[key] = true
            drop_pack(str(entry[1]),Vector2(clampf(player.x+random.randf_range(-100,100),70,size.x-70),270))
func _spawn_enemy() -> void:
    if enemies.size() >= 12: return
    var roll: float = random.randf()
    var kind: String = "scout"
    if level >= 3 and roll < 0.23: kind = "launcher"
    elif level >= 2 and roll < 0.40: kind = "armor"
    elif level >= 2 and roll < 0.72: kind = "weaver"
    enemies.append(make_enemy(kind,Vector2(random.randf_range(65,size.x-65),295)))
func _warn(owner: int, at: Vector2, target: Vector2, count: int, missile: bool) -> void:
    if warnings.size() >= 4: return
    warnings.append({"owner":owner,"position":at,"target":target,"count":count,"missile":missile,"time":0.9})
    events.append({"kind":"warning","position":at})
func _owner_alive(id: int) -> bool:
    if id == 0: return not boss.is_empty()
    for e in enemies:
        if int(e.id) == id: return true
    return false
func _advance_warnings(dt: float) -> void:
    for i in range(warnings.size()-1,-1,-1):
        var w: Dictionary = warnings[i]
        if not _owner_alive(int(w.owner)): warnings.remove_at(i); continue
        w.time -= dt
        if w.time > 0: continue
        var velocity: Vector2 = (Vector2(w.target)-Vector2(w.position)).normalized()*((310 if bool(w.missile) else 250)+maxi(0,level-2)*25)
        for shot in range(int(w.count)):
            if enemy_shots.size() >= 28: break
            if bool(w.missile) and _missile_count() >= 10: break
            var heading: Vector2 = velocity.rotated((shot-(int(w.count)-1)*0.5)*0.27)
            enemy_shots.append({"position":w.position,"previous":w.position,"velocity":heading,"missile":w.missile,"hp":2.0,"radius":12.0 if bool(w.missile) else 7.0})
        warnings.remove_at(i)
func _missile_count() -> int:
    var count: int = 0
    for shot in enemy_shots:
        if bool(shot.missile): count += 1
    return count
func _advance_enemies(dt: float) -> void:
    for i in range(enemies.size()-1,-1,-1):
        var e: Dictionary = enemies[i]
        e.previous = e.position; e.age += dt
        if str(e.kind) == "weaver": e.position.x = clampf(float(e.origin_x)+sin(float(e.age)*2.4)*70,40,size.x-40)
        if str(e.kind) != "launcher" or e.position.y < size.y*0.28 or float(e.age) > 9: e.position.y += float(e.speed)*dt
        if level >= 2 and e.position.y > 260 and e.position.y < player.y-70 and (str(e.kind) in ["launcher","armor"] or level >= 4):
            e.fire -= dt
            if e.fire <= 0:
                _warn(int(e.id),e.position,player,1,str(e.kind) == "launcher")
                e.fire = (3.8 if str(e.kind) == "launcher" else 4.8)-maxi(0,level-2)*0.45
        if e.position.y > size.y+60: enemies.remove_at(i)
func _advance_boss(dt: float) -> void:
    if boss.is_empty(): return
    boss.previous = boss.position; boss.age += dt
    boss.position = Vector2(size.x*0.5+sin(float(boss.age)*0.85)*size.x*0.22,455+sin(float(boss.age)*0.7)*16)
    var ratio: float = float(boss.hp)/float(boss.max_hp)
    var previous_stage: int = int(boss.stage)
    boss.stage = 3 if ratio <= 0.25 else (2 if ratio <= 0.60 else 1)
    if int(boss.stage) != previous_stage: events.append({"kind":"boss_phase","position":boss.position,"text":"Boss 第%d阶段 · 留意新的预警路线"%boss.stage})
    boss.fire -= dt; boss.escort -= dt
    if boss.fire <= 0:
        boss.attack += 1
        _boss_attack()
        boss.fire = [3.2,2.7,2.4,2.1,1.9,1.75][level-1]-0.12*(int(boss.stage)-1)
    if level >= 5 and boss.escort <= 0 and enemies.size() < (2 if level == 5 else 3):
        enemies.append(make_enemy("weaver" if int(boss.stage) >= 2 else "scout",Vector2(size.x*0.2 if int(boss.attack)%2 == 0 else size.x*0.8,295)))
        boss.escort = 5.2 if level == 5 else 4.6
func _boss_attack() -> void:
    var at: Vector2 = boss.position+Vector2(0,60)
    var stage: int = int(boss.stage)
    var cycle: int = int(boss.attack)
    # 固定区域火力之外，每轮留一条瞄准当前位置的普通弹道，必须走位躲开。
    if level >= 2: _warn(0,at,player,1,false)
    match level:
        1: _warn(0,at,player,1 if stage == 1 else 3,false)
        2:
            # Crossfire locks two positions before firing; neither stream tracks.
            _warn(0,at+Vector2(-70,0),Vector2(size.x*0.65,player.y),1,false)
            _warn(0,at+Vector2(70,0),Vector2(size.x*0.35,player.y),1,false)
        3: _warn(0,at,player,1 if stage == 1 else 3,true)
        4:
            # Side zones alternate; an open side remains visible in the warning.
            var side: float = -75 if cycle%2 == 0 else 75
            _warn(0,at+Vector2(side,0),Vector2(size.x*(0.25 if side < 0 else 0.75),player.y),3,true)
            if stage == 3: _warn(0,at-Vector2(side,0),player,1,false)
        5:
            _warn(0,at,player,3,false)
            if stage >= 2: _warn(0,at+Vector2(-75 if cycle%2 == 0 else 75,0),player,1,true)
        6:
            if cycle%3 == 1: _warn(0,at,player,3 if stage == 1 else 5,true)
            elif cycle%3 == 2:
                _warn(0,at+Vector2(-85,0),Vector2(size.x*0.65,player.y),2,false)
                _warn(0,at+Vector2(85,0),Vector2(size.x*0.35,player.y),2,false)
            else:
                _warn(0,at+Vector2(-80,0),Vector2(size.x*0.25,player.y),1,true)
                _warn(0,at+Vector2(80,0),Vector2(size.x*0.75,player.y),1,true)
func _kill_enemy(index: int) -> void:
    var e: Dictionary = enemies[index]
    events.append({"kind":"hit","position":e.position})
    score += int(e.points); kills += 1
    if kills%9 == 0: drop_pack("repair" if int(kills/9)%2 == 0 else "shield",e.position)
    enemies.remove_at(index)
func _splash(at: Vector2) -> void:
    for i in range(enemies.size()-1,-1,-1):
        if at.distance_to(enemies[i].position) < 50:
            enemies[i].hp -= 0.65
            if enemies[i].hp <= 0: _kill_enemy(i)
func _home(b: Dictionary, dt: float) -> void:
    var target: Vector2
    var best: float = INF
    for e in enemies:
        if e.position.y > b.position.y+40: continue
        var distance: float = b.position.distance_squared_to(e.position)
        if distance < best: best = distance; target = e.position
    if not boss.is_empty() and boss.position.y < b.position.y:
        var distance: float = b.position.distance_squared_to(boss.position)
        if distance < best: best = distance; target = boss.position
    if best == INF: return
    var velocity: Vector2 = b.velocity
    var turn: float = clampf(velocity.angle_to(target-Vector2(b.position)),-2.4*dt,2.4*dt)
    b.velocity = velocity.rotated(turn)
func _advance_bullets(dt: float) -> void:
    for i in range(bullets.size()-1,-1,-1):
        var b: Dictionary = bullets[i]
        b.age += dt
        if int(b.weapon) == 2: _home(b,dt)
        var previous: Vector2 = b.position
        var current: Vector2 = previous+Vector2(b.velocity)*dt
        var first: float = INF
        var hit_type: String = ""
        var hit_index: int = -1
        for e in range(enemies.size()):
            var contact: float = _hit_fraction(previous-Vector2(enemies[e].previous),current-Vector2(enemies[e].position),float(enemies[e].radius)+float(b.radius))
            if contact < first: first = contact; hit_type = "enemy"; hit_index = e
        for s in range(enemy_shots.size()):
            if not bool(enemy_shots[s].missile): continue
            var contact: float = _hit_fraction(previous-Vector2(enemy_shots[s].previous),current-Vector2(enemy_shots[s].position),float(enemy_shots[s].radius)+float(b.radius))
            if contact < first: first = contact; hit_type = "missile"; hit_index = s
        if not boss.is_empty():
            var contact: float = _hit_fraction(previous-Vector2(boss.previous),current-Vector2(boss.position),float(boss.radius)+float(b.radius))
            if contact < first: first = contact; hit_type = "boss"
        if hit_type != "":
            var impact: Vector2 = previous.lerp(current,first)
            if hit_type == "enemy":
                enemies[hit_index].hp -= float(b.damage)
                if enemies[hit_index].hp <= 0: _kill_enemy(hit_index)
                else: events.append({"kind":"impact","position":impact})
            elif hit_type == "missile":
                enemy_shots[hit_index].hp -= float(b.damage)
                if enemy_shots[hit_index].hp <= 0:
                    enemy_shots.remove_at(hit_index); score += 3
                    events.append({"kind":"intercept","position":impact})
            elif hit_type == "boss":
                damage_boss(float(b.damage))
                if phase != "boss" or over: return
                events.append({"kind":"impact","position":impact})
            if int(b.weapon) == 1: _splash(impact)
            bullets.remove_at(i)
        elif current.y < 160 or current.y > size.y+80 or current.x < -60 or current.x > size.x+60 or float(b.age) > 4:
            bullets.remove_at(i)
        else:
            b.previous = previous; b.position = current
func _advance_pickups(dt: float) -> void:
    for i in range(pickups.size()-1,-1,-1):
        var p: Dictionary = pickups[i]
        p.age += dt; p.position.y += 95*dt
        if p.position.distance_to(player) < 85: p.position = Vector2(p.position).move_toward(player,280*dt)
        if p.position.distance_to(player) < 48:
            collect_pack(str(p.kind)); pickups.remove_at(i)
        elif float(p.age) >= 12 or p.position.y > size.y+60:
            pickups.remove_at(i)
func tick(delta: float) -> void:
    if over: return
    var dt: float = clampf(delta,0,0.05)
    events.clear(); total_time += dt; invulnerable = maxf(0,invulnerable-dt)
    _advance_pickups(dt)
    if phase == "transition":
        transition_time -= dt
        if transition_time <= 0:
            level += 1; phase = "wave"; wave_time = 0; spawn_timer = 1.0
        return
    if phase == "wave":
        wave_time += dt; _scheduled_packs()
        spawn_timer -= dt
        if spawn_timer <= 0:
            _spawn_enemy(); spawn_timer += maxf(0.48,1.15-level*0.10)
        if wave_time >= wave_duration(): start_boss()
    else: _advance_boss(dt)
    _advance_enemies(dt); _advance_warnings(dt)
    for shot in enemy_shots:
        shot.previous = shot.position
        shot.position += Vector2(shot.velocity)*dt
    fire_timer -= dt
    if fire_timer <= 0: _fire_player(); fire_timer += fire_interval()
    missile_timer -= dt
    if weapon > 0 and missile_timer <= 0: _fire_missile()
    _advance_bullets(dt)
    if over or phase == "transition": return
    for i in range(enemy_shots.size()-1,-1,-1):
        var shot: Dictionary = enemy_shots[i]
        if _hit_fraction(Vector2(shot.previous)-player,Vector2(shot.position)-player,PLAYER_RADIUS+float(shot.radius)) < INF:
            enemy_shots.remove_at(i); damage_player("机体耐久耗尽 · 试着避开红色弹道")
            if over: return
        elif shot.position.y < 240 or shot.position.y > size.y+50 or shot.position.x < -60 or shot.position.x > size.x+60: enemy_shots.remove_at(i)
    if not boss.is_empty() and player.distance_to(boss.position) < PLAYER_RADIUS+float(boss.radius):
        damage_player("机体耐久耗尽 · 避开Boss核心")
        if over: return
    for i in range(enemies.size()-1,-1,-1):
        var e: Dictionary = enemies[i]
        if _hit_fraction(Vector2(e.previous)-player,Vector2(e.position)-player,PLAYER_RADIUS+float(e.radius)) < INF:
            enemies.remove_at(i); damage_player("机体耐久耗尽 · 给敌机留一点距离")
            if over: return
func _hit_fraction(from: Vector2, to: Vector2, radius: float) -> float:
    var c: float = from.length_squared()-radius*radius
    if c <= 0: return 0.0
    var motion: Vector2 = to-from
    var a: float = motion.length_squared()
    if a <= 0.000001: return INF
    var b: float = 2.0*from.dot(motion)
    var discriminant: float = b*b-4*a*c
    if discriminant < 0: return INF
    var time: float = (-b-sqrt(discriminant))/(2*a)
    return time if time >= 0 and time <= 1 else INF
