extends "res://games/suite/suite_game.gd"
const Model = preload("res://games/arcade/contest_model.gd")
var model = Model.new()
var difficulty: int = 0
func _rules_text() -> String:
    if game_id == "math_duel": return "双方使用各自的数字键盘输入答案，先到5分胜。\n答错该玩家本题锁定，双方答错会换新题。\n支持入门、进阶、挑战三档；上方题目与键盘倒置。\n数字0、删除、提交分别独立处理。"
    if game_id == "reaction4": return "各自在自己的分区等待变绿，先到5分胜。\n四个分区独立计分，支持四根手指同时抢按。\n抢拍只让自己的本轮失效，不影响其他玩家。"
    return "两人等待区域变绿，先正确点击者得分。\n提前抢拍则对方得分，先到10分获胜。\n每轮随机等待，支持双方同时按住屏幕。"
func _new_game() -> void:
    model.reset(game_id,difficulty)
    message = "各自在自己的区域操作"
func _game_layout() -> void:
    if game_id == "math_duel":
        var size: Vector2 = get_viewport_rect().size
        arena = Rect2(20,150,size.x-40,size.y-230)
func _extra_setup(r: Rect2) -> void:
    if game_id == "math_duel":
        for i in range(3):
            var level: int = [0,3,5][i]
            var width: float = (r.size.x-60)/3
            _button(Rect2(r.position+Vector2(24+i*(width+6),305),Vector2(width,62)),["入门","进阶","挑战"][i]+(" ✓" if difficulty == level else ""),"level%d"%level,P.GREEN if difficulty == level else P.BLUE)
        _text(Model.LABELS[difficulty],r.position+Vector2(r.size.x/2,407),25,P.MUTED)
func _zone(owner: int) -> Rect2:
    if players == 2:
        return Rect2(arena.position+Vector2(0,arena.size.y/2 if owner == 0 else 0),Vector2(arena.size.x,arena.size.y/2)).grow(-5)
    return Rect2(arena.position+Vector2((owner%2)*arena.size.x/2,0 if owner >= 2 else arena.size.y/2),arena.size/2).grow(-5)
func _local_point(r: Rect2, p: Vector2, top: bool) -> Vector2:
    return r.end-p if top else r.position+p
func _draw_play() -> void:
    if game_id == "math_duel":
        for owner in range(2): _math_panel(owner)
        return
    for owner in range(players):
        var r: Rect2 = _zone(owner)
        var top: bool = owner == 1 if players == 2 else owner >= 2
        var tint: Color = P.BLUE.lightened(0.5)
        if model.early[owner]: tint = P.RED.lightened(0.3)
        elif model.used[owner]: tint = P.WOOD_LO
        elif model.state == 1: tint = P.GREEN
        Polish.tile(self,r,tint,3)
        var lamp: Vector2 = r.get_center()
        var radius: float = minf(r.size.x,r.size.y)*0.15
        draw_circle(lamp,radius+7,Color("f8f5e9"))
        Polish.orb(self,lamp,radius,Color("4d9c60") if model.state == 1 else (Color("cd8154") if model.early[owner] else Color("8998a7")))
        _text("玩家%d · %d分"%[owner+1,model.scores[owner]],_local_point(r,Vector2(r.size.x/2,r.size.y*0.25),top),27,P.TEXT,top)
        var status: String = "等待变绿"
        if model.early[owner]: status = "抢拍，本轮失效" if players == 4 else "抢拍，对方得分"
        elif model.used[owner]: status = "%d毫秒"%model.response_ms[owner]
        elif model.state == 1: status = "现在点击！"
        elif model.state == 2: status = "准备下一轮"
        _text(status,r.get_center()+Vector2(0,(-1 if top else 1)*r.size.y*0.25),28,P.PIECE_HI if model.state == 1 else P.TEXT,top)
func _math_panel(owner: int) -> void:
    var r: Rect2 = _zone(owner)
    var top: bool = owner == 1
    var tint: Color = P.BLUE if top else P.RED
    _box(r,Color("fffef5"),20)
    var header := Rect2(r.position.x,r.end.y-120 if top else r.position.y,r.size.x,120)
    _box(header,tint.lightened(0.87),14)
    _text(model.question,_local_point(r,Vector2(r.size.x/2,40),top),44,P.TEXT,top)
    _text(("本题已锁定" if model.locked[owner] else ("输入："+model.inputs[owner])),_local_point(r,Vector2(r.size.x/2,99),top),35,tint,top)
    var labels: Array = ["1","2","3","4","5","6","7","8","9","删除","0","提交"]
    var actions: Array = ["digit1","digit2","digit3","digit4","digit5","digit6","digit7","digit8","digit9","delete","digit0","submit"]
    var width: float = (r.size.x-36)/3
    var height: float = (r.size.y-144)/4
    for i in range(12):
        var logical := Rect2(12+(i%3)*width,135+int(i/3)*height,width-6,height-6)
        var physical := Rect2(r.end-logical.end if top else r.position+logical.position,logical.size)
        _button(physical,labels[i],"math%d_%s"%[owner,actions[i]],tint.lightened(0.3) if model.locked[owner] else tint,top)
func _player_status(seat: int) -> String:
    return "玩家%d · %d分 · %s"%[seat+1,model.scores[seat],("本题锁定" if model.locked[seat] else Model.LABELS[difficulty]) if game_id == "math_duel" else ("等待变绿" if model.state == 0 else "注意反应")]
func _game_action(action: String) -> void:
    if action.begins_with("level"): difficulty = int(action.trim_prefix("level")); return
    if action == "difficulty": difficulty = (difficulty+1)%Model.LABELS.size(); return
    if action.begins_with("math"):
        var parts: PackedStringArray = action.trim_prefix("math").split("_")
        var owner: int = int(parts[0])
        var old: int = model.scores[owner]
        if model.key(owner,parts[1]): _feedback("eat" if model.scores[owner] > old else "click")
        else: _feedback("lose")
        if model.done: _end(model.result)
func _game_down(at: Vector2, _finger: int) -> void:
    if game_id == "math_duel": return
    for owner in range(players):
        if _zone(owner).has_point(at):
            if model.react(owner): _feedback("lose" if model.early[owner] else "eat")
            if model.done: _end(model.result)
            return
func _game_process(delta: float) -> bool:
    var before_state: int = model.state
    var changed: bool = model.tick(delta)
    if game_id != "math_duel" and before_state != 1 and model.state == 1: _feedback("countdown")
    if model.done: _end(model.result)
    return changed
