extends SceneTree
const Rules = preload("res://games/flight/flight_model.gd")
const Board = preload("res://games/flight/flight_board.gd")
var count: int = 0
var failed: int = 0
func check(value: bool, text: String) -> void:
    count += 1
    if not value:
        failed += 1
        push_error(text)
func _initialize() -> void:
    var board = Board.new()
    check(board.ring.size() == 52, "外圈恰好52格")
    check(board.hangar_rect(0) == Rect2(0,13,4,4), "参考图机场位于四角，不能占满六格")
    check(board.ring[0].is_equal_approx(Vector2(8.5,16)), "红方外圈入口位于底部中轴")
    check(is_equal_approx(board.ring[20].y,board.ring[32].y), "红方飞行航线水平贯通上部空白区")
    check(board.point_for(0,0,50).is_equal_approx(board.ring[0]), "完成外圈先回到同色冲刺入口")
    check(board.point_for(0,0,51).is_equal_approx(Vector2(8.5,14.5)), "冲刺第一格紧接外圈入口")
    check(board.point_for(0,0,56).is_equal_approx(Vector2(8.5,9.3)), "终点位于中央本方三角区")
    var unique := {}
    for point in board.ring:
        unique[point] = true
        check(point.x >= 0 and point.x < 17 and point.y >= 0 and point.y < 17, "路径不出棋盘")
    check(unique.size() == 52, "外圈没有重叠格")
    for i in range(52):
        check(board.ring[i].distance_to(board.ring[(i + 1) % 52]) <= 1.42, "跑道连续")
        check(board.rotate(board.ring[i], 1).is_equal_approx(board.ring[(i + 13) % 52]), "四方路径旋转一致")
    check(board.point_for(0,0,0).is_equal_approx(Vector2(4.6,16.45)), "红机掷6后必须停在机场旁起飞点，不能停在冲刺入口")
    check(board.point_for(0,0,1).is_equal_approx(Vector2(16.0/3.0,47.0/3.0)), "红机从起飞点走1步进入相邻蓝色斜角格")
    for seat in range(4):
        check(board.point_for(seat,0,0).is_equal_approx(board.launch_point(seat)), "四方起飞落点必须对应起字标记")
        check(board.point_for(seat,0,0).distance_to(board.point_for(seat,0,1)) < 1.5, "四方起飞后的第一格必须相邻")
        check(board.color_of(board.absolute(seat,50)) == seat, "外圈结束必须回到本方冲刺入口")
        check(board.point_for(seat,0,50).distance_to(board.point_for(seat,0,51)) <= 1.5, "环道与冲刺道必须连续")
    var launch_game = Rules.new()
    launch_game.reset(4)
    launch_game.positions[0][0] = 0
    check(launch_game.compute_move(0,0,1).get("to",-1) == 1, "起飞后1点只走相邻一格")
    var game = Rules.new()
    game.reset(3)
    check(game.seats == [0, 2, 1], "三人模式使用固定阵营，不能按人数改变起点")
    check(game.absolute_cell(2, 1) == 29, "玩家2位于对面")
    game.reset(2)
    check(game.legal_moves(4).is_empty(), "非6不能起飞")
    game.roll(6)
    check(game.phase == Rules.MOVE and game.legal_moves().size() == 4, "6可选择四架起飞")
    check(game.roll(4).is_empty(), "未走子不能重复掷骰")
    var result: Dictionary = game.move_piece(0)
    check(game.positions[0][0] == 0 and game.turn == 0 and game.phase == Rules.ROLL, "起飞留在本方起点并获得再掷")
    game.roll(6)
    game.move_piece(0)
    var penalty: Dictionary = game.roll(6)
    check(bool(penalty.get("penalty", false)) and game.positions[0][0] == -1 and game.turn == 2, "三连6撤回整轮并换人")
    game.reset(2)
    game.positions[0][0] = 53
    check(int(game.compute_move(0, 0, 2).get("to", -1)) == 55, "冲刺道内允许正常前进")
    check(game.compute_move(0, 0, 6).is_empty(), "超出终点不可走")
    check(int(game.compute_move(0, 0, 3).get("to", -1)) == 56, "精确到终点")
    game.positions[0][0] = 1
    check(int(game.compute_move(0, 0, 1).to) == 6, "同色格按棋盘颜色跳4格")
    game.positions[0][0] = 13
    check(int(game.compute_move(0, 0, 1).to) == 30, "跳到飞行格后飞12格")
    game.positions[0][0] = 17
    check(int(game.compute_move(0, 0, 1).to) == 30, "直接落飞行格也飞12格")
    game.positions[0][0] = 47
    check(int(game.compute_move(0, 0, 1).to) == 48, "末段不能借跳格跳入冲刺道")
    game.positions[0][0] = 49
    check(int(game.compute_move(0,0,1).to) == 50, "冲刺入口不跳回外圈或跨越冲刺格")
    game.reset(2)
    game.positions[0][0] = 4
    game.positions[2][0] = 31 # 红方进度5与绿方进度31均对应外圈7号格
    game.roll(1)
    result = game.move_piece(0)
    check(game.positions[2][0] == -1 and result.captures.size() == 1, "按全局格撞机，不能比较双方相对进度")
    game.positions[0][0] = 49
    check(int(game.compute_move(0,0,1).to) == 50, "冲刺入口不跳回外圈或跨越冲刺格")
    game.reset(2)
    game.positions[0][0] = 4
    game.positions[2][0] = 5
    game.roll(1)
    game.move_piece(0)
    check(game.positions[2][0] == 5, "相同相对进度但不同全局格不得撞机")
    game.reset(2)
    game.positions[0][0] = 25
    game.positions[2][0] = 0
    game.roll(1)
    game.move_piece(0)
    check(game.positions[2][0] == 0, "起飞格保护不能被撞回")
    game.reset(2)
    game.positions[0][0] = 53
    game.positions[2][0] = 55
    game.roll(2)
    game.move_piece(0)
    check(game.positions[2][0] == 55, "各自冲刺道不能互撞")
    game.reset(2)
    game.roll(1)
    check(game.turn == 2 and game.phase == Rules.ROLL, "无合法棋自动换手")
    game.reset(2)
    game.positions[0] = [56, 56, 56, 55]
    game.roll(1)
    game.move_piece(3)
    check(game.winner == 0 and game.phase == Rules.OVER, "四架到达才获胜")
    check(game.roll(6).is_empty() and game.move_piece(0).is_empty(), "结束后输入无效")
    for players in [2, 3, 4]:
        for seed_value in range(4):
            game.reset(players)
            game.rng.seed = seed_value + 77
            var actions: int = 0
            while game.phase != Rules.OVER and actions < 12000:
                if game.phase == Rules.ROLL:
                    game.roll()
                else:
                    var moves: Dictionary = game.legal_moves()
                    check(not moves.is_empty(), "走子阶段必须存在合法走法")
                    game.move_piece(game.best_move())
                actions += 1
                for seat in game.seats:
                    for progress in game.positions[seat]:
                        if progress < -1 or progress > Rules.FINISH:
                            check(false, "自动完整对局中进度越界")
            check(game.winner in game.seats and actions < 12000, "固定种子完整自动对局可结束")
    print("FLIGHT_TESTS: %d checks, %d failures" % [count, failed])
    quit(0 if failed == 0 else 1)
