extends RefCounted
const Data = preload("res://games/education/learning_data.gd")
const NOTES = ["do","re","mi","fa","sol","la","si"]
static var banks: Dictionary = {}
static var decks: Dictionary = {}
static var recent: Dictionary = {}
static func records(subject: String) -> Array:
    if banks.has(subject): return banks[subject]
    var bank: Array = []
    if subject == "poetry":
        for p in Data.POEMS:
            bank.append({"prompt":p.first,"answer":p.next,"explanation":["《%s》 %s"%[p.title,p.author],"%s，%s。"%[p.first,p.next]],"group":p.next.length()})
    elif subject == "pinyin":
        for s in Data.SYLLABLES:
            var explanation: Array = ["%s（%s） · 声母 %s，韵母 %s"%[s.word,s.full,s.initial,s.final]]
            if s.initial in ["j","q","x"] and str(s.final).begins_with("ü"): explanation.append("与 j、q、x 相拼时，ü 省略两点")
            bank.append({"prompt":"%s    %s + ?"%[s.word,s.initial],"answer":s.final,"explanation":explanation})
    elif subject == "english":
        for i in range(Data.WORDS.size()):
            var w: Dictionary = Data.WORDS[i]
            var word: String = str(w.english).replace("-"," ")
            bank.append({"prompt":"看图片，选单词","answer":word,"picture":i,"explanation":["%s = %s"%[word,w.chinese]]})
    else:
        # 7 single notes + 49 ordered pairs + 343 ordered triples are distinct listening tasks.
        for length in [1,2,3]:
            for value in range(int(pow(7,length))):
                var code: int = value
                var sequence: Array = []
                for i in range(length):
                    sequence.push_front(code%7); code = int(code/7)
                var names := PackedStringArray()
                for note in sequence: names.append(NOTES[note])
                var answer: String = " · ".join(names)
                bank.append({"prompt":"听一听，这是什么音？" if length == 1 else "听%d个音，选出顺序"%length,"answer":answer,"notes":sequence,"note":sequence[0],"group":length,"explanation":["唱名顺序："+answer]})
    for i in range(bank.size()): bank[i].id = "%s:%d"%[subject,i]
    banks[subject] = bank
    return bank
static func draw(subject: String, count: int = 10, consume: bool = true) -> Array:
    var bank: Array = records(subject)
    var selected: Array = []
    if not consume:
        for i in range(mini(count,bank.size())): selected.append(i)
    else:
        for i in range(mini(count,bank.size())):
            if not decks.has(subject) or decks[subject].is_empty():
                var shuffled: Array = range(bank.size())
                shuffled.shuffle()
                var avoid: Array = recent.get(subject,[])+selected
                # Draw from the end. Keep the previous round's items away from the rollover edge.
                decks[subject] = shuffled.filter(func(n): return n in avoid)+shuffled.filter(func(n): return n not in avoid)
            selected.append(decks[subject].pop_back())
        recent[subject] = selected.duplicate()
    var result: Array = []
    for idx in selected:
        var q: Dictionary = bank[idx].duplicate(true)
        var pool: Array = []
        for other in bank:
            if q.has("group") and other.group != q.group: continue
            if subject == "english" and _ambiguous(q.answer,other.answer): continue
            if other.answer != q.answer and other.answer not in pool: pool.append(other.answer)
        pool.shuffle(); pool = pool.slice(0,3); pool.append(q.answer); pool.shuffle()
        q.choices = pool
        result.append(q)
    return result
static func _ambiguous(a: String, b: String) -> bool:
    # Avoid making a broad noun and one of its subtypes competing correct answers.
    var families: Array = [["bird","eagle","owl","parrot","peacock","swan","flamingo","woodpecker","sparrow","ostrich","penguin","duck","goose","rooster","chick"],["fish","goldfish"],["hat","cap"],["shoe","boot","slipper"],["cup","mug"],["car","taxi"],["tree","palm"],["book","dictionary","notebook"],["train","tram"]]
    for group in families:
        if a in group and b in group and (a == group[0] or b == group[0]): return true
    return false
