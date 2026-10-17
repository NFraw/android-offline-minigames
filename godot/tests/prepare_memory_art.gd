extends SceneTree
func _initialize() -> void:
    var source: String = ProjectSettings.globalize_path("res://")+"../assets-library/artwork/memory/animals-source-v1.png"
    var image := Image.load_from_file(source)
    if image == null or image.is_empty(): push_error("动物原图读取失败"); quit(1); return
    image.resize(1536,768,Image.INTERPOLATE_LANCZOS)
    DirAccess.make_dir_recursive_absolute("res://games/puzzle/assets")
    var error: Error = image.save_png("res://games/puzzle/assets/animals-atlas.png")
    print("MEMORY_ATLAS_PREPARED: ",image.get_size()," error=",error)
    quit(0 if error == OK else 1)
