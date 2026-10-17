extends SceneTree
func _initialize() -> void: call_deferred("run")
func frames() -> void:
    for i in range(8): await process_frame
func capture(name: String) -> void:
    await frames()
    await RenderingServer.frame_post_draw
    root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../artifacts/"+name+".png"))
func run() -> void:
    root.size = Vector2i(720,1280)
    var platform = root.get_node("GamePlatform")
    platform.open_game("plane"); await frames()
    var shell = current_scene
    shell._primary()
    var game = shell.content
    game.set_game_paused(true)
    var m = game.model
    m.level = 2; m.wave_time = 15
    m.collect_pack("wing"); m.collect_pack("wing"); m.collect_pack("rapid"); m.collect_pack("missile")
    m.enemies = [m.make_enemy("weaver",Vector2(160,395),0),m.make_enemy("armor",Vector2(370,490),0),m.make_enemy("launcher",Vector2(555,360),0)]
    m.drop_pack("rapid",Vector2(460,735)); m.drop_pack("shield",Vector2(200,650))
    for i in range(3): m.bullets.append(m.make_bullet(Vector2(335+i*23,770-i*10),Vector2(0,-720)))
    m.enemy_shots = [{"position":Vector2(270,720),"previous":Vector2(270,720),"velocity":Vector2(0,310),"missile":true,"hp":2.0,"radius":12.0}]
    m.warnings = [{"owner":m.enemies[2].id,"position":Vector2(555,400),"target":Vector2(500,1050),"count":1,"missile":true,"time":0.7}]
    game.toast = "导弹已装备 · 三发与快射同时保留"; game.toast_time = 10
    game._sync_sprites(); game.queue_redraw()
    await capture("plane-campaign-wave")
    m.level = 6; m.collect_pack("homing"); m.collect_pack("rapid"); m.collect_pack("shield")
    m.start_boss(); m.boss.hp = float(m.boss.max_hp)*0.24; m.boss.fire = 0
    m.invulnerable = 5; m.tick(0.02)
    for i in range(3): m.bullets.append(m.make_bullet(Vector2(330+i*26,710),Vector2(0,-720)))
    for i in range(3): m.enemy_shots.append({"position":Vector2(240+i*115,600+i*70),"previous":Vector2(240+i*115,600+i*70),"velocity":Vector2(0,310),"missile":true,"hp":2.0,"radius":12.0})
    game.toast = "橙色导弹可以击落 · 留出安全空间"; game.toast_time = 10; game.intro_time = 0
    game._sync_sprites(); game.queue_redraw()
    await capture("plane-campaign-boss")
    print("PLANE_CAMPAIGN_PREVIEW_OK")
    quit()
