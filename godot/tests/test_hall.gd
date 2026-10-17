extends SceneTree
const Catalog = preload("res://games/suite/catalog.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    call_deferred("run")
func settle() -> void:
    for i in range(5): await process_frame
func run() -> void:
    var platform = root.get_node("GamePlatform")
    check(FileAccess.get_file_as_string("res://assets/licenses/Godot-and-third-party.txt").contains("MIT License"),"离线许可资源完整")
    change_scene_to_file("res://scenes/hall.tscn")
    await settle()
    check(current_scene.cards.size() == 37,"大厅包含全部37款游戏")
    for category in ["SOLO","DUEL","MULTI","EDUCATION"]:
        current_scene._set_filter(category)
        var count: int = 0
        for id in Catalog.GAMES:
            if (Catalog.GAMES[id].get("educational",false) if category == "EDUCATION" else Catalog.GAMES[id].category == category): count += 1
        check(current_scene.cards.size() == count,"分类筛选 "+category)
    current_scene._set_filter("")
    for method in ["_show_settings","_show_clear_confirmation","_show_licenses"]:
        current_scene.call(method)
        check(is_instance_valid(current_scene.overlay),"弹层 "+method)
        current_scene.close_modal()
    current_scene._show_rules("flight")
    check(is_instance_valid(current_scene.overlay),"玩法说明")
    current_scene.close_modal()
    current_scene._show_records("plane")
    check(is_instance_valid(current_scene.overlay),"成绩记录")
    current_scene.close_modal()
    await settle()
    var baseline: int = root.get_child_count()
    var sessions: Dictionary = {}
    for id in Catalog.GAMES:
        platform.open_game(id)
        await settle()
        check(platform.launch.game == id and is_instance_valid(current_scene.get("content")),"大厅启动 "+id)
        check(not sessions.has(platform.launch.session),"每次进入独立结算凭证 "+id)
        sessions[platform.launch.session] = true
        platform.exit_to_hall()
        await settle()
        check(platform.launch.game == "hall" and current_scene.cards.size() == 37,"返回大厅 "+id)
        check(root.get_child_count() == baseline,"切换无根节点泄漏 "+id)
    print("HALL TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
