package com.minigames.collection.games.sokoban

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 推箱子：滑动推箱子到目标点，全部到位过关。 */
class SokobanView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 8
        private const val ROWS = 8
        private const val WALL = 1
        private const val BOX = 2
        private const val GOAL = 3
    }

    private val level = intArrayOf(
        1, 1, 1, 1, 1, 1, 1, 1,
        1, 0, 0, 2, 3, 0, 0, 1,
        1, 0, 0, 0, 0, 0, 0, 1,
        1, 0, 2, 0, 0, 2, 0, 1,
        1, 0, 0, 0, 0, 3, 0, 1,
        1, 0, 0, 0, 3, 0, 0, 1,
        1, 0, 0, 0, 0, 0, 0, 1,
        1, 1, 1, 1, 1, 1, 1, 1
    )
    private val state = level.copyOf()
    private var px = 1
    private var py = 1
    private var moves = 0
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var over = false

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            move(if (Math.abs(vx) > Math.abs(vy)) if (vx > 0) 1 else -1 else 0, if (Math.abs(vy) > Math.abs(vx)) if (vy > 0) 1 else -1 else 0)
            return true
        }
    })

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5A54A.toInt() }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        cell = kotlin.math.min(w, h) / COLS.toFloat()
        boardLeft = (w - cell * COLS) / 2f
        boardTop = (h - cell * ROWS) / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        gesture.onTouchEvent(event)
    }

    private fun move(dx: Int, dy: Int) {
        if (over || paused || (dx == 0 && dy == 0)) return
        val nx = px + dx
        val ny = py + dy
        if (state[ny * COLS + nx] == WALL) return
        if (state[ny * COLS + nx] == BOX) {
            val bx = nx + dx
            val by = ny + dy
            if (state[by * COLS + bx] == WALL || state[by * COLS + bx] == BOX) return
            state[by * COLS + bx] = BOX
            state[ny * COLS + nx] = 0
            playSound(SoundManager.MOVE)
        }
        px = nx
        py = ny
        moves++
        // 胜利：所有目标点都有箱子
        var done = true
        for (i in state.indices) {
            if (level[i] == GOAL && state[i] != BOX) done = false
        }
        if (done) {
            over = true
            playSound(SoundManager.WIN)
            gameOver(GameResult(max(0, 300 - moves * 2), true, "过关！用了 " + moves + " 步"))
        }
        invalidate()
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val v = state[r * COLS + c]
            val x = boardLeft + c * cell
            val y = boardTop + r * cell
            val rect = RectF(x + 1f, y + 1f, x + cell - 1f, y + cell - 1f)
            when {
                v == WALL -> canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), wallPaint)
                v == BOX -> {
                    canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), floorPaint)
                    canvas.drawRoundRect(RectF(x + cell * 0.15f, y + cell * 0.15f, x + cell * 0.85f, y + cell * 0.85f), dp(6).toFloat(), dp(6).toFloat(), boxPaint)
                }
                level[r * COLS + c] == GOAL -> {
                    canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), floorPaint)
                    canvas.drawCircle(x + cell / 2f, y + cell / 2f, cell * 0.18f, goalPaint)
                }
                else -> canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), floorPaint)
            }
        }
        canvas.drawCircle(boardLeft + px * cell + cell / 2f, boardTop + py * cell + cell / 2f, cell * 0.3f, playerPaint)
        canvas.drawText("滑动移动，把箱子推到绿点 · 步数 " + moves, width / 2f, boardTop + cell * ROWS + dp(34).toFloat(), textPaint)
    }

    override fun reset() {
        for (i in state.indices) state[i] = level[i]
        px = 1
        py = 1
        moves = 0
        over = false
        setScore(0)
        start()
    }

    private fun max(a: Int, b: Int): Int = if (a > b) a else b
}
