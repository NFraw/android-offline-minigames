extends SceneTree
const Snake = preload("res://games/arcade/snake_model.gd")
const Tetris = preload("res://games/arcade/tetris_model.gd")
const Contest = preload("res://games/arcade/contest_model.gd")
const Timed = preload("res://games/arcade/timed_model.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    var s = Snake.new()
    s.reset()
    check(s.set_direction(0,Vector2i.UP) and not s.set_direction(0,Vector2i.LEFT) and s.pending[0] == Vector2i.UP,"一拍内快速转向不能反向掉头")
    s.bodies[0] = [Vector2i(0,0),Vector2i(0,1),Vector2i(0,2)]
    s.dirs[0] = Vector2i.UP; s.pending[0] = Vector2i.UP; s.step()
    check(s.done and not s.alive[0],"蛇顶边也必须判撞墙")
    s.reset()
    s.bodies[0] = [Vector2i(1,1),Vector2i(1,2),Vector2i(2,2),Vector2i(2,1)]
    s.dirs[0] = Vector2i.UP; s.pending[0] = Vector2i.RIGHT; s.foods = [Vector2i(8,8)]
    s.step()
    check(s.alive[0],"未吃食物时可进入本拍腾出的尾格")
    s.reset(true)
    s.bodies[0] = [Vector2i(0,10),Vector2i(1,10),Vector2i(2,10)]
    s.dirs[0] = Vector2i.LEFT; s.pending[0] = Vector2i.LEFT
    s.bodies[1] = [Vector2i(8,8),Vector2i(7,8),Vector2i(6,8)]
    s.dirs[1] = Vector2i.RIGHT; s.pending[1] = Vector2i.RIGHT; s.foods = [Vector2i(14,22)]
    s.step()
    check(not s.alive[0] and s.alive[1] and not s.done,"双蛇一死后另一方仍继续")
    var old: Vector2i = s.bodies[1][0]
    s.step()
    check(s.bodies[1][0] != old and not s.done,"幸存蛇继续移动")
    s.bodies[1] = [Vector2i(15,8),Vector2i(14,8),Vector2i(13,8),Vector2i(12,8)]
    s.step()
    check(s.done and "玩家2获胜" in s.result,"双蛇按身体长度结算")
    var t = Tetris.new()
    t.reset()
    var bag: Array = [t.kind]
    for i in range(6): t.spawn(); bag.append(t.kind)
    bag.sort()
    check(bag == [0,1,2,3,4,5,6],"七种方块每袋不遗漏")
    t.board.fill(0); t.done = false; t.kind = 0
    t.shape = [Vector2i(2,0),Vector2i(2,1),Vector2i(2,2),Vector2i(2,3)]
    t.position = Vector2i(-2,5)
    check(t.rotate() and t.valid(t.shape,t.position),"I方块贴墙旋转会尝试踢墙")
    t.board.fill(0)
    for x in range(10):
        if x not in [3,4,5,6]: t.board[19*10+x] = 1
    t.kind = 0; t.shape = Tetris.SHAPES[0].duplicate(); t.position = Vector2i(3,18); t.lock()
    check(t.lines == 1 and t.score == 100 and t.board.count(0) == 200,"完整行消除与计分")
    for i in range(40): t.board[i] = 1
    t.spawn()
    check(t.done,"顶部放不下新方块时结束")
    var c = Contest.new()
    c.reset("reaction")
    c.react(0)
    check(c.scores[0] == 0 and c.scores[1] == 1 and c.state == 2,"双人抢拍给对方得分")
    c.reset("reaction"); c.state = 1; c.timer = 3
    c.react(0); c.react(1)
    check(c.scores == [1,0,0,0],"双人绿灯仅先按者得分")
    c.reset("reaction4"); c.state = 1; c.timer = 3
    for owner in range(4): c.react(owner)
    check(c.scores == [1,1,1,1],"四人同轮各区独立得分")
    check(not c.react(0) and c.scores[0] == 1,"同一区重复按不重复计分")
    c.reset("reaction4"); c.react(2); c.state = 1; c.timer = 3
    c.react(0); c.react(1); c.react(3)
    check(c.scores == [1,1,0,1] and c.early[2],"抢拍只影响自己的区域")
    for mode in range(6):
        c.reset("math_duel",mode)
        for sample in range(100):
            c.new_question()
            check(c.answer >= 0 and c.answer <= Contest.LIMITS[mode],"六档算术结果范围")
    c.reset("math_duel",3)
    c.key(0,"digit0")
    check(c.inputs[0] == "0","数字0是数字键而非删除")
    c.key(0,"digit5"); c.key(0,"delete")
    check(c.inputs[0] == "","删除不追加字符")
    c.question = "10 + 0 = ?"; c.answer = 10
    c.key(0,"digit1"); c.key(0,"digit0"); c.key(0,"submit")
    check(c.scores[0] == 1 and not c.locked[0],"输入含0的答案可提交")
    c.answer = 99; c.inputs = ["0","0"]
    c.key(0,"submit")
    check(c.locked[0] and not c.key(0,"digit1") and c.answer == 99,"答错锁定本题且不立即换题")
    c.key(1,"submit"); c.tick(0.81)
    check(not c.locked[0] and not c.locked[1],"双方答错后换新题")
    var m = Timed.new()
    m.reset("whack"); m.tick(0.6)
    check(m.moles.size() == 1,"开局先出现单只地鼠")
    var hole: int = m.moles.keys()[0]
    check(m.moles[hole].remaining >= 1.2 and m.moles[hole].remaining <= 1.5,"开局保留充分反应时间")
    check(m.hit_hole(hole) and m.score == 10,"命中地鼠加10分")
    m.hit_hole(hole)
    check(m.score == 8,"误点空洞扣2分")
    m.tick(40)
    check(m.done and m.time_left == 0,"打地鼠30秒结束")
    m.reset("piano")
    var low: int = m.lowest()
    check(not m.hit_key(low,(m.rows[low].column+1)%4) and m.done,"点白块判负")
    m.reset("piano"); low = m.lowest()
    check(m.hit_key(low,m.rows[low].column) and m.score == 1,"只能按最下方未点黑块")
    check(not m.hit_key(low,m.rows[low].column) and m.score == 1,"已点行不能刷分")
    m.reset("piano"); m.speed = 2.99
    low = m.lowest(); m.hit_key(low,m.rows[low].column)
    check(m.speed == 3,"白块速度封顶")
    m.reset("piano"); m.started = true; m.tick(10)
    check(m.done,"只有漏掉出屏黑块才结束")
    print("ARCADE_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
