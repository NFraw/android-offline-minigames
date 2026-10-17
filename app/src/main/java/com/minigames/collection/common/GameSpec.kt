package com.minigames.collection.common

/**
 * 游戏分类枚举。
 *
 * 用于主菜单的分类筛选芯片（全部/单人/双人/多人），以及 GameActivity 的入场流程判断：
 *   - SOLO：单人游戏，展示规则弹窗后直接开始
 *   - DUEL：双人游戏，展示双视角同意页面（DualConsentView）
 *   - MULTI：多人游戏（2~4人），展示规则弹窗
 *
 * label 属性是显示在 UI 上的中文标签。
 *
 * Kotlin 语法说明：
 *   enum class 相当于 Java 的 enum，但可以带属性和方法。
 *   每个枚举值的 label 参数通过构造器传入，存储为实例属性。
 */
enum class GameCategory(val label: String) { SOLO("单人"), DUEL("双人"), MULTI("多人"), EDUCATION("益智") }

/**
 * 游戏元数据描述。
 *
 * 功能：声明一款游戏的所有静态信息（名称、图标、分类、规则、实现类等）。
 * 不包含运行时状态——运行时状态在对应的 GameView 子类中管理。
 *
 * 被调用位置：
 *   - GameRegistry.games：所有游戏的静态注册列表
 *   - MainActivity：主菜单卡片展示（name/emoji/color/desc/category）
 *   - GameActivity：入场流程（aiClass→模式选择、category→双人同意/规则弹窗、landscape→横屏）
 *   - DualConsentView：展示游戏名称和规则（name/emoji/hint）
 *
 * Kotlin 语法说明：
 *   data class 相当于 Java 的 POJO/Bean，自动生成 equals/hashCode/toString/copy 方法。
 *   参数带默认值（aiClass=null, landscape=false）相当于 Java 的方法重载。
 *
 * @param id 游戏唯一标识（如 "snake"、"gomoku"），用于 Intent 传参和最高分存储的 key
 * @param name 中文游戏名称（如 "贪吃蛇"），显示在卡片和弹窗标题
 * @param category 游戏分类（SOLO/DUEL/MULTI），决定入场流程和主菜单筛选
 * @param emoji 游戏图标 emoji（如 "🐍"），显示在卡片和弹窗标题
 * @param color 主题色（ARGB），用于卡片标签、分类芯片高亮等
 * @param desc 一句话玩法简介（如 "滑动控制，吃到食物不断变长"），显示在卡片
 * @param hint 详细规则说明（含换行），显示在规则弹窗和双人同意页
 * @param className 游戏 View 类的全限定名（如 "com.minigames.collection.games.snake.SnakeGameView"），
 *        GameActivity 通过 Class.forName(className) 反射创建实例，因此必须与实际类路径一致
 * @param aiClass 电脑对战模式的 View 类名（如五子棋的 GomokuAiView），
 *        非空时 GameActivity 会弹出模式选择（双人/电脑）；null 表示无电脑模式
 * @param landscape 是否强制横屏游玩（如切水果），true 时 GameActivity 设置 SCREEN_ORIENTATION_SENSOR_LANDSCAPE
 */
data class GameSpec(
    val id: String,
    val name: String,
    val category: GameCategory,
    val emoji: String,
    val color: Int,
    val desc: String,
    val hint: String,
    val className: String = "",
    val aiClass: String? = null,
    val landscape: Boolean = false,
    /**
     * 悬浮控制按钮（退出/暂停/重开/帮助）是否改放到屏幕左右两侧的"竖直居中"位置。
     * 默认 false = 放四角。需要整屏盲拍的游戏必须置 true：那些游戏的四个角正是玩家最常点击的区域，
     * 按钮会吃掉触摸，轻则漏判一次抢答，重则直接把整局退出（反应力对决、四人反应、抢答算术、乒乓对决）。
     */
    val sideControls: Boolean = false,
    val educational: Boolean = false,
    val runtime: GameRuntime = GameRuntime.Canvas(className)
)

/**
 * 游戏注册表（全局单例）。
 *
 * 功能：维护所有游戏的静态列表，提供按 id 查询和按分类筛选的方法。
 * 这是整个 App 的"游戏目录"——新增/删除游戏只需在此列表中增减条目。
 *
 * 被调用位置：
 *   - MainActivity：展示游戏卡片网格、分类筛选
 *   - GameActivity.onCreate()：通过 byId() 根据 Intent 传来的 gameId 查找 GameSpec
 *
 * 扩展提示：
 *   新增游戏：1) 实现 GameView 子类；2) 在此列表中添加 GameSpec 条目（className 必须与实际类路径一致）；
 *   删除游戏：移除对应 GameSpec 条目即可（不会影响已有的最高分记录）。
 *   注意 className 是字符串而非类引用——这样避免了编译期依赖，但拼写错误只能在运行时发现。
 */
object GameRegistry {
    /**
     * 全部已注册游戏列表（顺序决定主菜单显示顺序）。
     * 每个 GameSpec 条目对应一款游戏的完整描述。
     */
    val games: List<GameSpec> = listOf(
        GameSpec("flight", "飞行棋", GameCategory.MULTI, "✈️", 0xFFDB565A.toInt(),
            "2—4人同屏或人机，四架飞机一起回家",
            "掷6起飞，同色跳4格，飞格飞12格；三连6撤回本轮，终点须恰好到达。",
            "com.minigames.collection.games.flight.FlightView",
            landscape = true, runtime = GameRuntime.Godot("res://games/flight/flight.tscn")),
        GameSpec("snake", "贪吃蛇", GameCategory.SOLO, "🐍", 0xFF4A6CF7.toInt(),
            "滑动控制，吃到食物不断变长",
            "滑动屏幕控制方向，吃到食物蛇身变长并逐渐加速；撞墙或咬到自己游戏结束，长度越长分数越高。",
            "com.minigames.collection.games.snake.SnakeGameView",
            runtime = GameRuntime.Godot("res://games/arcade/snake.tscn")),
        
        GameSpec("game2048", "2048", GameCategory.SOLO, "🔢", 0xFFF59A52.toInt(),
            "滑动合并数字，合成 2048 获胜",
            "向四个方向滑动让数字移动，相同数字碰撞会翻倍合并；合成 2048 即获胜，继续玩可以冲击更高分。",
            "com.minigames.collection.games.game2048.Game2048View",
            runtime = GameRuntime.Godot("res://games/puzzle/game2048.tscn")),
        
        GameSpec("tetris", "俄罗斯方块", GameCategory.SOLO, "🧱", 0xFFB06CF5.toInt(),
            "消除满行，越消越快",
            "使用下方 ◀ ▶ 按钮左右移动方块，↻ 旋转，▼ 快速下落；方块填满一行会自动消除，堆到顶部游戏结束。",
            "com.minigames.collection.games.tetris.TetrisGameView",
            runtime = GameRuntime.Godot("res://games/arcade/tetris.tscn")),
        
        GameSpec("whack", "打地鼠", GameCategory.SOLO, "🔨", 0xFF3ED98A.toInt(),
            "30秒连击，后期两只、三只同时冒出",
            "点击「开始游戏」后进入 30 秒倒计时，地鼠冒出时点击它即可得分；每10秒提速，后期最多两只、三只同时出现，打中越多分数越高；点空洞扣2分。",
            "com.minigames.collection.games.whack.WhackView",
            runtime = GameRuntime.Godot("res://games/arcade/whack.tscn")),
        
        GameSpec("memory", "记忆翻牌", GameCategory.SOLO, "🃏", 0xFFFF7A45.toInt(),
            "翻牌配对，考验记忆力",
            "每次翻开两张牌，图案相同则配对成功；用尽量少的步数把 8 对牌全部配齐，步数越少得分越高。",
            "com.minigames.collection.games.memory.MemoryView",
            runtime = GameRuntime.Godot("res://games/puzzle/memory.tscn")),
        
        GameSpec("mines", "扫雷", GameCategory.SOLO, "💣", 0xFF37C2F0.toInt(),
            "点击翻格，长按插旗",
            "点击翻开格子，数字表示周围 8 格内的地雷数；长按可插旗标记地雷；翻开所有安全格即可获胜。",
            "com.minigames.collection.games.mines.MinesView",
            runtime = GameRuntime.Godot("res://games/puzzle/mines.tscn")),
        
        GameSpec("gomoku", "五子棋", GameCategory.DUEL, "⚫", 0xFFE0B577.toInt(),
            "双人对战或电脑对战，五连获胜",
            "进入后先选择「双人对战」或「电脑对战」；黑方先手轮流落子，横、竖、斜任意方向先连成五子的一方获胜。",
            "com.minigames.collection.games.gomoku.GomokuView",
            aiClass = "com.minigames.collection.games.gomoku.GomokuAiView",
            runtime = GameRuntime.Godot("res://games/stone/gomoku.tscn")),
        
        GameSpec("snakebattle", "贪吃蛇对战", GameCategory.DUEL, "🥊", 0xFF3ED98A.toInt(),
            "上下半屏各控一条蛇抢食物",
            "玩家 1 在上半屏滑动、玩家 2 在下半屏滑动，各自控制一条蛇抢食变长；撞墙、撞到对方蛇身或自己即死；一条蛇死后另一条继续玩，两条蛇都死后按长度比分数，更长者获胜。",
            "com.minigames.collection.games.snakebattle.SnakeBattleView",
            runtime = GameRuntime.Godot("res://games/arcade/snakebattle.tscn")),
        
        GameSpec("pong", "乒乓对决", GameCategory.DUEL, "🏓", 0xFF6C8BFF.toInt(),
            "上下分屏拖拍对战，先到 5 分胜",
            "玩家 1 在下半屏、玩家 2 在上半屏，手指按住球拍左右拖动；接住球并让对手漏球，先到 5 分获胜。",
            "com.minigames.collection.games.pong.PongView",
            sideControls = true,
            runtime = GameRuntime.Godot("res://games/physics/pong.tscn")),
        GameSpec("tictactoe", "井字棋", GameCategory.DUEL, "❌", 0xFFFF7A45.toInt(),
            "三子连线获胜，轮流先手",
            "两人轮流在 3×3 格子中落子，横、竖、斜任意方向三子连线者获胜；每局先手轮换。",
            "com.minigames.collection.games.tictactoe.TicTacToeView",
            runtime = GameRuntime.Godot("res://games/stone/tictactoe.tscn")),
        
        GameSpec("reversi", "黑白棋", GameCategory.DUEL, "⚪", 0xFF2E9E5B.toInt(),
            "双人对战或电脑对战，子多者胜",
            "进入后先选择「双人对战」或「电脑对战」；落子必须夹住至少一枚对方棋子并全部翻转；无合法落子时自动跳过；终局棋子多者获胜。",
            "com.minigames.collection.games.reversi.ReversiView",
            aiClass = "com.minigames.collection.games.reversi.ReversiAiView",
            runtime = GameRuntime.Godot("res://games/stone/reversi.tscn")),
        
        GameSpec("reaction", "反应力对决", GameCategory.DUEL, "⚡", 0xFFF5D547.toInt(),
            "变绿抢点，先到 10 分胜",
            "玩家 1 在下半屏、玩家 2 在上半屏，屏幕变绿后抢先点击自己半屏得分；变绿前点击（抢拍）判对方得分；先到 10 分获胜。",
            "com.minigames.collection.games.reaction.ReactionView",
            sideControls = true,
            runtime = GameRuntime.Godot("res://games/arcade/reaction.tscn")),
        
        GameSpec("weiqi", "围棋", GameCategory.DUEL, "🎱", 0xFF37C2F0.toInt(),
            "围地吃子，地盘大者胜",
            "黑白轮流落子，围住对方棋子即可提掉；禁止自杀，也禁止打劫时立即回提（需先在别处找劫材，虚手可解消劫争）；点击「虚手」跳过回合，双方连续虚手后终局，按棋子数与围空之和判定胜负。",
            "com.minigames.collection.games.weiqi.WeigiView",
            runtime = GameRuntime.Godot("res://games/stone/weiqi.tscn")),
        
        GameSpec("xiangqi_duel", "象棋（双人）", GameCategory.DUEL, "帥", 0xFFE8503A.toInt(),
            "红先黑后，将死对方获胜",
            "点击己方棋子选中，再点击合法位置走子；将死对方或无子可走即获胜，被将军时会有提示（信息条按各自朝向显示，双方都正读）。",
            "com.minigames.collection.games.xiangqi.XiangqiDuelView",
            runtime = GameRuntime.Godot("res://games/chinese/xiangqi_duel.tscn")),
        GameSpec("piano", "别踩白块", GameCategory.SOLO, "🎹", 0xFF37C2F0.toInt(),
            "从下往上依次点击黑块",
            "从下往上依次点击黑色方块得分，每点一个方块速度加快（有上限）；点到白块或点错行不扣分也不判负（会有提示音），只有漏掉黑块才会失败。",
            "com.minigames.collection.games.piano.PianoView",
            runtime = GameRuntime.Godot("res://games/arcade/piano.tscn")),
        
        GameSpec("sliding", "数字华容道", GameCategory.SOLO, "🧩", 0xFFFF7A45.toInt(),
            "滑动数字方块归位",
            "点击与空格相邻的方块使其滑动，将数字按 1~15 顺序排好即获胜，步数越少得分越高。",
            "com.minigames.collection.games.sliding.SlidingView",
            runtime = GameRuntime.Godot("res://games/puzzle/sliding.tscn")),
        
        GameSpec("fruit", "切水果", GameCategory.SOLO, "🍉", 0xFFFF6B6B.toInt(),
            "木纹道场里的经典切水果",
            "固定横屏；划开西瓜开始。普通水果每个1分，同一刀快速切中至少3个有连切奖励，偶尔暴击10分。漏掉3个水果结束，切中炸弹立即结束；每满100分恢复一次漏果机会。",
            "com.minigames.collection.games.fruit.FruitNinjaView",
            landscape = true,
            runtime = GameRuntime.Godot("res://games/physics/fruit.tscn")),
        GameSpec("bounce", "弹一弹", GameCategory.SOLO, "🟢", 0xFF3ED98A.toInt(),
            "反弹小球击碎方块",
            "滑动挡板反弹小球击碎上方全部方块即获胜，漏球即失败。",
            "com.minigames.collection.games.bounce.BounceView",
            runtime = GameRuntime.Godot("res://games/physics/bounce.tscn")),
        GameSpec("breakout", "打砖块", GameCategory.SOLO, "🧱", 0xFF37C2F0.toInt(),
            "反弹小球消完所有砖",
            "滑动挡板控制小球反弹，击碎全部砖块获胜；砖块有 1~3 点耐久，漏球失败。",
            "com.minigames.collection.games.breakout.BreakoutView",
            runtime = GameRuntime.Godot("res://games/physics/breakout.tscn")),
        GameSpec("plane", "飞机大战", GameCategory.SOLO, "✈️", 0xFF4A6CF7.toInt(),
            "六关战役 · 接补给升级火力，挑战终极Boss",
            "拖动飞机自动开火，双发/三发与快射升级主炮，导弹为独立冷却副武器；补给需接住，橙色敌方导弹可击落且不追踪，击败六关Boss通关。5点耐久，受击短暂无敌且保留升级。",
            "com.minigames.collection.games.plane.PlaneView",
            runtime = GameRuntime.Godot("res://games/plane/plane.tscn")),
        GameSpec("simon", "西蒙记忆", GameCategory.SOLO, "🧠", 0xFFF5D547.toInt(),
            "记住颜色序列",
            "观察颜色闪灯顺序，按相同顺序点击四个色块；每关序列加长，记错即结束。",
            "com.minigames.collection.games.simon.SimonView",
            runtime = GameRuntime.Godot("res://games/puzzle/simon.tscn")),
        
        GameSpec("animal", "斗兽棋", GameCategory.DUEL, "🐯", 0xFFE0B577.toInt(),
            "传统7×9棋盘，双人同屏，休闲或30秒比赛",
            "红方先走，同级互吃，鼠吃象，狮虎跳河（敌鼠挡路、己鼠不挡）；陷阱削弱敌兽；进敌穴、吃光或困住敌方获胜。比赛模式含30秒计时与防长追规则。",
            "com.minigames.collection.games.animal.AnimalView",
            landscape = true, runtime = GameRuntime.Godot("res://games/animal/animal.tscn")),
        GameSpec("darkchess", "暗棋", GameCategory.DUEL, "🎴", 0xFFB06CF5.toInt(),
            "翻牌暗棋对战",
            "玩家 1 先翻开一枚棋子，翻出的颜色即其阵营；每回合可翻暗子或走/吃相邻一格；吃子等级帅将>仕士>相象>车>马>炮>兵卒（兵卒吃帅将）；炮必须隔一子跳吃且不受等级限制；吃光对方获胜。",
            "com.minigames.collection.games.darkchess.DarkChessView",
            runtime = GameRuntime.Godot("res://games/chinese/darkchess.tscn")),
        GameSpec("memory_duel", "记忆对决", GameCategory.DUEL, "🃏⚔️", 0xFFFF7A45.toInt(),
            "轮流配对比分数",
            "横屏游玩；双人轮流翻牌，配对成功得 1 分并继续翻；全部配对后分数高者获胜。",
            "com.minigames.collection.games.memory.MemoryDuelView",
            landscape = true,
            runtime = GameRuntime.Godot("res://games/puzzle/memory_duel.tscn")),
        
        GameSpec("curling", "冰壶对决", GameCategory.MULTI, "🥌", 0xFF37C2F0.toInt(),
            "2~4 人同屏轮流投壶，离圆心近者得分",
            "开局先选人数（2/3/4 人）；按住蓄力，按住的那一点是固定支点，拖动手指改变瞄准方向——瞄准线延长后与底部发射线的交点就是抛出点，所以调整角度时抛出位置也会跟着左右移动；松手投壶。冰壶可碰撞推挤；每局每人投 2 壶，只有停在营垒（大本营）圈内的壶才参与比较，离圆心最近一方比对方更近的壶每壶计 1 分，无壶进营垒则为 0:0 空局；局数随人数（2 人 3 局、3 人 4 局、4 人 5 局），下一局先投者为上一局赢家。",
            "com.minigames.collection.games.curling.CurlingView",
            runtime = GameRuntime.Godot("res://games/physics/curling.tscn")),
        GameSpec("math_duel", "抢答算术", GameCategory.DUEL, "🧮", 0xFFF5D547.toInt(),
            "先答对者得分",
            "屏幕上出现算术题，谁先算出答案就点击自己半屏抢答得分；先到 5 分获胜。",
            "com.minigames.collection.games.math.MathDuelView",
            sideControls = true,
            runtime = GameRuntime.Godot("res://games/arcade/math_duel.tscn")),
        
        GameSpec("reaction4", "反应力四人战", GameCategory.MULTI, "⚡👥", 0xFFF5D547.toInt(),
            "四分区抢点",
            "四个分区同时变绿，抢先点击自己分区得分，抢拍无效；先到 5 分获胜。",
            "com.minigames.collection.games.reaction.Reaction4View",
            sideControls = true,
            runtime = GameRuntime.Godot("res://games/arcade/reaction4.tscn")),
        
        GameSpec("pool", "台球", GameCategory.DUEL, "🎱", 0xFF2E9E5B.toInt(),
            "传统 8 球，实球 vs 花球",
            "球桌长边跟随设备方向；开球后首杆合法进球确定球组（实球 1-7 / 花球 9-15），之后必须先碰己方球组；清光己方球组后打 8 号前必须先选袋口（叫袋）；犯规（母球落袋/没碰到球/先碰对方球组）对方球在手中（开球犯规限定发球区）；合法打进 8 号获胜，犯规或未叫袋打进 8 号判负。",
            "com.minigames.collection.games.pool.PoolView",
            runtime = GameRuntime.Godot("res://games/physics/pool.tscn")),
        GameSpec("poetry_adventure", "诗词小旅人", GameCategory.SOLO, "📜", 0xFF4A8790.toInt(),
            "看上句选四句中的下句",
            "每局10题，四选一。慢慢想，不限时；答对收集星星。看完讲解点击下一题；听音题先播放再作答。",
            educational = true,
            runtime = GameRuntime.Godot("res://games/education/poetry_adventure.tscn")),
        GameSpec("poetry_duel", "诗词接句对决", GameCategory.DUEL, "📜", 0xFF4A8790.toInt(),
            "上下对坐，四选一接诗句",
            "每局10题，四选一。先答对立即得1分；先答错，本题由另一方继续。双方答错或30秒超时后揭晓。看完讲解点击下一题；听音题先播放再作答。",
            educational = true,
            runtime = GameRuntime.Godot("res://games/education/poetry_duel.tscn")),
        GameSpec("pinyin_adventure", "拼音森林", GameCategory.SOLO, "🌳", 0xFF4A8790.toInt(),
            "看汉字和声母，补上韵母",
            "每局10题，四选一。慢慢想，不限时；答对收集星星。看完讲解点击下一题；听音题先播放再作答。",
            educational = true,
            runtime = GameRuntime.Godot("res://games/education/pinyin_adventure.tscn")),
        GameSpec("pinyin_duel", "拼音拼拼对决", GameCategory.DUEL, "🔤", 0xFF4A8790.toInt(),
            "比一比谁更熟悉韵母",
            "每局10题，四选一。先答对立即得1分；先答错，本题由另一方继续。双方答错或30秒超时后揭晓。看完讲解点击下一题；听音题先播放再作答。",
            educational = true,
            runtime = GameRuntime.Godot("res://games/education/pinyin_duel.tscn")),
        GameSpec("listening_adventure", "音符探险", GameCategory.SOLO, "🎵", 0xFF4A8790.toInt(),
            "听真实音符，选择唱名",
            "每局10题，四选一。慢慢想，不限时；答对收集星星。看完讲解点击下一题；听音题先播放再作答。",
            educational = true,
            runtime = GameRuntime.Godot("res://games/education/listening_adventure.tscn")),
        GameSpec("listening_duel", "听音小耳朵对决", GameCategory.DUEL, "🎶", 0xFF4A8790.toInt(),
            "同听一个音，各自选唱名",
            "每局10题，四选一。先答对立即得1分；先答错，本题由另一方继续。双方答错或30秒超时后揭晓。每人每题可播放两次基准do，随后自动播放一次题目；播放中双方一起听，不能插播。看完讲解点击下一题。",
            educational = true,
            runtime = GameRuntime.Godot("res://games/education/listening_duel.tscn")),
        GameSpec("english_adventure", "单词小岛", GameCategory.SOLO, "🏝️", 0xFF4A8790.toInt(),
            "看图片，找到英文单词",
            "每局10题，四选一。慢慢想，不限时；答对收集星星。看完讲解点击下一题；听音题先播放再作答。",
            educational = true,
            runtime = GameRuntime.Godot("res://games/education/english_adventure.tscn")),
        GameSpec("english_duel", "看图英语对决", GameCategory.DUEL, "🍎", 0xFF4A8790.toInt(),
            "看相同图片，比拼基础词汇",
            "每局10题，四选一。先答对立即得1分；先答错，本题由另一方继续。双方答错或30秒超时后揭晓。看完讲解点击下一题；听音题先播放再作答。",
            educational = true,
            runtime = GameRuntime.Godot("res://games/education/english_duel.tscn"))
    )

    /**
     * 按 id 查找游戏。
     * @param id 游戏唯一标识（对应 GameSpec.id）
     * @return 匹配的 GameSpec，未找到返回 null
     * 被 GameActivity.onCreate() 调用：intent.getStringExtra("game") 传入 gameId
     */
    fun byId(id: String): GameSpec? = games.find { it.id == id }

    /**
     * 按分类筛选游戏。
     * @param category 目标分类，null 表示返回全部
     * @return 筛选后的游戏列表
     * 被 MainActivity.games() 调用：主菜单分类芯片点击时更新 currentCategory 并刷新列表
     */
    fun filtered(category: GameCategory?): List<GameSpec> =
        when (category) { null -> games; GameCategory.EDUCATION -> games.filter { it.educational }; else -> games.filter { it.category == category } }
}


