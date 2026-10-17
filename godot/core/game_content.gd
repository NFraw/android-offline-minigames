extends Node2D
## 新引擎游戏的公共契约。游戏负责逻辑和表现，壳负责入场/暂停/设置/结算。
var round_success: bool = true
signal pause_requested
signal score_changed(value: int)
signal lives_changed(value: int)
signal round_finished(score: int, reason: String)
@export var game_title: String = "小游戏"
@export_multiline var game_rules: String = ""
@export var start_caption: String = "开始游戏"
@export var resume_caption: String = "继续游戏"
@export var lives_caption: String = "生命"
@export var max_lives: int = 0
@export var control_hint: String = ""
@export var end_caption: String = "本局结束"
@export var category_caption: String = "离线小游戏"
func prepare() -> void:
    pass
func start_round() -> void:
    pass
func set_game_paused(_value: bool) -> void:
    pass


@export var custom_chrome: bool = false
@export var show_score: bool = true
