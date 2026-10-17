extends RefCounted
static func circle_rect(center: Vector2, radius: float, rect: Rect2) -> Dictionary:
    var closest := Vector2(clampf(center.x,rect.position.x,rect.end.x),clampf(center.y,rect.position.y,rect.end.y))
    var delta: Vector2 = center-closest
    var distance: float = delta.length()
    if distance >= radius: return {}
    if distance > 0.0001: return {"normal":delta/distance,"depth":radius-distance}
    var gaps: Array = [center.x-rect.position.x,rect.end.x-center.x,center.y-rect.position.y,rect.end.y-center.y]
    var index: int = 0
    for i in range(1,4):
        if gaps[i] < gaps[index]: index = i
    return {"normal":[Vector2.LEFT,Vector2.RIGHT,Vector2.UP,Vector2.DOWN][index],"depth":radius+gaps[index]}
static func segment_distance(point: Vector2, a: Vector2, b: Vector2) -> float:
    var delta: Vector2 = b-a
    if delta.length_squared() < 0.00001: return point.distance_to(a)
    return point.distance_to(a+delta*clampf((point-a).dot(delta)/delta.length_squared(),0,1))
static func balls_step(balls: Array, size: Vector2, radius: float, delta: float, bounce: float, damping: float, stop: float, pockets: Array = []) -> Dictionary:
    var maximum: float = 0
    for ball in balls:
        if ball.active: maximum = maxf(maximum,ball.v.length())
    var steps: int = clampi(ceili(maximum*delta/(radius*0.5)),1,24)
    var dt: float = delta/steps
    var contacts: Array = []
    var captures: Array = []
    var impact: float = 0
    for step in range(steps):
        for ball in balls:
            if not ball.active: continue
            ball.p += ball.v*dt
            for i in range(pockets.size()):
                if ball.p.distance_to(pockets[i]) < radius*1.85:
                    ball.active = false; ball.v = Vector2.ZERO
                    captures.append(Vector2i(ball.num,i)); break
        for i in range(balls.size()):
            var a: Dictionary = balls[i]
            if not a.active: continue
            for j in range(i+1,balls.size()):
                var b: Dictionary = balls[j]
                if not b.active: continue
                var offset: Vector2 = b.p-a.p
                var distance: float = offset.length()
                if distance > radius*2: continue
                var normal: Vector2 = offset/distance if distance > 0.0001 else Vector2.RIGHT
                var relative: float = (b.v-a.v).dot(normal)
                if relative < 0:
                    var impulse: float = -(1+bounce)*relative/2
                    a.v -= normal*impulse; b.v += normal*impulse
                    contacts.append(Vector2i(a.num,b.num)); impact = maxf(impact,-relative)
                var separation: Vector2 = normal*(radius*2-distance+0.01)*0.5
                a.p -= separation; b.p += separation
        for ball in balls:
            if not ball.active: continue
            if ball.p.x < radius: ball.p.x = radius; ball.v.x = absf(ball.v.x)*bounce
            elif ball.p.x > size.x-radius: ball.p.x = size.x-radius; ball.v.x = -absf(ball.v.x)*bounce
            if ball.p.y < radius: ball.p.y = radius; ball.v.y = absf(ball.v.y)*bounce
            elif ball.p.y > size.y-radius: ball.p.y = size.y-radius; ball.v.y = -absf(ball.v.y)*bounce
    var moving: bool = false
    for ball in balls:
        if not ball.active: continue
        ball.v *= exp(-damping*delta)
        if ball.v.length() < stop: ball.v = Vector2.ZERO
        else: moving = true
    return {"moving":moving,"contacts":contacts,"pockets":captures,"impact":impact}
