package com.minigames.collection.games.gomoku

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
 * 五子棋：双人同屏轮流落子，先连成五子者胜。
 *
 * 【美术说明】棋盘改用 BoardArt 统一木质圆角底盘（与围棋/象棋/黑白棋同源），
 * 棋子改用哑光玻璃棋子（带左上高光与软投影），最后一手标记改为柔和金环，
 * 顶部状态改为胶囊条，视觉不再是一块生硬的纯色木板 + 扁平圆点。
 */
class GomokuView(context: Context) : GameView(context) {

    companion object {
        private const val N = 15
        private const val EMPTY = 0
        private const val BLACK = 1
        private const val WHITE = 2
    }

    private val board = IntArray(N * N)
    private var curPlayer = BLACK
    private var lastX = -1
    private var lastY = -1
    private var over = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var stoneR = 0f

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0B577.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A5A2B.toInt()
        strokeWidth = 2f
    }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2333.toInt() }
    private val blackHiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A5568.toInt() }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB9C0CE.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val turnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
    }

    /** 美术工具箱：统一木板 / 玻璃棋子 / 状态胶囊。 */
    private val art = BoardArt(this)
    /** 棋盘木板矩形（含内边距），onLayout 中计算。 */
    private val slabRect = RectF()
    /** 顶部状态胶囊矩形。 */
    private val statusRect = RectF()
    /** 棋盘尺寸变化时重建木纹渐变。 */
    private var lastBoardSide = 0f

    override fun onLayout(w: Int, h: Int) {
        // 顶部让出悬浮控件安全区，底部给状态胶囊留位置
        val topPad = dp(SAFE_TOP_DP).toFloat()
        val statusH = dp(38).toFloat()
        val headerBottom = topPad + dp(4).toFloat() + statusH
        // 棋盘是正方形且受屏宽限制（竖屏手机上 w*0.94 通常远小于屏高）。
        // 让棋盘紧贴顶部状态条之下开始、余量留在底部——若改成在整屏竖直居中，
        // 棋盘会被推到屏幕下半部，上方出现一大片空白，观感像"浮在半空"。
        val side = min(w * 0.94f, h - headerBottom - dp(24).toFloat()).coerceAtLeast(dp(120).toFloat())
        cell = side / (N - 1)
        stoneR = cell * 0.44f
        boardLeft = (w - cell * (N - 1)) / 2f
        boardTop = headerBottom + dp(20).toFloat() + cell * 0.5f

        // 木板比网格线各外扩半格，保证边缘交叉点不贴边
        slabRect.set(
            boardLeft - cell * 0.62f, boardTop - cell * 0.62f,
            boardLeft + cell * (N - 1) + cell * 0.62f, boardTop + cell * (N - 1) + cell * 0.62f)
        if (side != lastBoardSide) {
            lastBoardSide = side
            art.buildWoodSlab(slabRect)
        }

        // 顶部状态胶囊（放在安全区之下、棋盘之上）
        val bw = min(w * 0.72f, dp(300).toFloat())
        val by = topPad + dp(4).toFloat()
        statusRect.set(w / 2f - bw / 2f, by, w / 2f + bw / 2f, by + statusH)

        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val c = Math.round((event.x - boardLeft) / cell)
        val r = Math.round((event.y - boardTop) / cell)
        if (r !in 0 until N || c !in 0 until N) return
        val d = Math.hypot((event.x - (boardLeft + c * cell)).toDouble(), (event.y - (boardTop + r * cell)).toDouble())
        if (d > cell * 0.45) return
        if (board[r * N + c] != EMPTY) return
        board[r * N + c] = curPlayer
        lastX = c
        lastY = r
        playSound(SoundManager.CLICK)
        if (checkWin(c, r, curPlayer)) {
            over = true
            val winnerName = if (curPlayer == BLACK) "黑方" else "白方"
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, winnerName + "五连获胜！"))
        } else if (board.none { it == EMPTY }) {
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(0, false, "棋盘已满，平局！"))
        } else {
            curPlayer = if (curPlayer == BLACK) WHITE else BLACK
        }
    }

    private fun checkWin(x: Int, y: Int, player: Int): Boolean {
        val dirs = listOf(Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1))
        for ((dx, dy) in dirs) {
            var count = 1
            var i = 1
            while (true) {
                val nx = x + dx * i
                val ny = y + dy * i
                if (nx in 0 until N && ny in 0 until N && board[ny * N + nx] == player) count++ else break
                i++
            }
            i = 1
            while (true) {
                val nx = x - dx * i
                val ny = y - dy * i
                if (nx in 0 until N && ny in 0 until N && board[ny * N + nx] == player) count++ else break
                i++
            }
            if (count >= 5) return true
        }
        return false
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, boardPaintBg)
        // 木质圆角底盘（统一美术）
        art.drawWoodSlab(canvas, slabRect, 20f)
        // 网格线：细、半透明、圆头
        for (i in 0 until N) {
            art.drawGridLine(canvas, boardLeft, boardTop + i * cell, boardLeft + (N - 1) * cell, boardTop + i * cell, 1.2f)
            art.drawGridLine(canvas, boardLeft + i * cell, boardTop, boardLeft + i * cell, boardTop + (N - 1) * cell, 1.2f)
        }
        // 星位
        val stars = listOf(Pair(3, 3), Pair(11, 3), Pair(3, 11), Pair(11, 11), Pair(7, 7))
        for ((sx, sy) in stars) {
            art.drawStar(canvas, boardLeft + sx * cell, boardTop + sy * cell, cell * 0.09f)
        }
        // 棋子：哑光玻璃质感（与围棋/黑白棋同一套画法）
        for (r in 0 until N) for (c in 0 until N) {
            val v = board[r * N + c]
            if (v == EMPTY) continue
            art.drawGlassStone(canvas, boardLeft + c * cell, boardTop + r * cell, stoneR, dark = v == BLACK)
        }
        // 最后一手：金色圆环标记（比实心红点更精致，且不遮住棋子本身）
        if (lastX >= 0) {
            val mx = boardLeft + lastX * cell
            val my = boardTop + lastY * cell
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Palette.GOLD
                style = Paint.Style.STROKE
                strokeWidth = dp(2.5f)
            }
            canvas.drawCircle(mx, my, stoneR * 0.46f, ring)
        }
        // 回合状态胶囊
        val isBlack = curPlayer == BLACK
        val name = if (isBlack) "黑方落子" else "白方落子"
        art.drawStatusBar(canvas, statusRect, name,
            if (isBlack) Palette.STONE_DARK else Palette.STONE_LIGHT_RIM)
    }

    /** 背景色（与全库统一背景一致）。 */
    private val boardPaintBg = Paint().apply { color = Palette.BG }

    override fun reset() {
        for (i in board.indices) board[i] = EMPTY
        curPlayer = BLACK
        lastX = -1
        lastY = -1
        over = false
        start()
    }
}
