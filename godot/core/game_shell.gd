extends Control
## 统一入场、暂停、设置、结算；游戏内容只提供对局信号与生命周期方法。
const Session = preload("res://core/game_session.gd")
const INK := Color("25384b")
const MUTED := Color("788177")
const ACCENT := Color("367daf")
var session = Session.new()
const Content = preload("res://core/game_content.gd")
var content: Content
var hud_score: Label
var hud_lives: Label
var modal: Control
var modal_title: Label
var modal_body: Label
var restart_button: Button
var primary: Button
var settings_row: HBoxContainer
var modal_mode: String = "start"
var score: int = 0
var last_back_press_ms: int = -1000
var modal_scroll: ScrollContainer
var modal_column: VBoxContainer

func _ready() -> void:
    mouse_filter = Control.MOUSE_FILTER_IGNORE
    if GamePlatform.launch.game == "hall":
        get_tree().change_scene_to_file.call_deferred("res://scenes/hall.tscn")
        return
    if GamePlatform.launch.game == "lab":
        get_tree().change_scene_to_file.call_deferred("res://scenes/engine_lab.tscn")
        return
    var game_id: String = str(GamePlatform.launch.game)
    var screen_policy = preload("res://core/screen_policy.gd")
    screen_policy.apply(get_window(),game_id)
    await screen_policy.wait_for_window(get_window(),game_id)
    if not is_inside_tree() or str(GamePlatform.launch.game) != game_id: return
    content = load(str(GamePlatform.launch.scene)).instantiate()
    add_child(content)
    content.score_changed.connect(_on_score)
    content.lives_changed.connect(_on_lives)
    content.round_finished.connect(_on_finish)
    content.pause_requested.connect(_pause)
    _build_interface()
    content.prepare()
    if content.custom_chrome:
        session.start()
        content.start_round()
        modal.hide()
    else:
        _show_modal("start", content.game_title, "\n".join(preload("res://core/kids_book.gd").page(str(GamePlatform.launch.game),0)))
    print("GAME_VIEWPORT:",game_id," pixels=",get_window().size," logical=",get_viewport_rect().size)
    print("GAME_READY:", GamePlatform.launch.game," session=",GamePlatform.launch.get("session","preview"))

func _panel(color: Color, radius: int = 24) -> StyleBoxFlat:
    var box := StyleBoxFlat.new()
    box.bg_color = color
    box.border_color = color.lightened(0.10)
    box.set_border_width_all(1)
    box.set_corner_radius_all(radius)
    box.content_margin_left = 26
    box.content_margin_right = 26
    box.content_margin_top = 18
    box.content_margin_bottom = 18
    return box

func _label(text: String, font_size: int, tint: Color) -> Label:
    var label := Label.new()
    label.text = text
    label.add_theme_color_override("font_color", tint)
    label.add_theme_font_size_override("font_size", font_size)
    return label

func _button(text: String, action: Callable, highlighted: bool = false) -> Button:
    var button := Button.new()
    button.text = text
    button.custom_minimum_size = Vector2(0, 78)
    preload("res://core/dialog_buttons.gd").apply(button,Color("33749a"),highlighted)
    button.pressed.connect(func(): GamePlatform.feedback("ui_back" if text == "返回大厅" else "ui_click"); action.call())
    return button

func _build_interface() -> void:
    var hud := PanelContainer.new()
    hud.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE)
    hud.offset_left = 28
    hud.offset_right = -28
    hud.offset_top = 30
    hud.offset_bottom = 150
    hud.add_theme_stylebox_override("panel", _panel(Color(0.06, 0.12, 0.20, 0.92)))
    add_child(hud)
    hud.visible = not content.custom_chrome
    var row := HBoxContainer.new()
    row.add_theme_constant_override("separation", 22)
    hud.add_child(row)
    var title := VBoxContainer.new()
    title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    row.add_child(title)
    title.add_child(_label(content.game_title, 28, INK))
    hud_lives = _label(content.lives_caption, 19, ACCENT)
    hud_lives.visible = content.max_lives > 0
    _on_lives(content.max_lives)
    title.add_child(hud_lives)
    hud_score = _label("0000", 34, INK)
    row.add_child(hud_score)
    row.add_child(_button("暂停", _pause))
    var hint := _label(content.control_hint, 20, MUTED)
    hint.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_WIDE)
    hint.offset_top = -64
    hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    hint.mouse_filter = Control.MOUSE_FILTER_IGNORE
    add_child(hint)
    hint.visible = not content.custom_chrome
    modal = Control.new()
    modal.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    add_child(modal)
    var shade := ColorRect.new()
    shade.color = Color("182a4099")
    shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    modal.add_child(shade)
    var center := CenterContainer.new()
    center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    modal.add_child(center)
    var card := PanelContainer.new()
    card.add_theme_stylebox_override("panel", _panel(Color("fffaf0"), 24))
    center.add_child(card)
    modal_scroll = ScrollContainer.new()
    modal_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
    card.add_child(modal_scroll)
    var column := VBoxContainer.new()
    modal_column = column
    column.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    column.add_theme_constant_override("separation", 12)
    modal_scroll.add_child(column)
    column.add_child(_label(content.game_title+" · "+content.category_caption, 18, MUTED))
    modal_title = _label("", 36, INK)
    column.add_child(modal_title)
    modal_body = _label("", 22, MUTED)
    modal_body.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    column.add_child(modal_body)
    settings_row = HBoxContainer.new()
    settings_row.add_theme_constant_override("separation", 24)
    column.add_child(settings_row)
    for key in ["sound", "haptic"]:
        var toggle := CheckButton.new()
        toggle.text = "音效" if key == "sound" else "震动"
        toggle.button_pressed = bool(GamePlatform.launch[key])
        toggle.add_theme_font_size_override("font_size", 23)
        for state in ["font_color","font_hover_color","font_pressed_color","font_focus_color"]:
            toggle.add_theme_color_override(state,INK)
        toggle.toggled.connect(_change_setting.bind(key))
        settings_row.add_child(toggle)
    primary = _button("", _primary, true)
    column.add_child(primary)
    restart_button = _button("重新选局", _restart)
    column.add_child(restart_button)
    column.add_child(_button("图解怎么玩", _open_guide))
    column.add_child(_button("返回大厅", _exit))
    get_viewport().size_changed.connect(_fit_modal)
    _fit_modal.call_deferred()

func _fit_modal() -> void:
    if not is_instance_valid(modal_scroll): return
    var viewport: Vector2 = get_viewport_rect().size
    var width: float = minf(580,viewport.x-100)
    modal_column.custom_minimum_size.x = width
    modal_body.custom_minimum_size.x = width
    modal_scroll.custom_minimum_size = Vector2(width,minf(modal_column.get_combined_minimum_size().y,viewport.y-100))

func _change_setting(value: bool, key: String) -> void:
    GamePlatform.launch[key] = value
    GamePlatform.settings(bool(GamePlatform.launch.sound), bool(GamePlatform.launch.haptic))

func _show_modal(mode: String, title: String, body: String) -> void:
    modal_mode = mode
    modal_title.text = title
    modal_body.text = body
    primary.text = content.resume_caption if mode == "paused" else ("再来一局" if mode == "ended" else content.start_caption)
    restart_button.visible = content.custom_chrome and mode == "paused"
    settings_row.visible = mode != "ended"
    modal.show()
    modal_scroll.scroll_vertical = 0
    _fit_modal.call_deferred()

func _primary() -> void:
    if modal_mode == "paused":
        if session.resume():
            content.set_game_paused(false)
    else:
        session.start()
        content.start_round()
    modal.hide()
    print("GAME_RUNNING:", GamePlatform.launch.game, " round=", session.round_index)

func _pause() -> void:
    if session.pause():
        content.set_game_paused(true)
        _show_modal("paused", "休息一下", ("当前得分  %d\n" % score if content.show_score else "") + "对局已暂停，准备好就继续吧。")
        print("GAME_PAUSED:", GamePlatform.launch.game)

func _on_score(value: int) -> void:
    score = value
    hud_score.text = "%04d" % value

func _on_lives(value: int) -> void:
    var count: int = clampi(value, 0, content.max_lives)
    hud_lives.text = content.lives_caption + "  " + "● ".repeat(count) + "○ ".repeat(content.max_lives - count)

func _on_finish(value: int, reason: String) -> void:
    if not session.finish():
        return
    content.set_game_paused(true)
    GamePlatform.feedback("win_jingle" if content.round_success else "lose_jingle")
    var best: int = GamePlatform.complete_round(session.round_index, value)
    _show_modal("ended", content.end_caption, ("%s\n\n本局得分  %d\n最高纪录  %d" % [reason, value, best]) if content.show_score else reason)

func _exit() -> void:
    GamePlatform.exit_to_hall()

func _notification(what: int) -> void:
    if not is_node_ready() or not is_instance_valid(modal):
        return
    if what == NOTIFICATION_APPLICATION_FOCUS_OUT:
        _pause()
    elif what == NOTIFICATION_WM_GO_BACK_REQUEST or what == NOTIFICATION_WM_CLOSE_REQUEST:
        # Android同一次返回可能连续送达两份通知，不能把第二份当作再次退出。
        var now: int = _back_clock()
        if now-last_back_press_ms < 150: return
        last_back_press_ms = now
        if session.is_running():
            _pause()
            # Modal creation may take longer than the debounce window on an emulator.
            last_back_press_ms = _back_clock()
        else:
            _exit()



func _restart() -> void:
    session.start()
    content.start_round()
    modal.hide()

func _back_clock() -> int:
    return Time.get_ticks_msec()

func _open_guide() -> void:
    var guide = preload("res://core/guide_overlay.gd").new()
    guide.game_id = str(GamePlatform.launch.game)
    add_child(guide)
