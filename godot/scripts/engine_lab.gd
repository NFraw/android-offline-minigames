extends Control
## 环境验证场景，不计入正式游戏目录。
var hits: int = 0
var counter: Label
var orb: Panel
var animation: Tween
var lab_paused: bool = false

func _ready() -> void:
    var margin := MarginContainer.new()
    margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    for side in ["left", "right", "top", "bottom"]:
        margin.add_theme_constant_override("margin_" + side, 48 if side != "top" else 100)
    add_child(margin)
    var column := VBoxContainer.new()
    column.add_theme_constant_override("separation", 28)
    margin.add_child(column)
    var title := Label.new()
    title.text = "新版 · 引擎验证"
    title.add_theme_font_size_override("font_size", 40)
    column.add_child(title)
    var detail := Label.new()
    detail.text = "安卓离线开发环境\n触摸、动画、暂停和返回验证"
    detail.add_theme_font_size_override("font_size", 24)
    column.add_child(detail)
    var stage := Control.new()
    stage.custom_minimum_size = Vector2(0, 280)
    column.add_child(stage)
    orb = Panel.new()
    orb.position = Vector2(24, 70)
    orb.size = Vector2(110, 110)
    var style := StyleBoxFlat.new()
    style.bg_color = Color("4a6cf7")
    style.set_corner_radius_all(36)
    orb.add_theme_stylebox_override("panel", style)
    stage.add_child(orb)
    animation = create_tween().set_loops()
    animation.tween_property(orb, "position:x", 380.0, 1.2).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
    animation.tween_property(orb, "position:x", 24.0, 1.2).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
    counter = Label.new()
    counter.text = "触摸次数：0"
    counter.add_theme_font_size_override("font_size", 28)
    column.add_child(counter)
    _button(column, "点击测试", _on_test)
    _button(column, "暂停 / 继续", _on_pause)
    _button(column, "返回大厅", _on_exit)
    print("ENGINE_LAB_READY")

func _button(parent: Node, caption: String, action: Callable) -> void:
    var button := Button.new()
    button.text = caption
    button.custom_minimum_size = Vector2(0, 84)
    button.add_theme_font_size_override("font_size", 28)
    button.pressed.connect(action)
    parent.add_child(button)

func _on_test() -> void:
    hits += 1
    counter.text = "触摸次数：%d" % hits
    print("ENGINE_LAB_TOUCH:", hits)

func _on_pause() -> void:
    lab_paused = not lab_paused
    if lab_paused:
        animation.pause()
    else:
        animation.play()
    print("ENGINE_LAB_PAUSED:", lab_paused)

func _on_exit() -> void:
    print("ENGINE_LAB_EXIT")
    if Engine.has_singleton("MiniGamesHost"):
        Engine.get_singleton("MiniGamesHost").exitToHall()
    else:
        get_tree().quit()

func _notification(what: int) -> void:
    if what == NOTIFICATION_WM_GO_BACK_REQUEST or what == NOTIFICATION_WM_CLOSE_REQUEST:
        _on_exit()
