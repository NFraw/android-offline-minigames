extends SceneTree
const Model = preload("res://games/plane/plane_model.gd")
const Session = preload("res://core/game_session.gd")
var checks: int = 0
var failures: int = 0

func check(value: bool, message: String) -> void:
    checks += 1
    if not value:
        failures += 1
        push_error(message)

func _initialize() -> void:
    print("BACK_POLICY: quit_on_go_back=",ProjectSettings.get_setting("application/config/quit_on_go_back",true))
    check(not bool(ProjectSettings.get_setting("application/config/quit_on_go_back",true)),"安卓返回键交给游戏壳处理，不能同时自动退出")
    var session = Session.new()
    check(not session.finish(), "未开局不能结算")
    session.start()
    check(session.pause(), "运行中可以暂停")
    check(not session.pause(), "暂停必须幂等")
    check(session.resume(), "暂停后可以恢复")
    check(session.finish(), "首个结算有效")
    check(not session.finish(), "重复结算应被拒绝")
    check(not session.resume(), "结束后不可恢复")
    session.start()
    check(session.round_index == 2 and session.is_running(), "重开是新一局")

    var model = Model.new()
    model.configure(Vector2(720, 1280))
    model.reset()
    model.move_player(Vector2(-500, -500))
    check(model.player.x >= 24 and model.player.y >= 512, "玩家移动限制在下方可玩区域")
    model.reset()
    check(model.player.is_equal_approx(Vector2(360, 1024)), "重开复位飞机")
    model.enemies = [model.make_enemy("scout",Vector2(360,450),0), model.make_enemy("scout",Vector2(360,451),0)]
    model.bullets = [model.make_bullet(Vector2(360,480),Vector2(0,-900))]
    model.fire_timer = 10.0
    model.spawn_timer = 10.0
    model.tick(0.05)
    check(model.score == 10 and model.enemies.size() == 1 and model.bullets.is_empty(), "一颗子弹只命中一个敌机，扫掠检测不能漏判")
    model.reset()
    model.enemies = [model.make_enemy("scout",Vector2(360,430),1800)]
    model.bullets = [model.make_bullet(Vector2(360,480),Vector2(0,-900))]
    model.fire_timer = 10.0
    model.spawn_timer = 10.0
    model.tick(0.05)
    check(model.score == 10 and model.enemies.is_empty(), "敌机和子弹同帧相向穿越也必须命中")
    model.reset()
    model.enemies = [model.make_enemy("scout",Vector2(360,400),448), model.make_enemy("scout",Vector2(360,392),832)]
    model.bullets = [model.make_bullet(Vector2(360,480),Vector2(0,-900))]
    model.fire_timer = 10.0
    model.spawn_timer = 10.0
    model.tick(0.05)
    check(model.enemies.size() == 1 and is_equal_approx(float(model.enemies[0].speed), 448.0), "命中时间而非数组顺序决定先击落哪架敌机")
    model.reset()
    model.lives = 1
    model.enemies = [model.make_enemy("scout",Vector2(100,1400),0), model.make_enemy("scout",Vector2(200,1400),0)]
    model.tick(0.016)
    check(not model.over and model.lives == 1, "新版漏机不扣机体耐久")
    model.damage_player("耐久耗尽")
    check(model.over and model.lives == 0, "最后一点耐久耗尽后结束")
    var terminal_score: int = model.score
    model.tick(1.0)
    check(model.score == terminal_score and model.lives == 0, "结算后状态冻结")
    model.reset()
    model.enemies = [model.make_enemy("scout",model.player,0)]
    model.tick(0.016)
    check(not model.over and model.lives == 4, "新版碰撞扣一点耐久并保护玩家")
    model.reset()
    model.tick(0.001)
    check(model.bullets.size() == 1, "新一局立即自动射击")
    print("GODOT_TESTS: %d checks, %d failures" % [checks, failures])
    quit(0 if failures == 0 else 1)


