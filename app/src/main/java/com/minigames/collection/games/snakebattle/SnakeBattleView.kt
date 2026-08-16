package com.minigames.collection.games.snakebattle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.max
import kotlin.math.min

/**
 * 双人贪吃蛇对战：屏幕左半区滑动控制玩家1（绿），右半区控制玩家2（橙）。
 * 抢食物变长，撞墙/撞蛇身/撞对方即死，最后存活的玩家获胜。
 */
class SnakeBattleView(context: Context) : GameView(context) {

    private data class Pt(val x: Int, val y: Int)

    private data class Btn(val rect: RectF, val dx: Int, val dy: Int, val label: String)

    private val body1 = ArrayDeque<Pt>()
    private val body2 = ArrayDeque<Pt>()
    private var dir1x = 1; private var dir1y = 0
    private var dir2x = -1; private var dir2y = 0
    private val foods = ArrayDeque<Pt>()
    private var alive1 = true
    private var alive2 = true
    private var over = false
    private var started1 = false
    private var started2 = false
    private var acc = 0f
    private var speed = 5.5f

    private var cols = 16
    private var rows = 24
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    /** pointerId -> [zone(0上/1下), downX, downY]，支持双人同时滑动 */
    private val touchState = HashMap<Int, FloatArray>()

    // 两位玩家各自的方向键（上/下半屏各一套）
    private val pad1 = ArrayList<Btn>()
    private val pad2 = ArrayList<Btn>()
    private var padHint1Y = 0f
    private var padHint2Y = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x0DFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val body1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
    private val head1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7BEBAD.toInt() }
    private val body2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5A54A.toInt() }
    private val head2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8C98A.toInt() }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
    private val zonePaint = Paint().apply { color = 0x0AFFFFFF.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 41f
        color = 0xFF8A93A6.toInt()
    }
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
        textSize = 46f
        color = 0xFFFFFFFF.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val padH = dp(156)
        val availH = h - padH * 2
        cell = min(w / 16f, availH / 24f)
        cols = (w / cell).toInt().coerceAtMost(16)
        rows = (availH / cell).toInt()
        boardLeft = (w - cols * cell) / 2f
        boardTop = padH + (availH - rows * cell) / 2f

        // 上/下半屏各一套方向键（十字，紧凑尺寸）
        pad1.clear()
        pad2.clear()
        val bs = dp(42)
        val gap = dp(7)
        fun makePad(list: ArrayList<Btn>, cx: Float, cy: Float) {
            list.add(Btn(RectF(cx - bs / 2f, cy - bs - gap, cx + bs / 2f, cy - gap), 0, -1, "▲"))
            list.add(Btn(RectF(cx - bs - gap - bs / 2f, cy - bs / 2f, cx - gap - bs / 2f, cy + bs / 2f), -1, 0, "◀"))
            list.add(Btn(RectF(cx + gap + bs / 2f, cy - bs / 2f, cx + bs + gap + bs / 2f, cy + bs / 2f), 1, 0, "▶"))
            list.add(Btn(RectF(cx - bs / 2f, cy + gap, cx + bs / 2f, cy + bs + gap), 0, 1, "▼"))
        }
        makePad(pad1, w / 2f, padH / 2f)
        makePad(pad2, w / 2f, h - padH / 2f)
        padHint1Y = padH / 2f
        padHint2Y = h - padH / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            for (b in pad1) {
                if (b.rect.contains(event.x, event.y)) {
                    setDir(0, b.dx, b.dy)
                    playSound(SoundManager.MOVE)
                    return
                }
            }
            for (b in pad2) {
                if (b.rect.contains(event.x, event.y)) {
                    setDir(1, b.dx, b.dy)
                    playSound(SoundManager.MOVE)
                    return
                }
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)
                val zone = if (y < height / 2f) 0 else 1
                // 同半屏已有手指则替换（后按下的生效）
                val old = touchState.entries.firstOrNull { it.value[0] == zone.toFloat() }?.key
                if (old != null) touchState.remove(old)
                touchState[id] = floatArrayOf(zone.toFloat(), x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val st = touchState[id] ?: continue
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val dx = x - st[1]
                    val dy = y - st[2]
                    if (Math.abs(dx) > 44 || Math.abs(dy) > 44) {
                        if (Math.abs(dx) > Math.abs(dy)) {
                            setDir(st[0].toInt(), if (dx > 0) 1 else -1, 0)
                        } else {
                            setDir(st[0].toInt(), 0, if (dy > 0) 1 else -1)
                        }
                        st[1] = x
                        st[2] = y
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                touchState.remove(event.getPointerId(event.actionIndex))
            }
        }
    }

    private fun setDir(zone: Int, dx: Int, dy: Int) {
        if (zone == 0) {
            if (dx == -dir1x && dy == -dir1y) return
            dir1x = dx
            dir1y = dy
            started1 = true
        } else {
            if (dx == -dir2x && dy == -dir2y) return
            dir2x = dx
            dir2y = dy
            started2 = true
        }
    }

    private fun spawnFood() {
        val occupied = body1.toSet() + body2.toSet() + foods.toSet()
        val free = (0 until cols).flatMap { x -> (0 until rows).map { y -> Pt(x, y) } }
            .filter { it !in occupied }
        if (free.isNotEmpty()) foods.addLast(free.random())
    }

    override fun update(dt: Float) {
        if (over) return
        acc += dt * speed
        while (acc >= 1f) {
            acc -= 1f
            step()
            if (over) return
        }
    }

    private fun step() {
        var h1: Pt? = null
        var h2: Pt? = null
        if (alive1 && started1) {
            val head = body1.first()
            h1 = Pt(head.x + dir1x, head.y + dir1y)
            if (h1.x < 0 || h1.x >= cols || h1.y < 0 || h1.y >= rows) {
                alive1 = false
                h1 = null
            }
        }
        if (alive2 && started2) {
            val head = body2.first()
            h2 = Pt(head.x + dir2x, head.y + dir2y)
            if (h2.x < 0 || h2.x >= cols || h2.y < 0 || h2.y >= rows) {
                alive2 = false
                h2 = null
            }
        }
        // 头撞头
        if (h1 != null && h2 != null && h1 == h2) {
            alive1 = false
            alive2 = false
        }
        // 撞对方身体
        if (h1 != null && body2.any { it == h1 }) alive1 = false
        if (h2 != null && body1.any { it == h2 }) alive2 = false
        // 撞自己
        if (h1 != null && body1.any { it == h1 }) alive1 = false
        if (h2 != null && body2.any { it == h2 }) alive2 = false

        if (alive1 && h1 != null) {
            body1.addFirst(h1)
            val eaten = foods.indexOf(h1)
            if (eaten >= 0) {
                foods.removeAt(eaten)
                playSound(SoundManager.EAT)
                spawnFood()
            } else {
                body1.removeLast()
            }
        }
        if (alive2 && h2 != null) {
            body2.addFirst(h2)
            val eaten = foods.indexOf(h2)
            if (eaten >= 0) {
                foods.removeAt(eaten)
                playSound(SoundManager.EAT)
                spawnFood()
            } else {
                body2.removeLast()
            }
        }
        if (!alive1 && !alive2) {
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(0, false, "同归于尽！玩家1 " + body1.size + " 节 vs 玩家2 " + body2.size + " 节"))
        } else if (!alive1) {
            over = true
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, "玩家 2 获胜！玩家1 " + body1.size + " 节 vs 玩家2 " + body2.size + " 节"))
        } else if (!alive2) {
            over = true
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, "玩家 1 获胜！玩家1 " + body1.size + " 节 vs 玩家2 " + body2.size + " 节"))
        }
    }

    private fun drawSnake(canvas: Canvas, body: ArrayDeque<Pt>, head: Paint, tail: Paint) {
        body.forEachIndexed { i, p ->
            val r = RectF(
                boardLeft + p.x * cell + 1.5f, boardTop + p.y * cell + 1.5f,
                boardLeft + (p.x + 1) * cell - 1.5f, boardTop + (p.y + 1) * cell - 1.5f)
            canvas.drawRoundRect(r, cell * 0.22f, cell * 0.22f, if (i == 0) head else tail)
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 上下分区提示
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height / 2f), zonePaint)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF.toInt()
            strokeWidth = 2f
        })
        for (x in 0..cols) {
            canvas.drawLine(boardLeft + x * cell, boardTop, boardLeft + x * cell, boardTop + rows * cell, gridPaint)
        }
        for (y in 0..rows) {
            canvas.drawLine(boardLeft, boardTop + y * cell, boardLeft + cols * cell, boardTop + y * cell, gridPaint)
        }
        foods.forEach { f ->
            canvas.drawCircle(
                boardLeft + f.x * cell + cell / 2f,
                boardTop + f.y * cell + cell / 2f,
                cell * 0.28f, foodPaint)
        }
        if (alive1) drawSnake(canvas, body1, head1Paint, body1Paint)
        if (alive2) drawSnake(canvas, body2, head2Paint, body2Paint)
        // 两位玩家各自的方向键（提示文案在各自半屏左侧，避免压住按键）
        labelPaint.textAlign = Paint.Align.LEFT
        labelPaint.textSize = 31f
        val lh = (labelPaint.ascent() + labelPaint.descent()) / 2f
        canvas.drawText("玩家1 方向键", dp(20).toFloat(), padHint1Y - lh, labelPaint)
        if (!started1) canvas.drawText("（滑动也行）", dp(20).toFloat(), padHint1Y - lh + dp(21).toFloat(), labelPaint)
        canvas.drawText("玩家2 方向键", dp(20).toFloat(), padHint2Y - lh, labelPaint)
        if (!started2) canvas.drawText("（滑动也行）", dp(20).toFloat(), padHint2Y - lh + dp(21).toFloat(), labelPaint)
        labelPaint.textAlign = Paint.Align.CENTER
        drawPad(canvas, pad1)
        drawPad(canvas, pad2)
    }

    private fun drawPad(canvas: Canvas, pad: List<Btn>) {
        for (b in pad) {
            canvas.drawRoundRect(b.rect, dp(13).toFloat(), dp(13).toFloat(), padPaint)
            canvas.drawRoundRect(b.rect, dp(13).toFloat(), dp(13).toFloat(), padStroke)
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() - (padTextPaint.ascent() + padTextPaint.descent()) / 2f, padTextPaint)
        }
    }

    override fun reset() {
        body1.clear()
        body2.clear()
        foods.clear()
        val cy = rows / 2
        body1.addFirst(Pt(cols / 4, cy))
        body1.addLast(Pt(cols / 4 - 1, cy))
        body1.addLast(Pt(cols / 4 - 2, cy))
        body2.addFirst(Pt(cols * 3 / 4, cy))
        body2.addLast(Pt(cols * 3 / 4 + 1, cy))
        body2.addLast(Pt(cols * 3 / 4 + 2, cy))
        dir1x = 1; dir1y = 0
        dir2x = -1; dir2y = 0
        alive1 = true
        alive2 = true
        over = false
        started1 = false
        started2 = false
        acc = 0f
        speed = 5.5f
        repeat(3) { spawnFood() }
        start()
    }
}
