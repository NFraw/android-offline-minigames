package com.minigames.collection.games.sliding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 数字华容道（15 拼图）：点击空格相邻的方块滑动归位。 */
class SlidingView(context: Context) : GameView(context) {

    companion object {
        private const val N = 4
        private const val SIZE = N * N
    }

    private val board = IntArray(SIZE)
    private var blank = SIZE - 1
    private var moves = 0
    private var over = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val blankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 46f
        color = 0xFFE8EAF0.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w, h) * 0.92f
        cell = side / N
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (r !in 0 until N || c !in 0 until N) return
        val idx = r * N + c
        val br = blank / N
        val bc = blank % N
        if (abs(r - br) + abs(c - bc) == 1) {
            // 与空格相邻：滑动
            board[blank] = board[idx]
            board[idx] = 0
            blank = idx
            moves++
            playSound(SoundManager.MOVE)
            if (isSolved()) {
                over = true
                val finalScore = max(0, 500 - moves * 8)
                setScore(finalScore)
                playSound(SoundManager.WIN)
                gameOver(GameResult(finalScore, true, "拼图完成！用了 " + moves + " 步"))
            }
        } else {
            playSound(SoundManager.CLICK)
        }
    }

    private fun isSolved(): Boolean {
        for (i in 0 until SIZE - 1) if (board[i] != i + 1) return false
        return board[SIZE - 1] == 0
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until N) for (c in 0 until N) {
            val v = board[r * N + c]
            val rect = RectF(
                boardLeft + c * cell + 2f, boardTop + r * cell + 2f,
                boardLeft + (c + 1) * cell - 2f, boardTop + (r + 1) * cell - 2f)
            if (v == 0) {
                canvas.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, blankPaint)
            } else {
                canvas.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, cellPaint)
                numPaint.textSize = cell * 0.42f
                canvas.drawText(v.toString(), rect.centerX(), rect.centerY() - (numPaint.descent() + numPaint.ascent()) / 2f, numPaint)
            }
        }
        canvas.drawText("步数 " + moves, width / 2f, boardTop + cell * N + dp(36).toFloat(), hintPaint)
        canvas.drawText("点击与空格相邻的方块进行滑动", width / 2f, boardTop + cell * N + dp(64).toFloat(), hintPaint)
    }

    override fun reset() {
        do {
            val list = (1 until SIZE).toMutableList()
            list.shuffle()
            for (i in 0 until SIZE - 1) board[i] = list[i]
            board[SIZE - 1] = 0
            blank = SIZE - 1
        } while (isSolved() || !isSolvable())
        moves = 0
        over = false
        setScore(0)
        start()
    }

    /** 逆序数奇偶性判断可解性（空格在最后一行时） */
    private fun isSolvable(): Boolean {
        var inv = 0
        val vals = board.filter { it != 0 }
        for (i in vals.indices) {
            for (j in i + 1 until vals.size) {
                if (vals[i] > vals[j]) inv++
            }
        }
        return inv % 2 == 0
    }

    private fun abs(v: Int): Int = if (v < 0) -v else v
    private fun max(a: Int, b: Int): Int = if (a > b) a else b
}
