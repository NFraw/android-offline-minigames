extends SceneTree
const Model = preload("res://games/plane/plane_model.gd")
func _initialize() -> void: call_deferred("run")
func run() -> void:
    root.content_scale_size = Vector2i(1200,1040)
    root.size = Vector2i(1200,1040)
    var sheet := Control.new(); root.add_child(sheet)
    var bg := ColorRect.new(); bg.color = Color("101f33"); bg.size = Vector2(1200,1040); sheet.add_child(bg)
    var tactics: Array = ["瞄准预警 → 换位躲避","左右交叉 → 寻找空隙","导弹齐射 → 迎击拦截","左右分区 → 提前换边","航母护卫 → 多路观察","三阶段轮换 → 综合挑战"]
    for i in range(6):
        var card := Panel.new()
        card.position = Vector2(22+(i%3)*393,20+int(i/3)*510); card.size = Vector2(370,488)
        var style := StyleBoxFlat.new(); style.bg_color = Color("182e45"); style.set_corner_radius_all(18)
        card.add_theme_stylebox_override("panel",style); sheet.add_child(card)
        var art := TextureRect.new(); art.texture = load("res://games/plane/assets/boss-%d.png"%(i+1))
        art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE; art.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED; art.position = Vector2(30,64); art.size = Vector2(310,310); card.add_child(art)
        var title := Label.new(); title.text = Model.BOSS_NAMES[i]; title.position = Vector2(0,12); title.size = Vector2(370,40); title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; title.add_theme_font_size_override("font_size",24); card.add_child(title)
        var label := Label.new(); label.text = "第%d关 · 耐久%d\n%s"%[i+1,int(Model.BOSS_HP[i]),tactics[i]]; label.position = Vector2(0,392); label.size = Vector2(370,80); label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; label.add_theme_font_size_override("font_size",21); label.add_theme_color_override("font_color",Color("9edbdc")); card.add_child(label)
    for i in range(8): await process_frame
    await RenderingServer.frame_post_draw
    root.get_texture().get_image().save_png(ProjectSettings.globalize_path("res://../artifacts/plane-six-bosses.png"))
    print("SIX_BOSSES_PREVIEW_OK")
    quit()
