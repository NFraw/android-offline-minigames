extends SceneTree
func _initialize() -> void: call_deferred("run")
func run() -> void:
    root.size = Vector2i(1280,720)
    var platform = root.get_node("GamePlatform")
    platform.launch.game = "fruit"
    platform.launch.scene = "res://games/physics/fruit.tscn"
    var shell = load("res://scenes/game_shell.tscn").instantiate()
    root.add_child(shell)
    for i in range(30): await process_frame
    shell._pause()
    for i in range(8): await process_frame
    root.get_texture().get_image().save_png("res://../pause-warm-preview.png")
    quit()
