extends RefCounted
const Polish = preload("res://core/game_polish.gd")
# Godot counterpart of common/BoardArt.kt; future board games share this palette.
const Palette = {
    "BG":Color("f5f0e6"),"WOOD_HI":Color("f3e0bc"),"WOOD":Color("e7ce9e"),
    "WOOD_LO":Color("d4b37e"),"WOOD_FRAME":Color("bf9560"),"WOOD_LINE":Color("8a5a2b8a"),
    "PIECE_HI":Color("fef8ec"),"PIECE":Color("f3e0be"),"PIECE_RIM":Color("b99a6e"),
    "CARD":Color.WHITE,"TEXT":Color("1c2333"),"MUTED":Color("788177"),
    "RED":Color("c6534f"),"BLUE":Color("367daf"),"WATER":Color("73b8cf"),
    "WATER_HI":Color("d1edf1"),"GREEN":Color("52936b"),"SHADOW":Color("182d392b"),
    "SHADE":Color("192a35c4")
}
static func draw_wood_slab(v, rect: Rect2) -> void:
    v._box(Rect2(rect.position+Vector2(0,7),rect.size),Palette.SHADOW,18)
    v._box(rect,Palette.WOOD_FRAME,18)
    Polish.surface(v,rect.grow(-7),"wood")
    v.draw_rect(rect.grow(-8),Color("fff8dd55"),false,1.2)
static func draw_cream_piece(v, at: Vector2, radius: float, tint: Color, name: String, rank: int, top: bool, scale: float = 1) -> void:
    var r: float = radius*scale
    v.draw_circle(at+Vector2(0,3),r,Palette.SHADOW)
    v.draw_circle(at,r,tint)
    Polish.orb(v,at,r-2,Palette.PIECE_HI,false)
    v.draw_arc(at,r-5,0,TAU,32,Palette.PIECE_RIM,1,true)
    v.draw_set_transform(at,PI if top else 0.0,Vector2.ONE*scale)
    v._text(name,Vector2(0,-2),int(radius*1.10),tint)
    if rank > 0: v._text(str(rank),Vector2(radius*0.54,radius*0.47),int(radius*0.42),tint)
    v.draw_set_transform(Vector2.ZERO)
