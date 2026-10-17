extends "res://core/game_content.gd"
const Model = preload("res://games/plane/plane_model.gd")
const PLAYER = preload("res://games/plane/assets/player.svg")
const ENEMY = preload("res://games/plane/assets/enemy.svg")
const ENEMY_TEXTURES = {"scout":ENEMY,"launcher":ENEMY,"weaver":preload("res://games/plane/assets/enemy-weaver.svg"),"armor":preload("res://games/plane/assets/enemy-armor.svg")}
const BACKGROUND = preload("res://games/plane/assets/space.svg")
const BOSS_PATHS = ["res://games/plane/assets/boss-1.png","res://games/plane/assets/boss-2.png","res://games/plane/assets/boss-3.png","res://games/plane/assets/boss-4.png","res://games/plane/assets/boss-5.png","res://games/plane/assets/boss-6.png"]
const BOSS_WIDTHS = [258.0,292.0,278.0,320.0,338.0,370.0]
const INK := Color("e7f1fc")
const CYAN := Color("75eadb")
const ORANGE := Color("ffc084")
const PURPLE := Color("c7b2fb")
var model = Model.new()
var ship: Sprite2D
var boss_sprite: Sprite2D
var boss_texture_level: int = 0
var boss_hit_time: float = 0
var enemy_sprites: Array[Sprite2D] = []
var stars: Array = []
var sparks: Array = []
var elapsed: float = 0
var touch_id: int = -1
var active: bool = false
var flash: float = 0
var screen_size := Vector2.ZERO
var font: Font
var styles: Dictionary = {}
var toast: String = ""
var toast_time: float = 0
var intro_time: float = 0

func prepare() -> void:
    screen_size = get_viewport_rect().size
    font = ThemeDB.fallback_font
    model.configure(screen_size); model.reset()
    var random := RandomNumberGenerator.new(); random.seed = 2027
    for i in range(80): stars.append({"position":Vector2(random.randf_range(0,screen_size.x),random.randf_range(0,screen_size.y)),"depth":random.randf_range(0.25,1.0)})
    ship = Sprite2D.new(); ship.texture = PLAYER; ship.scale = Vector2.ONE*0.69
    add_child(ship); ship.position = model.player
    boss_sprite = Sprite2D.new(); boss_sprite.visible = false; add_child(boss_sprite)
    get_viewport().size_changed.connect(_resize)
    set_process(false); queue_redraw()
func start_round() -> void:
    model.reset(); sparks.clear(); elapsed = 0; flash = 0; touch_id = -1; active = true
    round_success = true; toast = "拖动飞机 · 自动开火"; toast_time = 3; intro_time = 0
    score_changed.emit(0); lives_changed.emit(5)
    _sync_sprites(); set_process(true)
    print("PLANE_ROUND_STARTED campaign=6")
func set_game_paused(value: bool) -> void:
    touch_id = -1; active = not value and not model.over; set_process(active)
func _resize() -> void:
    screen_size = get_viewport_rect().size; model.configure(screen_size); _sync_sprites(); queue_redraw()
func _process(delta: float) -> void:
    var dt: float = minf(delta,0.05)
    boss_hit_time = maxf(0,boss_hit_time-dt)
    elapsed += dt; toast_time = maxf(0,toast_time-dt); intro_time = maxf(0,intro_time-dt); flash = maxf(0,flash-dt*2.5)
    for star in stars: star.position.y = fmod(star.position.y+dt*(30+float(star.depth)*100),screen_size.y)
    for i in range(sparks.size()-1,-1,-1):
        sparks[i].position += sparks[i].velocity*dt; sparks[i].life -= dt
        if sparks[i].life <= 0: sparks.remove_at(i)
    var old_score: int = model.score; var old_lives: int = model.lives
    model.tick(dt)
    for event in model.events:
        match str(event.kind):
            "fire": GamePlatform.feedback("laser")
            "hit": _burst(event.position,ORANGE,12); GamePlatform.feedback("hit_soft")
            "impact":
                _burst(event.position,CYAN,2)
                if not model.boss.is_empty() and event.position.distance_to(model.boss.position) < float(model.boss.radius)+20: boss_hit_time = 0.07
            "intercept": _burst(event.position,ORANGE,16); GamePlatform.feedback("hit_glass")
            "damage": flash = 0.65; _burst(model.player,ORANGE,16); GamePlatform.feedback("ui_error",true)
            "shield": _burst(model.player,CYAN,18); GamePlatform.feedback("hit_metal")
            "pack":
                toast = str(event.text)+" · 已装备"; toast_time = 2.8
                _burst(model.player,CYAN,14); GamePlatform.feedback("powerup")
            "warning": GamePlatform.feedback("ui_tick")
            "boss_phase":
                toast = str(event.text); toast_time = 2.4; GamePlatform.feedback("ui_tick")
            "boss":
                toast = str(event.text); toast_time = 3; intro_time = 2.3
                GamePlatform.feedback("levelup")
            "boss_down": _burst(event.position,ORANGE,60); GamePlatform.feedback("explode",true)
            "level": toast = str(event.text); toast_time = 3; GamePlatform.feedback("levelup")
            "end":
                if not model.victory: _burst(model.player,CYAN,32); GamePlatform.feedback("explode",true)
    if old_score != model.score: score_changed.emit(model.score)
    if old_lives != model.lives: lives_changed.emit(model.lives)
    _sync_sprites(); queue_redraw()
    if model.over:
        active = false; round_success = model.victory; set_process(false)
        round_finished.emit(model.score,model.reason)
func _burst(location: Vector2, color: Color, count: int) -> void:
    for i in range(count):
        if sparks.size() >= 80: break
        var angle: float = TAU*i/count+randf_range(-0.15,0.15)
        sparks.append({"position":location,"velocity":Vector2.from_angle(angle)*randf_range(65,260),"life":randf_range(0.3,0.7),"color":color})
func _sync_sprites() -> void:
    if not is_instance_valid(ship): return
    ship.position = model.player
    ship.modulate = Color("ffb0b0") if model.over and not model.victory else Color(1,1,1,0.45 if model.invulnerable > 0 and sin(elapsed*28) > 0 else 1.0)
    while enemy_sprites.size() < model.enemies.size():
        var sprite := Sprite2D.new(); sprite.texture = ENEMY; add_child(sprite); enemy_sprites.append(sprite)
    for i in range(enemy_sprites.size()):
        var sprite: Sprite2D = enemy_sprites[i]
        sprite.visible = i < model.enemies.size()
        if not sprite.visible: continue
        var e: Dictionary = model.enemies[i]
        sprite.texture = ENEMY_TEXTURES[e.kind]
        sprite.position = e.position
        sprite.rotation = sin(float(e.age)*2.4)*0.12 if e.kind == "weaver" else 0
        sprite.scale = Vector2.ONE*{"scout":0.56,"weaver":0.64,"armor":0.88,"launcher":0.76}[e.kind]
        sprite.modulate = {"scout":Color.WHITE,"weaver":Color.WHITE,"armor":Color.WHITE,"launcher":ORANGE}[e.kind]
    boss_sprite.visible = not model.boss.is_empty()
    if boss_sprite.visible:
        if boss_texture_level != model.level:
            boss_sprite.texture = load(BOSS_PATHS[model.level-1])
            boss_texture_level = model.level
        boss_sprite.scale = Vector2.ONE*BOSS_WIDTHS[model.level-1]/boss_sprite.texture.get_width()
        boss_sprite.modulate = Color("ffd6bf") if boss_hit_time > 0 else Color.WHITE
        boss_sprite.position = model.boss.position
        boss_sprite.rotation = sin(float(model.boss.age))*0.025
func _unhandled_input(event: InputEvent) -> void:
    if not active: return
    if event is InputEventScreenTouch:
        if event.pressed and touch_id == -1 and event.position.y > 250:
            touch_id = event.index; model.move_player(event.position+Vector2(0,-55))
        elif not event.pressed and event.index == touch_id: touch_id = -1
    elif event is InputEventScreenDrag and event.index == touch_id: model.move_player(event.position+Vector2(0,-55))
    elif event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT and event.position.y > 250: model.move_player(event.position+Vector2(0,-55))
    elif event is InputEventMouseMotion and Input.is_mouse_button_pressed(MOUSE_BUTTON_LEFT) and event.position.y > 250: model.move_player(event.position+Vector2(0,-55))
    _sync_sprites()
func _box(rect: Rect2, color: Color, radius: int = 12) -> void:
    var key: String = color.to_html()+str(radius)
    if not styles.has(key):
        var style := StyleBoxFlat.new(); style.bg_color = color; style.set_corner_radius_all(radius); styles[key] = style
    draw_style_box(styles[key],rect)
func _text(caption: String, at: Vector2, font_size: int = 24, color: Color = INK, center: bool = false) -> void:
    var offset: float = font.get_string_size(caption,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x/2 if center else 0.0
    draw_string(font,at-Vector2(offset,0),caption,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,color)
func _rocket(at: Vector2, direction: Vector2, color: Color, enemy: bool = false) -> void:
    draw_set_transform(at,direction.angle()+PI/2)
    draw_colored_polygon(PackedVector2Array([Vector2(0,-14),Vector2(7,-2),Vector2(7,10),Vector2(11,15),Vector2(3,12),Vector2(-3,12),Vector2(-11,15),Vector2(-7,10),Vector2(-7,-2)]),color)
    draw_line(Vector2(0,-5),Vector2(0,7),Color("fff8dc"),3,true)
    draw_line(Vector2(0,13),Vector2(0,24+sin(elapsed*35)*5),Color("ffb35c") if enemy else Color("8ceef7"),4,true)
    draw_set_transform(Vector2.ZERO)
func _draw_pickup(p: Dictionary) -> void:
    var at: Vector2 = p.position
    var color: Color = {"wing":CYAN,"rapid":Color("ffe07c"),"missile":ORANGE,"homing":PURPLE,"shield":Color("84d2f3"),"repair":Color("9ee69b")}[p.kind]
    _box(Rect2(at-Vector2.ONE*31,Vector2.ONE*62),Color("20374e"),12)
    draw_arc(at,34,0,TAU,24,Color(color,0.65),2,true)
    match str(p.kind):
        "wing":
            for i in range(2 if model.lanes == 1 else 3): draw_line(at+Vector2(-12+i*12,13),at+Vector2(-12+i*12,-13),color,5,true)
        "rapid": draw_polyline(PackedVector2Array([at+Vector2(5,-20),at+Vector2(-10,1),at+Vector2(3,1),at+Vector2(-6,20)]),color,5,true)
        "missile","homing":
            _rocket(at,Vector2.UP,color)
            if p.kind == "homing": draw_arc(at,23,-PI*0.9,PI*0.65,24,color,2,true)
        "shield": draw_colored_polygon(PackedVector2Array([at+Vector2(-18,-14),at+Vector2(18,-14),at+Vector2(15,7),at+Vector2(0,22),at+Vector2(-15,7)]),color)
        "repair": draw_line(at-Vector2(16,0),at+Vector2(16,0),color,7,true); draw_line(at-Vector2(0,16),at+Vector2(0,16),color,7,true)
    _text({"wing":"双发" if model.lanes == 1 else "三发","rapid":"快射","missile":"导弹","homing":"追踪","shield":"护盾","repair":"维修"}[p.kind],at+Vector2(0,57),23,color,true)
func _draw_hud() -> void:
    _box(Rect2(28,158,screen_size.x-56,79),Color("14283fe8"))
    _text("第%d / 6关 · %s"%[model.level,Model.LEVEL_NAMES[model.level-1]],Vector2(45,186),26)
    _text("护盾 %d"%model.shields,Vector2(screen_size.x-90,186),24,CYAN,true)
    var weapon_name: String = ["普通弹","副导弹","副追踪弹"][model.weapon]
    var lanes_name: String = ["单发","双发","三发"][model.lanes-1]
    _text("%s · %s · %s"%[weapon_name,lanes_name,["标准射速","快射Ⅰ","快射Ⅱ"][model.rapid]],Vector2(45,220),25,CYAN)
    if model.phase == "wave":
        _box(Rect2(28,237,screen_size.x-56,4),Color("243e59"),2)
        _box(Rect2(28,237,(screen_size.x-56)*minf(1,model.wave_time/model.wave_duration()),4),CYAN,2)
    if not model.boss.is_empty():
        _text(Model.BOSS_NAMES[model.level-1]+" · 阶段%d"%model.boss.stage,Vector2(screen_size.x/2,269),23,ORANGE,true)
        _box(Rect2(90,279,screen_size.x-180,10),Color("334257"),5)
        _box(Rect2(90,279,(screen_size.x-180)*float(model.boss.hp)/float(model.boss.max_hp),10),ORANGE,5)
    if model.phase == "transition":
        _box(Rect2(80,screen_size.y*0.43,screen_size.x-160,115),Color("16344d"),18)
        _text("航段完成 · 下一关准备",Vector2(screen_size.x/2,screen_size.y*0.43+44),29,CYAN,true)
        _text("%d"%maxi(1,ceili(model.transition_time)),Vector2(screen_size.x/2,screen_size.y*0.43+88),30,INK,true)
    elif model.phase == "boss" and toast_time <= 0:
        var y: float = screen_size.y-145
        _box(Rect2(50,y-36,screen_size.x-100,56),Color("16344d"),12)
        _text(Model.BOSS_TACTICS[model.level-1],Vector2(screen_size.x/2,y),26,CYAN,true)
    elif toast_time > 0:
        var y: float = screen_size.y*0.46 if intro_time > 0 else screen_size.y-145
        _box(Rect2(50,y-36,screen_size.x-100,56),Color("16344d"),12)
        _text(toast,Vector2(screen_size.x/2,y),26,ORANGE if intro_time > 0 else CYAN,true)
func _draw() -> void:
    if screen_size == Vector2.ZERO: return
    draw_texture_rect(BACKGROUND,Rect2(Vector2.ZERO,screen_size),false)
    for star in stars:
        draw_circle(star.position,0.7+float(star.depth),Color(0.65,0.85,0.97,float(star.depth)*0.6))
    for w in model.warnings:
        var from: Vector2 = w.position
        var direction: Vector2 = (Vector2(w.target)-from).normalized()
        var tint := Color(1,0.66,0.32,0.38+0.20*absf(sin(elapsed*10)))
        for i in range(int(w.count)):
            var heading: Vector2 = direction.rotated((i-(int(w.count)-1)*0.5)*0.27)
            draw_dashed_line(from,from+heading*minf(700,screen_size.y*0.65),tint,2.5,13)
        draw_arc(from,20,0,TAU,24,ORANGE,3,true)
    for shot in model.enemy_shots:
        if bool(shot.missile): _rocket(shot.position,shot.velocity,ORANGE,true)
        else:
            draw_circle(shot.position,8,Color("e97a6c")); draw_circle(shot.position,3,Color("ffe4b6"))
    for bullet in model.bullets:
        if int(bullet.weapon) > 0: _rocket(bullet.position,bullet.velocity,PURPLE if int(bullet.weapon) == 2 else CYAN)
        else:
            draw_line(bullet.position+Vector2(0,12),bullet.position-Vector2(0,10),Color("76f9e840"),12,true)
            draw_line(bullet.position+Vector2(0,10),bullet.position-Vector2(0,10),CYAN,5,true)
            draw_line(bullet.position+Vector2(0,5),bullet.position-Vector2(0,10),Color("ecfff7"),2,true)
    for e in model.enemies:
        if e.kind == "launcher":
            for side in [-1,1]: _rocket(e.position+Vector2(side*36,0),Vector2.DOWN,ORANGE,true)
        if e.max_hp > 1:
            var y: float = e.position.y-float(e.radius)-19
            _box(Rect2(e.position.x-23,y,46,4),Color("2e415b"),2)
            _box(Rect2(e.position.x-23,y,46*maxf(0,float(e.hp)/float(e.max_hp)),4),PURPLE if e.kind == "armor" else ORANGE,2)
    for pickup in model.pickups: _draw_pickup(pickup)
    if not model.over:
        if model.shields > 0: draw_arc(model.player,49+sin(elapsed*3)*2,0,TAU,40,Color(CYAN,0.5),2.5,true)
        var tail: float = 23+sin(elapsed*32)*8
        for offset in [-6.0,6.0]:
            var at: Vector2 = model.player+Vector2(offset,38)
            draw_colored_polygon(PackedVector2Array([at+Vector2(-4,0),at+Vector2(4,0),at+Vector2(0,tail)]),CYAN)
    for spark in sparks:
        var tint: Color = spark.color; tint.a = clampf(float(spark.life)/0.5,0,1)
        draw_line(spark.position,spark.position-spark.velocity*0.035,tint,3,true)
    if flash > 0: draw_rect(Rect2(Vector2.ZERO,screen_size),Color(0.95,0.25,0.3,flash*0.12))
    _draw_hud()
