extends RefCounted
static var textures: Dictionary = {}
static func texture(index: int) -> Texture2D:
    var sheet: int = int(index/64)
    if not textures.has(sheet): textures[sheet] = load("res://games/education/assets/english-atlas-%d.png"%sheet)
    return textures[sheet]
static func draw(v, index: int, center: Vector2, radius: float, top: bool = false) -> void:
    var tex: Texture2D = texture(index)
    var cell: Vector2 = tex.get_size()/8.0
    var source := Rect2(Vector2(index%8,int((index%64)/8))*cell,cell).grow(-2)
    if top: v.draw_set_transform(center,PI)
    v.draw_texture_rect_region(tex,Rect2((Vector2.ZERO if top else center)-Vector2.ONE*radius,Vector2.ONE*radius*2),source,Color.WHITE,false,true)
    if top: v.draw_set_transform(Vector2.ZERO)
