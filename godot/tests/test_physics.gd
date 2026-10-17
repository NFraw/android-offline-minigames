extends SceneTree
const Physics = preload("res://core/physics2d.gd")
const Rally = preload("res://games/physics/rally_model.gd")
const Pool = preload("res://games/physics/pool_model.gd")
const Curling = preload("res://games/physics/curling_model.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func ball(at: Vector2, velocity: Vector2, num: int, owner: int = 0) -> Dictionary:
    return {"p":at,"v":velocity,"num":num,"owner":owner,"active":true}
func prepare_eight(p) -> void:
    p.reset(); p.break_shot = false; p.groups = [1,2]
    for b in p.balls:
        if b.num in range(1,8): b.active = false
    p.need_eight = true; p.first_contact = 8
    p.get_ball(8).active = false
    p.shot_events = [Vector2i(8,2)]
func _initialize() -> void:
    check(Physics.circle_rect(Vector2(11,11),1,Rect2(0,0,10,10)).is_empty(),"圆矩形角落不能按AABB误碰撞")
    check(not Physics.circle_rect(Vector2(10.5,5),1,Rect2(0,0,10,10)).is_empty(),"边缘真实圆矩形碰撞")
    var balls: Array = [ball(Vector2(100,100),Vector2(1800,0),0),ball(Vector2(170,100),Vector2.ZERO,1)]
    var step: Dictionary = Physics.balls_step(balls,Vector2(1000,500),15,0.05,1,0,0.1)
    check(not step.contacts.is_empty() and balls[1].v.x > 1000,"高速子步进不会穿过目标球")
    balls = [ball(Vector2(100,100),Vector2(-100,0),0),ball(Vector2(129,100),Vector2(100,0),1)]
    Physics.balls_step(balls,Vector2(1000,500),15,0.001,1,0,0.1)
    check(balls[0].v.x == -100 and balls[1].v.x == 100,"已分离方向不重复施加碰撞冲量")
    var r = Rally.new()
    r.reset("breakout")
    check(r.bricks.size() == 40,"打砖块40块")
    for b in r.bricks: check(b.hp >= 1 and b.hp <= 3,"砖块耐久1到3")
    r.serve_time = 0; r.ball = Vector2(950,1450); r.velocity = Vector2(0,200)
    r.tick(0.02)
    check(r.done and not r.won,"漏球判负")
    r.reset("bounce")
    r.serve_time = 0
    for b in r.bricks: b.hp = 0
    r.tick(0.01)
    check(r.done and r.won,"清完所有砖块获胜")
    r.reset("pong"); r.serve_time = 0; r.ball = Vector2(500,-50); r.velocity = Vector2(0,-200)
    r.tick(0.01)
    check(r.scores == [1,0] and not r.done,"对方漏球后计分并重新发球")
    r.set_paddle(0,-1000); r.set_paddle(1,5000)
    check(r.paddles == [100.0,900.0],"双人球拍不越界")
    var fruit = load("res://games/fruit/fruit_model.gd").new()
    fruit.critical_chance = 0
    fruit.fruits = [fruit.make_fruit(Vector2(500,300),Vector2.ZERO)]
    fruit.cut(Vector2(450,300),Vector2(550,300))
    check(fruit.score == 1,"正式经典切水果每果1分")
    fruit.fruits = [fruit.make_fruit(Vector2(500,300),Vector2.ZERO,0,true)]
    fruit.cut(Vector2(450,300),Vector2(550,300))
    check(fruit.done and fruit.bomb_hits == 1,"正式经典切水果炸弹立即结束")
    var solution: Dictionary = Curling.aim(Vector2(500,1500),Vector2(600,1300))
    check(not solution.is_empty() and solution.direction.y < 0,"冰壶求解投出方向")
    check(absf((Vector2(500,1500)-solution.launch).cross(Vector2(600,1300)-Vector2(500,1500))) < 0.1,"投出点由固定支点连线与底线交点得到")
    check(Curling.aim(Vector2(500,1500),Vector2(520,1500)).is_empty(),"平行底线拒绝投壶")
    check(Curling.aim(Vector2(500,1790),Vector2(510,1500)).is_empty(),"支点在底线下方拒绝投壶")
    check(Curling.aim(Vector2(500,1500),Vector2(502,1501)).is_empty(),"微小拖动不能兜底出手")
    var c = Curling.new()
    c.reset(4)
    check(c.rounds == 5 and c.players == 4,"4人5局，每人每局两壶")
    var reverse_finger: Dictionary = Curling.aim(Vector2(500,1500),Vector2(500,1790))
    check(c.throw_stone(reverse_finger,0.6) and c.balls[0].v.y < 0,"实际出壶复用求解方向，不二次用触点算方向")
    check(not c.throw_stone(solution,0.6),"移动期间不重复投壶")
    c.reset()
    c.balls = [ball(Vector2(500,400),Vector2.ZERO,1,0),ball(Vector2(520,400),Vector2.ZERO,2,0),ball(Vector2(600,400),Vector2.ZERO,3,1),ball(Vector2(50,1500),Vector2.ZERO,4,1)]
    c.score_round()
    check(c.scores == [2,0,0,0],"营垒内比敌方最近壶更近的己方壶逐个计分")
    c.reset()
    c.balls = [ball(Vector2(50,1500),Vector2.ZERO,1,0)]
    c.score_round()
    check(c.scores == [0,0,0,0],"圈外石头不计分，允许0比0空局")
    var p = Pool.new()
    p.reset()
    check(p.balls.size() == 16 and p.get_ball(8).active,"台球完整16球")
    check(not p.shoot(p.get_ball(0).p+Vector2(2,0)),"无效短拖动不能出杆")
    p.shoot(Vector2(-100,250))
    for frame in range(650):
        p.tick(0.016)
        if p.phase != "moving": break
    check(p.first_contact > 0 and p.phase != "moving" and not p.done,"高速真实开球命中并最终停稳，不会空杆误判")
    p.reset(); p.break_shot = false; p.turn = 1; p.first_contact = 9
    p.get_ball(9).active = false; p.shot_events = [Vector2i(9,1)]; p.resolve_shot()
    check(p.groups == [1,2] and p.turn == 1,"第二位玩家首次进花球时正确分配双方组别")
    p.reset(); p.first_contact = 1; p.get_ball(8).active = false; p.shot_events = [Vector2i(8,2)]; p.resolve_shot()
    check(not p.done and p.get_ball(8).active,"开球进8摆回，不能误判负")
    p.reset(); p.break_shot = false; p.first_contact = 8; p.resolve_shot()
    check(p.turn == 1 and p.ball_in_hand and p.phase == "place","开放台面首碰8号也犯规")
    p.reset(); p.first_contact = -1; p.resolve_shot()
    check(p.kitchen_only and not p.place_cue(Vector2(700,250)),"开球犯规自由球限制在发球区")
    check(not p.place_cue(Vector2.ZERO) and p.place_cue(Vector2(140,350)),"自由球不可放袋口，可放合法空位")
    prepare_eight(p); p.call_pocket = 2; p.resolve_shot()
    check(p.done and p.winner == 0,"合法叫袋并进8获胜")
    prepare_eight(p); p.call_pocket = 1; p.resolve_shot()
    check(p.done and p.winner == 1,"8号进错袋判负")
    prepare_eight(p); p.call_pocket = -1; p.resolve_shot()
    check(p.winner == 1,"未叫袋打进8判负")
    prepare_eight(p); p.get_ball(0).active = false; p.call_pocket = 2; p.resolve_shot()
    check(p.winner == 1,"8号与母球同进判负")
    print("PHYSICS_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)

