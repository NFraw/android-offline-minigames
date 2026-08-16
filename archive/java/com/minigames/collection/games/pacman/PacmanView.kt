package com.minigames.collection.games.pacman

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 吃豆人（简化）：滑动控制吃豆人吃豆，避开幽灵，吃满所有豆子获胜。 */
class PacmanView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 15
        private const val ROWS = 15
        private const val WALL = 1
    }

    private val maze = IntArray(COLS * ROWS)
    private val pellets = BooleanArray(COLS * ROWS)
    private var px = 0f
    private var py = 0f
    private var gx = 0f
    private var gy = 0f
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var eaten = 0
    private var total = 0
    private var score = 0
    private var over = false
    private var dirX = 0f
    private var dirY = 0f
    private var targetX = 0f
    private var targetY = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val pelletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5D547.toInt() }
    private val pacPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD54F.toInt() }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
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
        if (over || paused) return
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            targetX = event.x
            targetY = event.y
        }
    }

    override fun update(dt: Float) {
        if (over) return
        // 方向：朝触摸点移动
        val dx = targetX - px
        val dy = targetY - py
        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len > 5f) {
            dirX = dx / len
            dirY = dy / len
        }
        var nx = px + dirX * cell * 2.2f * dt
        var ny = py + dirY * cell * 2.2f * dt
        // 撞墙反弹（简化：保持原位）
        if (!isWall(nx, ny)) {
            px = nx
            py = ny
        } else {
            // 尝试只在一个轴移动
            if (!isWall(nx, py)) px = nx else if (!isWall(px, ny)) py = ny
        }
        // 吃豆
        val c = ((px - boardLeft) / cell).toInt()
        val r = ((py - boardTop) / cell).toInt()
        if (r in 0 until ROWS && c in 0 until COLS && pellets[r * COLS + c]) {
            pellets[r * COLS + c] = false
            eaten++
            score += 10
            setScore(score)
            playSound(SoundManager.EAT)
            if (eaten >= total) {
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(score, true, "豆子全吃完！得分 " + score))
                return
            }
        }
        // 幽灵追踪玩家
        val gdx = px - gx
        val gdy = py - gy
        val glen = Math.hypot(gdx.toDouble(), gdy.toDouble()).toFloat()
        if (glen > 1f) {
            val step = cell * 1.4f * dt
            val gnx = gx + gdx / glen * step
            val gny = gy + gdy / glen * step
            if (!isWall(gnx, gny)) {
                gx = gnx
                gy = gny
            }
        }
        // 碰撞检测
        if (Math.hypot((px - gx).toDouble(), (py - gy).toDouble()) < cell * 0.7f) {
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(score, false, "被幽灵抓住了！得分 " + score))
            return
        }
        invalidate()
    }

    private fun isWall(x: Float, y: Float): Boolean {
        val c = ((x - boardLeft) / cell).toInt()
        val r = ((y - boardTop) / cell).toInt()
        if (r !in 0 until ROWS || c !in 0 until COLS) return true
        return maze[r * COLS + c] == WALL
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val x = boardLeft + c * cell
            val y = boardTop + r * cell
            if (maze[r * COLS + c] == WALL) {
                canvas.drawRoundRect(android.graphics.RectF(x + 1f, y + 1f, x + cell - 1f, y + cell - 1f), dp(6).toFloat(), dp(6).toFloat(), wallPaint)
            } else if (pellets[r * COLS + c]) {
                canvas.drawCircle(x + cell / 2f, y + cell / 2f, cell * 0.12f, pelletPaint)
            }
        }
        canvas.drawCircle(gx, gy, cell * 0.4f, ghostPaint)
        canvas.drawCircle(px, py, cell * 0.38f, pacPaint)
        canvas.drawText("吃豆 " + eaten + " / " + total + " · 滑动控制吃豆人", width / 2f, boardTop + cell * ROWS + dp(34).toFloat(), textPaint)
    }

    override fun reset() {
        for (i in maze.indices) maze[i] = 0
        // 简单迷宫：棋盘格墙
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            if (r == 0 || r == ROWS - 1 || c == 0 || c == COLS - 1) {
                maze[r * COLS + c] = WALL
            } else if (r % 4 == 0 && c % 4 == 0) {
                maze[r * COLS + c] = WALL
            }
        }
        maze[1 * COLS + 1] = 0
        // 鬼魂出生点挖开（(12,12) 是 4 的倍数交叉点，原本是墙）
        maze[12 * COLS + 12] = 0
        maze[12 * COLS + 11] = 0
        maze[11 * COLS + 12] = 0
        total = 0
        for (r in 1 until ROWS - 1) for (c in 1 until COLS - 1) {
            pellets[r * COLS + c] = maze[r * COLS + c] == 0
            if (pellets[r * COLS + c]) total++
        }
        px = boardLeft + cell * 1.5f
        py = boardTop + cell * 1.5f
        gx = boardLeft + cell * (COLS - 2.5f)
        gy = boardTop + cell * (ROWS - 2.5f)
        dirX = 0f
        dirY = 0f
        targetX = px
        targetY = py
        eaten = 0
        score = 0
        over = false
        setScore(0)
        start()
    }
}