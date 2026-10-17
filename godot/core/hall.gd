extends Control
const Thumbnail = preload("res://core/game_thumbnail.gd")
const Book = preload("res://core/kids_book.gd")
const Catalog = preload("res://games/suite/catalog.gd")
const P = preload("res://core/board_art.gd").Palette
var platform: Node
var grid: GridContainer
var filter: String = ""
var cards: Array = []
var records: Dictionary = {}
var filters: Dictionary = {}
var overlay: Control
var footer: Label
var last_back_ms: int = -1000
func _ready() -> void:
    platform = get_node("/root/GamePlatform")
    if platform.launch.game != "hall":
        get_tree().change_scene_to_file.call_deferred("res://scenes/game_shell.tscn")
        return
    preload("res://core/screen_policy.gd").apply(get_window(),"hall")
    set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    var background := ColorRect.new()
    background.mouse_filter = Control.MOUSE_FILTER_IGNORE
    background.color = P.BG
    background.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    add_child(background)
    var margin := MarginContainer.new()
    margin.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    for edge in ["left","right","top","bottom"]: margin.add_theme_constant_override("margin_"+edge,24)
    add_child(margin)
    var column := VBoxContainer.new()
    column.add_theme_constant_override("separation",16)
    margin.add_child(column)
    var heading := HBoxContainer.new()
    column.add_child(heading)
    var title: Label = _label("离线小游戏",31)
    title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    heading.add_child(title)
    heading.add_child(_button("设置",_show_settings))
    column.add_child(_label("随时开一局 · 单人、双人和多人同屏",20,P.MUTED))
    var bar := HBoxContainer.new()
    bar.add_theme_constant_override("separation",12)
    column.add_child(bar)
    for entry in [["","全部"],["SOLO","单人"],["DUEL","双人"],["MULTI","多人"],["EDUCATION","益智"]]:
        var button: Button = _button(entry[1],_set_filter.bind(entry[0]))
        button.toggle_mode = true
        button.button_pressed = entry[0] == ""
        button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
        bar.add_child(button)
        filters[entry[0]] = button
    var scroll := ScrollContainer.new()
    scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
    scroll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
    scroll.scroll_deadzone = 12
    column.add_child(scroll)
    grid = GridContainer.new()
    grid.mouse_filter = Control.MOUSE_FILTER_PASS
    grid.add_theme_constant_override("h_separation",14)
    grid.add_theme_constant_override("v_separation",14)
    grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    scroll.add_child(grid)
    footer = _label("%d款游戏 · 完全离线 · 成绩与设置保存在本机"%Catalog.GAMES.size(),18,P.MUTED)
    footer.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    column.add_child(footer)
    get_viewport().size_changed.connect(_resize)
    _resize()
    records = platform.hall_records()
    _refresh()
    print("GAME_HALL_READY session=",platform.launch.get("session","preview"))
    platform.check_for_updates.call_deferred(false)
func _style(tint: Color, radius: int = 16) -> StyleBoxFlat:
    var box := StyleBoxFlat.new()
    box.bg_color = tint
    box.border_color = tint.darkened(0.10)
    box.set_border_width_all(1)
    box.shadow_color = Color("21314214")
    box.shadow_size = 3
    box.shadow_offset = Vector2(0,2)
    box.corner_radius_top_left = radius; box.corner_radius_top_right = radius
    box.corner_radius_bottom_left = radius; box.corner_radius_bottom_right = radius
    box.content_margin_left = 16; box.content_margin_right = 16
    box.content_margin_top = 12; box.content_margin_bottom = 12
    return box
func _label(caption: String, size: int = 22, tint: Color = P.TEXT) -> Label:
    var label := Label.new()
    label.mouse_filter = Control.MOUSE_FILTER_IGNORE
    label.text = caption
    label.add_theme_font_size_override("font_size",size)
    label.add_theme_color_override("font_color",tint)
    return label
func _button(caption: String, action: Callable) -> Button:
    var button := Button.new()
    button.mouse_filter = Control.MOUSE_FILTER_PASS
    button.action_mode = BaseButton.ACTION_MODE_BUTTON_RELEASE
    button.text = caption
    button.custom_minimum_size.y = 50
    button.add_theme_font_size_override("font_size",21)
    button.add_theme_color_override("font_color",P.PIECE_HI)
    button.add_theme_color_override("font_pressed_color",P.PIECE_HI)
    button.add_theme_color_override("font_hover_color",P.PIECE_HI)
    button.add_theme_stylebox_override("normal",_style(P.BLUE,12))
    button.add_theme_stylebox_override("pressed",_style(P.RED,12))
    button.add_theme_stylebox_override("hover",_style(P.BLUE.lightened(0.12),12))
    button.pressed.connect(func(): platform.feedback("ui_click"); action.call())
    return button
func _resize() -> void:
    if is_instance_valid(grid): grid.columns = clampi(int((get_viewport_rect().size.x-60)/280),2,5)
func _set_filter(value: String) -> void:
    filter = value
    for key in filters: filters[key].button_pressed = key == value
    _refresh()
func _refresh() -> void:
    if not is_instance_valid(grid): return
    for child in grid.get_children(): grid.remove_child(child); child.queue_free()
    cards.clear()
    for id in Catalog.GAMES:
        var spec: Dictionary = Catalog.GAMES[id]
        if filter == "EDUCATION":
            if not spec.get("educational",false): continue
        elif not filter.is_empty() and spec.category != filter: continue
        if str(spec.scene).is_empty(): continue
        cards.append(id)
        var panel := PanelContainer.new()
        panel.mouse_filter = Control.MOUSE_FILTER_PASS
        panel.set_meta("game_id",id)
        panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
        panel.custom_minimum_size.y = 202
        panel.add_theme_stylebox_override("panel",_style(_card_tint(id)))
        grid.add_child(panel)
        var column := VBoxContainer.new()
        column.mouse_filter = Control.MOUSE_FILTER_PASS
        column.add_theme_constant_override("separation",9)
        panel.add_child(column)
        var header := HBoxContainer.new()
        header.mouse_filter = Control.MOUSE_FILTER_PASS
        header.add_theme_constant_override("separation",10)
        column.add_child(header)
        var names := VBoxContainer.new()
        names.mouse_filter = Control.MOUSE_FILTER_PASS
        names.size_flags_horizontal = Control.SIZE_EXPAND_FILL
        names.add_theme_constant_override("separation",7)
        header.add_child(names)
        var title: Label = _label(spec.title,25)
        title.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
        names.add_child(title)
        var theme_name := _label(str(Book.info(id)[2]),17,Color(Book.info(id)[1]))
        theme_name.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
        names.add_child(theme_name)
        var thumbnail := Thumbnail.new()
        thumbnail.game_id = id
        thumbnail.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
        header.add_child(thumbnail)
        var description: Label = _label(str(spec.get("description","离线小游戏")),18,P.MUTED)
        description.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
        description.max_lines_visible = 2
        description.size_flags_vertical = Control.SIZE_EXPAND_FILL
        column.add_child(description)
        var record: Dictionary = records.get(id,{"best":0,"history":[]})
        var label: String = "最高 %d"%int(record.get("best",0))
        if Array(record.get("history",[])).is_empty(): label = "暂无记录"
        column.add_child(_label(label,18,P.MUTED))
        var row := HBoxContainer.new()
        row.mouse_filter = Control.MOUSE_FILTER_PASS
        row.add_theme_constant_override("separation",8)
        column.add_child(row)
        for action in [["开始",_open_game.bind(id)],["记录",_show_records.bind(id)],["玩法",_show_rules.bind(id)]]:
            var button: Button = _button(action[0],action[1])
            button.add_theme_stylebox_override("normal",_style(Color(Book.info(id)[1]),12))
            button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
            row.add_child(button)
func _card_tint(id: String) -> Color:
    # 大厅使用浅色卡片，关卡中的深空配色不用于卡片底盘。
    return Color("eef6fb") if id == "plane" else Color(Book.info(id)[0]).lerp(Color.WHITE,0.55)
func _open_game(id: String) -> void:
    platform.open_game(id)
func close_modal() -> void:
    if is_instance_valid(overlay):
        overlay.hide()
        overlay.queue_free()
    overlay = null
func _modal(title: String) -> VBoxContainer:
    close_modal()
    overlay = Control.new()
    overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    overlay.mouse_filter = Control.MOUSE_FILTER_STOP
    add_child(overlay)
    var shade := ColorRect.new()
    shade.color = P.SHADE
    shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    overlay.add_child(shade)
    var center := CenterContainer.new()
    center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    overlay.add_child(center)
    var panel := PanelContainer.new()
    panel.custom_minimum_size.x = minf(650,get_viewport_rect().size.x-50)
    panel.add_theme_stylebox_override("panel",_style(P.PIECE_HI,24))
    center.add_child(panel)
    var content := VBoxContainer.new()
    content.add_theme_constant_override("separation",16)
    panel.add_child(content)
    var heading: Label = _label(title,28)
    heading.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    content.add_child(heading)
    return content
func _body(content: VBoxContainer, text: String) -> void:
    var label: Label = _label(text,22)
    label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
    label.custom_minimum_size.x = minf(590,get_viewport_rect().size.x-100)
    content.add_child(label)
func _show_settings() -> void:
    var content: VBoxContainer = _modal("游戏设置")
    for entry in [["sound","音效"],["haptic","震动"],["voice","中文语音"]]:
        var toggle := CheckButton.new()
        toggle.text = entry[1]
        toggle.custom_minimum_size.y = 52
        toggle.add_theme_font_size_override("font_size",24)
        toggle.add_theme_color_override("font_color",P.TEXT)
        toggle.button_pressed = bool(platform.launch.get(entry[0],true))
        for state in ["font_color","font_hover_color","font_pressed_color","font_focus_color"]:
            toggle.add_theme_color_override(state,P.TEXT)
        toggle.toggled.connect(_toggle.bind(entry[0]))
        content.add_child(toggle)
    _body(content,"设置对所有游戏生效；关闭语音不会关闭其他音效。")
    _body(content,"游戏可离线游玩；检查和下载新版时才使用网络。")
    content.add_child(_button("检查更新",_check_updates))
    content.add_child(_button("开源许可",_show_licenses))
    content.add_child(_button("清空成绩与记录",_show_clear_confirmation))
    content.add_child(_button("关闭",close_modal))
func _check_updates() -> void:
    if platform.plugin:
        platform.check_for_updates(true)
    else:
        var content: VBoxContainer = _modal("检查更新")
        _body(content,"请在安卓应用中检查和安装更新。")
        content.add_child(_button("返回设置",_show_settings))
func _toggle(value: bool, key: String) -> void:
    platform.launch[key] = value
    platform.preferences(bool(platform.launch.sound),bool(platform.launch.haptic),bool(platform.launch.voice))
    platform.feedback("ui_switch")
func _show_records(id: String) -> void:
    var content: VBoxContainer = _modal(str(Catalog.GAMES[id].title)+" · 记录")
    var record: Dictionary = records.get(id,{"best":0,"history":[]})
    var entries: PackedStringArray = []
    for value in record.get("history",[]): entries.append(str(value))
    _body(content,"最高成绩：%d\n最近记录：%s"%[int(record.get("best",0)),"、".join(entries) if not entries.is_empty() else "暂无"])
    content.add_child(_button("开始游戏",_open_game.bind(id)))
    content.add_child(_button("关闭",close_modal))
func _show_rules(id: String) -> void:
    close_modal()
    overlay = preload("res://core/guide_overlay.gd").new()
    overlay.game_id = id
    add_child(overlay)
func _show_clear_confirmation() -> void:
    var content: VBoxContainer = _modal("确认清空")
    _body(content,"将清空本机所有游戏的最高成绩和最近记录。此操作不可撤销。")
    content.add_child(_button("取消",_show_settings))
    content.add_child(_button("确认清空",_clear_records))
func _clear_records() -> void:
    platform.clear_scores()
    records = platform.hall_records()
    _refresh()
    footer.text = "成绩和记录已清空"
    close_modal()
func _notification(what: int) -> void:
    if not is_node_ready() or platform == null: return
    if what in [NOTIFICATION_WM_GO_BACK_REQUEST,NOTIFICATION_WM_CLOSE_REQUEST]:
        var now: int = Time.get_ticks_msec()
        if now-last_back_ms < 150: return
        last_back_ms = now
        if is_instance_valid(overlay): close_modal()
        else: platform.quit_app()

func _show_licenses() -> void:
    var content: VBoxContainer = _modal("开源许可")
    var scroll := ScrollContainer.new()
    scroll.custom_minimum_size = Vector2(minf(590,get_viewport_rect().size.x-100),380)
    scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
    content.add_child(scroll)
    var text := RichTextLabel.new()
    text.fit_content = true
    text.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    text.custom_minimum_size.x = minf(560,get_viewport_rect().size.x-130)
    text.add_theme_color_override("default_color",P.TEXT)
    text.add_theme_font_size_override("normal_font_size",16)
    text.text = FileAccess.get_file_as_string("res://assets/licenses/Godot-and-third-party.txt")
    scroll.add_child(text)
    content.add_child(_button("返回设置",_show_settings))
