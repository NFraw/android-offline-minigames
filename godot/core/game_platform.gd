extends Node
const Catalog = preload("res://games/suite/catalog.gd")
var launch: Dictionary = {"game":"hall","scene":"res://scenes/hall.tscn","session":"preview","best":0,"sound":true,"haptic":true,"voice":true}
var plugin: Object
var exiting: bool = false
var local_config := ConfigFile.new()
var receipts: Dictionary = {}
var session_counter: int = 0
var last_audio: Dictionary = {}
func _ready() -> void:
    if Engine.has_singleton("MiniGamesHost"):
        plugin = Engine.get_singleton("MiniGamesHost")
        var decoded = JSON.parse_string(plugin.getLaunchConfig())
        if decoded is Dictionary: launch = decoded
        if plugin.has_signal("game_requested"): plugin.connect("game_requested",open_game)
    else:
        local_config.load("user://preview.cfg")
        for key in ["sound","haptic","voice"]: launch[key] = local_config.get_value("preferences",key,true)
        for argument in OS.get_cmdline_user_args():
            if argument.begins_with("--game="):
                var id: String = argument.trim_prefix("--game=")
                if Catalog.GAMES.has(id) and not str(Catalog.GAMES[id].scene).is_empty(): _select_local(id)
func _select_local(id: String) -> void:
    session_counter += 1
    var preferences: Dictionary = {"sound":launch.get("sound",true),"haptic":launch.get("haptic",true),"voice":launch.get("voice",true)}
    launch = {"game":id,"session":"preview-%d-%d"%[Time.get_ticks_usec(),session_counter],"best":int(local_config.get_value(id,"best",0))}
    launch.merge(preferences)
    launch.scene = "res://scenes/hall.tscn" if id == "hall" else Catalog.GAMES[id].scene
    launch.landscape = false if id == "hall" else Catalog.GAMES[id].landscape
func open_game(id: String) -> void:
    if exiting: return
    if id != "hall" and (not Catalog.GAMES.has(id) or str(Catalog.GAMES[id].scene).is_empty()): return
    exiting = true
    call_deferred("_switch_scene",id)
func _switch_scene(id: String) -> void:
    if plugin:
        var decoded = JSON.parse_string(plugin.selectGame(id))
        if decoded is Dictionary: launch = decoded
        else: exiting = false; return
    else: _select_local(id)
    var target: String = "res://scenes/hall.tscn" if id == "hall" else "res://scenes/game_shell.tscn"
    print("GAME_SWITCH:",id," session=",launch.session)
    var error: Error = get_tree().change_scene_to_file(target)
    if error != OK: push_error("无法切换游戏："+id); exiting = false; return
    await get_tree().process_frame
    exiting = false
func exit_to_hall() -> void:
    open_game("hall")
func quit_app() -> void:
    if exiting: return
    exiting = true
    if plugin: plugin.exitToHall()
    else: get_tree().quit()
func hall_records() -> Dictionary:
    var records: Dictionary = {}
    if plugin:
        if launch.game == "hall":
            var decoded = JSON.parse_string(plugin.getLaunchConfig())
            if decoded is Dictionary: launch = decoded
        for game in launch.get("games",[]): records[str(game.id)] = game
    else:
        for id in Catalog.GAMES:
            records[id] = {"id":id,"best":int(local_config.get_value(id,"best",0)),"history":local_config.get_value(id,"history",[])}
    return records
func complete_round(round_index: int, score: int) -> int:
    if plugin:
        var response = JSON.parse_string(plugin.completeRound(round_index,score))
        if response is Dictionary: launch.best = int(response.get("best",score))
    else:
        var receipt: String = str(launch.session)+":"+str(round_index)
        if receipts.has(receipt): return int(launch.best)
        receipts[receipt] = true
        launch.best = maxi(int(launch.best),score)
        var history: Array = local_config.get_value(str(launch.game),"history",[])
        history.push_front(score)
        if history.size() > 10: history.resize(10)
        local_config.set_value(str(launch.game),"best",launch.best)
        local_config.set_value(str(launch.game),"history",history)
        local_config.save("user://preview.cfg")
    print("GAME_RESULT: game=%s round=%d score=%d best=%d"%[launch.game,round_index,score,int(launch.best)])
    return int(launch.best)
func settings(sound: bool, haptic: bool) -> void:
    preferences(sound,haptic,bool(launch.get("voice",true)))
func preferences(sound: bool, haptic: bool, voice_enabled: bool) -> void:
    launch.sound = sound; launch.haptic = haptic; launch.voice = voice_enabled
    if plugin: plugin.setPreferences(sound,haptic,voice_enabled)
    else:
        for key in ["sound","haptic","voice"]: local_config.set_value("preferences",key,launch[key])
        local_config.save("user://preview.cfg")
func clear_scores() -> void:
    if plugin: plugin.clearScores()
    else:
        for id in Catalog.GAMES:
            local_config.set_value(id,"best",0); local_config.set_value(id,"history",[])
        local_config.save("user://preview.cfg")
    launch.best = 0
func check_for_updates(manual: bool = false) -> void:
    if plugin: plugin.checkForUpdates(manual)
func feedback(cue: String, vibrate: bool = false) -> void:
    var resolved: String = audio_cue(cue)
    var now: int = Time.get_ticks_msec()
    if now-int(last_audio.get(resolved,-10000)) < 65 or not bool(launch.get("sound",true)):
        if vibrate and plugin: plugin.pulse(25)
        return
    last_audio[resolved] = now
    if plugin: plugin.feedback(resolved,vibrate)
func audio_cue(cue: String) -> String:
    var id: String = str(launch.game)
    if cue == "lose": return "ui_error"
    if cue == "win": return "powerup"
    if cue == "hit": return "zap"
    if cue == "click":
        if id in ["pool","curling"]: return "hit_metal"
        if id in ["memory","memory_duel"]: return "board_slide"
        return "ui_click"
    if cue == "move":
        if id in ["gomoku","weiqi","reversi","tictactoe"]: return "board_place"
        return "board_slide"
    if cue == "eat":
        if id in ["gomoku","weiqi","reversi","xiangqi_duel","darkchess","animal","flight"]: return "board_capture"
        if id in ["memory","memory_duel","game2048"]: return "combo"
        if id == "tetris": return "hit_glass"
        if id == "whack": return "hit_heavy"
        if id == "fruit": return "hit_soft"
        return "coin"
    return cue
func voice(cue: String) -> void:
    if plugin: plugin.speak(cue)
func haptic(milliseconds: int = 25) -> void:
    if plugin: plugin.pulse(clampi(milliseconds,1,300))
