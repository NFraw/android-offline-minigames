extends RefCounted
const BoardArt = preload("res://core/board_art.gd")
const P = BoardArt.Palette
const Polish = preload("res://core/game_polish.gd")
static func paint(v) -> void:
    var rect: Rect2 = v.board_rect
    var c: float = v.cell
    BoardArt.draw_wood_slab(v,rect.grow(12))
    for index in range(63):
        var p: Vector2i = v.Model.point(index)
        var r := Rect2(rect.position+Vector2(p)*c,Vector2.ONE*c)
        var owner: int = v.Model.trap(index)
        var den: int = v.Model.den(index)
        var tint: Color = P.RED if owner == 0 or den == 0 else P.BLUE
        v.draw_rect(r,P.PIECE_HI if (p.x+p.y)%2 == 0 else P.WOOD_HI)
        if owner >= 0 or den >= 0:
            v.draw_rect(r,tint.lightened(0.62) if den < 0 else tint)
            v.draw_set_transform(r.get_center(),PI if owner == 1 or den == 1 else 0)
            v._text("陷阱" if den < 0 else "兽穴",Vector2.ZERO,int(c*0.30),tint.darkened(0.35) if den < 0 else P.PIECE_HI)
            v.draw_set_transform(Vector2.ZERO)
        v.draw_rect(r,P.WOOD_LINE,false,1.2)
    for x in [1,4]:
        var river := Rect2(rect.position+Vector2(x,3)*c,Vector2(2,3)*c)
        Polish.surface(v,river,"ice")
        v.draw_rect(river,Color("76b4ca80"))
        v.draw_rect(river,P.WOOD_LINE,false,1.2)
        for row in range(3):
            var y: float = river.position.y+(row+0.5)*c
            var points := PackedVector2Array()
            for step in range(25):
                points.append(Vector2(river.position.x+0.22*c+step*c*1.56/24,y+sin(step*PI/6)*c*0.04))
            v.draw_polyline(points,P.WATER_HI,1.5,true)
        for top in [false,true]:
            var center: Vector2 = river.position+Vector2(c,c*(0.65 if top else 2.35))
            v.draw_set_transform(center,PI if top else 0)
            v._text("小 河",Vector2.ZERO,int(c*0.29),P.BLUE.darkened(0.25))
            v.draw_set_transform(Vector2.ZERO)
    if not v.model.last_move.is_empty():
        var from: Vector2 = v._center(v.model.last_move.from)
        var to: Vector2 = v._center(v.model.last_move.to)
        v.draw_line(from,to,P.GREEN*Color(1,1,1,0.5),2,true)
        v.draw_arc(from,c*0.30,0,TAU,32,P.GREEN,2,true)
        v.draw_arc(to,c*0.43,0,TAU,32,P.GREEN,2,true)
    if v.selected >= 0:
        v.draw_rect(Rect2(v._center(v.selected)-Vector2.ONE*c/2,Vector2.ONE*c).grow(-2),P.GREEN,false,3)
        for move in v.model.moves(v.selected):
            var center: Vector2 = v._center(move.to)
            if move.captured == 0:
                v.draw_circle(center,c*0.12,P.GREEN)
            else:
                v.draw_arc(center,c*0.44,0,TAU,32,P.RED,3,true)
    for index in range(63):
        var piece: int = v.model.board[index]
        if piece == 0 or (not v.animation.is_empty() and index == v.animation.to): continue
        token(v,index,piece)
    if not v.animation.is_empty():
        var a: Dictionary = v.animation
        var t: float = clampf(v.animation_time/v.animation_duration,0,1)
        if a.captured != 0 and t < 0.94:
            var shrink: float = 1-clampf((t-0.72)/0.22,0,1)
            token(v,a.to,a.captured,shrink)
        var center: Vector2 = v._center(a.from).lerp(v._center(a.to),smoothstep(0,1,t))
        var lift: float = sin(t*PI)*c*(0.75 if a.jump else 0.14)
        v.draw_circle(center+Vector2(0,4),c*0.34,P.SHADOW)
        token_at(v,center-Vector2(0,lift),a.piece,1+sin(t*PI)*0.05)
static func token(v, index: int, piece: int, scale: float = 1) -> void:
    token_at(v,v._center(index),piece,scale)
static func token_at(v, at: Vector2, piece: int, scale: float) -> void:
    if scale <= 0: return
    var top: bool = piece < 0
    BoardArt.draw_cream_piece(v,at,v.cell*0.40,P.BLUE if top else P.RED,v.Model.NAMES[absi(piece)],absi(piece),top,scale)
