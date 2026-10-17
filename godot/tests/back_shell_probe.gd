extends "res://core/game_shell.gd"
var exit_count: int = 0
var pause_cost_ms: int = 300
func _ready() -> void:
    modal = Control.new()
    add_child(modal)
    session.start()
func _pause() -> void:
    session.pause()
    clock_ms += pause_cost_ms
func _exit() -> void:
    exit_count += 1
var clock_ms: int = 1000
func _back_clock() -> int:
    return clock_ms
