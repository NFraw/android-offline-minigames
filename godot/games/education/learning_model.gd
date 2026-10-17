extends RefCounted
const Bank = preload("res://games/education/learning_bank.gd")
const NOTES = ["do","re","mi","fa","sol","la","si"]
var questions: Array = []
var index: int = 0
var duel: bool = false
var scores: Array = [0,0]
var selections: Array = [-1,-1]
var revealing: bool = false
var finished: bool = false
var heard: bool = false
var remaining: float = 30.0
var music: bool = false
var reveal_time: float = 0.0
func question() -> Dictionary:
    return questions[index] if not questions.is_empty() else {}
func reset(id: String, consume: bool = true) -> void:
    duel = id.ends_with("_duel")
    music = id.begins_with("listening")
    index = 0; scores = [0,0]; selections = [-1,-1]
    revealing = false; finished = false; heard = false; remaining = 30; reveal_time = 0
    questions = Bank.draw(id.get_slice("_",0),10,consume)
func submit(player: int, choice: int) -> bool:
    if finished or revealing or (music and not heard): return false
    if player < 0 or player > (1 if duel else 0) or choice < 0 or choice >= 4: return false
    if selections[player] != -1: return false
    selections[player] = choice
    if not duel or question().choices[choice] == question().answer or (selections[0] >= 0 and selections[1] >= 0): reveal()
    return true
func reveal() -> void:
    if revealing or finished: return
    revealing = true; reveal_time = 0
    for player in range(2 if duel else 1):
        if selections[player] >= 0 and question().choices[selections[player]] == question().answer: scores[player] += 1
func tick(delta: float) -> void:
    if finished: return
    if revealing: reveal_time += delta
    elif duel and (not music or heard):
        remaining = maxf(0,remaining-delta)
        if remaining <= 0: reveal()
func advance() -> bool:
    if finished or not revealing: return false
    if index == questions.size()-1: finished = true; return false
    index += 1; selections = [-1,-1]; revealing = false; remaining = 30; heard = false; reveal_time = 0
    return true
