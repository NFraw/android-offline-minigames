extends SceneTree
func _initialize() -> void: call_deferred("run")
func settle() -> void:
    for i in range(8): await process_frame
func run() -> void:
    root.size = Vector2i(720,1280)
    var platform = root.get_node("GamePlatform")
    platform.open_game("snake")
    await settle()
    current_scene.content.rules_open = true
    current_scene.content.queue_redraw()
    await settle()
    await RenderingServer.frame_post_draw
    root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../artifacts/dialog-rules.png"))
    root.size = Vector2i(1280,720)
    platform.open_game("flight")
    await settle()
    await RenderingServer.frame_post_draw
    root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../artifacts/dialog-flight.png"))
    root.size = Vector2i(720,1280)
    platform.open_game("plane")
    await settle()
    current_scene._open_guide()
    await settle()
    await RenderingServer.frame_post_draw
    root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../artifacts/dialog-native-guide.png"))
    print("DIALOG_PREVIEW_OK")
    quit()
