extends SceneTree
const Model = preload("res://games/chinese/chinese_model.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func kings(m) -> void:
    m.reset()
    m.board.fill(0)
    m.board[85] = 1; m.board[4] = 11; m.board[49] = 7
func dark(m) -> void:
    m.reset("darkchess"); m.board.fill(0); m.revealed.fill(true); m.player_sides = [0,1]
func _initialize() -> void:
    var m = Model.new()
    m.reset()
    check(m.board.size() == 90 and m.board.count(0) == 58 and m.board[85] == 1 and m.board[4] == 11,"标准象棋32子布阵")
    check(not m.in_check(0) and not m.in_check(1),"开局双方未被将")
    kings(m); m.board[64] = 6; m.board[37] = 7; m.board[1] = 12
    check(m.legal_xiangqi(64,1),"炮隔一子可吃士，不按等级限制")
    m.board[37] = 0
    check(not m.legal_xiangqi(64,1),"无炮架不能吃子")
    m.board[37] = 7; m.board[19] = 17
    check(not m.legal_xiangqi(64,1),"隔两子不能吃目标")
    m.board[19] = 0; m.board[1] = 2
    check(not m.legal_xiangqi(64,1),"不能吃己方子")
    kings(m); m.board[82] = 4
    check(m.legal_xiangqi(82,63),"马走日")
    m.board[73] = 7
    check(not m.legal_xiangqi(82,63),"蹩马腿")
    kings(m); m.board[83] = 3
    check(m.legal_xiangqi(83,63),"相走田")
    m.board[73] = 7
    check(not m.legal_xiangqi(83,63),"塞象眼")
    m.board[47] = 3
    check(not m.legal_xiangqi(47,27),"相不可过河")
    kings(m)
    check(m.legal_xiangqi(85,84) and not m.legal_xiangqi(85,83),"将帅仅九宫内一步")
    m.board[49] = 5
    check(not m.legal_xiangqi(49,48),"不能移动屏障导致将帅照面")
    kings(m); m.board[40] = 7
    check(m.legal_xiangqi(40,39) and not m.legal_xiangqi(49,48),"兵过河才可横走")
    m.board[76] = 15
    check(m.in_check(0),"敌车攻击帅时将军")
    m.reset()
    var before: PackedInt32Array = m.board.duplicate()
    check(not m.play(54,45).is_empty(),"红兵合法前进")
    check(not m.undo(0,false) and m.undo_left[0] == 3,"未同意悔棋不撤回不扣次数")
    check(m.undo(0,true) and m.board == before and m.turn == 0 and m.undo_left[0] == 2,"同意悔棋恢复棋盘回合并只扣一次")
    kings(m); m.turn = 1; m.board[12] = 5; m.board[14] = 5
    m.judge()
    check(m.done and m.winner == 0,"无合法走法困毙判负")
    m.reset("darkchess")
    check(m.board.size() == 32 and m.revealed.count(false) == 32,"4乘8暗棋32子")
    var color: int = Model.side(m.board[0])
    m.flip(0)
    check(m.player_sides == [color,1-color] and m.turn == 1,"首翻决定阵营并换手")
    check(m.flip(0).is_empty(),"已翻开的棋子不能重复翻")
    dark(m); m.board[0] = 7; m.board[1] = 11
    check(1 in m.targets(0),"暗棋兵吃将")
    m.board[0] = 1; m.board[1] = 17
    check(1 not in m.targets(0),"帅不能吃卒")
    m.board[0] = 7; m.board[1] = 17
    check(1 in m.targets(0),"同级可互吃")
    dark(m); m.board[0] = 6; m.board[1] = 3; m.revealed[1] = false; m.board[2] = 11
    check(2 in m.targets(0),"暗棋炮隔相邻暗子也可吃将")
    m.board[1] = 11; m.board[2] = 0
    check(1 not in m.targets(0),"暗棋炮不可直接相邻吃")
    dark(m); m.board[0] = 6; m.board[1] = 3; m.board[3] = 11
    check(3 in m.targets(0),"暗棋炮可隔一子吃远处明子")
    dark(m); m.board[0] = 7; m.board[1] = 11
    m.play(0,1)
    check(m.done and m.winner == 0,"暗棋吃光对方判胜")
    dark(m); m.board[0] = 5; m.board[31] = 11; m.quiet = 119
    m.play(0,1)
    check(m.done and m.winner == 2,"120步无吃子和棋")
    var rng := RandomNumberGenerator.new()
    rng.seed = 78
    for match_id in range(3):
        m.reset()
        for step in range(60):
            if m.done: break
            var choices: Array = []
            for from in range(90):
                for to in m.targets(from): choices.append(Vector2i(from,to))
            check(not choices.is_empty(),"未结束象棋有合法动作")
            if choices.is_empty(): break
            var chosen: Vector2i = choices[rng.randi_range(0,choices.size()-1)]
            var owner: int = m.turn
            var move: Dictionary = m.play(chosen.x,chosen.y)
            check(not move.is_empty() and not m.in_check(owner),"随机对局绝不允许自己被将")
    print("CHINESE_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
