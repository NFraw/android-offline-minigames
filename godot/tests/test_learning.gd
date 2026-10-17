extends SceneTree
var checks: int = 0
var failures: int = 0
func check(ok: bool, label: String) -> void:
    checks += 1
    if not ok: failures += 1; push_error(label)
func _initialize() -> void:
    var script = load("res://games/education/learning_model.gd")
    check(script != null,"新版应提供益智答题逻辑")
    if script == null: quit(1); return
    var m = script.new()
    for subject in ["poetry","pinyin","english","listening"]:
        m.reset(subject+"_adventure")
        check(m.questions.size() == 10,"每个主题从合并题库抽取10题")
        for q in m.questions:
            check(q.choices.size() == 4 and q.choices.count(q.answer) == 1,"四个选项有且只有一个正确答案")
            var unique: Dictionary = {}
            for value in q.choices: unique[value] = true
            check(unique.size() == 4,"选项不重复")
            if subject == "english": check(ResourceLoader.exists("res://games/education/assets/english-atlas-%d.png"%int(q.picture/64)),"英语题生成图片存在")
        m.tick(100)
        check(not m.revealing,"单人练习不限时")
    for subject in ["poetry","pinyin","english","listening"]:
        for first in [0,1]:
            m.reset(subject+"_duel"); m.heard = true
            var correct: int = m.question().choices.find(m.question().answer)
            var wrong: int = (correct+1)%4
            check(not m.submit(-1,0) and not m.submit(2,0) and not m.submit(0,4),"拒绝无效玩家和选项")
            check(m.submit(first,correct) and m.revealing and m.scores[first] == 1,"任一方先答对立即得分并揭晓 "+subject)
            check(not m.submit(1-first,correct) and m.scores[1-first] == 0,"后答者不能再得分")
            m.tick(100)
            check(m.index == 0 and m.scores[first] == 1,"讲解不自动跳过，也不重复计分")
            check(m.advance() and m.selections == [-1,-1] and m.remaining == 30,"下一题清除选择并重置计时")
            m.heard = true
            correct = m.question().choices.find(m.question().answer); wrong = (correct+1)%4
            check(m.submit(first,wrong) and not m.revealing and m.scores[first] == 1,"先答错不揭晓，留给对方作答")
            check(not m.submit(first,correct),"答错玩家本题不能重答")
            check(m.submit(1-first,correct) and m.revealing and m.scores[1-first] == 1,"对方接答正确立即得分")
            m.advance(); m.heard = true
            correct = m.question().choices.find(m.question().answer); wrong = (correct+1)%4
            m.submit(first,wrong); m.submit(1-first,wrong)
            check(m.revealing and m.scores == [1,1],"双方答错则揭晓，双方都不得分")
            m.advance(); m.heard = true
            correct = m.question().choices.find(m.question().answer)
            m.submit(first,(correct+1)%4); m.tick(30)
            check(m.revealing and m.scores == [1,1],"一方答错另一方超时，不发放分数")
    m.reset("listening_duel"); m.tick(100)
    check(m.remaining == 30 and not m.submit(0,0),"未播放音符不倒计时也不可作答")
    m.heard = true; m.tick(30)
    check(m.revealing,"播放音符后正常倒计时")
    m.reset("pinyin_adventure")
    for i in range(10):
        m.submit(0,m.question().choices.find(m.question().answer)); m.advance()
    check(m.finished and m.scores[0] == 10,"10题结束并保留完整分数")
    check(not m.submit(0,0) and not m.advance(),"结束后输入冻结")
    print("LEARNING TESTS: %d checks, %d failures"%[checks,failures])
    quit(0 if failures == 0 else 1)
