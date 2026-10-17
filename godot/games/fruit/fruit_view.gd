extends "res://games/suite/suite_game.gd"
const Model = preload("res://games/fruit/fruit_model.gd")
const ART = preload("res://games/fruit/assets/fruits.png")
const WOOD = preload("res://games/fruit/assets/dojo.png")
var model = Model.new()
var rect := Rect2()
var cell: float = 1
var world_scale: float = 1
var touches: Dictionary = {}
var trails: Array = []
var popups: Array = []
var death_wait: float = 0
var blast_time: float = 0
var animation_clock: float = 0
const CREAM = Color("fff1c8")
const GOLD = Color("ffc341")
var fruit_audio = preload("res://games/fruit/fruit_audio.gd").new()
func _ready() -> void:
    add_child(fruit_audio)
    super._ready()
func _rules_text() -> String:
    return "划过水果，每个1分；同一刀快速切中3个以上，有额外连切奖励。\n漏掉3次水果结束；切中炸弹立即结束。\n每获得100分恢复一次漏果机会；偶尔切出10分暴击。"
func _new_game() -> void:
    model.reset()
    touches.clear(); trails.clear(); popups.clear()
    death_wait = 0; blast_time = 0
    message = "划过水果 · 避开炸弹"
    _game_layout()
func _game_layout() -> void:
    var size: Vector2 = get_viewport_rect().size
    world_scale = size.y/720.0
    model.configure(size/world_scale)
    rect = Rect2(Vector2.ZERO,size)
    arena = rect
func _world(at: Vector2) -> Vector2:
    return at/world_scale
func _screen(at: Vector2) -> Vector2:
    return at*world_scale
func _world_transform() -> void:
    draw_set_transform(Vector2.ZERO,0,Vector2.ONE*world_scale)
func _letter(caption: String, at: Vector2, size: int, tint: Color = CREAM) -> void:
    var width: float = font.get_string_size(caption,HORIZONTAL_ALIGNMENT_LEFT,-1,size).x
    var baseline := at+Vector2(-width/2,size*0.34)
    draw_string_outline(font,baseline,caption,HORIZONTAL_ALIGNMENT_LEFT,-1,size,5,Color("39200f"))
    draw_string(font,baseline,caption,HORIZONTAL_ALIGNMENT_LEFT,-1,size,tint)
func _sprite(index: int, at: Vector2, radius: float, angle: float = 0, alpha: float = 1) -> void:
    var cell_size: Vector2 = ART.get_size()/4.0
    var source := Rect2(Vector2(index%4,int(index/4))*cell_size,cell_size).grow(-2)
    draw_set_transform(at*world_scale,angle,Vector2.ONE*world_scale)
    draw_texture_rect_region(ART,Rect2(-Vector2.ONE*radius*1.28,Vector2.ONE*radius*2.56),source,Color(1,1,1,alpha),false,true)
    _world_transform()
func _hud_button(r: Rect2, caption: String, action: String) -> void:
    _box(r,Color("3b200db3"),12)
    draw_rect(r.grow(-2),Color("e4bb7566"),false,1)
    _letter(caption,r.get_center(),26)
    buttons.append({"rect":Rect2(r.position*world_scale,r.size*world_scale),"action":action})
func _draw() -> void:
    if font == null: return
    buttons.clear()
    draw_texture_rect(WOOD,get_viewport_rect(),false)
    _world_transform()
    for stain in model.stains: _stain(stain)
    for half in model.halves: _sprite(8+int(half.type),half.p,half.radius,half.angle,clampf(half.life/0.25,0,1))
    for fruit in model.fruits:
        _sprite(7 if fruit.bomb else int(fruit.type),fruit.p,fruit.radius,float(fruit.get("angle",0)))
    for drop in model.drops:
        var tint: Color = drop.color; tint.a = minf(1,drop.life*3)
        draw_circle(drop.p,drop.radius,tint)
    for trail in trails:
        var fade: float = maxf(0,1-trail.age/0.19)
        draw_line(trail.from,trail.to,Color(0.50,0.84,1,fade*0.17),17*fade+1,true)
        draw_line(trail.from,trail.to,Color(0.87,0.96,1,fade*0.6),8*fade+1,true)
        draw_line(trail.from,trail.to,Color(1,1,1,fade),3.2*fade+0.3,true)
    for popup in popups:
        var tint: Color = popup.color; tint.a = minf(1,popup.life*2)
        _letter(popup.text,popup.p,popup.size,tint)
    if stage == "setup":
        _draw_menu()
    else:
        _sprite(0,Vector2(43,43),27,-0.15)
        _letter(str(model.score),Vector2(103+str(model.score).length()*13,45),62)
        _letter("最高 %d"%int(get_node("/root/GamePlatform").launch.get("best",0)),Vector2(120,99),22,Color("e8c98f"))
        for i in range(3):
            var at := Vector2(model.size.x-142+i*48,43)
            var tint := Color("ef442d") if i < 3-model.lives else Color("eed6a677")
            for sign in [-1,1]:
                draw_line(at+Vector2(-12,-12*sign),at+Vector2(12,12*sign),Color("3c2518"),10,true)
                draw_line(at+Vector2(-12,-12*sign),at+Vector2(12,12*sign),tint,5,true)
        _hud_button(Rect2(24,model.size.y-67,60,45),"Ⅱ","pause")
        if model.elapsed < 4 and not model.done: _letter("划过水果，避开炸弹",Vector2(model.size.x/2,model.size.y-34),25,Color("f1d7a4"))
    if blast_time > 0:
        draw_rect(Rect2(Vector2.ZERO,model.size),Color(1,0.92,0.72,blast_time*0.8))
    draw_set_transform(Vector2.ZERO)
    if rules_open: _draw_rules()
func _stain(s: Dictionary) -> void:
    var tint: Color = s.color
    tint.a = minf(0.42,s.life*0.15)
    draw_circle(s.p,s.radius*0.65,tint)
    for i in range(7):
        var angle: float = s.angle+i*TAU/7
        var reach: float = s.radius*(0.65+0.2*sin(i*4.8+float(s.seed)))
        draw_circle(Vector2(s.p)+Vector2.from_angle(angle)*reach,s.radius*(0.16+0.08*absf(sin(i+float(s.seed)))),tint)
        draw_line(s.p,Vector2(s.p)+Vector2.from_angle(angle)*reach,tint,s.radius*0.15,true)
func _draw_menu() -> void:
    var w: float = model.size.x
    _letter("切水果",Vector2(w/2,139),104,GOLD)
    _letter("经 典 模 式",Vector2(w/2,225),30,CREAM)
    _letter("三次漏果 · 炸弹即结束",Vector2(w/2,278),26,Color("edd09c"))
    _sprite(0,Vector2(w/2,434),98,sin(animation_clock)*0.08)
    _letter("切开西瓜开始",Vector2(w/2,565),32,CREAM)
    _hud_button(Rect2(w/2-235,630,210,50),"图解玩法","rules")
    _hud_button(Rect2(w/2+25,630,210,50),"返回大厅","exit")
    _hud_button(Rect2(w-95,24,66,45),"?","rules")
func _input(event: InputEvent) -> void:
    if stage == "setup" and not paused and not rules_open:
        var finger: int = -1
        var at := Vector2.ZERO
        var down: bool = false
        var moving: bool = false
        var up: bool = false
        if event is InputEventScreenTouch:
            finger = event.index; at = event.position; down = event.pressed; up = not event.pressed
        elif event is InputEventScreenDrag: finger = event.index; at = event.position; moving = true
        elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
            finger = 0; at = event.position; down = event.pressed; up = not event.pressed
        elif event is InputEventMouseMotion and event.button_mask & MOUSE_BUTTON_MASK_LEFT: finger = 0; at = event.position; moving = true
        if finger >= 0:
            var world: Vector2 = _world(at)
            if down: touches[finger] = {"at":world}
            elif (moving or up) and touches.has(finger):
                var before: Vector2 = touches[finger].at
                if before.distance_to(world) > 10 and Model.Physics.segment_distance(Vector2(model.size.x/2,434),before,world) < 98:
                    fruit_audio.swipe(before.distance_to(world))
                    _action("begin"); return
                touches[finger].at = world
                if up: touches.erase(finger)
    super._input(event)
func _game_down(at: Vector2, finger: int) -> void:
    if model.done: return
    touches[finger] = {"at":_world(at)}
func _game_drag(at: Vector2, finger: int) -> void:
    if not touches.has(finger) or model.done: return
    var world: Vector2 = _world(at)
    var before: Vector2 = touches[finger].at
    fruit_audio.swipe(before.distance_to(world))
    model.cut(before,world,finger)
    if before.distance_to(world) > 2:
        if trails.size() >= 72: trails.pop_front()
        trails.append({"from":before,"to":world,"age":0.0})
    touches[finger].at = world
    _events()
func _game_up(at: Vector2, finger: int) -> void:
    _game_drag(at,finger); touches.erase(finger); model.release(finger)
func _events() -> void:
    if score != model.score:
        score = model.score; score_changed.emit(score)
    for event in model.events:
        match str(event.kind):
            "slice": fruit_audio.slice(int(event.type))
            "critical": fruit_audio.slice(int(event.type)); _popup("暴击 +10",event.p,42,Color("ffdc3b")); _feedback("combo")
            "combo": _popup("%d连切  +%d"%[event.count,event.bonus],event.p-Vector2(0,65),42,GOLD); _feedback("combo")
            "miss": _popup("×",event.p,46,Color("fa482d")); _feedback("ui_error")
            "restore": _popup("恢复一次机会！",event.p,32,GOLD); _feedback("powerup")
            "blast": _popup("炸弹！",event.p,60,Color("ffda69")); blast_time = 0.85; fruit_audio.bomb(); get_node("/root/GamePlatform").haptic(100)
    model.events.clear()
func _popup(caption: String, at: Vector2, size: int, tint: Color) -> void:
    if popups.size() >= 12: popups.pop_front()
    popups.append({"text":caption,"p":at,"size":size,"color":tint,"life":0.95})
func _process(delta: float) -> void:
    if paused: return
    animation_clock += minf(delta,0.05)
    super._process(delta)
    if stage == "setup": queue_redraw()
func _game_process(delta: float) -> bool:
    model.tick(delta); _events()
    for trail in trails: trail.age += delta
    trails = trails.filter(func(t): return t.age < 0.19)
    for popup in popups: popup.life -= delta; popup.p.y -= delta*35
    popups = popups.filter(func(p): return p.life > 0)
    blast_time = maxf(0,blast_time-delta*2)
    if model.done:
        death_wait += delta
        if death_wait >= (1.5 if model.bomb_hits > 0 else 0.8): _end(model.result,false)
    return true
func _clear_controls() -> void:
    touches.clear(); trails.clear(); model.combos.clear()
    fruit_audio.stop_all()
