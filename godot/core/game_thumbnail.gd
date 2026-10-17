extends Control
## 大厅专用静态小图：共用素材与Canvas绘制，不逐帧更新、不拦截滚动。
const Book = preload("res://core/kids_book.gd")
const Animals = preload("res://core/memory_art.gd")
const Polish = preload("res://core/game_polish.gd")
var game_id: String = "flight"
var accent: Color
var font: Font
var background: StyleBoxFlat
func _ready() -> void:
    mouse_filter = Control.MOUSE_FILTER_IGNORE
    custom_minimum_size = Vector2(76,76)
    font = ThemeDB.fallback_font
    accent = Color(Book.info(game_id)[1])
    background = StyleBoxFlat.new()
    background.bg_color = accent.lerp(Color.WHITE,0.88)
    background.set_corner_radius_all(10)
    resized.connect(queue_redraw)
func _box(rect: Rect2, color: Color, radius: int = 5) -> void:
    var style := StyleBoxFlat.new()
    style.bg_color = color
    style.set_corner_radius_all(radius)
    draw_style_box(style,rect)
func _text(caption: String, at: Vector2, font_size: int = 24, color: Color = Color("263b4b")) -> void:
    var width: float = font.get_string_size(caption,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size).x
    draw_string(font,at+Vector2(-width/2,font_size*0.34),caption,HORIZONTAL_ALIGNMENT_LEFT,-1,font_size,color)
func line(a: Vector2, b: Vector2, color: Color, width: float = 4) -> void:
    draw_line(a,b,color,width,true)
func tile(x: float, y: float, w: float, h: float, color: Color) -> void:
    _box(Rect2(x,y,w,h),color)
func grid() -> void:
    for i in range(4):
        line(Vector2(20+i*20,20),Vector2(20+i*20,80),accent.lightened(0.4),1.5)
        line(Vector2(20,20+i*20),Vector2(80,20+i*20),accent.lightened(0.4),1.5)
func _draw() -> void:
    if background == null: return
    draw_style_box(background,Rect2(Vector2.ZERO,size))
    draw_set_transform(Vector2.ZERO,0,size/100.0)
    var white := Color("fffdf6")
    var ink := Color("263b4b")
    var red := Color("d46a55")
    if game_id.begins_with("poetry") or game_id.begins_with("pinyin") or game_id.begins_with("english") or game_id.begins_with("listening"):
        tile(15,16,70,67,white)
        var symbol: String = "诗" if game_id.begins_with("poetry") else ("ā" if game_id.begins_with("pinyin") else ("♪" if game_id.begins_with("listening") else "ABC"))
        _text(symbol,Vector2(50,46),29,accent)
        line(Vector2(26,69),Vector2(73,69),accent,3)
        if game_id.ends_with("duel"): _text("2",Vector2(82,82),20,red)
        draw_set_transform(Vector2.ZERO)
        return
    match game_id:
        "flight":
            tile(15,18,43,43,white)
            for y in range(3):
                for x in range(2): draw_circle(Vector2(28+x*17,28+y*12),3,accent)
            draw_colored_polygon(PackedVector2Array([Vector2(71,37),Vector2(87,77),Vector2(71,65),Vector2(53,77)]),accent)
        "plane":
            draw_colored_polygon(PackedVector2Array([Vector2(50,15),Vector2(78,72),Vector2(56,62),Vector2(56,81),Vector2(44,81),Vector2(44,62),Vector2(22,72)]),accent)
            line(Vector2(50,15),Vector2(50,61),white,2)
        "snake", "snakebattle":
            for p in [Vector2(25,65),Vector2(25,49),Vector2(41,49),Vector2(57,49),Vector2(57,33)]: tile(p.x-7,p.y-7,14,14,accent)
            draw_circle(Vector2(60,30),2,white)
            draw_circle(Vector2(78,32),7,red)
            if game_id == "snakebattle":
                for x in range(3): tile(42+x*14,69,12,12,red)
                draw_circle(Vector2(73,72),2,white)
        "game2048":
            tile(15,15,70,70,Color("c68348"))
            _text("2048",Vector2(50,50),27,white)
        "tetris":
            for cell in [Vector2(0,0),Vector2(0,1),Vector2(1,1),Vector2(2,1)]: tile(20+cell.x*19,20+cell.y*19,17,17,accent)
            for x in range(3): tile(39+x*15,65,13,13,red)
        "whack":
            Polish.mole(self,Vector2(50,51),31)
        "memory", "memory_duel":
            tile(13,22,48,59,accent.lightened(0.5))
            tile(39,15,48,59,white)
            Animals.draw_animal(self,Vector2(63,43),20,0 if game_id == "memory" else 1)
            if game_id == "memory_duel": _text("2",Vector2(27,62),24,white)
        "animal":
            tile(13,13,74,74,white)
            Animals.draw_animal(self,Vector2(50,50),33,7)
        "mines":
            for y in range(3):
                for x in range(3): tile(15+x*24,15+y*24,22,22,Color("c0c0c0"))
            _text("1",Vector2(26,26),20,Color("305fa5"))
            line(Vector2(53,43),Vector2(53,76),ink,3)
            draw_colored_polygon(PackedVector2Array([Vector2(53,43),Vector2(72,50),Vector2(53,57)]),red)
        "gomoku", "weiqi", "reversi":
            grid()
            if game_id == "gomoku":
                for i in range(5): draw_circle(Vector2(22+i*14,50),6,ink)
            else:
                for i in range(4):
                    var at := Vector2(35+(i%2)*29,35+int(i/2)*29)
                    draw_circle(at,11,ink if i%2 == 0 else white)
                if game_id == "weiqi": draw_arc(Vector2(35,35),16,0,TAU,24,red,2,true)
        "tictactoe":
            for i in range(2):
                line(Vector2(36+i*27,16),Vector2(36+i*27,84),accent,3)
                line(Vector2(16,36+i*27),Vector2(84,36+i*27),accent,3)
            line(Vector2(18,18),Vector2(30,30),red,4)
            line(Vector2(30,18),Vector2(18,30),red,4)
            draw_arc(Vector2(50,50),9,0,TAU,24,accent,4,true)
        "xiangqi_duel", "darkchess":
            draw_circle(Vector2(39,42),25,Color("e2c795"))
            draw_circle(Vector2(39,42),21,white)
            _text("炮",Vector2(39,42),27,red)
            draw_circle(Vector2(70,69),17,accent if game_id == "darkchess" else white)
            _text("?" if game_id == "darkchess" else "将",Vector2(70,69),20,white if game_id == "darkchess" else ink)
        "pong":
            tile(20,17,10,39,accent)
            tile(70,47,10,39,red)
            line(Vector2(35,64),Vector2(60,40),accent.lightened(0.2),2)
            draw_circle(Vector2(54,43),8,white)
        "reaction", "reaction4":
            var count: int = 4 if game_id == "reaction4" else 2
            for i in range(count):
                var p := Vector2(32+(i%2)*36,32+int(i/2)*36) if count == 4 else Vector2(32+i*36,50)
                draw_circle(p,15,Color("6fa05d") if i == 0 else accent.lightened(0.6))
                if i == 0: draw_arc(p,20,0,TAU,24,white,3,true)
        "piano":
            tile(16,20,68,61,white)
            for x in range(1,4): line(Vector2(16+x*17,20),Vector2(16+x*17,81),ink,2)
            for x in range(3): tile(27+x*17,20,12,35,ink)
        "sliding":
            for i in range(8):
                tile(15+(i%3)*24,15+int(i/3)*24,21,21,accent if i == 0 else white)
                _text(str(i+1),Vector2(25+(i%3)*24,25+int(i/3)*24),17,white if i == 0 else ink)
        "fruit":
            Polish.fruit(self,Vector2(44,54),28,0)
            line(Vector2(47,80),Vector2(81,25),white,5)
            Polish.fruit(self,Vector2(73,31),13,1)
        "bounce", "breakout":
            for y in range(2):
                for x in range(3): tile(14+x*25,18+y*15,22,12,accent if y == 0 else red)
            tile(34,77,39,7,accent)
            draw_circle(Vector2(59,60),7,white)
            line(Vector2(47,73),Vector2(54,66),accent,2)
        "simon":
            for i in range(4): tile(17+(i%2)*35,17+int(i/2)*35,31,31,[Color("d46b58"),Color("4e91b8"),Color("73a45a"),Color("d0ac42")][i])
            draw_arc(Vector2(32,32),10,0,TAU,24,white,3,true)
        "curling":
            for i in range(3): draw_circle(Vector2(57,41),26-i*8,[accent,white,red][i])
            draw_circle(Vector2(29,72),13,Color("677d6a"))
            line(Vector2(22,69),Vector2(34,69),white,5)
        "math_duel":
            tile(13,20,74,60,white)
            _text("3 + 2",Vector2(50,40),24,accent)
            _text("= 5",Vector2(50,65),24,ink)
        "pool":
            line(Vector2(15,81),Vector2(46,48),Color("b18b54"),5)
            draw_circle(Vector2(47,45),12,white)
            draw_circle(Vector2(72,31),16,ink)
            draw_circle(Vector2(72,31),9,white)
            _text("8",Vector2(72,31),15,ink)
    draw_set_transform(Vector2.ZERO)
