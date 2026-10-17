extends Control
const Book = preload("res://core/kids_book.gd")
var game_id: String = "snake"
var page_index: int = 0
func _ready() -> void:
    mouse_filter = Control.MOUSE_FILTER_IGNORE
    custom_minimum_size = Vector2(200,175)
func _draw() -> void:
    Book.demo(self,game_id,page_index,Rect2(Vector2.ZERO,size))
func _text(caption: String, at: Vector2, font_size: int = 26, tint: Color = Color("25384b")) -> void:
    var font: Font = ThemeDB.fallback_font
    var width: float = font.get_string_size(caption,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x
    draw_string(font,at-Vector2(width/2,-font_size*0.34),caption,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,tint)
func _box(rect: Rect2, tint: Color, radius: int = 14) -> void:
    var style := StyleBoxFlat.new()
    style.bg_color = tint
    style.set_corner_radius_all(radius)
    draw_style_box(style,rect)
