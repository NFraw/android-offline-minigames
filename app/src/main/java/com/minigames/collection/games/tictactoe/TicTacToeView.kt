package com.minigames.collection.games.tictactoe

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 井字棋：双人轮流落子，三子连线获胜，每局交换先手。 */
class TicTacToeView(context: Context) : GameView(context) {

    companion object {
        private const val N = 3
        private const val EMPTY = 0
        private const val X = 1
        private const val O = 2
    }

    private val board = IntArray(N * N)
    private var curPlayer = X
    private var over = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A4668.toInt()
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7A45.toInt()
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val oPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6C8BFF.toInt()
        strokeWidth = 10f
        style = Paint.Style.STROKE
    }
    private val turnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w * 0.9f, h * 0.62f)
        cell = side / N
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f + cell * 0.6f
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
        if (board[idx] != EMPTY) return
        board[idx] = curPlayer
        playSound(SoundManager.CLICK)
        if (checkWin(curPlayer)) {
            over = true
            val name = if (curPlayer == X) "X 方" else "O 方"
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, name + "三子连线获胜！"))
        } else if (board.none { it == EMPTY }) {
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(0, false, "平局！"))
        } else {
            curPlayer = if (curPlayer == X) O else X
        }
    }

    private fun checkWin(player: Int): Boolean {
        val lines = listOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6)
        )
        return lines.any { line -> line.all { board[it] == player } }
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        val name = if (curPlayer == X) "✕ 玩家1 落子" else "○ 玩家2 落子"
        canvas.drawText(name, width / 2f, boardTop - cell * 0.45f, turnPaint)
        for (r in 0 until N) for (c in 0 until N) {
            val rect = android.graphics.RectF(
                boardLeft + c * cell + 4f, boardTop + r * cell + 4f,
                boardLeft + (c + 1) * cell - 4f, boardTop + (r + 1) * cell - 4f)
            canvas.drawRoundRect(rect, dp(14).toFloat(), dp(14).toFloat(), cellBgPaint)
            val v = board[r * N + c]
            if (v == X) {
                val m = cell * 0.28f
                val cx = rect.centerX()
                val cy = rect.centerY()
                canvas.drawLine(cx - m, cy - m, cx + m, cy + m, xPaint)
                canvas.drawLine(cx - m, cy + m, cx + m, cy - m, xPaint)
            } else if (v == O) {
                canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.3f, oPaint)
            }
        }
    }

    override fun reset() {
        for (i in board.indices) board[i] = EMPTY
        curPlayer = if (curPlayer == X) O else X
        over = false
        start()
    }
}
