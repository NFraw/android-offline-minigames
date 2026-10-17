package com.minigames.collection.games.reversi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.BoardArt
import com.minigames.collection.common.BoardArt.Palette
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/**
 * 黑白棋（奥赛罗）：8x8，双方夹住对方棋子即可翻转，无子可下时跳过，最终子多者胜。
 *
 * 【美术说明】棋盘底色由原来的绿绒（0xFF2E7D4F）改为 BoardArt 统一木质圆角底盘，
 * 与五子棋/围棋视觉同源；格子分隔线改为浅色圆头细线，棋子改用哑光玻璃质感；
 * 合法落点提示由半透明白点改为金色小圆环（不与白子混淆）。
 */
class ReversiView(context: Context) : GameView(context) {

    companion object {
        private const val N = 8
        private const val EMPTY = 0
        private const val BLACK = 1
        private const val WHITE = 2
    }

    private val board = IntArray(N * N)
    private var curPlayer = BLACK
    private var over = false
    private var passCount = 0

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val validMoves = ArrayList<Int>()

    /** 美术工具箱：统一木板 / 玻璃棋子 / 状态胶囊。 */
    private val art = BoardArt(this)
    /** 棋盘木板矩形（含内边距），onLayout 中计算。 */
    private val slabRect = RectF()
    /** 顶部状态胶囊矩形。 */
    private val statusRect = RectF()
    /** 棋盘尺寸变化时重建木纹渐变（onLayout 可能被多次调用，避免每帧 new Shader）。 */
    private var lastBoardSide = 0f

    /** 背景色（与全库统一背景一致）。 */
    private val bgPaint = Paint().apply { color = Palette.BG }
    /** 合法落点提示：金色小圆环（STROKE），比半透明白点更醒目且不与白子混淆。 */
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.GOLD
        style = Paint.Style.STROKE
    }

    private val dirs = listOf(
        Pair(-1, -1), Pair(0, -1), Pair(1, -1),
        Pair(-1, 0), Pair(1, 0),
        Pair(-1, 1), Pair(0, 1), Pair(1, 1)
    )

    override fun onLayout(w: Int, h: Int) {
        // 顶部让出悬浮控件安全区，底部给状态胶囊留位置
        val topPad = dp(SAFE_TOP_DP).toFloat()
        val bottomPad = dp(96).toFloat()
        val statusH = dp(38).toFloat()
        // 8×8 棋盘是正方形：竖屏手机上受屏宽限制，算出的边长通常远小于屏高。
        // 若在整段可用高度里居中，上下各留一大片空白、棋盘像"浮在屏幕中间"；
        // 这里让棋盘紧贴顶部状态胶囊之下，余量全部落到屏幕底部。
        val headerBottom = topPad + dp(4).toFloat() + statusH
        val side = min(w * 0.92f, h - headerBottom - bottomPad).coerceAtLeast(dp(120).toFloat())
        cell = side / N
        boardLeft = (w - side) / 2f
        boardTop = headerBottom + dp(20).toFloat()

        // 木板比棋盘各外扩 0.34 格，形成一圈柔和的木框
        slabRect.set(
            boardLeft - cell * 0.34f, boardTop - cell * 0.34f,
            boardLeft + side + cell * 0.34f, boardTop + side + cell * 0.34f)
        if (side != lastBoardSide) {
            lastBoardSide = side
            art.buildWoodSlab(slabRect)
        }

        // 顶部状态胶囊（放在安全区之下）
        val bw = min(w * 0.8f, dp(330).toFloat())
        val by = topPad + dp(4).toFloat()
        statusRect.set(w / 2f - bw / 2f, by, w / 2f + bw / 2f, by + statusH)

        hintPaint.strokeWidth = dp(2.2f)
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val fc = (event.x - boardLeft) / cell
        val fr = (event.y - boardTop) / cell
        if (fc < 0f || fr < 0f) return  // 负值 toInt() 向零截断会把棋盘左/上外侧一格解析成第 0 行/列，误落子
        val c = fc.toInt()
        val r = fr.toInt()
        if (r !in 0 until N || c !in 0 until N) return
        val idx = r * N + c
        if (board[idx] != EMPTY || !isValid(idx, curPlayer)) return
        board[idx] = curPlayer
        flip(idx, curPlayer)
        playSound(SoundManager.CLICK)
        nextTurn()
    }

    private fun isValid(idx: Int, player: Int): Boolean {
        if (board[idx] != EMPTY) return false
        val r = idx / N
        val c = idx % N
        for ((dr, dc) in dirs) {
            var nr = r + dr
            var nc = c + dc
            var seenOpp = false
            while (nr in 0 until N && nc in 0 until N) {
                val v = board[nr * N + nc]
                if (v == EMPTY) break
                if (v == player) {
                    if (seenOpp) return true
                    break
                }
                seenOpp = true
                nr += dr
                nc += dc
            }
        }
        return false
    }

    private fun flip(idx: Int, player: Int) {
        val r = idx / N
        val c = idx % N
        val opp = if (player == BLACK) WHITE else BLACK
        for ((dr, dc) in dirs) {
            var nr = r + dr
            var nc = c + dc
            val toFlip = ArrayList<Int>()
            while (nr in 0 until N && nc in 0 until N) {
                val v = board[nr * N + nc]
                if (v == EMPTY) {
                    toFlip.clear()
                    break
                }
                if (v == player) break
                toFlip.add(nr * N + nc)
                nr += dr
                nc += dc
            }
            if (toFlip.isNotEmpty() && nr in 0 until N && nc in 0 until N && board[nr * N + nc] == player) {
                for (f in toFlip) board[f] = player
            }
        }
    }

    private fun nextTurn() {
        val opp = if (curPlayer == BLACK) WHITE else BLACK
        if (hasMove(opp)) {
            curPlayer = opp
            passCount = 0
        } else if (hasMove(curPlayer)) {
            passCount++
            playSound(SoundManager.MOVE)
        } else {
            over = true
            val b = board.count { it == BLACK }
            val w = board.count { it == WHITE }
            playSound(if (b > w) SoundManager.WIN else SoundManager.LOSE)
            val msg = if (b > w) "黑方获胜！" else if (w > b) "白方获胜！" else "平局！"
            gameOver(GameResult(0, b > w, msg + "（黑 " + b + " : " + w + " 白）"))
        }
    }

    private fun hasMove(player: Int): Boolean {
        for (i in board.indices) {
            if (isValid(i, player)) return true
        }
        return false
    }

    private fun computeValidMoves() {
        validMoves.clear()
        for (i in board.indices) {
            if (isValid(i, curPlayer)) validMoves.add(i)
        }
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 木质圆角底盘（统一美术，替代原来的绿绒格子底色）
        art.drawWoodSlab(canvas, slabRect, 20f)
        // 格子分隔线：浅色圆头细线，形成柔和的网格（不再一格一块绿底 + 硬描边）
        for (i in 0..N) {
            art.drawGridLine(canvas, boardLeft, boardTop + i * cell, boardLeft + N * cell, boardTop + i * cell, 1.2f)
            art.drawGridLine(canvas, boardLeft + i * cell, boardTop, boardLeft + i * cell, boardTop + N * cell, 1.2f)
        }
        computeValidMoves()
        for (r in 0 until N) for (c in 0 until N) {
            val cx = boardLeft + (c + 0.5f) * cell
            val cy = boardTop + (r + 0.5f) * cell
            val v = board[r * N + c]
            if (v != EMPTY) {
                // 棋子：哑光玻璃质感（与围棋/五子棋同一套画法）
                art.drawGlassStone(canvas, cx, cy, cell * 0.38f, dark = v == BLACK)
            } else if (validMoves.contains(r * N + c)) {
                // 合法落点：金色小圆环
                canvas.drawCircle(cx, cy, cell * 0.17f, hintPaint)
            }
        }
        // 回合状态胶囊：黑 X : Y 白
        val isBlack = curPlayer == BLACK
        val b = board.count { it == BLACK }
        val w = board.count { it == WHITE }
        art.drawStatusBar(
            canvas, statusRect,
            (if (isBlack) "黑方回合" else "白方回合") + " · 黑 " + b + " : " + w + " 白",
            if (isBlack) Palette.STONE_DARK else Palette.STONE_LIGHT_RIM)
    }

    override fun reset() {
        for (i in board.indices) board[i] = EMPTY
        val m = N / 2
        board[(m - 1) * N + m - 1] = WHITE
        board[(m - 1) * N + m] = BLACK
        board[m * N + m - 1] = BLACK
        board[m * N + m] = WHITE
        curPlayer = BLACK
        over = false
        passCount = 0
        start()
    }
}
