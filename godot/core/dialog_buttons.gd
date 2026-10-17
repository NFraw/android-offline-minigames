extends RefCounted
## 介绍小窗：纯色色块、大字，不使用纹理、描边或投影。
const TEXT := Color("24384b")
const SECONDARY := Color("e3edf4")
static func primary(action: String, caption: String) -> bool:
    return action in ["begin","close_rules"] or action.begins_with("ready") or caption.contains("✓")
static func fill(accent: Color, strong: bool, pressed: bool = false) -> Color:
    var color: Color = accent.darkened(0.12) if strong else SECONDARY
    return color.darkened(0.10) if pressed else color
static func font_size(font: Font, caption: String, rect: Rect2) -> int:
    var result: int = mini(32,int(rect.size.y*0.55))
    while result > 18 and font.get_string_size(caption,HORIZONTAL_ALIGNMENT_LEFT,-1,result).x > rect.size.x-24:
        result -= 1
    return result
static func draw(v, rect: Rect2, caption: String, accent: Color, strong: bool, top: bool = false, pressed: bool = false) -> void:
    v._box(rect,fill(accent,strong,pressed),10)
    var center: Vector2 = rect.get_center()
    v.draw_set_transform(center,PI if top else 0.0)
    var size: int = font_size(v.font,caption,rect)
    var width: float = v.font.get_string_size(caption,HORIZONTAL_ALIGNMENT_LEFT,-1,size).x
    v.draw_string(v.font,Vector2(-width/2,size*0.34),caption,HORIZONTAL_ALIGNMENT_LEFT,-1,size,Color.WHITE if strong else TEXT)
    v.draw_set_transform(Vector2.ZERO)
static func apply(button: Button, accent: Color, strong: bool = false) -> void:
    button.custom_minimum_size.y = 64
    button.add_theme_font_size_override("font_size",30)
    for state in ["normal","hover","pressed","disabled","focus"]:
        var style := StyleBoxFlat.new()
        style.bg_color = fill(accent,strong,state == "pressed")
        if state == "disabled": style.bg_color = Color("edf0f2")
        if state == "focus":
            style.bg_color = Color.TRANSPARENT
            style.border_color = TEXT
            style.set_border_width_all(2)
        style.set_corner_radius_all(10)
        style.content_margin_left = 16
        style.content_margin_right = 16
        style.content_margin_top = 8
        style.content_margin_bottom = 8
        button.add_theme_stylebox_override(state,style)
    for state in ["font_color","font_hover_color","font_pressed_color","font_focus_color"]:
        button.add_theme_color_override(state,Color.WHITE if strong else TEXT)
    button.add_theme_color_override("font_disabled_color",Color("83929d"))
