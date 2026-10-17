package com.minigames.collection.games.tetris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 俄罗斯方块：屏幕下方四个按钮 ◀ ▶ ↻ ▼ 控制。 */
class TetrisGameView(context: Context) : GameView(context) {

    companion object {
        private val COLS = 10
        private val ROWS = 20
        private val SHAPES = listOf(
            listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(3, 0)), // I
            listOf(Pair(1, 0), Pair(1, 1), Pair(2, 0), Pair(2, 1)), // O
            listOf(Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(2, 1)), // T
            listOf(Pair(0, 0), Pair(1, 0), Pair(1, 1), Pair(2, 1)), // S
            listOf(Pair(1, 0), Pair(2, 0), Pair(0, 1), Pair(1, 1)), // Z
            listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(2, 1)), // J
            listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(0, 1))  // L
        )
        private val COLORS = intArrayOf(
            0xFF37C2F0.toInt(), 0xFFF5D547.toInt(), 0xFFB06CF5.toInt(),
            0xFF3ED98A.toInt(), 0xFFF0604A.toInt(), 0xFF4A6CF7.toInt(), 0xFFF5A54A.toInt()
        )
    }

    private val board = IntArray(COLS * ROWS)
    private var curShape = 0
    private var curCells = mutableListOf<Pair<Int, Int>>()
    private var curX = 3
    private var curY = 0
    private var nextShape = 0
    private var score = 0
    private var lines = 0
    private var fallAcc = 0f
    private var softDrop = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val btnRects = Array(4) { RectF() }
    private val btnLabels = arrayOf("◀", "▶", "↻", "▼")

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8EAF0.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A93A6.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 41f
    }

    override fun onLayout(w: Int, h: Int) {
        // 布局必须保证"棋盘 + 间隔 + 按钮行"整体放得下。
        // 旧实现用 h/(ROWS+3.5) 且 boardTop 只按棋盘高度居中，算下来按钮底边会落到
        // h 的 1.03 倍（横屏更夸张），◀▶↻▼ 有一截被切在屏幕外，点不到。
        val topPad = dp(SAFE_TOP_DP).toFloat()
        val bottomPad = dp(16).toFloat()
        val availH = (h - topPad - bottomPad).coerceAtLeast(dp(160).toFloat())
        val contentH0 = ROWS + 2.5f   // 棋盘 20 行 + 0.7 格间隔 + 1.8 格按钮
        cell = min(w / (COLS + 6.5f), availH / contentH0)
        val totalW = (COLS + 6.5f) * cell
        boardLeft = (w - totalW) / 2f
        boardTop = topPad + (availH - contentH0 * cell) / 2f
        val btnSize = cell * 1.8f
        val gap2 = cell * 0.4f
        val rowY = boardTop + ROWS * cell + cell * 0.7f
        val total = btnSize * 4 + gap2 * 3
        val startX = (w - total) / 2f
        for (i in 0..3) {
            btnRects[i].set(
                startX + i * (btnSize + gap2), rowY,
                startX + i * (btnSize + gap2) + btnSize, rowY + btnSize)
        }
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN && !paused) {
            val x = event.x
            val y = event.y
            when {
                btnRects[0].contains(x, y) -> moveX(-1)
                btnRects[1].contains(x, y) -> moveX(1)
                btnRects[2].contains(x, y) -> rotate()
                btnRects[3].contains(x, y) -> softDrop = true
            }
        } else if (event.action == MotionEvent.ACTION_UP) {
            softDrop = false
        }
    }

    private fun fallInterval(): Float {
        // 加速梯度加大：每消除一行下落间隔缩短，封底 0.07s
        return (0.9f - lines * 0.015f).coerceAtLeast(0.07f)
    }

    private fun fits(cells: List<Pair<Int, Int>>, ox: Int, oy: Int): Boolean {
        for ((cx, cy) in cells) {
            val x = cx + ox
            val y = cy + oy
            if (x < 0 || x >= COLS || y >= ROWS) return false
            if (y >= 0 && board[y * COLS + x] != 0) return false
        }
        return true
    }

    private fun spawnPiece() {
        curShape = nextShape
        nextShape = (0 until SHAPES.size).random()
        curCells = SHAPES[curShape].toMutableList()
        curX = 3
        curY = 0
        if (!fits(curCells, curX, curY)) {
            playSound(SoundManager.LOSE)
            gameOver(GameResult(score, false, "方块堆到顶了，共消除 " + lines + " 行"))
        }
    }

    /**
     * 顺时针旋转 90°。
     *
     * 修复要点：旋转中心必须是形状**自身**包围盒的中心，而不是 4×4 网格的中心。
     * 旧实现用 (3 - y, x)（等价于绕 (1.5, 1.5) 旋转），而各形状的重心并不在盒心，
     * 于是每按一次 ↻ 方块都会自己平移：O 形右下各走 1 格、L 形右移 2 格、T 形右移 1.25 格；
     * 贴墙或贴底时 fits() 失败还会静默无反应（连音效都没有），玩家会以为按钮坏了。
     * 这里改为绕自身包围盒旋转 + 简单的 wall-kick 尝试。
     */
    private fun rotate() {
        val minX = curCells.minOf { it.first }
        val minY = curCells.minOf { it.second }
        val maxY = curCells.maxOf { it.second }
        // 归一化到包围盒左上角后旋转：(x, y) → (maxY - y, x - minX)
        val rotated = curCells.map { Pair(maxY - it.second, it.first - minX) }
        // 旋转后的包围盒左上角落在原点，把锚点补回原来的绝对位置
        val nx = curX + minX
        val ny = curY + minY
        for (kick in intArrayOf(0, 1, -1, 2, -2)) {
            if (fits(rotated, nx + kick, ny)) {
                curCells = rotated.toMutableList()
                curX = nx + kick
                curY = ny
                playSound(SoundManager.MOVE)
                return
            }
        }
        playSound(SoundManager.CLICK)  // 确实转不动时也要有反馈，不能静默
    }

    private fun moveX(dx: Int) {
        if (fits(curCells, curX + dx, curY)) {
            curX += dx
            playSound(SoundManager.MOVE)
        }
    }

    private fun tryDrop(): Boolean {
        if (fits(curCells, curX, curY + 1)) {
            curY += 1
            return true
        }
        lockPiece()
        return false
    }

    private fun lockPiece() {
        for ((cx, cy) in curCells) {
            val y = cy + curY
            if (y >= 0 && y < ROWS) board[y * COLS + curX + cx] = curShape + 1
        }
        clearLines()
        spawnPiece()
    }

    private fun clearLines() {
        var cleared = 0
        var writeRow = ROWS - 1
        for (readRow in ROWS - 1 downTo 0) {
            var full = true
            for (c in 0 until COLS) if (board[readRow * COLS + c] == 0) full = false
            if (!full) {
                for (c in 0 until COLS) board[writeRow * COLS + c] = board[readRow * COLS + c]
                writeRow--
            } else cleared++
        }
        for (r in 0..writeRow) for (c in 0 until COLS) board[r * COLS + c] = 0
        if (cleared > 0) {
            lines += cleared
            val pts = intArrayOf(0, 100, 300, 500, 800)
            score += pts[cleared.coerceAtMost(4)]
            setScore(score)
            playSound(SoundManager.EAT)
        }
    }

    override fun update(dt: Float) {
        fallAcc += dt * (if (softDrop) 18f else 1f / fallInterval())
        while (fallAcc >= 1f) {
            fallAcc -= 1f
            if (!tryDrop()) return
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val v = board[r * COLS + c]
            val x = boardLeft + c * cell
            val y = boardTop + r * cell
            val rect = RectF(x + 1f, y + 1f, x + cell - 1f, y + cell - 1f)
            if (v == 0) {
                canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, emptyPaint)
            } else {
                tilePaint.color = COLORS[v - 1]
                canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, tilePaint)
            }
        }
        tilePaint.color = COLORS[curShape]
        for ((cx, cy) in curCells) {
            val x = boardLeft + (curX + cx) * cell
            val y = boardTop + (curY + cy) * cell
            if (curY + cy >= 0) {
                canvas.drawRoundRect(RectF(x + 1f, y + 1f, x + cell - 1f, y + cell - 1f), cell * 0.12f, cell * 0.12f, tilePaint)
            }
        }
        // 预览
        val px = boardLeft + (COLS + 0.8f) * cell
        val py = boardTop + cell * 0.6f
        canvas.drawText("下一个", px + cell * 0.75f, py - cell * 0.15f, labelPaint)
        val nx = boardLeft + (COLS + 1.55f) * cell
        val ny = boardTop + cell * 1.6f
        val nextCells = SHAPES[nextShape]
        val minX = nextCells.minOf { it.first }
        val minY = nextCells.minOf { it.second }
        tilePaint.color = COLORS[nextShape]
        for ((cx, cy) in nextCells) {
            val x = nx + (cx - minX) * cell * 0.55f
            val y = ny + (cy - minY) * cell * 0.55f
            canvas.drawRoundRect(RectF(x + 1f, y + 1f, x + cell * 0.55f - 1f, y + cell * 0.55f - 1f), cell * 0.1f, cell * 0.1f, tilePaint)
        }
        for (i in 0..3) {
            canvas.drawRoundRect(btnRects[i], dp(14).toFloat(), dp(14).toFloat(), btnPaint)
            btnTextPaint.textSize = btnRects[i].height() * 0.55f
            val y = btnRects[i].centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f
            canvas.drawText(btnLabels[i], btnRects[i].centerX(), y, btnTextPaint)
        }
        canvas.drawText("消行 " + lines, px + cell * 0.75f, boardTop + cell * 5.4f, labelPaint)
    }

    override fun reset() {
        for (i in board.indices) board[i] = 0
        score = 0
        lines = 0
        fallAcc = 0f
        softDrop = false
        nextShape = (0 until SHAPES.size).random()
        setScore(0)
        spawnPiece()
        start()
    }
}
