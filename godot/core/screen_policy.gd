extends RefCounted
## GameRegistry generates Catalog; device rotation must never change a game's direction.
const Catalog = preload("res://games/suite/catalog.gd")

static func apply(window: Window, id: String) -> void:
    window.content_scale_mode = Window.CONTENT_SCALE_MODE_CANVAS_ITEMS
    window.content_scale_aspect = Window.CONTENT_SCALE_ASPECT_EXPAND
    window.content_scale_factor = 1.0
    if id == "hall":
        window.content_scale_size = Vector2i(720,960)
        DisplayServer.screen_set_orientation(DisplayServer.SCREEN_SENSOR)
        return
    var landscape: bool = bool(Catalog.GAMES.get(id,{}).get("landscape",false))
    window.content_scale_size = Vector2i(1280,720) if landscape else Vector2i(720,1280)
    DisplayServer.screen_set_orientation(DisplayServer.SCREEN_LANDSCAPE if landscape else DisplayServer.SCREEN_PORTRAIT)

static func wait_for_window(window: Window, id: String) -> void:
    # Android rotates asynchronously. Build the initial layout after the surface catches up.
    if OS.get_name() != "Android": return
    var landscape: bool = bool(Catalog.GAMES.get(id,{}).get("landscape",false))
    var deadline: int = Time.get_ticks_msec()+2000
    var stable_frames: int = 0
    while Time.get_ticks_msec() < deadline:
        await window.get_tree().process_frame
        var pixels: Vector2i = window.size
        if pixels.x > 0 and pixels.y > 0 and (pixels.x > pixels.y) == landscape:
            stable_frames += 1
            if stable_frames >= 2: return
        else:
            stable_frames = 0
