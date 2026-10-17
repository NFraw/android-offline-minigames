extends SceneTree
var failures: int = 0
func _initialize() -> void: call_deferred("run")
func frames() -> void:
    for i in range(4): await process_frame
func mouse(at: Vector2, pressed: bool) -> void:
    var event := InputEventMouseButton.new()
    event.button_index = MOUSE_BUTTON_LEFT; event.pressed = pressed; event.position = at
    Input.parse_input_event(event)
func swipe(from: Vector2) -> void:
    mouse(from,true); await process_frame
    for i in range(1,9):
        var event := InputEventMouseMotion.new()
        event.position = from-Vector2(0,i*22)
        event.relative = Vector2(0,-22); event.button_mask = MOUSE_BUTTON_MASK_LEFT
        Input.parse_input_event(event); await process_frame
    mouse(from-Vector2(0,176),false); await frames()
func run() -> void:
    root.size = Vector2i(720,1280)
    change_scene_to_file("res://scenes/hall.tscn"); await frames()
    var hall = current_scene
    var scroll = hall.grid.get_parent()
    var panel = hall.grid.get_child(0)
    await swipe(panel.get_global_rect().position+Vector2(80,90))
    if scroll.scroll_vertical < 30: failures += 1; push_error("从卡片内容拖动应滚动")
    scroll.scroll_vertical = 0; await frames()
    var thumbnail = panel.get_child(0).get_child(0).get_child(1)
    await swipe(thumbnail.get_global_rect().get_center())
    if scroll.scroll_vertical < 30: failures += 1; push_error("从游戏小图拖动应滚动")
    scroll.scroll_vertical = 0; await frames()
    var button = panel.get_child(0).get_child(panel.get_child(0).get_child_count()-1).get_child(0)
    await swipe(button.get_global_rect().get_center())
    if scroll.scroll_vertical < 30: failures += 1; push_error("从按钮区域拖动应滚动")
    if root.get_node("GamePlatform").launch.game != "hall": failures += 1; push_error("拖动不应误进游戏")
    scroll.scroll_vertical = 0; await frames()
    var record_button = panel.get_child(0).get_child(panel.get_child(0).get_child_count()-1).get_child(1)
    var at: Vector2 = record_button.get_global_rect().get_center()
    mouse(at,true); await process_frame; mouse(at,false); await frames()
    if not is_instance_valid(hall.overlay): failures += 1; push_error("正常点按记录按钮仍生效")
    if hall._card_tint("plane").get_luminance() < 0.85: failures += 1; push_error("飞机卡片保持浅色")
    print("HALL DRAG TESTS: 6 checks, %d failures"%failures)
    quit(0 if failures == 0 else 1)
