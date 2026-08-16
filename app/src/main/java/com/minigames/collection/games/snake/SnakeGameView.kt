package com.minigames.collection.games.snake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.max
import kotlin.math.min

/** 贪吃蛇：滑动控制方向，吃到食物变长加速，撞墙/咬到自己结束。 */
class SnakeGameView(context: Context) : GameView(context) {

    private data class Pt(val x: Int, val y: Int)

    private val body = ArrayDeque<Pt>()
    private var dirX = 1
    private var dirY = 0
    private var nextDirX = 1
    private var nextDirY = 0
    private var food = Pt(0, 0)
    private var score = 0
    private var speed = 5f
    private var acc = 0f
    private var alive = true
    private var started = false
    private var cols = 16
    private var rows = 24
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (Math.abs(vx) > Math.abs(vy)) {
                setDir(if (vx > 0) 1 else -1, 0)
            } else {
                setDir(0, if (vy > 0) 1 else -1)
            }
            return true
        }
    })

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x0DFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6C8BFF.toInt() }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    override fun onLayout(w: Int, h: Int) {
        val cellByW = w / 16f
        val cellByH = h / 28f
        cell = max(10f, min(cellByW, cellByH))
        cols = (w / cell).toInt().coerceAtMost(16)
        rows = (h / cell).toInt()
        boardLeft = (w - cols * cell) / 2f
        boardTop = (h - rows * cell) / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        gesture.onTouchEvent(event)
    }

    private fun setDir(dx: Int, dy: Int) {
        if (dx == -dirX && dy == -dirY) return
        nextDirX = dx
        nextDirY = dy
        started = true
    }

    private fun spawnFood() {
        val occupied = body.toSet()
        val free = (0 until cols).flatMap { x -> (0 until rows).map { y -> Pt(x, y) } }
            .filter { it !in occupied }
        if (free.isEmpty()) {
            gameOver(GameResult(score, true, "你赢了！整个棋盘都被蛇填满了"))
            return
        }
        food = free.random()
    }

    override fun update(dt: Float) {
        if (!alive || !started) return
        acc += dt * speed
        while (acc >= 1f) {
            acc -= 1f
            step()
            if (!alive) return
        }
    }

    private fun step() {
        dirX = nextDirX
        dirY = nextDirY
        val head = body.first()
        val nh = Pt(head.x + dirX, head.y + dirY)
        if (nh.x < 0 || nh.x >= cols || nh.y < 0 || nh.y >= rows) {
            die("撞到墙了")
            return
        }
        if (body.any { it == nh }) {
            die("咬到自己了")
            return
        }
        body.addFirst(nh)
        if (nh == food) {
            score += 10
            setScore(score)
            playSound(SoundManager.EAT)
            if (score % 50 == 0 && speed < 13f) speed += 0.6f
            spawnFood()
        } else {
            body.removeLast()
        }
    }

    private fun die(reason: String) {
        alive = false
        playSound(SoundManager.LOSE)
        gameOver(GameResult(score, false, reason + "，蛇身长度 " + body.size))
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f
        color = 0xFF8A93A6.toInt()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        if (!started) {
            canvas.drawText("👆 滑动屏幕开始", width / 2f, boardTop + rows * cell / 2f, hintPaint)
        }
        for (x in 0..cols) {
            canvas.drawLine(boardLeft + x * cell, boardTop, boardLeft + x * cell, boardTop + rows * cell, gridPaint)
        }
        for (y in 0..rows) {
            canvas.drawLine(boardLeft, boardTop + y * cell, boardLeft + cols * cell, boardTop + y * cell, gridPaint)
        }
        // 食物
        canvas.drawCircle(
            boardLeft + food.x * cell + cell / 2f,
            boardTop + food.y * cell + cell / 2f,
            cell * 0.32f, foodPaint)
        // 蛇身
        body.forEachIndexed { i, p ->
            val r = RectF(
                boardLeft + p.x * cell + 1.5f, boardTop + p.y * cell + 1.5f,
                boardLeft + (p.x + 1) * cell - 1.5f, boardTop + (p.y + 1) * cell - 1.5f)
            canvas.drawRoundRect(r, cell * 0.22f, cell * 0.22f, if (i == 0) headPaint else bodyPaint)
        }
        // 蛇眼
        if (alive) {
            val hx = boardLeft + body.first().x * cell + cell / 2f
            val hy = boardTop + body.first().y * cell + cell / 2f
            val ex = dirX * cell * 0.22f
            val ey = dirY * cell * 0.22f
            val px = if (dirX != 0) 0f else cell * 0.15f
            val py = if (dirY != 0) 0f else cell * 0.15f
            canvas.drawCircle(hx + ex - px, hy + ey - py, cell * 0.09f, eyePaint)
            canvas.drawCircle(hx + ex + px, hy + ey + py, cell * 0.09f, eyePaint)
        }
    }

    override fun reset() {
        body.clear()
        val cy = rows / 2
        val cx = cols / 2
        body.addFirst(Pt(cx, cy))
        body.addLast(Pt(cx - 1, cy))
        body.addLast(Pt(cx - 2, cy))
        dirX = 1; dirY = 0
        nextDirX = 1; nextDirY = 0
        score = 0
        speed = 5f
        acc = 0f
        alive = true
        started = false
        setScore(0)
        spawnFood()
        start()
    }
}
