extends SceneTree
const Model = preload("res://games/animal/animal_model.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func empty(m) -> void:
    m.reset()
    m.board.fill(0)
func has(m, source: int, target: int) -> bool:
    for move in m.moves(source):
        if move.to == target: return true
    return false
func _initialize() -> void:
    var m = Model.new()
    m.reset()
    check(m.board.size() == 63 and m.board.count(0) == 47,"7列9行，16只兽")
    check(m.board[0] == -7 and m.board[6] == -6 and m.board[14] == -1 and m.board[20] == -8,"上方布阵按用户图纸")
    check(m.board[56] == 6 and m.board[62] == 7 and m.board[42] == 8 and m.board[48] == 1,"下方布阵按用户图纸")
    var waters: int = 0
    for i in range(63):
        if Model.water(i): waters += 1
        check(Model.water(i) == Model.water(62-i),"河区中心对称")
    check(waters == 12 and Model.den(3) == 1 and Model.den(59) == 0,"两片2乘3河流及两端兽穴")
    check(Model.trap(10) == 1 and Model.trap(52) == 0 and not Model.water(10),"每方三陷阱，前方陷阱为陆地")
    empty(m)
    m.board[28] = 3; m.board[0] = -8
    check(has(m,28,21) and has(m,28,35) and not has(m,28,29),"普通兽四向一步，不入水")
    check(not has(m,28,22) and not has(m,28,42),"不能斜走或越格")
    m.board[28] = 1
    check(has(m,28,29),"鼠可以入河")
    m.board[29] = -1
    check(not has(m,28,29),"岸鼠不能直接吃水鼠")
    m.board[28] = 0; m.board[29] = 1; m.board[30] = -1
    check(has(m,29,30),"水鼠可互吃")
    m.board[28] = -8
    check(not has(m,29,28),"水鼠不能直接吃岸象")
    empty(m)
    m.board[7] = 1; m.board[8] = -8
    check(has(m,7,8),"岸鼠吃象")
    m.board[7] = 8; m.board[8] = -1
    check(not has(m,7,8),"象不能吃鼠")
    for rank in range(1,9):
        m.board[7] = rank; m.board[8] = -rank
        check(has(m,7,8),"同类可以互吃")
    empty(m)
    m.board[51] = 1; m.board[58] = -8
    check(has(m,51,58),"任意兽可吃陷入己方陷阱的敌兽")
    m.board[51] = 8; m.board[58] = -1
    check(has(m,51,58),"敌鼠入陷阱也可被象吃")
    empty(m)
    m.board[10] = 7; m.board[11] = -2
    check(not has(m,10,11),"进入敌方陷阱的兽失去战斗力")
    m.board[10] = -2; m.board[11] = 7
    check(has(m,11,10),"己方陷阱不提供无敌保护，仍按等级作战")
    empty(m)
    m.board[21] = 6; m.board[0] = -8
    check(has(m,21,24),"虎横跳两格宽河")
    m.board[22] = -1
    check(not has(m,21,24),"敌鼠在跳跃线上阻挡")
    m.board[22] = 1
    check(has(m,21,24),"按提供文档，己鼠不阻挡跳河")
    m.board[22] = 0; m.board[29] = -1
    check(has(m,21,24),"敌鼠不在跳跃线上不阻挡")
    m.board[24] = -8
    check(not has(m,21,24),"不能跳到比自己强的兽上")
    m.board[24] = -2
    check(has(m,21,24),"可以跳河吃弱兽")
    empty(m)
    m.board[15] = 7; m.board[0] = -8
    check(has(m,15,43),"狮可纵跳三格长河")
    m.board[29] = -1
    check(not has(m,15,43),"纵跳检查全部经过水格")
    empty(m)
    m.board[52] = 8; m.board[0] = -8
    check(not has(m,52,59),"不可进入己方兽穴")
    m.board[52] = 0; m.board[10] = 1
    check(not m.play(10,3).is_empty() and m.winner == 0 and m.reason == "进入对方兽穴","任意兽入敌穴胜")
    empty(m)
    m.board[7] = 8; m.board[8] = -2
    m.play(7,8)
    check(m.winner == 0 and m.reason == "对方已无棋子","吃光敌方胜")
    empty(m)
    m.board[0] = -1; m.board[1] = 2; m.board[7] = 2; m.board[48] = 1
    m.play(48,47)
    check(m.winner == 0 and m.reason == "对方所有棋子均无法移动","对方无合法走法也判负")
    m.reset(true)
    check(not m.tick(29.9) and m.tick(0.2) and m.winner == 1,"比赛每步30秒超时判负")
    m.reset(false)
    check(not m.tick(300) and m.winner == -1,"休闲模式无倒计时")
    empty(m)
    m.competitive = true; m.board[14] = 2; m.board[62] = -8
    for i in range(17):
        m.history[0].append({"piece":2,"from":7 if i%2 == 0 else 14,"to":14 if i%2 == 0 else 7,"trap":false,"threats":[]})
    check(not has(m,14,7) and has(m,14,21),"17/5限制原区域，仍可走出区域")
    m.history[0][10].trap = true
    check(has(m,14,7),"序列经过陷阱解除17/5限制")
    empty(m)
    m.competitive = true; m.board[21] = 7; m.board[17] = -2
    for i in range(6):
        m.history[0].append({"piece":7,"from":21,"to":24 if i in [0,2] else 21,"trap":false,"threats":[-2]})
    check(not has(m,21,24),"7步内禁止第三次同格追杀同兽")
    m.board[28] = -8
    check(has(m,21,24),"被追的兽不受长追限制")
    var rng := RandomNumberGenerator.new()
    rng.seed = 20261016
    for match_id in range(4):
        m.reset()
        for step in range(350):
            if m.winner >= 0: break
            var choices: Array = m.all_moves()
            check(not choices.is_empty(),"未结束对局必须有合法走法")
            if choices.is_empty(): break
            var chosen: Dictionary = choices[rng.randi_range(0,choices.size()-1)]
            var owner: int = m.turn
            var before: PackedInt32Array = m.board.duplicate()
            var applied: Dictionary = m.play(chosen.from,chosen.to)
            check(not applied.is_empty() and m.board[chosen.from] == 0 and m.board[chosen.to] == chosen.piece,"随机对局棋子一致")
            check(m.turn == 1-owner and m.board.count(0) >= before.count(0),"轮流走棋且不会凭空增加棋子")
    print("ANIMAL_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
