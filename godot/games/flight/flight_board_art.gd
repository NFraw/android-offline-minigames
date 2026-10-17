extends RefCounted
## 参考传统飞行棋图纸。所有底盘、落点和箭头均用矢量绘制。
const Polish = preload("res://core/game_polish.gd")
const LINE = Color("41504e")
static func point(v, p: Vector2) -> Vector2:
    return v.board_rect.position+p*v.cell
static func polygon(v, vertices: PackedVector2Array, color: Color) -> void:
    var pixels := PackedVector2Array()
    for vertex in vertices: pixels.append(point(v,vertex))
    v.draw_colored_polygon(pixels,color)
    pixels.append(pixels[0])
    v.draw_polyline(pixels,LINE,1.5,true)
static func spot(v, p: Vector2, radius: float = 0.33) -> void:
    v.draw_circle(point(v,p),v.cell*radius,Color("fffef8"))
    v.draw_arc(point(v,p),v.cell*radius,0,TAU,32,LINE,1.25,true)
static func paint(v) -> void:
    v._box(Rect2(v.board_rect.position+Vector2(-11,-5),v.board_rect.size+Vector2(22,22)),Color("152c382b"),18)
    Polish.tile(v,v.board_rect.grow(10),Color("e0e7df"),0)
    v.draw_rect(v.board_rect,Color("fffef5"))
    for seat in range(4):
        var r: Rect2 = v.model.board.hangar_rect(seat)
        r = Rect2(point(v,r.position),r.size*v.cell)
        Polish.tile(v,r,v.COLORS[seat],0)
        v.draw_rect(r,LINE,false,1.8)
        var center: Vector2 = r.get_center()
        v.draw_set_transform(center,PI if v._top_seat(seat) else 0.0)
        v._text(v.NAMES[seat]+(" · 电脑" if seat in v.model.seats and not v._human(seat) else ""),Vector2(0,-v.cell*1.60),16,Color.WHITE)
        v.draw_set_transform(Vector2.ZERO)
        for piece in range(4): spot(v,v.model.board.base_point(seat,piece),0.62)
    for index in range(52):
        polygon(v,v.model.board.polygons[index],v.COLORS[v.model.board.color_of(index)].lightened(0.10))
        spot(v,v.model.board.ring[index])
        if index%13 == 0:
            v.draw_set_transform(point(v,v.model.board.ring[index]),index/13*PI/2)
            v._text("↱",Vector2.ZERO,20,v.COLORS[index/13].darkened(0.3))
            v.draw_set_transform(Vector2.ZERO)
        elif index in [20,33,46,7]:
            var seat: int = posmod((index-20)/13,4)
            v.draw_set_transform(point(v,v.model.board.ring[index]),seat*PI/2)
            v._text("→",Vector2.ZERO,20,v.COLORS[v.model.board.color_of(index)].darkened(0.3))
            v.draw_set_transform(Vector2.ZERO)
    # 终点前五个矩形格；起飞坪与外圈入口使用同一个坐标来源。
    for seat in range(4):
        for progress in range(v.model.board.HOME_START,v.model.board.FINISH):
            var y: float = 14-(progress-v.model.board.HOME_START)
            var rect := PackedVector2Array()
            for p in [Vector2(8,y),Vector2(9,y),Vector2(9,y+1),Vector2(8,y+1)]: rect.append(v.model.board.rotate(p,seat))
            polygon(v,rect,v.COLORS[seat].lightened(0.08))
            spot(v,v.model.board.point_for(seat,0,progress),0.32)
        # 空白区域的两段同色飞行箭头；中轴跑道处留空。
        for x in [6.15,6.85,9.35,10.05]:
            var arrow := PackedVector2Array()
            var y: float = v.model.board.ring[20].y
            for p in [Vector2(x,y-0.23),Vector2(x+0.25,y-0.23),Vector2(x+0.52,y),Vector2(x+0.25,y+0.23),Vector2(x,y+0.23),Vector2(x+0.27,y)]: arrow.append(v.model.board.rotate(p,seat))
            var pixels := PackedVector2Array()
            for p in arrow: pixels.append(point(v,p))
            v.draw_colored_polygon(pixels,v.COLORS[seat])
        var goal := PackedVector2Array()
        for p in [Vector2(7,10),Vector2(10,10),Vector2(8.5,8.5)]: goal.append(v.model.board.rotate(p,seat))
        polygon(v,goal,v.COLORS[seat])
        spot(v,v.model.board.point_for(seat,0,v.model.board.FINISH),0.31)
        # 独立起飞坪：飞机进度0与起字标记共用此位置。
        var launch: Vector2 = v.model.board.launch_point(seat)
        v.draw_circle(point(v,launch),v.cell*0.40,v.COLORS[seat])
        v.draw_set_transform(point(v,launch),seat*PI/2)
        if not v.model.positions[seat].has(0): v._text("起",Vector2.ZERO,15,Color.WHITE)
        v.draw_set_transform(Vector2.ZERO)
    v.draw_circle(point(v,Vector2(8.5,8.5)),v.cell*0.30,Color("fffef5"))
    v._text("终",point(v,Vector2(8.5,8.5)),14,LINE)