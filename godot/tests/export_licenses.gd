extends SceneTree
func _initialize() -> void:
    DirAccess.make_dir_recursive_absolute("res://assets/licenses")
    var file = FileAccess.open("res://assets/licenses/Godot-and-third-party.txt",FileAccess.WRITE)
    file.store_string(Engine.get_license_text()+"\n\nThird-party notices\n"+JSON.stringify(Engine.get_copyright_info(),"  ")+"\n\nLicense texts\n"+JSON.stringify(Engine.get_license_info(),"  "))
    quit()
