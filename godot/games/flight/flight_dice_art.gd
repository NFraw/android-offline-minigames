extends RefCounted
## 轻量真立方体投影：六面点数保持对面和为7；不使用模型或贴图。
const NORMALS = [Vector3(0,0,1),Vector3(0,0,-1),Vector3(1,0,0),Vector3(-1,0,0),Vector3(0,1,0),Vector3(0,-1,0)]
const VALUES = [1,6,2,5,3,4]
static func _orientation(value: int) -> Basis:
    var angle := Vector3.ZERO
    match value:
        6: angle.y = PI
        2: angle.y = -PI/2
        5: angle.y = PI/2
        3: angle.x = PI/2
        4: angle.x = -PI/2
    return Basis.from_euler(Vector3(-0.26,0.32,-0.06))*Basis.from_euler(angle)
static func _project(p: Vector3, edge: float) -> Vector2:
    return Vector2(p.x,-p.y)*(edge*0.5)*(5.5/(5.5-p.z))
static func draw(v, center: Vector2, edge: float, value: int, spinning: bool = false, progress: float = 1.0, tint: Color = Color.WHITE) -> void:
    var basis: Basis = _orientation(value)
    if spinning:
        var spin_end: Basis = Basis.from_euler(Vector3(PI*7.4,PI*9.3,PI*5.1))
        if progress < 0.80:
            var t: float = 1.0-pow(1.0-progress/0.80,2.1)
            basis = Basis.from_euler(Vector3(PI*7.4,PI*9.3,PI*5.1)*t)
        else:
            basis = Basis(spin_end.get_rotation_quaternion().slerp(basis.get_rotation_quaternion(),smoothstep(0,1,(progress-0.80)/0.20)))
    var faces: Array = []
    for i in range(6):
        var normal: Vector3 = NORMALS[i]
        var rotated: Vector3 = basis*normal
        if rotated.z <= 1.0/5.5: continue
        var u: Vector3 = Vector3.UP.cross(normal).normalized()
        if absf(normal.y) > 0.5: u = Vector3.RIGHT
        var w: Vector3 = normal.cross(u).normalized()
        faces.append({"normal":normal,"u":u,"w":w,"z":rotated.z,"value":VALUES[i]})
    faces.sort_custom(func(a,b): return a.z < b.z)
    for face in faces:
        var outline := PackedVector2Array()
        for corner in [Vector2(-1,-1),Vector2(1,-1),Vector2(1,1),Vector2(-1,1)]:
            outline.append(center+_project(basis*(face.normal+face.u*corner.x+face.w*corner.y),edge))
        var shade: Color = Color("fffdf4").darkened((1.0-face.z)*0.24).lerp(tint,0.035)
        v.draw_colored_polygon(outline,shade)
        outline.append(outline[0])
        v.draw_polyline(outline,Color("b8b4a9"),maxf(1,edge*0.014),true)
        var dots: Array = []
        var number: int = face.value
        if number%2 == 1: dots.append(Vector2.ZERO)
        if number >= 2: dots.append_array([Vector2(-0.5,-0.5),Vector2(0.5,0.5)])
        if number >= 4: dots.append_array([Vector2(-0.5,0.5),Vector2(0.5,-0.5)])
        if number == 6: dots.append_array([Vector2(-0.5,0),Vector2(0.5,0)])
        for dot in dots:
            var pip := PackedVector2Array()
            for step in range(16):
                var angle: float = TAU*step/16
                var p: Vector2 = dot+Vector2(cos(angle),sin(angle))*0.15
                pip.append(center+_project(basis*(face.normal*1.008+face.u*p.x+face.w*p.y),edge))
            v.draw_colored_polygon(pip,Color("cf4b4f") if number == 1 else Color("354454"))