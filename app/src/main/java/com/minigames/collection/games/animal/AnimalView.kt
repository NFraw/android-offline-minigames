package com.minigames.collection.games.animal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.BoardArt
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs
import kotlin.math.min

/**
 * 斗兽棋：象狮虎豹狼狗猫鼠互吃，鼠吃象，先进入对方兽穴或吃光对方获胜。
 *
 * 【美术说明】底色统一为 Palette.BG；格子保留原有语义（陆地/河面/陷阱一眼可辨），
 * 但配色收敛到柔和卡通风：陆地暖米色、河面柔和蓝、陷阱柔和橙棕，全部改大圆角 + 柔和描边，
 * 去掉原先生硬的深棕描边；棋子改为 BoardArt 的奶油色圆棋子（红方文字偏红、黑方偏深），
 * 底部提示改为 art.drawCenterText。棋盘整体让出顶部悬浮控件安全区 dp(SAFE_TOP_DP)。
 */
class AnimalView(context: Context) : GameView(context) {

    companion object {
        private const val N = 9
        private const val M = 7
        private const val RANK = 8
        // 棋子编码：1..8（象1 狮2 虎3 豹4 狼5 狗6 猫7 鼠8），黑方用负值，0 为空
        private const val ELEPHANT = 1
        private const val LION = 2
        private const val TIGER = 3
        private const val LEOPARD = 4
        private const val WOLF = 5
        private const val DOG = 6
        private const val CAT = 7
        private const val RAT = 8
        private val NAMES = arrayOf("", "象", "狮", "虎", "豹", "狼", "狗", "猫", "鼠")
        private val RANK_OF = intArrayOf(0, 8, 7, 6, 5, 4, 3, 2, 1)
    }

    private val board = IntArray(N * M)
    private var selected = -1
    private var turn = 0
    private var over = false
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val cellRect = RectF()

    /** 美术工具箱：统一奶油棋子 / 居中提示文字。 */
    private val art = BoardArt(this)
    /** 背景色（与全库统一背景 Palette.BG 一致）。 */
    private val bgPaint = Paint().apply { color = BoardArt.Palette.BG }
    /** 陆地格：暖米色（低饱和，不抢棋子）。 */
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEBD9BA.toInt() }
    /** 陆地上的一圈柔和描边（替代原来没有边界的平铺色块）。 */
    private val cellStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33BF9560.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    /** 河面：柔和蓝（比原来的高饱和青蓝低一档）。 */
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9BD7F0.toInt() }
    private val waterStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x5590C0DC.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val waterMark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x336FA8C8.toInt() }
    /** 陷阱：柔和橙棕。 */
    private val trapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEBB183.toInt() }
    private val trapStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66C98A55.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    /** 兽穴：略深的暖棕，与陷阱区分。 */
    private val denPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9A273.toInt() }
    private val denStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x77B9793F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    /** 红方棋子文字（偏红）。 */
    private val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB02A2A.toInt()
        textAlign = Paint.Align.CENTER
    }
    /** 黑方棋子文字（偏深）。 */
    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF20242E.toInt()
        textAlign = Paint.Align.CENTER
    }
    /** 格子上的辅助文字（"陷"/"穴"）。 */
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    override fun onLayout(w: Int, h: Int) {
        // 顶部让出悬浮控件安全区，底部给提示文字留位置。
        // 安全区之后再留 56dp：悬浮控件（重开/帮助）比安全区最低沿还要低一点，
        // 棋盘贴太紧时顶行的棋子会被「?」按钮压住。
        val topPad = dp(SAFE_TOP_DP).toFloat() + dp(56).toFloat()
        val bottomPad = dp(84).toFloat()
        // 棋盘是 9 列 × 7 行（宽大于高）。竖屏手机上受屏宽限制，算出的棋盘高度
        // 通常只有可用高度的一半左右。若在整段可用高度里居中，上下会各留一大片空白、
        // 棋盘像"悬在屏幕中间"；这里让它贴着头部下方开始，余量全部落到提示文字上方。
        cell = min(w / N.toFloat(), (h - topPad - bottomPad) / M.toFloat()) * 0.98f
        boardLeft = (w - cell * N) / 2f
        boardTop = topPad
        reset()
    }

    /**
     * 是否为河面。
     * 兽穴正前方的陷阱格（黑方 (4,1)、红方 (4,5)）必须是陆地：
     * 本作棋盘被压缩成 9×7（标准是 7×9），河面横带取 y∈{1,2}∪{4,5}，
     * 正好把这两个陷阱格也包了进去。结果它们被渲染成水格（不显示"陷"字）、
     * 且除鼠以外的棋子根本进不去，兽穴正前方的陷阱形同虚设。
     */
    private fun isWater(x: Int, y: Int): Boolean {
        if (trapOwner(x, y) >= 0) return false
        return (x == 3 || x == 4 || x == 5) && (y == 1 || y == 2 || y == 4 || y == 5)
    }

    /** 陷阱归属方：围绕兽穴的三个格。黑方兽穴在上 (4,0)，红方兽穴在下 (4,6)。 */
    private fun trapOwner(x: Int, y: Int): Int {
        if ((x == 3 && y == 0) || (x == 5 && y == 0) || (x == 4 && y == 1)) return 1
        if ((x == 3 && y == 6) || (x == 5 && y == 6) || (x == 4 && y == 5)) return 0
        return -1
    }

    private fun inBounds(x: Int, y: Int) = x in 0 until N && y in 0 until M

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val fc = (event.x - boardLeft) / cell
        val fr = (event.y - boardTop) / cell
        if (fc < 0f || fr < 0f) return  // 负值 toInt() 向零截断会把棋盘外一格解析成第 0 行/列
        val c = fc.toInt()
        val r = fr.toInt()
        if (!inBounds(c, r)) return
        val idx = r * N + c
        if (selected < 0) {
            val p = board[idx]
            if (p != 0 && side(p) == turn) {
                selected = idx
                playSound(SoundManager.CLICK)
            }
            return
        }
        if (idx == selected) {
            selected = -1
            return
        }
        val p = board[selected]
        if (board[idx] != 0 && side(board[idx]) == turn) {
            selected = idx
            playSound(SoundManager.CLICK)
            return
        }
        // 移动合法性
        val sx = selected % N
        val sy = selected / N
        val captured = board[idx]
        val canMove = canMoveTo(sx, sy, c, r, p)
        if (canMove && canCapture(sx, sy, p, c, r, captured)) {
            board[idx] = p
            board[selected] = 0
            selected = -1
            playSound(if (captured != 0) SoundManager.EAT else SoundManager.MOVE)
            // 走子方进入对方兽穴获胜：黑方兽穴在上 idx=4，红方兽穴在下 idx=(M-1)*N+4。
            // 己方兽穴已在 canMoveTo 中拦截，不可能走进自己巢穴。
            val blackDen = 4
            val redDen = (M - 1) * N + 4
            if ((idx == blackDen && turn == 0) || (idx == redDen && turn == 1)) {
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(0, true, (if (turn == 0) "红方" else "黑方") + "进入兽穴获胜！"))
            } else if (board.count { it != 0 && side(it) == 1 - turn } == 0) {
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(0, true, (if (turn == 0) "红方" else "黑方") + "吃光对方获胜！"))
            } else {
                turn = 1 - turn
            }
        } else {
            selected = -1
        }
        invalidate()
    }

    private fun side(p: Int): Int = if (p > 0) 0 else 1

    private fun canMoveTo(sx: Int, sy: Int, tx: Int, ty: Int, p: Int): Boolean {
        // 任何棋子都不能进入己方兽穴
        val didx = ty * N + tx
        if (side(p) == 0 && didx == (M - 1) * N + 4) return false
        if (side(p) == 1 && didx == 4) return false
        val dx = abs(tx - sx)
        val dy = abs(ty - sy)
        if (dx + dy == 1) {
            // 普通一步：仅鼠可进/出水面，其余棋子不能进水
            if (isWater(tx, ty) && rankOf(p) != 1) return false
            return true
        }
        // 狮虎跳河：纵向跨一段河（2 格水，跳距 3）或横向跨整条河（3 格水，跳距 4），
        // 被越过的格子必须全是水面且无子；落点天然是陆地（对岸）。
        if (rankOf(p) == 6 || rankOf(p) == 7) {
            if (dy == 0 && dx == 4) {
                val lo = min(sx, tx)
                return isWater(lo + 1, sy) && isWater(lo + 2, sy) && isWater(lo + 3, sy) &&
                    board[sy * N + lo + 1] == 0 && board[sy * N + lo + 2] == 0 && board[sy * N + lo + 3] == 0
            }
            if (dx == 0 && dy == 3) {
                val lo = min(sy, ty)
                return isWater(sx, lo + 1) && isWater(sx, lo + 2) &&
                    board[(lo + 1) * N + sx] == 0 && board[(lo + 2) * N + sx] == 0
            }
        }
        return false
    }

    private fun canCapture(sx: Int, sy: Int, p: Int, tx: Int, ty: Int, target: Int): Boolean {
        if (target == 0) return true
        // 陷阱：站在【攻击方所属陷阱】里的棋子被削弱，任何棋子都能吃（含鼠吃象）。
        // 站在自己陷阱里的棋子不享受保护，按正常等级/水域规则比较。
        // 旧实现写成 `if (tp >= 0) return tp == side(p)`，把"目标在己方陷阱"直接判为不可吃 ——
        // 等于给己方陷阱里的棋子加了无敌效果（例：红鼠吃不了站在黑方自己陷阱里的黑象），
        // 与标准规则相反，会直接改写胜负。
        val tp = trapOwner(tx, ty)
        if (tp >= 0 && tp == side(p)) return true
        val pWater = isWater(sx, sy)
        val tWater = isWater(tx, ty)
        // 水中鼠的庇护：岸上棋子不能吃水中的鼠
        if (tWater && !pWater) return false
        // 水中的鼠不能吃岸上棋子
        if (pWater && !tWater) return false
        // 水中对水中：只有鼠能进水，鼠在河中可互吃
        if (pWater) return true
        val pr = rankOf(p)
        val tr = rankOf(target)
        // 鼠吃象
        if (pr == 1 && tr == 8) return true
        // 象不能吃鼠
        if (pr == 8 && tr == 1) return false
        // 等级相同不能互吃；等级高者吃低者
        return pr > tr
    }

    private fun rankOf(p: Int): Int {
        val a = if (p > 0) p else -p
        return RANK_OF[a]
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until M) for (c in 0 until N) {
            val x = boardLeft + c * cell
            val y = boardTop + r * cell
            cellRect.set(x, y, x + cell, y + cell)
            val rr = dp(10f)
            val den = (r == 0 || r == M - 1) && c == 4
            if (isWater(c, r)) {
                // 河面：柔和蓝 + 浅描边 + 三道水纹暗示
                canvas.drawRoundRect(cellRect, rr, rr, waterPaint)
                canvas.drawRoundRect(cellRect, rr, rr, waterStroke)
                val cyy = y + cell / 2f
                for (k in 0 until 3) {
                    val wy = cyy - cell * 0.18f + k * cell * 0.18f
                    canvas.drawLine(x + cell * 0.24f, wy, x + cell * 0.76f, wy, waterMark)
                }
            } else if (trapOwner(c, r) >= 0) {
                // 陷阱：柔和橙棕，保留"陷"字语义
                canvas.drawRoundRect(cellRect, rr, rr, trapPaint)
                canvas.drawRoundRect(cellRect, rr, rr, trapStroke)
                markPaint.color = 0x99FFFFFF.toInt()
                markPaint.textSize = cell * 0.36f
                canvas.drawText("陷", x + cell / 2f, centerBaseline(y + cell / 2f, markPaint), markPaint)
            } else if (den) {
                // 兽穴：略深的暖棕，"穴"字保持醒目
                canvas.drawRoundRect(cellRect, rr, rr, denPaint)
                canvas.drawRoundRect(cellRect, rr, rr, denStroke)
                markPaint.color = 0xCC5A3A1E.toInt()
                markPaint.textSize = cell * 0.42f
                canvas.drawText("穴", x + cell / 2f, centerBaseline(y + cell / 2f, markPaint), markPaint)
            } else {
                // 陆地：暖米色 + 柔和描边，大圆角
                canvas.drawRoundRect(cellRect, rr, rr, cellPaint)
                canvas.drawRoundRect(cellRect, rr, rr, cellStroke)
            }
            val p = board[r * N + c]
            if (p != 0) {
                val a = if (p > 0) p else -p
                // 奶油色圆棋子（美术工具箱统一画法）；黑方棋子字面朝上（p < 0）
                art.drawCreamPiece(
                    canvas, x + cell / 2f, y + cell / 2f, cell * 0.4f, NAMES[a],
                    if (p > 0) redTextPaint.color else blackTextPaint.color,
                    facingUp = p < 0, selected = r * N + c == selected)
            }
        }
        // 底部提示：放在棋盘之下，避开右下角得分胶囊区域
        art.drawCenterText(
            canvas,
            (if (turn == 0) "红方" else "黑方") + "回合 · 鼠吃象 · 狮虎跳河 · 进对方陷阱被任意棋子吃",
            width / 2f, boardTop + cell * M + dp(26f), 13f, BoardArt.Palette.DIM)
    }

    override fun reset() {
        for (i in board.indices) board[i] = 0
        // 官方标准布局（兽穴 (4,0)/(4,6) 留空）：
        // 黑方（负数，上）：狮 象 鼠 猫 [穴] 狼 狗 豹 虎
        // 红方（正数，下）：虎 豹 狗 狼 [穴] 猫 鼠 象 狮
        val redLeft = intArrayOf(TIGER, LEOPARD, DOG, WOLF)
        val redRight = intArrayOf(CAT, RAT, ELEPHANT, LION)
        val blackLeft = intArrayOf(LION, ELEPHANT, RAT, CAT)
        val blackRight = intArrayOf(WOLF, DOG, LEOPARD, TIGER)
        for (i in 0 until 4) {
            board[0 * N + i] = -blackLeft[i]
            board[0 * N + 5 + i] = -blackRight[i]
            board[(M - 1) * N + i] = redLeft[i]
            board[(M - 1) * N + 5 + i] = redRight[i]
        }
        selected = -1
        turn = 0
        over = false
        start()
    }
}