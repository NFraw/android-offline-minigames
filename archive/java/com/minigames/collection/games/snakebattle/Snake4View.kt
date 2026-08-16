package com.minigames.collection.games.snakebattle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 四人贪吃蛇：屏幕四分区各控一条蛇抢食物，最后存活的玩家获胜。 */
class Snake4View(context: Context) : GameView(context) {

    private data class Pt(val x: Int, val y: Int)

    private inner class SideSnake(val id: Int) {
        val body = ArrayDeque<Pt>()
        var dirX = 0
        var dirY = 0
        var alive = false
        var started = false
    }

    private val snakes = Array(4) { SideSnake(it) }
    private val foods = ArrayList<Pt>()
    private var cols = 16
    private var rows = 24
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var acc = 0f
    private var speed = 4.5f
    private var over = false

    private val touchState = HashMap<Int, FloatArray>()
    private val colors = intArrayOf(0xFF3ED98A.toInt(), 0xFFF5A54A.toInt(), 0xFF37C2F0.toInt(), 0xFFB06CF5.toInt())

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 32f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        cell = min(w / 16f, h / 24f)
        cols = (w / cell).toInt().coerceAtMost(16)
        rows = (h / cell).toInt()
        boardLeft = (w - cols * cell) / 2f
        boardTop = (h - rows * cell) / 2f
        reset()
    }

    private fun zoneOf(x: Float, y: Float): Int {
        val left = x < width / 2f
        val top = y < height / 2f
        return when {
            left && top -> 0
            !left && top -> 1
            left -> 2
            else -> 3
        }
    }

    override fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val zone = zoneOf(event.getX(idx), event.getY(idx))
                val old = touchState.entries.firstOrNull { it.value[0] == zone.toFloat() }?.key
                if (old != null) touchState.remove(old)
                touchState[id] = floatArrayOf(zone.toFloat(), event.getX(idx), event.getY(idx))
                snakes[zone].started = true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val st = touchState[id] ?: continue
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val dx = x - st[1]
                    val dy = y - st[2]
                    if (Math.abs(dx) > 40 || Math.abs(dy) > 40) {
                        val s = snakes[st[0].toInt()]
                        if (Math.abs(dx) > Math.abs(dy)) {
                            setDir(s, if (dx > 0) 1 else -1, 0)
                        } else {
                            setDir(s, 0, if (dy > 0) 1 else -1)
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

    private fun setDir(s: SideSnake, dx: Int, dy: Int) {
        if (dx == -s.dirX && dy == -s.dirY) return
        s.dirX = dx
        s.dirY = dy
    }

    private fun spawnFood() {
        val occupied = HashSet<Pt>()
        for (s in snakes) occupied.addAll(s.body)
        occupied.addAll(foods)
        val free = (0 until cols).flatMap { x -> (0 until rows).map { y -> Pt(x, y) } }.filter { it !in occupied }
        if (free.isNotEmpty()) foods.add(free.random())
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
        val allBody = snakes.flatMap { it.body }.toSet()
        val newHeads = HashMap<Int, Pt>()
        val dead = BooleanArray(4)
        for (s in snakes) {
            if (!s.alive || !s.started) continue
            val head = s.body.first()
            val nh = Pt(head.x + s.dirX, head.y + s.dirY)
            if (nh.x < 0 || nh.x >= cols || nh.y < 0 || nh.y >= rows) {
                dead[s.id] = true
                continue
            }
            if (allBody.contains(nh) || s.body.drop(1).any { it == nh }) {
                dead[s.id] = true
                continue
            }
            newHeads[s.id] = nh
        }
        // 头碰头
        val headSet = HashMap<Pt, Int>()
        for ((id, h) in newHeads) {
            if (headSet.containsKey(h)) {
                dead[id] = true
                dead[headSet[h]!!] = true
            } else {
                headSet[h] = id
            }
        }
        for (s in snakes) {
            if (dead[s.id]) s.alive = false
        }
        for (s in snakes) {
            if (!s.alive || !s.started) continue
            val nh = newHeads[s.id] ?: continue
            s.body.addFirst(nh)
            val eaten = foods.indexOf(nh)
            if (eaten >= 0) {
                foods.removeAt(eaten)
                playSound(SoundManager.EAT)
                spawnFood()
            } else {
                s.body.removeLast()
            }
        }
        val aliveCount = snakes.count { it.alive }
        if (aliveCount <= 1) {
            over = true
            val winner = snakes.firstOrNull { it.alive }
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, if (winner != null) "玩家 " + (winner.id + 1) + " 获胜！" else "同归于尽！"))
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), Paint().apply { color = 0x22FFFFFF.toInt(); strokeWidth = 2f })
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, Paint().apply { color = 0x22FFFFFF.toInt(); strokeWidth = 2f })
        for (f in foods) {
            canvas.drawCircle(boardLeft + f.x * cell + cell / 2f, boardTop + f.y * cell + cell / 2f, cell * 0.26f, foodPaint)
        }
        for (s in snakes) {
            if (!s.alive) continue
            bodyPaint.color = colors[s.id]
            s.body.forEachIndexed { i, p ->
                val r = RectF(boardLeft + p.x * cell + 1f, boardTop + p.y * cell + 1f, boardLeft + (p.x + 1) * cell - 1f, boardTop + (p.y + 1) * cell - 1f)
                canvas.drawRoundRect(r, cell * 0.2f, cell * 0.2f, bodyPaint)
            }
            val label = if (s.started) "玩家 " + (s.id + 1) else "P" + (s.id + 1) + " 滑动开始"
            labelPaint.textSize = 29f
            labelPaint.color = colors[s.id]
            val lx = when (s.id) {
                0 -> width * 0.25f
                1 -> width * 0.75f
                2 -> width * 0.25f
                else -> width * 0.75f
            }
            val ly = if (s.id < 2) dp(24).toFloat() else height - dp(16).toFloat()
            canvas.drawText(label, lx, ly, labelPaint)
        }
    }

    override fun reset() {
        foods.clear()
        touchState.clear()
        val cy = rows / 2
        val cx = cols / 2
        val starts = arrayOf(
            Pt(cx / 2, cy / 2), Pt(cx + cx / 2, cy / 2),
            Pt(cx / 2, cy + cy / 2), Pt(cx + cx / 2, cy + cy / 2)
        )
        val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(-1, 0))
        for (i in 0 until 4) {
            val s = snakes[i]
            s.body.clear()
            s.body.addFirst(starts[i])
            s.body.addLast(Pt(starts[i].x - dirs[i][0], starts[i].y - dirs[i][1]))
            s.dirX = dirs[i][0]
            s.dirY = dirs[i][1]
            s.alive = true
            s.started = false
        }
        acc = 0f
        speed = 4.5f
        over = false
        repeat(4) { spawnFood() }
        start()
    }
}
