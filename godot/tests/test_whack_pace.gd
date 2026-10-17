extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    var m = load("res://games/arcade/timed_model.gd").new()
    m.reset("whack")
    check(m.get("moles") is Dictionary,"地鼠应支持多个洞同时出现")
    if m.get("moles") is Dictionary:
        for remaining in [29.0,19.0,9.0]:
            m.reset("whack"); m.time_left = remaining; m.spawn_time = 0
            m.tick(0.01)
            var expected: int = 1 if remaining > 20 else (2 if remaining > 10 else 3)
            check(m.moles.size() == expected,"前中后段分别同时出现1/2/3只")
            var holes: Array = m.moles.keys()
            for hole in holes: check(m.hit_hole(hole),"同一波多只均可命中")
            check(m.score == expected*10 and m.moles.is_empty(),"分别计分，命中一只不影响其他洞")
            m.tick(0.5)
            check(not m.moles.is_empty(),"打完不等整秒，继续出现下一波")
            var current: int = m.moles.keys()[0]
            m.hit_hole(current)
            var score: int = m.score
            check(not m.hit_hole(current) and m.score == maxi(0,score-2),"同一个洞不能重复得分")
        m.tick(30)
        check(m.done and m.moles.is_empty(),"30秒结束清空洞口，冻结分数")
    print("WHACK PACE TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
