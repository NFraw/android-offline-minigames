extends SceneTree
const Model = preload("res://games/puzzle/puzzle_model.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    var m = Model.new()
    m.rng.seed = 191
    m.reset("game2048")
    m.cells = PackedInt32Array([2,2,4,4,0,0,0,0,0,0,0,0,0,0,0,0])
    m.slide(Vector2i.LEFT,false)
    check(Array(m.cells.slice(0,4)) == [4,8,0,0] and m.score == 12,"2048相邻合并且不二次合并")
    m.cells = PackedInt32Array([2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0])
    m.slide(Vector2i.LEFT,false)
    check(Array(m.cells.slice(0,4)) == [4,4,0,0],"四个2只合成两个4")
    m.cells = PackedInt32Array([4,8,0,0,0,0,0,0,0,0,0,0,0,0,0,0])
    var before: PackedInt32Array = m.cells.duplicate()
    check(not m.slide(Vector2i.LEFT) and m.cells == before,"无效滑动不生成新块")
    m.cells = PackedInt32Array([2,4,2,4,4,2,4,2,2,4,2,4,4,2,4,2])
    m.slide(Vector2i.LEFT,false)
    check(m.done,"无空位也无可合并相邻块时结束")
    m.reset("sliding")
    for sample in range(20):
        m.reset("sliding")
        var inversions: int = 0
        for i in range(16):
            for j in range(i+1,16):
                if m.cells[i] > 0 and m.cells[j] > 0 and m.cells[i] > m.cells[j]: inversions += 1
        var blank_row: int = 4-int(m.cells.find(0)/4)
        check((inversions+blank_row)%2 == 1 and not m.solved_sliding(),"15拼图可解且不直接完成")
    m.cells = PackedInt32Array([1,2,3,4,5,6,7,8,9,10,11,12,13,14,0,15])
    check(not m.tap(0) and m.tap(15) and m.done and m.score == 492,"拼图只走相邻空格并计算步数得分")
    for sample in range(30):
        m.reset("mines")
        m.tap(sample%108)
        check(m.cells.count(-1) == 15 and m.cells[sample%108] >= 0 and not m.done,"扫雷首触安全且固定15雷")
        for i in range(108):
            if m.cells[i] >= 0: m.reveal(i)
        check(m.done and m.revealed.count(true) == 93,"扫雷空白洪泛和揭开所有安全格获胜")
    m.reset("mines")
    check(m.flag(0) and not m.tap(0) and not m.placed,"旗格不能翻开且不触发布雷")
    m.flag(0); m.tap(0)
    var mine: int = m.cells.find(-1)
    m.reveal(mine)
    check(m.done and m.score == 0,"踩雷结算一次并清零得分")
    m.reset("memory_duel")
    m.cells = PackedInt32Array([0,1,0,1,2,2,3,3,4,4,5,5,6,6,7,7])
    m.tap(0)
    check(not m.tap(0),"不能重复翻同一张牌")
    m.tap(1)
    check(m.lock_time > 0 and not m.tap(2),"配错展示期间阻止再翻牌")
    m.tick(0.81)
    check(m.first == -1 and m.turn == 1,"配错盖牌后再换手")
    m.tap(0); m.tap(2)
    check(m.scores[1] == 1 and m.turn == 1,"配对得分并继续当前回合")
    m.reset("memory_duel")
    for level in range(Model.LEVELS.size()):
        check(m.cols == Model.LEVELS[level],"双人记忆按4/6/8真实递进")
        var pairs: Dictionary = {}
        for i in range(m.cells.size()):
            if not pairs.has(m.cells[i]): pairs[m.cells[i]] = []
            pairs[m.cells[i]].append(i)
        for pair in pairs.values(): m.tap(pair[0]); m.tap(pair[1])
    check(m.done and m.scores[0] == 58 and m.score == 580,"三关总计58对累计比分")
    m.reset("simon")
    m.sequence = [2,2]; m.start_show()
    m.tick(1.3)
    check(m.showing and m.lit == 2 and not m.tap(2),"西蒙播放时不接受输入")
    m.tick(0.91)
    check(m.lit == -1 and m.showing,"连续同色之间必须完全灭灯")
    m.tick(0.66)
    check(m.lit == 2 and m.showing,"间隔后重新点亮相同颜色")
    m.tick(0.91); m.tick(0.46)
    check(not m.showing,"完整播完后才进入输入")
    m.tap(2); m.tap(2)
    check(m.score == 10 and m.sequence.size() == 3 and m.showing,"复现后加长序列并重新播放")
    m.showing = false; m.input_index = 0
    m.tap((m.sequence[0]+1)%4)
    check(m.done,"西蒙输入错误结束")
    print("PUZZLE_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
