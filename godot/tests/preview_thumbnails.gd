extends SceneTree
const Icon = preload("res://core/game_thumbnail.gd")
const Catalog = preload("res://games/suite/catalog.gd")
func _initialize() -> void: call_deferred("run")
func run() -> void:
    root.size = Vector2i(1080,880)
    var sheet := Control.new()
    root.add_child(sheet)
    var bg := ColorRect.new()
    bg.color = Color("fffdf8")
    bg.size = Vector2(1080,880)
    sheet.add_child(bg)
    var index: int = 0
    for id in Catalog.GAMES:
        var icon := Icon.new()
        icon.game_id = id
        icon.position = Vector2(30+(index%6)*175,18+int(index/6)*170)
        icon.size = Vector2(112,112)
        sheet.add_child(icon)
        var label := Label.new()
        label.text = Catalog.GAMES[id].title
        label.position = icon.position+Vector2(-15,117)
        label.size.x = 145
        label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
        label.add_theme_color_override("font_color",Color("263b4b"))
        label.add_theme_font_size_override("font_size",20)
        sheet.add_child(label)
        index += 1
    for i in range(8): await process_frame
    await RenderingServer.frame_post_draw
    root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../artifacts/thumbnails-all.png"))
    sheet.queue_free()
    await process_frame
    root.size = Vector2i(720,1280)
    change_scene_to_file("res://scenes/hall.tscn")
    for i in range(8): await process_frame
    await RenderingServer.frame_post_draw
    root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../artifacts/thumbnails-hall.png"))
    print("THUMBNAILS_PREVIEW_OK: ",index)
    quit()
