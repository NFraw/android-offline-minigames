extends SceneTree
const Model = preload("res://games/plane/plane_model.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, label: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(label)
func fresh():
    var m = Model.new()
    m.configure(Vector2(720,1280)); m.reset()
    m.fire_timer = 50; m.spawn_timer = 50
    return m
func _initialize() -> void: call_deferred("run")
func run() -> void:
    var m = fresh()
    if not m.has_method("collect_pack"):
        check(false,"缺少可叠加技能包与六关战役")
        print("PLANE CAMPAIGN TESTS: %d checks, %d failures"%[checks,failures]); quit(1); return
    m.collect_pack("wing"); m.collect_pack("rapid"); m.collect_pack("wing")
    m.collect_pack("missile"); m.collect_pack("homing"); m.collect_pack("rapid")
    check(m.lanes == 3 and m.rapid == 2 and m.weapon == 2,"三发、射速和追踪导弹同时保留")
    m.fire_timer = 0; m.tick(0.001)
    check(m.bullets.size() == 4 and m.bullets[0].weapon == 0 and m.bullets[3].weapon == 2,"三发普通弹与独立追踪副武器")
    for i in range(8): m.collect_pack("wing"); m.collect_pack("rapid")
    check(m.lanes == 3 and m.rapid == 2,"升级封顶不无限叠加")
    m = fresh()
    m.enemy_shots = [{"position":Vector2(360,500),"previous":Vector2(360,500),"velocity":Vector2(0,240),"missile":true,"hp":2.0,"radius":12.0}]
    m.bullets = [m.make_bullet(Vector2(360,550),Vector2(0,-900)),m.make_bullet(Vector2(360,552),Vector2(0,-900))]
    m.tick(0.05)
    check(m.enemy_shots.is_empty() and m.score == 3,"玩家能击落迎面飞来的敌方导弹")
    m = fresh()
    m.enemy_shots = [{"position":Vector2(300,500),"previous":Vector2(300,500),"velocity":Vector2(20,240),"missile":true,"hp":2.0,"radius":12.0}]
    m.move_player(Vector2(650,1000)); m.tick(0.05)
    check(m.enemy_shots[0].velocity == Vector2(20,240),"敌方导弹飞行中不跟随玩家转向")
    m = fresh(); m.collect_pack("wing"); m.collect_pack("shield")
    m.damage_player("测试"); check(m.lives == 5 and m.shields == 0,"护盾先抵消一次伤害")
    m.invulnerable = 0; m.damage_player("测试"); m.damage_player("测试")
    check(m.lives == 4 and m.lanes == 2,"受击短暂无敌，保留已有升级")
    m = fresh(); m.wave_time = m.wave_duration(); m.enemies.clear(); m.tick(0.01)
    check(m.phase == "boss" and not m.boss.is_empty(),"每关巡航结束进入Boss战")
    m.damage_boss(10000)
    check(m.phase == "transition" and not m.over,"普通Boss击败后进入下一关准备")
    m.tick(0.05); m.transition_time = 0; m.tick(0.01)
    check(m.level == 2 and m.phase == "wave","过关保留升级并推进关卡")
    m.level = 6; m.start_boss(); m.damage_boss(10000)
    check(m.over and m.victory,"第六关终极Boss击败才通关")
    var score: int = m.score; m.tick(0.05)
    check(m.score == score,"结算后冻结")
    var signatures: Array = []
    for stage in range(1,7):
        var battle = fresh(); battle.level = stage; battle.start_boss()
        battle.boss.hp = float(battle.boss.max_hp)*0.20; battle.boss.fire = 0
        battle.tick(0.01)
        var signature: String = str(battle.warnings)
        signatures.append(signature)
        check(battle.warnings.size() > 0 and battle.warnings.size() <= 4,"第%d关Boss有预警且数量受限"%stage)
    check(Model.BOSS_NAMES.size() == 6,"六个独立Boss")
    m = fresh()
    m.enemy_shots = [{"position":Vector2(300,239),"previous":Vector2(300,239),"velocity":Vector2(0,-240),"missile":false,"hp":1.0,"radius":7.0}]
    m.tick(0.01)
    check(m.enemy_shots.is_empty(),"离开战场上沿的敌弹及时回收")
    print("PLANE CAMPAIGN TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
