extends SceneTree
const Model = preload("res://games/plane/plane_model.gd")
var failures: int = 0
var checks: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    var m = Model.new()
    m.reset(); m.lanes = 3; m.rapid = 2; m.weapon = 2
    m.spawn_timer = 50; m.tick(0.01)
    var guided: int = 0
    var normal: int = 0
    for b in m.bullets:
        if b.weapon == 2: guided += 1
        if b.weapon == 0: normal += 1
    check(guided == 1 and normal == 3,"满装备发射三枚普通弹和一枚独立冷却追踪弹")
    m.bullets.clear(); m.fire_timer = 0; m.tick(0.01)
    check(m.bullets.size() == 3,"快射不能绕过导弹冷却")
    m.reset(); m.drop_pack("homing",Vector2(60,270))
    m.pickups[0].age = 12; m._advance_pickups(0.01)
    check(m.weapon == 0 and m.pickups.is_empty(),"漏接补给过期消失，不自动获得")
    m.reset()
    for i in range(5): m.drop_pack("wing",Vector2(60,270))
    check(m.lanes == 1,"补给溢出不自动获得旧补给")
    m.collect_pack("wing"); m.collect_pack("wing"); m.collect_pack("wing")
    check(m.shields == 0,"重复满级装备不无限提供护盾")
    m.reset(); m.drop_pack("wing",m.player); m._advance_pickups(0.01)
    check(m.lanes == 2,"实际接到补给仍能升级")
    m.reset(); m.level = 6; m.lanes = 3; m.rapid = 2; m.weapon = 2; m.shields = 2
    m.random.seed = 20261016; m.start_boss()
    for i in range(1800):
        m.tick(0.05)
        if m.over: break
    check(m.over and not m.victory,"终关满装备原地挂机不能获胜")
    print("PLANE BALANCE TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
