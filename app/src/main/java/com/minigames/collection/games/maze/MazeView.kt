package com.minigames.collection.games.maze

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 迷宫：滑动小球穿越迷宫到达终点。 */
class MazeView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 15
        private const val ROWS = 15
        private const val WALL = 1
    }

    private val maze = IntArray(COLS * ROWS)
    private var px = 0.5f
    private var py = 0.5f
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var time = 0f
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        cell = kotlin.math.min(w, h) / COLS.toFloat()
        boardLeft = (w - cell * COLS) / 2f
        boardTop = (h - cell * ROWS) / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            // 球朝触摸方向滚动（倾斜球效果）
            val dx = event.x - (boardLeft + px * cell)
            val dy = event.y - (boardTop + py * cell)
            val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (len > cell * 0.2f) {
                val step = cell * 3.5f * 0.016f
                tryMove(dx / len * step, dy / len * step)
            }
        }
    }

    private fun tryMove(dx: Float, dy: Float) {
        var nx = px + dx / cell
        var ny = py + dy / cell
        if (!isWall(nx, py)) px = nx
        if (!isWall(px, ny)) py = ny
        // 到达终点
        val c = px.toInt()
        val r = py.toInt()
        if (c == COLS - 2 && r == ROWS - 2) {
            over = true
            val finalScore = max(0, 300 - time.toInt() * 3)
            setScore(finalScore)
            playSound(SoundManager.WIN)
            gameOver(GameResult(finalScore, true, "走出迷宫！用时 " + time.toInt() + " 秒"))
        }
        invalidate()
    }

    private fun isWall(x: Float, y: Float): Boolean {
        val c = x.toInt()
        val r = y.toInt()
        if (r !in 0 until ROWS || c !in 0 until COLS) return true
        return maze[r * COLS + c] == WALL
    }

    override fun update(dt: Float) {
        if (!over) time += dt
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            if (maze[r * COLS + c] == WALL) {
                canvas.drawRoundRect(android.graphics.RectF(
                    boardLeft + c * cell, boardTop + r * cell,
                    boardLeft + (c + 1) * cell, boardTop + (r + 1) * cell), dp(4).toFloat(), dp(4).toFloat(), wallPaint)
            }
        }
        canvas.drawCircle(boardLeft + (COLS - 2) * cell + cell / 2f, boardTop + (ROWS - 2) * cell + cell / 2f, cell * 0.3f, goalPaint)
        canvas.drawCircle(boardLeft + px * cell, boardTop + py * cell, cell * 0.32f, ballPaint)
        canvas.drawText("滑动小球走向绿点 · 用时 " + time.toInt() + "s", width / 2f, boardTop + cell * ROWS + dp(34).toFloat(), textPaint)
    }

    override fun reset() {
        for (i in maze.indices) maze[i] = WALL
        // 简单迷宫生成：曼哈顿走廊
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            if (r % 2 == 1 || c % 2 == 1) maze[r * COLS + c] = 0
        }
        maze[0 * COLS + 0] = 0
        maze[0 * COLS + 1] = 0
        maze[1 * COLS + 0] = 0
        px = 0.5f
        py = 0.5f
        time = 0f
        over = false
        setScore(0)
        start()
    }

    private fun max(a: Int, b: Int): Int = if (a > b) a else b
}
