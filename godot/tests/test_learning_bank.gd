extends SceneTree
const Bank = preload("res://games/education/learning_bank.gd")
var checks: int = 0
var failures: int = 0
func check(ok: bool, caption: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(caption)
func _initialize() -> void:
    Bank.decks.clear(); Bank.recent.clear()
    for subject in ["poetry","pinyin","english","listening"]:
        var bank: Array = Bank.records(subject)
        check(bank.size() >= 300,"每个主题至少300道不同题目 "+subject)
        var unique: Dictionary = {}
        for q in bank:
            var content: String = q.prompt+str(q.get("picture",""))+str(q.get("notes",[]))
            check(not unique.has(content),"题干/图片/音序不重复 "+subject)
            unique[content] = true
            if subject == "english": check(q.picture >= 0 and q.picture < 320,"320词图一一对应")
        var drawn: Dictionary = {}
        for round_index in range(5):
            var model = load("res://games/education/learning_model.gd").new()
            model.reset(subject+("_duel" if round_index%2 == 0 else "_adventure"))
            for q in model.questions:
                check(not drawn.has(q.id),"跨单人双人连续五局不重复 "+subject)
                check(q.choices.size() == 4 and q.choices.count(q.answer) == 1,"每题四选一且答案唯一")
                drawn[q.id] = true
        var before: Array = Bank.decks[subject].duplicate()
        Bank.draw(subject,10,false)
        check(Bank.decks[subject] == before,"准备页预览不消耗正式题目")
        Bank.decks[subject] = [0]
        Bank.recent[subject] = [1,2,3,4,5,6,7,8,9,10]
        var rollover: Array = Bank.draw(subject,10)
        var ids: Array = []
        for q in rollover: ids.append(q.id)
        var dedup: Dictionary = {}
        for id in ids: dedup[id] = true
        check(dedup.size() == 10,"题库轮换边界同局不重复")
        print("BANK_COUNT ",subject,"=",bank.size())
    var chess = load("res://games/chinese/chinese_model.gd")
    check(chess.name_of(1) == "帥" and chess.name_of(11) == "將" and chess.name_of(5) == "車" and chess.name_of(16) == "砲","象棋棋字使用传统写法")
    print("LEARNING BANK TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
