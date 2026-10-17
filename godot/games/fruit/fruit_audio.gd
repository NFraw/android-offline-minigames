extends Node
## Dedicated fruit foley, shared by desktop and Android, bounded polyphony.
const SWIPES = [preload("res://games/fruit/assets/audio/swipe_0.wav"),preload("res://games/fruit/assets/audio/swipe_1.wav"),preload("res://games/fruit/assets/audio/swipe_2.wav")]
const SLICES = [preload("res://games/fruit/assets/audio/slice_0.wav"),preload("res://games/fruit/assets/audio/slice_1.wav"),preload("res://games/fruit/assets/audio/slice_2.wav"),preload("res://games/fruit/assets/audio/slice_3.wav")]
const BOMB = preload("res://games/fruit/assets/audio/bomb.wav")
var voices: Array[AudioStreamPlayer] = []
var swipe_distance: float = 0
var last_swipe: int = -1000
var last_slice: int = -1000
var variant: int = 0
func _ready() -> void:
    for i in range(8):
        var player := AudioStreamPlayer.new()
        add_child(player); voices.append(player)
func enabled() -> bool:
    return bool(get_node("/root/GamePlatform").launch.get("sound",true))
func play_clip(clip: AudioStream, volume: float, pitch: float = 1) -> void:
    if not enabled(): return
    var player: AudioStreamPlayer = voices[variant%voices.size()]
    for voice in voices:
        if not voice.playing: player = voice; break
    player.stream = clip; player.volume_db = volume; player.pitch_scale = pitch; player.play()
    variant += 1
func swipe(distance: float) -> void:
    swipe_distance += distance
    var now: int = Time.get_ticks_msec()
    if swipe_distance < 85 or now-last_swipe < 125: return
    swipe_distance = 0; last_swipe = now
    play_clip(SWIPES[variant%3],-7,randf_range(0.94,1.08))
func slice(fruit_type: int) -> void:
    var now: int = Time.get_ticks_msec()
    if now-last_slice < 35: return
    last_slice = now
    play_clip(SLICES[(fruit_type+variant)%4],-3,randf_range(0.92,1.09))
func bomb() -> void:
    stop_all()
    play_clip(BOMB,-2)
func stop_all() -> void:
    for player in voices: player.stop()
    swipe_distance = 0
