extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    var script = load("res://games/fruit/fruit_model.gd")
    check(script != null,"经典水果模型存在")
    if script == null: quit(1); return
    var m = script.new(); m.reset(); m.critical_chance = 0; m.spawn_time = 100
    m.fruits = [m.make_fruit(Vector2(300,300),Vector2.ZERO)]
    check(m.cut(Vector2(300,300),Vector2(300,300)) == 0,"点按不能代替切割")
    check(m.cut(Vector2(200,300),Vector2(400,300)) == 1 and m.score == 1,"每个普通水果一分")
    check(m.halves.size() == 2 and m.drops.size() > 0 and m.stains.size() == 1,"切开形成两半、果汁和木板汁迹")
    check(m.cut(Vector2(200,300),Vector2(400,300)) == 0,"切开的水果不能重复刷分")
    m.reset(); m.critical_chance = 0
    for i in range(3): m.fruits.append(m.make_fruit(Vector2(300+i*100,300),Vector2.ZERO))
    m.cut(Vector2(200,300),Vector2(600,300))
    check(m.score == 6,"三果一刀：基础3分加连切3分")
    m.reset(); m.critical_chance = 0
    m.fruits = [m.make_fruit(Vector2(500,300),Vector2.ZERO),m.make_fruit(Vector2(300,300),Vector2.ZERO,0,true)]
    m.cut(Vector2(200,300),Vector2(600,300))
    check(m.done and m.lives == 0 and m.score == 0,"先切到炸弹立即结束，后方水果不得分")
    m.reset(); m.spawn_time = 100
    for i in range(3):
        m.fruits = [m.make_fruit(Vector2(300,m.size.y+200),Vector2(0,100))]
        m.tick(0.01)
        check(m.lives == 2-i and m.done == (i == 2),"第三次漏果才结束")
    var score: int = m.score; m.cut(Vector2.ZERO,Vector2(1000,1000)); m.tick(0.01)
    check(m.score == score,"结束后冻结分数")
    m.reset(); m.spawn_time = 100
    m.fruits = [m.make_fruit(Vector2(300,m.size.y+200),Vector2(0,100),0,true)]
    m.tick(0.01)
    check(m.lives == 3 and m.missed == 0,"漏炸弹不扣命")
    m.lives = 1; m.score = 99; m._add_score(1)
    check(m.lives == 2,"每过100分恢复一次漏果机会")
    m.reset(); m.critical_chance = 0
    for i in range(3):
        m.fruits = [m.make_fruit(Vector2(300,300),Vector2.ZERO)]
        m.cut(Vector2(200,300),Vector2(400,300),i)
    check(m.score == 3,"不同手指不合并成同一刀连切")
    print("FRUIT CLASSIC TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
