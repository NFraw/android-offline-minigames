extends SceneTree
const Model = preload("res://games/stone/stone_model.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    var m = Model.new()
    m.reset("tictactoe")
    for i in [0,3,1,4,2]: check(not m.place(i).is_empty(),"井字棋落子")
    check(m.winner == 1 and m.place(5).is_empty(),"三连胜后禁止再落子")
    m.reset("tictactoe",2)
    check(m.turn == 2 and not m.place(0).is_empty() and m.place(0).is_empty(),"可轮换先手且不能覆盖")
    m.reset("tictactoe")
    for i in [0,1,2,4,3,5,7,6,8]: m.place(i)
    check(m.winner == 0,"井字棋平局")
    m.reset("gomoku")
    for x in range(4): m.board[7*15+x+3] = 1
    m.place(7*15+7)
    check(m.winner == 1,"五子连珠")
    m.reset("gomoku")
    for x in range(4): m.board[7*15+x+3] = 1
    m.turn = 2
    var block: int = m.best_move()
    check(block in [7*15+2,7*15+7],"五子棋AI防守活四")
    m.reset("gomoku")
    m.turn = 2
    for x in range(4): m.board[x] = 2
    for y in range(2,6): m.board[y*15+10] = 1
    for x in range(11,15): m.board[6*15+x] = 1
    check(m.best_move() == 4,"AI有立即获胜机会时优先取胜而非防守双威胁")
    m.reset("reversi")
    check(m.legal_moves().size() == 4 and m.board.count(1) == 2,"黑白棋开局四个合法落点")
    var index: int = m.legal_moves()[0]
    var result: Dictionary = m.place(index)
    check(result.changed.size() == 1 and m.board.count(1) == 4 and m.board.count(2) == 1,"夹吃翻转")
    check(m.place(0).is_empty(),"黑白棋不能无夹线落子")
    var rng := RandomNumberGenerator.new()
    rng.seed = 181
    for match_id in range(5):
        m.reset("reversi")
        for step in range(65):
            if m.winner >= 0: break
            var options: Array = m.legal_moves()
            check(not options.is_empty(),"自动跳过后必须有合法落点")
            if options.is_empty(): break
            m.place(options[rng.randi_range(0,options.size()-1)])
        check(m.winner >= 0 and m.board.count(1)+m.board.count(2) <= 64,"黑白棋完整对局结束")
    m.reset("weiqi")
    m.board[1] = 1; m.board[15] = 1; m.board[0] = 2
    m.turn = 1
    m.place(16)
    check(m.board[0] == 2,"已有无气非法测试局面不会被远处落子误提")
    m.reset("weiqi")
    m.board[1] = 1; m.board[0] = 2
    m.turn = 1
    m.place(15)
    check(m.board[0] == 0 and m.captures[0] == 1,"围棋提走无气敌群")
    m.reset("weiqi")
    m.board[1] = 2; m.board[15] = 2
    check(not m.legal(0) and m.board[0] == 0,"围棋禁止自杀且试算不改棋盘")
    # Single-stone ko on an interior point.
    m.reset("weiqi")
    for i in [1,15,31]: m.board[i] = 1
    for i in [16,2,18,32]: m.board[i] = 2
    check(not m.place(17).is_empty() and m.board[16] == 0,"构造单子劫并提子")
    check(not m.legal(16),"禁止立即回提打劫")
    check(m.pass_turn() and m.winner == -1 and m.passes == 1,"一次虚手只换手")
    m.pass_turn()
    check(m.winner >= 0,"连续两次虚手结算面积")
    m.reset("weiqi")
    m.board.fill(1); m.board[0] = 0
    check(m.area_score()[0] == 225,"面积计子及己方围空")
    print("STONE_TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
