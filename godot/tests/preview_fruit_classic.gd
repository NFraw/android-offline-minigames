extends SceneTree
func _initialize() -> void: call_deferred("run")
func run() -> void:
    root.size = Vector2i(1280,720)
    root.content_scale_size = Vector2i(1280,720)
    var game = load("res://games/physics/fruit.tscn").instantiate()
    root.add_child(game)
    await process_frame
    game.set_process(false); game.start_round()
    await process_frame; await process_frame
    root.get_texture().get_image().save_png("res://../fruit-menu.png")
    game._action("begin")
    game.model.critical_chance = 0
    game.model.fruits = [game.model.make_fruit(Vector2(400,300),Vector2(40,-180),0),game.model.make_fruit(Vector2(600,340),Vector2(-50,-120),2),game.model.make_fruit(Vector2(780,310),Vector2.ZERO,1),game.model.make_fruit(Vector2(900,400),Vector2.ZERO,0,true)]
    game.model.cut(Vector2(300,310),Vector2(500,310))
    game.trails = [{"from":Vector2(300,310),"to":Vector2(500,310),"age":0.03}]
    game._events()
    game.queue_redraw()
    await process_frame; await process_frame
    root.get_texture().get_image().save_png("res://../fruit-gameplay.png")
    game.queue_free(); await process_frame
    quit()
