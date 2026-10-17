extends Control
const DialogButtons = preload("res://core/dialog_buttons.gd")
const Book = preload("res://core/kids_book.gd")
const Picture = preload("res://core/guide_picture.gd")
var game_id: String = "snake"
var page_index: int = 0
func _ready() -> void:
    set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    _refresh()
func _refresh() -> void:
    for child in get_children(): remove_child(child); child.queue_free()
    var shade := ColorRect.new()
    shade.color = Color("182a40c9")
    shade.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    add_child(shade)
    var center := CenterContainer.new()
    center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    add_child(center)
    var panel := PanelContainer.new()
    panel.custom_minimum_size.x = minf(640,get_viewport_rect().size.x-48)
    var style := StyleBoxFlat.new()
    style.bg_color = Color("fffaf0")
    style.set_corner_radius_all(24)
    style.set_content_margin_all(22)
    panel.add_theme_stylebox_override("panel",style)
    center.add_child(panel)
    var column := VBoxContainer.new()
    column.add_theme_constant_override("separation",16)
    panel.add_child(column)
    var heading := Label.new()
    heading.text = Book.info(game_id)[2]+" · "+["怎么玩","再看一步","小小挑战"][page_index]
    heading.add_theme_font_size_override("font_size",29)
    heading.add_theme_color_override("font_color",Color("25384b"))
    heading.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    column.add_child(heading)
    var art = Picture.new()
    art.game_id = game_id; art.page_index = page_index
    column.add_child(art)
    for caption in Book.page(game_id,page_index):
        var label := Label.new()
        label.text = caption
        label.add_theme_font_size_override("font_size",27)
        label.add_theme_color_override("font_color",Color("25384b"))
        label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
        label.custom_minimum_size.x = minf(560,get_viewport_rect().size.x-110)
        label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
        column.add_child(label)
    if page_index == 2:
        var goal := Label.new()
        goal.text = str(Book.info(game_id)[3]).split("：")[-1]
        goal.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
        goal.add_theme_font_size_override("font_size",23)
        goal.add_theme_color_override("font_color",Color(Book.info(game_id)[1]))
        goal.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
        column.add_child(goal)
    var row := HBoxContainer.new()
    row.add_theme_constant_override("separation",12)
    column.add_child(row)
    _button(row,"上一页",_turn.bind(-1))
    var counter := Label.new()
    counter.text = "%d / 3"%(page_index+1)
    counter.custom_minimum_size.x = 80
    counter.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    counter.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    counter.add_theme_font_size_override("font_size",28)
    counter.add_theme_color_override("font_color",DialogButtons.TEXT)
    row.add_child(counter)
    _button(row,"下一页",_turn.bind(1))
    _button(column,"看懂了，继续",queue_free)
func _button(parent: Control, caption: String, action: Callable) -> void:
    var button := Button.new()
    button.text = caption
    button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    DialogButtons.apply(button,Color(Book.info(game_id)[1]),caption == "看懂了，继续")
    button.pressed.connect(action)
    parent.add_child(button)
func _turn(delta: int) -> void:
    page_index = (page_index+delta+3)%3
    _refresh()
