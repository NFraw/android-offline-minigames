extends RefCounted
const MemoryArt = preload("res://core/memory_art.gd")
# 每款游戏自己的视觉主题、三页图解与可实践的小目标；不收集儿童数据。
# [主题背景、主色、风格名、能力提示、三页的短句]
const DATA = {
"poetry_adventure":["f3ead6","927044","诗笺书屋","先读上句，再回想下句",["读一读上面的诗句|从四个选项中找到下一句","答完会显示完整诗句和作者|看完讲解，再点下一题","一局十题，答对收集星星|看完讲解，再点下一题"]],
"poetry_duel":["f3ead6","927044","诗笺书屋","先读上句，再回想下句",["读一读上面的诗句|先答对得分，答错换对方","答完会显示完整诗句和作者|看完讲解，再点下一题","双方都答错或超时，就揭晓|看完讲解，再一起进入下一题"]],
"pinyin_adventure":["e4efdb","57814d","拼音森林","把声母和韵母连起来读",["看汉字和已经给出的声母|从四个选项中补上韵母","选项的韵母不标声调|答完可看完整拼音和声调","一局十题，慢慢想再选|看完讲解，再点下一题"]],
"pinyin_duel":["e4efdb","57814d","拼音森林","把声母和韵母连起来读",["看汉字和已经给出的声母|先答对得分，答错换对方","选项的韵母不标声调|答完可看完整拼音和声调","双方都答错或超时，就揭晓|看完讲解，再一起进入下一题"]],
"listening_adventure":["e8e3f4","786299","音乐森林","先听基准音，再比较题目音高",["先听基准do，再播放题目|从四个唱名中选择答案","题目可以重复听|音效关闭时，点暂停开启音效","一局十题，积累音高记忆|双人从播放题目后开始计时"]],
"listening_duel":["e8e3f4","786299","音乐森林","先听基准音，再比较题目音高",["每人每题可听两次|先播基准，再自动播放题目","播放时双方一起听|另一位不能插播或重启","先答对得分，答错换对方|题目播完后，才开始计时"]],
"english_adventure":["dfedf1","3f8393","单词小岛","看清图片，把图像和单词连起来",["仔细看图片画的是什么|从四个英文单词中选择","答完会出现中文意思|读一读，再点下一题","一局十题，答对收集星星|看完讲解，再点下一题"]],
"english_duel":["dfedf1","3f8393","单词小岛","看清图片，把图像和单词连起来",["仔细看图片画的是什么|先答对得分，答错换对方","答完会出现中文意思|读一读，再点下一题","双方都答错或超时，就揭晓|看完讲解，再一起进入下一题"]],

"flight":["e6f2f8","33749a","航空地图","计划：掷完骰子，比较哪架飞机更合适",["掷骰子，6点让飞机出发|点亮的飞机才可以移动","同色格可以跳一跳|碰到对手，就送它回机场","把四架飞机都送回终点|先想好走哪架，再点击"]],
"plane":["101f38","4c9bb1","星际航线","预判：看清预警路线，给自己留出安全空间",["拖动飞机，会自动开火|补给要接住，错过就会消失","双发、三发和快射可以叠加|导弹是单独冷却的副武器","橙色敌方导弹能被打下来|普通敌弹要躲开，别站着不动"]],
"animal":["f0edda","65874a","丛林棋阵","推理：走之前看看对方能吃到哪里",["轮流走一只小动物|先到对方兽穴就获胜","大动物吃小动物|小老鼠能吃大象","狮虎能跳河，小鼠能下水|陷阱里的棋子要小心"]],
"snake":["e7efd9","527644","果园小径","计划：给下一次转弯留出空间",["按方向，带小蛇找水果|也可以在棋盘上滑动","吃到水果，身体变长|不能马上往回掉头","别撞墙，也别撞身体|提前看两步，给自己留路"]],
"snakebattle":["e1ede5","437961","双蛇竞技场","协作：各管自己的方向，尊重对手",["每人控制一条蛇|上方按键朝向上方玩家","吃水果，长得更长|不能撞墙或任何蛇身","一条出局，另一条继续|两条都结束后比较长度"]],
"game2048":["f4e8cc","bd7442","数字实验室","推理：合并前，先看看空格够不够",["向一个方向滑动|相同数字碰到一起会合并","2加2变4，4加4变8|一次滑动，每块只合并一次","合成2048就成功|留一点空位，更容易继续"]],
"tetris":["e3e8f4","596c9b","方块街机","空间：观察形状，填好下面的空隙",["左右移动，点击旋转|把方块放进合适的空隙","一整行填满，就会消掉|点落到底，可马上落下","别让方块堆到顶|先补空洞，再考虑下一块"]],
"whack":["e4edd5","688346","花园地鼠","专注：先认准地鼠，再动手",["小地鼠探出头时点它|点中一次，加10分","空洞里没有地鼠|点空洞会扣2分","一局30秒，后期两只、三只一起出|连续点中，比乱点更有用"]],
"memory":["edf0d9","6b8c52","动物观察站","记忆：把位置和图案一起记住",["点开两张卡片|找到一样的两张，就配成一对","不一样会重新盖回|记住它们刚才的位置","找齐全部配对就成功|动物、数字、颜色都能练"]],
"memory_duel":["e4eade","547c73","记忆探险","记忆：轮到别人时，也可以认真观察",["两人轮流翻两张卡|找到相同的一对，就得分","配对成功，可以继续翻|配错就换另一位玩家","三关累计比较配对数|先从动物卡片开始吧"]],
"mines":["c0c0c0","304c85","经典扫雷","推理：用周围的数字，判断安全位置",["点格子，找安全的空地|第一下不会踩到雷","数字表示周围八格有几颗雷|确定有雷，长按插一面旗","翻开所有安全格就获胜|不确定时，先看旁边的数字"]],
"gomoku":["eee0c0","886136","五子棋馆","计划：既看自己的连线，也看对手",["黑白轮流放一颗棋子|只能放在空的交叉点","横、竖、斜都可以|先连成五颗相同棋子就赢","对手快连起来时要挡住|下之前，找一找两边的机会"]],
"pong":["dcebe2","3f7561","乒乓球场","协调：盯住来球，把球拍移到它前面",["每人拖动自己这一侧的球拍|两个人可以同时操作","用球拍把小球挡回去|漏过一球，对手得1分","先得到5分就获胜|看球的方向，不要只追球"]],
"tictactoe":["eaf0f8","4a739d","井字练习本","推理：先找能赢的一步，再防对手",["轮流在空格里画叉或圆|每次只能放一个","横、竖、斜连成三个就赢|九格满了也可能平局","下一局交换先手|试着提前想一步"]],
"reversi":["e3eadd","4f7655","黑白棋社","推理：比较落子后会翻过来哪些棋子",["用自己的棋子夹住对方|夹住的一整条都会翻色","必须能夹住，才能落子|小圆环提示可以放的位置","双方都不能下时比棋子数|角落通常比较不容易被翻"]],
"reaction":["f1e6d7","a46e48","反应训练场","自控：没有信号时，学会耐心等待",["把手放在自己的半边|先等待，不要着急点","区域变绿，马上点一下|先点到的人得分","提前点，对手得分|先到10分获胜"]],
"reaction4":["e8e6ef","76618e","四人信号站","自控：只看自己的信号，别被别人带动",["每人守住自己的小区域|四个人可以一起玩","看到变绿再点|提前点，这一轮自己失效","先到5分就获胜|本轮没抢到，等下一次信号"]],
"weiqi":["eee6cf","646350","围棋小院","推理：观察棋群周围还剩几口气",["黑白轮流落子|交叉点上的空路叫气","围住对手所有的气|就可以提走这一群棋子","不想下可以点跳过|两人连续跳过，比较占地"]],
"xiangqi_duel":["efdfc3","9b4d35","楚河汉界","计划：走棋前先保护好自己的将帅",["点自己的棋，再点落点|红方先走，双方轮流","炮要隔一枚棋子才能吃|马怕蹩腿，象不能过河","将帅遇到危险要先解围|悔棋必须请对方同意"]],
"piano":["eceaf2","66678e","节奏琴房","协调：跟着顺序点，保持稳定的节奏",["从最下面一行开始|只点黑色的琴键","点到白块，这局结束|没有点到，黑块滑出也结束","黑块会慢慢变快|先点稳，再试着更快"]],
"sliding":["eadfc9","a67a45","拼图工坊","空间：利用空格，把数字送回位置",["点击空格旁边的一块|它会滑进空格里","从1开始按顺序排好|最后右下角留一个空格","先排好一小行，再继续|卡住时，试着绕一下路"]],
"fruit":["f4e6d0","b57e48","木纹道场","判断：手指划过去之前，先看有没有炸弹",["滑动手指，切开水果|可以一次划过好几个","别碰黑色炸弹|切中炸弹立即结束","漏掉三个水果就结束|每100分恢复一次机会"]],
"bounce":["e2e8f0","4d7e9d","反弹实验室","预测：观察反弹方向，提前移动挡板",["左右拖动下面的挡板|把小球接住弹回去","击中方块就能消掉它|试试让球从不同角度反弹","清完方块就成功|小球掉下去，这局结束"]],
"breakout":["e8e2ef","82639c","彩砖街机","预测：看清小球将要落在哪里",["拖动挡板，接住小球|把球反弹到上面的砖块","数字是砖块还能被撞几次|打掉所有砖块就成功","别让小球落到底部|提前移动，比临时追球更稳"]],
"simon":["e4e4f0","6b6693","闪光音序室","记忆：把闪光分成一小段一小段来记",["先等一会儿，看颜色依次亮起|播放时不用点","看到轮到你，再照顺序点|相同颜色可能连续出现","每轮多一个颜色|可以默念颜色帮助记忆"]],
"darkchess":["e9ddce","85634e","翻棋茶馆","判断：翻开新棋前，看看附近有没有危险",["先翻开一枚棋，确定双方颜色|之后轮流翻棋或移动","大棋吃小棋，兵卒能吃将帅|炮吃棋要隔着一枚棋子","炮可以隔一子吃远处的明子|藏着的棋子不能直接吃"]],
"curling":["e1f0f2","4d8797","冰壶赛场","估计：比较力度大小和最终停下的位置",["所有玩家从同一个方向投壶|轮到谁，就把设备交给谁","在底部虚线框按住，再向后拉|拉动范围有限，松手就投出","越靠近圆心越有机会得分|观察上一壶，再调整力度"]],
"math_duel":["edf0dc","648053","算术挑战台","计算：先算清楚，再提交答案",["两人看各自的题目|用自己的大键盘输入答案","点提交，答对就得分|答错，本题先等一等","先得到5分就获胜|选适合自己的题目范围"]],
"pool":["e4e4da","547667","台球俱乐部","空间：想一想球碰到边后会往哪走",["向白球后面拖动，再松手|白球会撞向前方","认清自己的实球或花球|先把自己的一组打完","最后先点袋口，再打8号球|白球进袋，对方可以重新摆球"]]
}
static func info(id: String) -> Array:
    return DATA.get(id,DATA.snake)
static func page(id: String, index: int) -> PackedStringArray:
    return str(info(id)[4][clampi(index,0,2)]).split("|")
static func text(v, caption: String, at: Vector2, size: int = 28, tint: Color = Color("25384b")) -> void:
    v._text(caption,at,size,tint)
static func demo(v, id: String, page_index: int, r: Rect2) -> void:
    var accent := Color(info(id)[1])
    var ink := Color("25384b")
    var white := Color("fffdf6")
    var c: Vector2 = r.get_center()
    var u: float = minf(r.size.x/6,r.size.y/2.6)
    if id.begins_with("poetry") or id.begins_with("pinyin") or id.begins_with("english") or id.begins_with("listening"):
        v._box(Rect2(c-Vector2(u*2.5,u),Vector2(u*5,u*2)),white,14)
        var label: String = "床前明月光 → ?" if id.begins_with("poetry") else ("m + a → mā" if id.begins_with("pinyin") else ("♪  do  re  mi" if id.begins_with("listening") else "图片 → apple"))
        text(v,label,c-Vector2(0,u*0.35),int(u*0.46),accent)
        for i in range(4):
            v._box(Rect2(c+Vector2((i-2)*u*1.05+u*0.08,u*0.05),Vector2(u*0.9,u*0.65)),accent if i == page_index else accent.lightened(0.7),8)
            text(v,["A","B","C","D"][i],c+Vector2((i-1.5)*u*1.05,u*0.37),int(u*0.4),white if i == page_index else ink)
    elif id in ["memory","memory_duel"]:
        for i in range(3):
            var p: Vector2 = c+Vector2((i-1)*u*1.5,0)
            v._box(Rect2(p-Vector2.ONE*u*0.55,Vector2.ONE*u*1.1),accent.lightened(0.65) if i < 2 else accent,12)
            if i < 2: MemoryArt.draw_animal(v,p,u*0.51,1 if page_index == 1 and i == 1 else 0)
            else: text(v,"?",p,int(u*0.7),white)
    elif id == "plane" and page_index > 0:
        if page_index == 1:
            v._box(Rect2(c-Vector2(u*2.2,u),Vector2(u*4.4,u*2)),accent.lightened(0.8),14)
            for i in range(3):
                var p: Vector2 = c+Vector2((i-1)*u*0.8,0)
                v.draw_line(p+Vector2(0,u*0.5),p-Vector2(0,u*0.4),accent,7,true)
                v.draw_colored_polygon(PackedVector2Array([p-Vector2(0,u*0.75),p+Vector2(-u*0.2,-u*0.25),p+Vector2(u*0.2,-u*0.25)]),accent)
            text(v,"快",c+Vector2(u*1.8,0),int(u*0.5),accent)
        else:
            var missile: Vector2 = c+Vector2(u,0)
            v.draw_colored_polygon(PackedVector2Array([missile+Vector2(0,u*0.7),missile+Vector2(-u*0.25,-u*0.3),missile-Vector2(0,u*0.7),missile+Vector2(u*0.25,-u*0.3)]),Color("c68148"))
            v.draw_dashed_line(c-Vector2(u*1.8,0),c+Vector2(u*0.3,0),accent,4,8)
            text(v,"×",missile,int(u*0.8),white)
            text(v,"可击落",c-Vector2(u,0),int(u*0.4),accent)
    elif id in ["snake","snakebattle"]:
        for i in range(5): v._box(Rect2(c+Vector2((i-3)*u*0.65,-u*0.25),Vector2.ONE*u*0.6),accent,6)
        v.draw_circle(c+Vector2(u*1.4,0),u*0.28,Color("ce644a"))
        text(v,"→",c+Vector2(0,u*0.8),int(u*0.7),accent)
    elif id in ["simon","reaction","reaction4"]:
        for i in range(4):
            var p: Vector2 = c+Vector2((i%2-0.5)*u*1.1,(int(i/2)-0.5)*u*1.1)
            var colors: Array = [Color("d46b58"),Color("4e91b8"),Color("73a45a"),Color("d0ac42")]
            v._box(Rect2(p-Vector2.ONE*u*0.46,Vector2.ONE*u*0.92),colors[i] if i == page_index%4 else colors[i].lightened(0.7),12)
            if i == page_index%4: v.draw_arc(p,u*0.35,0,TAU,28,white,4,true)
    elif id == "mines":
        for y in range(2):
            for x in range(4):
                var p: Vector2 = c+Vector2((x-1.5)*u,(y-0.5)*u)
                v.draw_rect(Rect2(p-Vector2.ONE*u*0.48,Vector2.ONE*u*0.96),Color("bdbdbd"))
                v.draw_rect(Rect2(p-Vector2.ONE*u*0.48,Vector2.ONE*u*0.96),Color.WHITE,false,3)
        text(v,"1",c+Vector2(-u*0.5,-u*0.5),int(u*0.7),Color.BLUE)
        v.draw_line(c+Vector2(u*0.5,0),c+Vector2(u*0.5,u*0.85),ink,3)
        v.draw_colored_polygon(PackedVector2Array([c+Vector2(u*0.5,0),c+Vector2(u*1.1,u*0.2),c+Vector2(u*0.5,u*0.4)]),Color("c94d43"))
    elif id == "piano":
        for i in range(5): v.draw_rect(Rect2(c+Vector2((i-2.5)*u,-u),Vector2(u,2*u)),white)
        for i in range(1,5): v.draw_line(c+Vector2((i-2.5)*u,-u),c+Vector2((i-2.5)*u,u),ink,2)
        v.draw_rect(Rect2(c+Vector2(-u*0.5,-u),Vector2(u,u*1.5)),ink)
        text(v,"↓",c+Vector2(0,u*0.9),int(u*0.65),accent)
    elif id == "flight" and page_index == 0:
        var die: Vector2 = c-Vector2(u*1.2,0)
        v._box(Rect2(die-Vector2.ONE*u*0.72,Vector2.ONE*u*1.44),white,12)
        for row in range(3):
            for col in range(2): v.draw_circle(die+Vector2((col-0.5)*u*0.7,(row-1)*u*0.43),u*0.08,accent)
        text(v,"→",c,int(u*0.6),accent)
        v.draw_colored_polygon(PackedVector2Array([c+Vector2(u*1.3,-u*0.8),c+Vector2(u*0.6,u*0.5),c+Vector2(u*1.3,0),c+Vector2(u*2,u*0.5)]),accent)
    elif id == "animal":
        if page_index == 1:
            animal(v,c-Vector2(u*1.4,0),u*0.65,1)
            text(v,"鼠",c+Vector2(-u*1.4,u*0.85),int(u*0.35))
            text(v,"→",c,int(u*0.7),accent)
            var elephant: Vector2 = c+Vector2(u*1.4,0)
            for sign in [-1,1]: v.draw_circle(elephant+Vector2(sign*u*0.55,0),u*0.42,Color("9baeba"))
            v.draw_circle(elephant,u*0.55,Color("b5c4cb"))
            v.draw_line(elephant,elephant+Vector2(u*0.15,u*0.7),Color("9baeba"),u*0.28,true)
            for sign in [-1,1]: v.draw_circle(elephant+Vector2(sign*u*0.22,-u*0.1),u*0.05,ink)
            text(v,"象",c+Vector2(u*1.4,u*0.85),int(u*0.35))
        elif page_index == 2:
            v._box(Rect2(c-Vector2(u*0.75,u*0.9),Vector2(u*1.5,u*1.8)),Color("83c5d5"),8)
            animal(v,c-Vector2(u*1.6,0),u*0.55,2)
            v.draw_arc(c,u*1.5,PI,TAU,32,accent,4,true)
            text(v,"→",c+Vector2(u*1.5,0),int(u*0.55),accent)
        else:
            animal(v,c-Vector2(u*1.3,0),u*0.7,2)
            text(v,"→",c,int(u*0.7),accent)
            v._box(Rect2(c+Vector2(u*0.7,-u*0.6),Vector2(u*1.2,u*1.2)),accent,12)
            text(v,"穴",c+Vector2(u*1.3,0),int(u*0.65),white)
    elif id in ["xiangqi_duel","darkchess"] and page_index >= 1:
        var labels: Array = ["炮","子","将"]
        for i in range(3):
            var p: Vector2 = c+Vector2((i-1)*u*1.8,0)
            v.draw_circle(p,u*0.54,white)
            v.draw_arc(p,u*0.54,0,TAU,28,accent,3,true)
            text(v,labels[i],p,int(u*0.55),accent if i < 2 else Color("b85749"))
        v.draw_arc(c,u*1.8,PI,TAU,32,accent,3,true)
    elif id in ["pool","curling","pong","bounce","breakout","fruit","plane","flight"]:
        v._box(Rect2(c-Vector2(u*2.2,u),Vector2(u*4.4,u*2)),accent.lightened(0.8),14)
        if id == "curling":
            for i in range(3): v.draw_circle(c+Vector2(u,0),u*(0.8-i*0.23),[accent,white,Color("c36a56")][i])
        elif id in ["bounce","breakout"]:
            for i in range(4): v._box(Rect2(c+Vector2((i-2)*u,u*-0.8),Vector2(u*0.9,u*0.4)),accent,4)
        elif id in ["plane","flight"]:
            v.draw_colored_polygon(PackedVector2Array([c+Vector2(0,-u*0.9),c+Vector2(-u*0.6,u*0.6),c,c+Vector2(u*0.6,u*0.6)]),accent)
        elif id == "fruit": v.draw_circle(c+Vector2(u,0),u*0.5,Color("c26b48"))
        else: v._box(Rect2(c+Vector2(-u*1.7,-u*0.7),Vector2(u*0.3,u*1.4)),accent,5)
        v.draw_circle(c-Vector2(u,0),u*0.24,accent)
        v.draw_dashed_line(c-Vector2(u*0.7,0),c+Vector2(u*0.55,0),accent,3,9)
        text(v,"→",c+Vector2(0,u*0.75),int(u*0.55),accent)
    elif id == "whack":
        v.draw_circle(c,u*0.7,Color("826048"))
        animal(v,c-Vector2(0,u*0.2),u*0.65,1)
    elif id in ["game2048","sliding","math_duel","tetris"]:
        for i in range(3):
            var p: Vector2 = c+Vector2((i-1)*u*1.5,0)
            v._box(Rect2(p-Vector2.ONE*u*0.58,Vector2.ONE*u*1.16),accent.lightened(0.65),10)
            text(v,["2","2","4"][i] if id == "game2048" else (["3","+2","5"][i] if id == "math_duel" else str(i+1)),p,int(u*0.6),ink)
        text(v,"→",c+Vector2(0,u*0.85),int(u*0.5),accent)
    else:
        for i in range(4):
            v.draw_line(c+Vector2((i-1.5)*u,-u*1.1),c+Vector2((i-1.5)*u,u*1.1),accent,2)
            v.draw_line(c+Vector2(-u*1.5,(i-1.5)*u*0.7),c+Vector2(u*1.5,(i-1.5)*u*0.7),accent,2)
        for i in range(3):
            var p: Vector2 = c+Vector2((i-1)*u,0)
            if id == "tictactoe":
                v.draw_line(p-Vector2.ONE*u*0.22,p+Vector2.ONE*u*0.22,accent,5,true)
                v.draw_line(p+Vector2(-1,1)*u*0.22,p+Vector2(1,-1)*u*0.22,accent,5,true)
            else:
                v.draw_circle(p,u*0.32,ink if i%2 == 0 else white)
                if id in ["xiangqi_duel","darkchess","animal"]: text(v,"炮" if id != "animal" else "虎",p,int(u*0.4),Color("c76a51"))
static func animal(v, center: Vector2, radius: float, value: int) -> void:
    var species: int = value%8
    var variant: int = int(value/8)
    var fur: Color = [Color("dbac75"),Color("9c8172"),Color("edc17c"),Color("e4d9b8"),Color("a5bec4"),Color("b4bc92"),Color("ce9c90"),Color("a8aecc")][species]
    fur = fur.lightened((variant%4)*0.08)
    var dark := Color("4a3a34")
    var ear: float = radius*(0.38 if species in [0,2,6] else 0.30)
    for sign in [-1,1]:
        var p: Vector2 = center+Vector2(sign*radius*0.65,-radius*0.6)
        if species == 3: # 兔子
            var outline := PackedVector2Array()
            for step in range(20): outline.append(p+Vector2(cos(TAU*step/20)*ear*0.6,sin(TAU*step/20)*ear*1.8))
            v.draw_colored_polygon(outline,fur)
        elif species in [0,2]:
            v.draw_colored_polygon(PackedVector2Array([p+Vector2(-ear,ear),p+Vector2(0,-ear),p+Vector2(ear,ear)]),fur)
        else: v.draw_circle(p,ear,fur)
    v.draw_circle(center,radius*0.83,fur)
    if species == 7:
        for sign in [-1,1]: v.draw_circle(center+Vector2(sign*radius*0.32,-radius*0.06),radius*0.28,Color("fff9e9"))
    if species == 4:
        for sign in [-1,1]: v.draw_circle(center+Vector2(sign*radius*0.37,-radius*0.04),radius*0.22,dark)
    for sign in [-1,1]:
        var p: Vector2 = center+Vector2(sign*radius*0.30,-radius*0.08)
        v.draw_circle(p,radius*0.065,dark)
        v.draw_circle(p-Vector2(radius*0.02,radius*0.02),radius*0.018,Color.WHITE)
    v.draw_circle(center+Vector2(0,radius*0.23),radius*0.11,dark)
    if species == 5: v.draw_circle(center+Vector2(0,radius*0.43),radius*0.16,fur.darkened(0.18))
    if species == 2:
        for sign in [-1,1]:
            for y in range(2): v.draw_line(center+Vector2(sign*radius*0.47,radius*(0.1+y*0.18)),center+Vector2(sign*radius*0.72,radius*(0.05+y*0.18)),dark,radius*0.06,true)
    # 大棋盘用围巾色和点纹区分同一动物的不同花纹，4×4默认八种动物各一对。
    if variant > 0:
        var band: Color = [Color("5792ac"),Color("b46355"),Color("6f8b50"),Color("8d72a1")][variant%4]
        v._box(Rect2(center+Vector2(-radius*0.7,radius*0.5),Vector2(radius*1.4,radius*0.25)),band,3)
        for i in range(int(variant/4)+1): v.draw_circle(center+Vector2((i-1)*radius*0.22,-radius*0.46),radius*0.055,dark)
