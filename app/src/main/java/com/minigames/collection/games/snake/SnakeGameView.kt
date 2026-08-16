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

/** 贪吃蛇：屏幕滑动或下方方向键控制，吃到食物变长加速，撞墙/咬到自己结束（边界墙清晰可见）。 */
class SnakeGameView(context: Context) : GameView(context) {

    private data class Pt(val x: Int, val y: Int)

    private data class Btn(val rect: RectF, val dx: Int, val dy: Int, val label: String)

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
    private var boardW = 0f
    private var boardH = 0f

    // 方向键（下排控制区）
    private val padBtns = ArrayList<Btn>()
    private var padAreaTop = 0f

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
    private val wallFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4668.toInt() }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8EAF0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6C8BFF.toInt() }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A3557.toInt()
        style = Paint.Style.FILL
    }
    private val padStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A6CF7.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val padTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 53f
        color = 0xFFFFFFFF.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val padH = dp(150)
        val availH = h - padH
        val cellByW = w / 16f
        val cellByH = availH / 24f
        cell = max(10f, min(cellByW, cellByH))
        cols = (w / cell).toInt().coerceAtMost(16)
        rows = (availH / cell).toInt().coerceAtLeast(8)
        boardW = cols * cell
        boardH = rows * cell
        boardLeft = (w - boardW) / 2f
        boardTop = (availH - boardH) / 2f
        padAreaTop = (h - padH).toFloat()

        // 方向键布局（十字）
        padBtns.clear()
        val bs = dp(62)
        val gap = dp(12)
        val cx = w / 2f
        val cy = padAreaTop + dp(62)
        padBtns.add(Btn(RectF(cx - bs / 2f, cy - bs - gap, cx + bs / 2f, cy - gap), 0, -1, "▲"))
        padBtns.add(Btn(RectF(cx - bs - gap - bs / 2f, cy - bs / 2f, cx - gap - bs / 2f, cy + bs / 2f), -1, 0, "◀"))
        padBtns.add(Btn(RectF(cx + gap + bs / 2f, cy - bs / 2f, cx + bs + gap + bs / 2f, cy + bs / 2f), 1, 0, "▶"))
        padBtns.add(Btn(RectF(cx - bs / 2f, cy + gap, cx + bs / 2f, cy + bs + gap), 0, 1, "▼"))
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) {
            for (b in padBtns) {
                if (b.rect.contains(event.x, event.y)) {
                    setDir(b.dx, b.dy)
                    playSound(SoundManager.MOVE)
                    return
                }
            }
        }
        gesture.onTouchEvent(event)
    }

    private fun setDir(dx: Int, dy: Int) {
        // 与当前方向或待执行方向相反都不接受，防止快速连按时丢指令/误判 180° 掉头
        if ((dx == -dirX && dy == -dirY) || (dx == -nextDirX && dy == -nextDirY)) return
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
        textSize = 46f
        color = 0xFF8A93A6.toInt()
    }
    private val padHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 32f
        color = 0xFF8A93A6.toInt()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        if (!started) {
            canvas.drawText("👆 滑动屏幕或点击下方方向键开始", width / 2f, boardTop + boardH / 2f, hintPaint)
        }
        // 边界墙（清晰可见：外圈填充 + 亮色描边）
        val wallMargin = dp(5)
        canvas.drawRoundRect(
            RectF(boardLeft - wallMargin, boardTop - wallMargin, boardLeft + boardW + wallMargin, boardTop + boardH + wallMargin),
            dp(10).toFloat(), dp(10).toFloat(), wallFillPaint)
        canvas.drawRoundRect(
            RectF(boardLeft - wallMargin, boardTop - wallMargin, boardLeft + boardW + wallMargin, boardTop + boardH + wallMargin),
            dp(10).toFloat(), dp(10).toFloat(), wallPaint)
        // 网格
        for (x in 0..cols) {
            canvas.drawLine(boardLeft + x * cell, boardTop, boardLeft + x * cell, boardTop + boardH, gridPaint)
        }
        for (y in 0..rows) {
            canvas.drawLine(boardLeft, boardTop + y * cell, boardLeft + boardW, boardTop + y * cell, gridPaint)
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
        // 方向键
        canvas.drawText("方向键（也可滑动屏幕）", width / 2f, padAreaTop + dp(14).toFloat(), padHintPaint)
        for (b in padBtns) {
            canvas.drawRoundRect(b.rect, dp(14).toFloat(), dp(14).toFloat(), padPaint)
            canvas.drawRoundRect(b.rect, dp(14).toFloat(), dp(14).toFloat(), padStroke)
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() - (padTextPaint.ascent() + padTextPaint.descent()) / 2f, padTextPaint)
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
