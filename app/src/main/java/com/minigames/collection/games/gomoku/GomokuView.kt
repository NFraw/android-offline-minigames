package com.minigames.collection.games.gomoku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 五子棋：双人同屏轮流落子，先连成五子者胜。 */
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
        textSize = 28f
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w * 0.96f, h * 0.78f)
        cell = side / (N - 1)
        stoneR = cell * 0.42f
        boardLeft = (w - cell * (N - 1)) / 2f
        boardTop = (h - cell * (N - 1)) / 2f + cell * 0.7f
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
        canvas.drawColor(0xFF141A2E.toInt())
        // 棋盘
        val left = boardLeft - cell * 0.5f
        val top = boardTop - cell * 0.5f
        val right = boardLeft + (N - 1) * cell + cell * 0.5f
        val bottom = boardTop + (N - 1) * cell + cell * 0.5f
        canvas.drawRoundRect(android.graphics.RectF(left, top, right, bottom), cell * 0.4f, cell * 0.4f, boardPaint)
        for (i in 0 until N) {
            canvas.drawLine(boardLeft, boardTop + i * cell, boardLeft + (N - 1) * cell, boardTop + i * cell, linePaint)
            canvas.drawLine(boardLeft + i * cell, boardTop, boardLeft + i * cell, boardTop + (N - 1) * cell, linePaint)
        }
        // 星位
        val stars = listOf(Pair(3, 3), Pair(11, 3), Pair(3, 11), Pair(11, 11), Pair(7, 7))
        for ((sx, sy) in stars) {
            canvas.drawCircle(boardLeft + sx * cell, boardTop + sy * cell, cell * 0.1f, linePaint)
        }
        // 棋子
        for (r in 0 until N) for (c in 0 until N) {
            val v = board[r * N + c]
            if (v == EMPTY) continue
            val cx = boardLeft + c * cell
            val cy = boardTop + r * cell
            if (v == BLACK) {
                canvas.drawCircle(cx, cy, stoneR, blackHiPaint)
                canvas.drawCircle(cx, cy, stoneR * 0.85f, blackPaint)
            } else {
                canvas.drawCircle(cx, cy, stoneR, whiteBorderPaint)
                canvas.drawCircle(cx, cy, stoneR * 0.95f, whitePaint)
            }
        }
        // 最后一手标记
        if (lastX >= 0) {
            canvas.drawCircle(boardLeft + lastX * cell, boardTop + lastY * cell, cell * 0.14f, markPaint)
        }
        // 回合提示
        turnPaint.color = if (curPlayer == BLACK) 0xFFE8EAF0.toInt() else 0xFFB9C0CE.toInt()
        val name = if (curPlayer == BLACK) "● 黑方落子" else "○ 白方落子"
        canvas.drawText(name, width / 2f, boardTop - cell * 1.35f, turnPaint)
    }

    override fun reset() {
        for (i in board.indices) board[i] = EMPTY
        curPlayer = BLACK
        lastX = -1
        lastY = -1
        over = false
        start()
    }
}
