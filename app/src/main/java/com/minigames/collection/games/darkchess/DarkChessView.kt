package com.minigames.collection.games.darkchess

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.minigames.collection.common.BoardArt
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import com.minigames.collection.games.xiangqi.XiangqiRules
import kotlin.math.min

/**
 * 暗棋（半盘翻棋）：4×8 共 32 格随机暗置整副象棋棋子，复用象棋棋子编码与视觉。
 * 主流规则（维基百科/公园暗棋/百度百科）：
 * - 玩家 1 先翻开一枚棋子，翻出的颜色即为其阵营，另一色归玩家 2；
 * - 每回合三选一：翻一枚暗子、把己方明子走相邻一格（空位）、吃相邻敌子；
 * - 吃子等级 帅将>仕士>相象>车>马>炮>兵卒，同阶互吃；
 * - 兵卒可吃帅将，帅将不能吃兵卒；
 * - 炮（包）不能直接相邻吃子，只能沿直线恰好隔一枚棋子（炮架，任意颜色）跳吃，
 *   且不受等级限制，可吃任何子（含帅将）；
 * - 吃光对方全部棋子，或对方无暗子可翻且无明子可走，即获胜。
 *
 * 【美术说明】棋盘改为 BoardArt 统一木质圆角底盘 + 半透明圆头网格线，
 * 明子改用奶油色圆棋子（与象棋/斗兽棋同源），状态胶囊改用 art.drawStatusBar，
 * 顶部玩家信息条整体避开悬浮控件安全区 dp(SAFE_TOP_DP)。
 */
class DarkChessView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 4
        private const val ROWS = 8
        /** 连续无吃子的手数上限（达到即判和棋），约合 60 个回合。 */
        private const val DRAW_PLIES = 120
        // 每方 16 子（复用象棋棋子编码，红 1..7 / 黑 11..17）
        private val RED_PIECES = intArrayOf(
            XiangqiRules.R_KING,
            XiangqiRules.R_ADVISOR, XiangqiRules.R_ADVISOR,
            XiangqiRules.R_ELEPHANT, XiangqiRules.R_ELEPHANT,
            XiangqiRules.R_ROOK, XiangqiRules.R_ROOK,
            XiangqiRules.R_HORSE, XiangqiRules.R_HORSE,
            XiangqiRules.R_CANNON, XiangqiRules.R_CANNON,
            XiangqiRules.R_PAWN, XiangqiRules.R_PAWN, XiangqiRules.R_PAWN, XiangqiRules.R_PAWN, XiangqiRules.R_PAWN
        )
    }

    private val board = IntArray(COLS * ROWS)
    private val revealed = BooleanArray(COLS * ROWS)
    private var selected = -1
    private var targets = ArrayList<Int>()
    private var turn = 0        // 0=玩家1（下），1=玩家2（上）
    private var color1 = 0      // 玩家1 的颜色：1=红，-1=黑，0=未定（首次翻子决定）
    private var over = false
    /** 自上一次吃子以来经过的手数（用于和棋判定）。 */
    private var quietPlies = 0
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    /** 双方信息条中心 y（上方玩家 / 下方玩家）。 */
    private var topStripY = 0f
    private var bottomStripY = 0f

    /** 美术工具箱：统一木板 / 奶油棋子 / 状态胶囊。 */
    private val art = BoardArt(this)
    /** 棋盘木板矩形（比格线各外扩一圈内边距），onLayout 中计算。 */
    private val slabRect = RectF()
    /** 棋盘尺寸变化时重建木纹渐变。 */
    private var lastSlabSide = 0f

    /** 玩家 p 的阵营色（1=红，-1=黑）。 */
    private fun playerColor(p: Int): Int = if (p == 0) color1 else -color1

    private fun playerColorName(p: Int): String = if (playerColor(p) == 1) "红方" else "黑方"

    /** 棋子 p 的阵营色（1=红，-1=黑）。 */
    private fun pieceSideCode(p: Int): Int =
        if (XiangqiRules.sideOf(p) == XiangqiRules.RED) 1 else -1

    /** 背景色（与全库统一背景 Palette.BG 一致）。 */
    private val bgPaint = Paint().apply { color = BoardArt.Palette.BG }
    /** 暗子（未翻开）：柔和的"木牌背面"底色与内圈环线。 */
    private val faceDownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC49A66.toInt() }
    private val faceDownInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF.toInt()
        style = Paint.Style.STROKE
    }
    /** 红方棋子文字（偏红）。 */
    private val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB02A2A.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    /** 黑方棋子文字（偏深）。 */
    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF20242E.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80E8EAF0.toInt() }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7A45.toInt()
        style = Paint.Style.STROKE
    }
    private val trapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = BoardArt.Palette.DIM
    }

    override fun onLayout(w: Int, h: Int) {
        // 顶部让出悬浮控件安全区：玩家 2 的状态胶囊不再贴屏幕顶边、也不被悬浮按钮压住
        val topPad = dp(SAFE_TOP_DP).toFloat()
        topStripY = topPad + dp(26).toFloat()
        bottomStripY = h - dp(74).toFloat()
        // 棋盘区域：状态胶囊之下、底部说明文字之上
        val topLimit = topStripY + dp(28).toFloat()
        val bottomLimit = bottomStripY - dp(86).toFloat()
        cell = min((w - dp(20).toFloat()) / COLS, (bottomLimit - topLimit) / ROWS)
        boardLeft = (w - cell * COLS) / 2f
        boardTop = topLimit + (bottomLimit - topLimit - cell * ROWS) / 2f
        // 木质底盘：格线外扩 6dp，圆角 20dp 由 drawWoodSlab 统一处理
        val pad = dp(6).toFloat()
        slabRect.set(
            boardLeft - pad, boardTop - pad,
            boardLeft + cell * COLS + pad, boardTop + cell * ROWS + pad)
        if (slabRect.width() != lastSlabSide) {
            lastSlabSide = slabRect.width()
            art.buildWoodSlab(slabRect)
        }
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val fc = (event.x - boardLeft) / cell
        val fr = (event.y - boardTop) / cell
        // 负值 toInt() 向零截断会把棋盘上方一格带（覆盖玩家 2 的信息条与顶部留白）解析成第 0 行，
        // 轻点信息条就会误翻顶行暗子，首翻时还会顺带决定双方阵营并消耗回合。
        if (fc < 0f || fr < 0f) return
        val c = fc.toInt()
        val r = fr.toInt()
        if (r !in 0 until ROWS || c !in 0 until COLS) return
        val idx = r * COLS + c
        if (!revealed[idx]) {
            onFlip(idx)
            return
        }
        val p = board[idx]
        if (selected < 0) {
            if (p != 0 && pieceSideCode(p) == playerColor(turn)) select(idx)
            return
        }
        if (idx == selected) {
            selected = -1
            targets.clear()
            invalidate()
            return
        }
        if (p != 0 && pieceSideCode(p) == playerColor(turn)) {
            select(idx)  // 改选己方另一子
            return
        }
        tryMove(idx)
    }

    private fun onFlip(idx: Int) {
        revealed[idx] = true
        selected = -1
        targets.clear()
        playSound(SoundManager.CLICK)
        // 首次翻子决定双方阵营
        if (color1 == 0) color1 = pieceSideCode(board[idx])
        turn = 1 - turn
        quietPlies++
        if (!over && quietPlies >= DRAW_PLIES) {
            over = true
            playSound(SoundManager.CLICK)
            gameOver(GameResult(0, false, "连续 " + (DRAW_PLIES / 2) + " 回合无吃子，判和棋"))
            return
        }
        if (!over) checkStalemate()
        invalidate()
    }

    private fun select(idx: Int) {
        selected = idx
        targets = legalTargets(idx).toCollection(ArrayList())
        playSound(SoundManager.CLICK)
        invalidate()
    }

    private fun tryMove(to: Int) {
        val from = selected
        if (from < 0 || to !in targets) {
            selected = -1
            targets.clear()
            invalidate()
            return
        }
        val captured = board[to]
        board[to] = board[from]
        board[from] = 0
        revealed[to] = true
        selected = -1
        targets.clear()
        playSound(if (captured != 0) SoundManager.EAT else SoundManager.MOVE)
        // 和棋兜底：连续多手无吃子且双方都不主动进攻时，局面可以无限走闲、永远不结束。
        // 达到阈值直接判和，避免玩家被迫用悬浮层的重开/退出逃离。
        quietPlies = if (captured != 0) 0 else quietPlies + 1
        if (captured != 0) checkEnd()
        if (!over && quietPlies >= DRAW_PLIES) {
            over = true
            playSound(SoundManager.CLICK)
            gameOver(GameResult(0, false, "连续 " + (DRAW_PLIES / 2) + " 回合无吃子，判和棋"))
            return
        }
        if (!over) {
            turn = 1 - turn
            checkStalemate()
        }
        invalidate()
    }

    /** 选中的己方明子的全部合法落点：相邻空格/可吃的敌子 + 炮的隔山跳吃落点。 */
    private fun legalTargets(from: Int): ArrayList<Int> {
        val out = ArrayList<Int>()
        val sp = board[from]
        if (sp == 0) return out
        val mySide = pieceSideCode(sp)
        val x = from % COLS
        val y = from / COLS
        for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            val nx = x + dx
            val ny = y + dy
            if (nx !in 0 until COLS || ny !in 0 until ROWS) continue
            val to = ny * COLS + nx
            val t = board[to]
            if (t == 0) out.add(to)
            else if (revealed[to] && pieceSideCode(t) != mySide && !isCannon(sp) && canCapture(sp, t)) out.add(to)
        }
        // 炮的隔山吃：沿直线恰好隔一枚棋子（炮架，任意颜色）跳吃，不受等级限制
        if (isCannon(sp)) {
            for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val mx = x + dx
                val my = y + dy
                if (mx !in 0 until COLS || my !in 0 until ROWS || board[my * COLS + mx] == 0) continue
                val tx = x + 2 * dx
                val ty = y + 2 * dy
                if (tx !in 0 until COLS || ty !in 0 until ROWS) continue
                val to = ty * COLS + tx
                val t = board[to]
                if (t != 0 && revealed[to] && pieceSideCode(t) != mySide) out.add(to)
            }
        }
        return out
    }

    private fun isCannon(v: Int): Boolean =
        v == XiangqiRules.R_CANNON || v == XiangqiRules.B_CANNON

    /** 等级：帅将 7、仕士 6、相象 5、车 4、马 3、炮 2、兵卒 1。 */
    private fun rank(v: Int): Int {
        val a = if (v in 1..7) v else v - 10
        return when (a) {
            XiangqiRules.R_KING -> 7
            XiangqiRules.R_ADVISOR -> 6
            XiangqiRules.R_ELEPHANT -> 5
            XiangqiRules.R_ROOK -> 4
            XiangqiRules.R_HORSE -> 3
            XiangqiRules.R_CANNON -> 2
            else -> 1
        }
    }

    /** 普通吃子判定（不含炮）：兵卒吃帅将、帅将不能吃兵卒、同阶互吃、大子吃小子。 */
    private fun canCapture(attacker: Int, target: Int): Boolean {
        val ar = rank(attacker)
        val tr = rank(target)
        if (ar == 1 && tr == 7) return true
        if (ar == 7 && tr == 1) return false
        return ar >= tr
    }

    private fun checkEnd() {
        var redCount = 0
        var blackCount = 0
        for (i in board.indices) {
            if (board[i] == 0) continue
            if (XiangqiRules.sideOf(board[i]) == XiangqiRules.RED) redCount++ else blackCount++
        }
        if (redCount == 0 || blackCount == 0) {
            over = true
            val survivor = if (redCount > 0) 1 else -1
            val winnerP = if (playerColor(0) == survivor) 0 else 1
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, winnerP == 0, "玩家 " + (winnerP + 1) + "（" + playerColorName(winnerP) + "）吃光对方获胜！"))
        }
    }

    /** 当前回合方若无暗子可翻、且无明子可走，则判负。 */
    private fun checkStalemate() {
        if (hasAnyMove()) return
        over = true
        val winnerP = 1 - turn
        playSound(SoundManager.WIN)
        gameOver(GameResult(0, winnerP == 0, "玩家 " + (winnerP + 1) + "（" + playerColorName(winnerP) + "）获胜！对方无暗子可翻且无明子可走"))
    }

    private fun hasAnyMove(): Boolean {
        for (i in board.indices) if (!revealed[i]) return true
        for (i in board.indices) {
            if (!revealed[i] || board[i] == 0 || pieceSideCode(board[i]) != playerColor(turn)) continue
            if (legalTargets(i).isNotEmpty()) return true
        }
        return false
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        drawPlayerStrips(canvas)
        drawBoard(canvas)
        drawHints(canvas)
    }

    private fun drawBoard(canvas: Canvas) {
        // 木质圆角底盘（统一美术：柔和渐变木板 + 浅色内描边，替代旧的深棕硬框）
        art.drawWoodSlab(canvas, slabRect, 20f)
        // 网格线：细、半透明、圆头，比原来的实线柔和
        for (r in 0..ROWS) {
            art.drawGridLine(canvas, boardLeft, boardTop + r * cell, boardLeft + cell * COLS, boardTop + r * cell, 1.0f)
        }
        for (c in 0..COLS) {
            art.drawGridLine(canvas, boardLeft + c * cell, boardTop, boardLeft + c * cell, boardTop + cell * ROWS, 1.0f)
        }
        // 单元格
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val idx = r * COLS + c
            val cx = boardLeft + c * cell + cell / 2f
            val cy = boardTop + r * cell + cell / 2f
            if (!revealed[idx]) {
                // 暗子用大圆角木牌 + 顶部内高光 + 浅色内环，保持"背面可翻"的暗示
                val rect = RectF(
                    boardLeft + c * cell + 2f, boardTop + r * cell + 2f,
                    boardLeft + (c + 1) * cell - 2f, boardTop + (r + 1) * cell - 2f)
                canvas.drawRoundRect(rect, dp(10f), dp(10f), faceDownPaint)
                faceDownInnerPaint.strokeWidth = dp(2f)
                canvas.drawCircle(cx, cy, cell * 0.26f, faceDownInnerPaint)
                trapPaint.textSize = cell * 0.3f
                trapPaint.color = 0x99FFFFFF.toInt()
                canvas.drawText("暗", cx, cy - (trapPaint.descent() + trapPaint.ascent()) / 2f, trapPaint)
            } else {
                val p = board[idx]
                // 选中态交给 drawCreamPiece 的金色柔光，这里不再单独画柔光/亮边
                if (p != 0) drawPieceAt(canvas, p, cx, cy, idx == selected)
            }
        }
        // 合法落点提示：空位圆点 / 吃子红圈
        capPaint.strokeWidth = dp(2f)
        for (t in targets) {
            val tx = boardLeft + (t % COLS) * cell + cell / 2f
            val ty = boardTop + (t / COLS) * cell + cell / 2f
            if (board[t] == 0) {
                canvas.drawCircle(tx, ty, cell * 0.12f, hintPaint)
            } else {
                canvas.drawCircle(tx, ty, cell * 0.4f, capPaint)
            }
        }
    }

    /** 奶油色圆棋子（美术工具箱统一画法）；玩家 2 阵营的棋子字面朝上，便于上方玩家阅读。 */
    private fun drawPieceAt(canvas: Canvas, p: Int, cx: Float, cy: Float, selected: Boolean = false) {
        val isRed = XiangqiRules.sideOf(p) == XiangqiRules.RED
        val r = cell * 0.42f
        // facingUp 语义保持原样：属于玩家 2 阵营的棋子字面朝上
        val facingUp = pieceSideCode(p) == playerColor(1)
        art.drawCreamPiece(
            canvas, cx, cy, r, XiangqiRules.pieceName(p),
            if (isRed) redTextPaint.color else blackTextPaint.color,
            facingUp = facingUp, selected = selected)
    }

    /** 上下玩家信息条：玩家 2（顶部）整体旋转 180° 正对玩家 2。 */
    private fun drawPlayerStrips(canvas: Canvas) {
        drawStrip(canvas, 1, topStripY, facingUp = true)
        drawStrip(canvas, 0, bottomStripY, facingUp = false)
    }

    private fun drawStrip(canvas: Canvas, p: Int, cy: Float, facingUp: Boolean) {
        canvas.save()
        if (facingUp) {
            canvas.translate(width / 2f, cy)
            canvas.rotate(180f)
            canvas.translate(-width / 2f, -cy)
        }
        val isTurn = turn == p && !over
        val name = "玩家 " + (p + 1) + " · " + (if (color1 == 0) "未定" else playerColorName(p))
        val bw = dp(190).toFloat()
        val bh = dp(40).toFloat()
        val rect = RectF(width / 2f - bw / 2f, cy - bh / 2f, width / 2f + bw / 2f, cy + bh / 2f)
        // 状态胶囊：深色面板 + 左侧阵营色点 + 居中文字，当前回合用亮绿高亮
        if (isTurn) {
            art.drawStatusBar(canvas, rect, name + " · 回合", BoardArt.Palette.GREEN, 0xFF2E9E5B.toInt(), sizeSp = 15f)
        } else {
            art.drawStatusBar(canvas, rect, name, if (p == 0) 0x66E8503A.toInt() else 0x66E8EAF0.toInt())
        }
        canvas.restore()
    }

    private fun drawHints(canvas: Canvas) {
        val line1 = if (color1 == 0)
            "玩家 1 先翻开一枚棋子，其颜色即玩家 1 的阵营"
        else
            "点击翻开暗子，或选己方明子走相邻格 / 吃子"
        // 底部说明放在下方状态胶囊之上，避开右下角得分胶囊区域
        art.drawCenterText(canvas, line1, width / 2f, bottomStripY - dp(46).toFloat(), 13f, BoardArt.Palette.DIM)
        art.drawCenterText(
            canvas, "吃子等级：帅将>仕士>相象>车>马>炮>兵卒 · 炮隔一子跳吃 · 兵卒吃帅将",
            width / 2f, bottomStripY - dp(28).toFloat(), 11f, BoardArt.Palette.DIM, bold = false)
    }

    override fun reset() {
        val all = ArrayList<Int>()
        for (v in RED_PIECES) {
            all.add(v)
            all.add(v + 10)  // 红 1..7 → 黑 11..17
        }
        all.shuffle()
        for (i in board.indices) {
            board[i] = all[i]
            revealed[i] = false
        }
        selected = -1
        targets.clear()
        turn = 0
        color1 = 0
        over = false
        quietPlies = 0
        start()
    }
}
