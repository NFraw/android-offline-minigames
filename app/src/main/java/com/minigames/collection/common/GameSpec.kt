package com.minigames.collection.common

enum class GameCategory(val label: String) { SOLO("单人"), DUEL("双人") }

data class GameSpec(
    val id: String,
    val name: String,
    val category: GameCategory,
    val emoji: String,
    val color: Int,
    val desc: String,
    val hint: String,
    val className: String
)

object GameRegistry {
    val games: List<GameSpec> = listOf(
        GameSpec("snake", "贪吃蛇", GameCategory.SOLO, "🐍", 0xFF4A6CF7.toInt(),
            "滑动控制，吃到食物不断变长",
            "滑动屏幕控制方向，吃到食物蛇身变长并逐渐加速；撞墙或咬到自己游戏结束，长度越长分数越高。",
            "com.minigames.collection.games.snake.SnakeGameView"),
        GameSpec("game2048", "2048", GameCategory.SOLO, "🔢", 0xFFF59A52.toInt(),
            "滑动合并数字，合成 2048 获胜",
            "向四个方向滑动让数字移动，相同数字碰撞会翻倍合并；合成 2048 即获胜，继续玩可以冲击更高分。",
            "com.minigames.collection.games.game2048.Game2048View"),
        GameSpec("tetris", "俄罗斯方块", GameCategory.SOLO, "🧱", 0xFFB06CF5.toInt(),
            "消除满行，越消越快",
            "使用下方 ◀ ▶ 按钮左右移动方块，↻ 旋转，▼ 快速下落；方块填满一行会自动消除，堆到顶部游戏结束。",
            "com.minigames.collection.games.tetris.TetrisGameView"),
        GameSpec("whack", "打地鼠", GameCategory.SOLO, "🔨", 0xFF3ED98A.toInt(),
            "30 秒限时，点击地鼠得分",
            "点击「开始游戏」后进入 30 秒倒计时，地鼠冒出时点击它即可得分；地鼠出现越来越快，打中越多分数越高。",
            "com.minigames.collection.games.whack.WhackView"),
        GameSpec("memory", "记忆翻牌", GameCategory.SOLO, "🃏", 0xFFFF7A45.toInt(),
            "翻牌配对，考验记忆力",
            "每次翻开两张牌，图案相同则配对成功；用尽量少的步数把 8 对牌全部配齐，步数越少得分越高。",
            "com.minigames.collection.games.memory.MemoryView"),
        GameSpec("mines", "扫雷", GameCategory.SOLO, "💣", 0xFF37C2F0.toInt(),
            "点击翻格，长按插旗",
            "点击翻开格子，数字表示周围 8 格内的地雷数；长按可插旗标记地雷；翻开所有安全格即可获胜。",
            "com.minigames.collection.games.mines.MinesView"),
        GameSpec("gomoku", "五子棋", GameCategory.DUEL, "⚫", 0xFFE0B577.toInt(),
            "同屏轮流落子，五连获胜",
            "黑方先手，两人轮流在交叉点落子；横、竖、斜任意方向先连成五子的一方获胜。",
            "com.minigames.collection.games.gomoku.GomokuView"),
        GameSpec("snakebattle", "贪吃蛇对战", GameCategory.DUEL, "🥊", 0xFF3ED98A.toInt(),
            "上下半屏各控一条蛇抢食物",
            "玩家 1 在上半屏滑动、玩家 2 在下半屏滑动，各自控制一条蛇抢食变长；撞墙、撞到对方蛇身或自己即败。",
            "com.minigames.collection.games.snakebattle.SnakeBattleView"),
        GameSpec("pong", "乒乓对决", GameCategory.DUEL, "🏓", 0xFF6C8BFF.toInt(),
            "上下分屏拖拍对战，先到 5 分胜",
            "玩家 1 在上半屏、玩家 2 在下半屏，手指按住球拍左右拖动；接住球并让对手漏球，先到 5 分获胜。",
            "com.minigames.collection.games.pong.PongView"),
        GameSpec("tictactoe", "井字棋", GameCategory.DUEL, "❌", 0xFFFF7A45.toInt(),
            "三子连线获胜，轮流先手",
            "两人轮流在 3×3 格子中落子，横、竖、斜任意方向三子连线者获胜；每局先手轮换。",
            "com.minigames.collection.games.tictactoe.TicTacToeView"),
        GameSpec("reversi", "黑白棋", GameCategory.DUEL, "⚪", 0xFF2E9E5B.toInt(),
            "夹子翻转，子多者胜",
            "落子必须夹住至少一枚对方棋子并全部翻转；无合法落子时自动跳过；终局棋子多者获胜。",
            "com.minigames.collection.games.reversi.ReversiView"),
        GameSpec("reaction", "反应力对决", GameCategory.DUEL, "⚡", 0xFFF5D547.toInt(),
            "变绿抢点，先到 10 分胜",
            "屏幕变绿后抢先点击自己半屏得分；变绿前点击（抢拍）判对方得分；先到 10 分获胜。",
            "com.minigames.collection.games.reaction.ReactionView"),
        GameSpec("weiqi", "围棋", GameCategory.DUEL, "🎱", 0xFF37C2F0.toInt(),
            "围地吃子，地盘大者胜",
            "黑白轮流落子，围住对方棋子即可提掉；点击「虚手」跳过回合，双方连续虚手后终局，按棋子数与围空之和判定胜负。",
            "com.minigames.collection.games.weiqi.WeigiView"),
        GameSpec("xiangqi_duel", "象棋（双人）", GameCategory.DUEL, "♜", 0xFFE8503A.toInt(),
            "红先黑后，将死对方获胜",
            "点击己方棋子选中，再点击合法位置走子；将死对方或无子可走即获胜，被将军时会有提示。",
            "com.minigames.collection.games.xiangqi.XiangqiDuelView"),
        GameSpec("xiangqi_ai", "象棋（人机）", GameCategory.SOLO, "🤖", 0xFF4A6CF7.toInt(),
            "执红对电脑，将死电脑获胜",
            "你执红先行，点击棋子选中再点目标走子；电脑执黑应战，将死对方或无子可走即获胜。",
            "com.minigames.collection.games.xiangqi.XiangqiAiView")
    )

    fun byId(id: String): GameSpec? = games.find { it.id == id }

    fun filtered(category: GameCategory?): List<GameSpec> =
        if (category == null) games else games.filter { it.category == category }
}
