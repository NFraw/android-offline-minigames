extends RefCounted
const TRACK: int = 52
const OUTER_STEPS: int = 50
const HOME_START: int = 51
const FINISH: int = 56
const FLY_PROGRESS: int = 18
const UNITS: float = 17.0
const CENTER := Vector2(8.5,8.5)
var ring: Array[Vector2] = []
var polygons: Array[PackedVector2Array] = []
func _init() -> void:
    # 一象限13格：宽矩形、外斜角、内转角的两块三角形。
    var quarter: Array[PackedVector2Array] = [
        _rect(7,15,1,2), _rect(6,15,1,2),
        PackedVector2Array([Vector2(6,15),Vector2(4,15),Vector2(6,17)]),
        _rect(4,14,2,1), _rect(4,13,2,1),
        PackedVector2Array([Vector2(4,13),Vector2(6,13),Vector2(6,11)]),
        PackedVector2Array([Vector2(4,11),Vector2(6,11),Vector2(4,13)]),
        _rect(3,11,1,2), _rect(2,11,1,2),
        PackedVector2Array([Vector2(2,11),Vector2(0,11),Vector2(2,13)]),
        _rect(0,10,2,1), _rect(0,9,2,1), _rect(0,8,2,1)]
    var all_cells: Array[PackedVector2Array] = []
    for seat in range(4):
        for polygon in quarter:
            var rotated := PackedVector2Array()
            for point in polygon: rotated.append(rotate(point,seat))
            all_cells.append(rotated)
    # 外圈固定编号只描述图纸，与每方从独立起飞坪出发的相对进度分开。
    for index in range(TRACK):
        var polygon: PackedVector2Array = all_cells[posmod(index-1,TRACK)]
        polygons.append(polygon)
        var center := Vector2.ZERO
        for point in polygon: center += point
        ring.append(center/polygon.size())
func _rect(x: float,y: float,w: float,h: float) -> PackedVector2Array:
    return PackedVector2Array([Vector2(x,y),Vector2(x+w,y),Vector2(x+w,y+h),Vector2(x,y+h)])
func rotate(point: Vector2, quarter: int) -> Vector2:
    var result: Vector2 = point-CENTER
    for i in range(posmod(quarter,4)): result = Vector2(-result.y,result.x)
    return result+CENTER
func start_index(seat: int) -> int:
    return posmod(3+seat*13,TRACK)
func absolute(seat: int,progress: int) -> int:
    if progress <= 0 or progress > OUTER_STEPS: return -1
    return posmod(start_index(seat)+progress-1,TRACK)
func color_of(index: int) -> int:
    return posmod(index,4)
func base_point(seat: int,piece: int) -> Vector2:
    return rotate(Vector2(1.12+(piece%2)*1.76,14.35+(piece/2)*1.75),seat)
func hangar_rect(seat: int) -> Rect2:
    var a: Vector2 = rotate(Vector2(0,13),seat)
    var b: Vector2 = rotate(Vector2(4,17),seat)
    return Rect2(Vector2(minf(a.x,b.x),minf(a.y,b.y)),Vector2(4,4))
func point_for(seat: int,piece: int,progress: int) -> Vector2:
    if progress < 0: return base_point(seat,piece)
    if progress == 0: return launch_point(seat)
    if progress <= OUTER_STEPS: return ring[absolute(seat,progress)]
    if progress < FINISH: return rotate(Vector2(8.5,14.5-(progress-HOME_START)),seat)
    return rotate(Vector2(8.5,9.3),seat)
func launch_point(seat: int) -> Vector2:
    return rotate(Vector2(4.6,16.45),seat)
