extends RefCounted
# 两款游戏共用一张32动物图集；纹理仅首次需要时加载，翻牌不创建/解码图片。
const ANIMALS = ["猫","金毛犬","兔子","棕熊","大熊猫","狐狸","狮子","老虎","考拉","浣熊","松鼠","刺猬","水獭","河狸","獾","狼","大象","长颈鹿","斑马","河马","犀牛","鹿","绵羊","马","企鹅","雪鸮","鹦鹉","鸭子","公鸡","小猪","青蛙","海龟"]
static var atlas: Texture2D
static func texture() -> Texture2D:
    if atlas == null: atlas = load("res://games/puzzle/assets/animals-atlas.png")
    return atlas
static func name_of(value: int) -> String:
    return ANIMALS[posmod(value,32)]
static func source_rect(value: int) -> Rect2:
    var index: int = posmod(value,32)
    var cell: float = texture().get_width()/8.0
    return Rect2(Vector2(index%8,int(index/8))*cell,Vector2.ONE*cell)
static func draw_animal(v, at: Vector2, radius: float, value: int, top: bool = false) -> void:
    var center: Vector2 = at
    if top:
        v.draw_set_transform(at,PI)
        center = Vector2.ZERO
    var rect := Rect2(center-Vector2.ONE*radius,Vector2.ONE*radius*2)
    v.draw_texture_rect_region(texture(),rect,source_rect(value),Color.WHITE,false,true)
    if top: v.draw_set_transform(Vector2.ZERO)
