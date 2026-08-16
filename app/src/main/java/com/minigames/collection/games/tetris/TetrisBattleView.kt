package com.minigames.collection.games.tetris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 俄罗斯方块对战：上下分屏各玩各的，消行会给对方加垃圾行，先堆到顶者输。 */
class TetrisBattleView(context: Context) : GameView(context) {

    companion object {
        private val COLS = 10
        private val ROWS = 16
        private val SHAPES = listOf(
            listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(3, 0)),
            listOf(Pair(1, 0), Pair(1, 1), Pair(2, 0), Pair(2, 1)),
            listOf(Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(2, 1)),
            listOf(Pair(0, 0), Pair(1, 0), Pair(1, 1), Pair(2, 1)),
            listOf(Pair(1, 0), Pair(2, 0), Pair(0, 1), Pair(1, 1)),
            listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(2, 1)),
            listOf(Pair(0, 0), Pair(1, 0), Pair(2, 0), Pair(0, 1))
        )
        private val COLORS = intArrayOf(
            0xFF37C2F0.toInt(), 0xFFF5D547.toInt(), 0xFFB06CF5.toInt(),
            0xFF3ED98A.toInt(), 0xFFF0604A.toInt(), 0xFF4A6CF7.toInt(), 0xFFF5A54A.toInt()
        )
    }

    private inner class PlayerSide(val top: Boolean) {
        val board = IntArray(COLS * ROWS)
        var shape = 0
        var cells = mutableListOf<Pair<Int, Int>>()
        var x = 3
        var y = 0
        var next = 0
        var fallAcc = 0f
        var lines = 0
        var started = false
        var over = false
    }

    private val p1 = PlayerSide(true)
    private val p2 = PlayerSide(false)
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop1 = 0f
    private var boardTop2 = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        cell = min(w / (COLS + 1.6f), h / (ROWS * 2 + 1f))
        val bw = COLS * cell
        boardLeft = (w - bw) / 2f
        val bh = ROWS * cell
        boardTop1 = (h - bh * 2) / 2f
        boardTop2 = boardTop1 + bh
        reset()
    }

    /** pointerId -> [zone(0上/1下), downX, downY]，双人同时操作 */
    private val touchState = HashMap<Int, FloatArray>()

    private fun sideOf(zone: Int): PlayerSide = if (zone == 0) p1 else p2

    override fun onTouch(event: MotionEvent) {
        if (paused) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val zone = if (event.getY(idx) < height / 2f) 0 else 1
                val old = touchState.entries.firstOrNull { it.value[0] == zone.toFloat() }?.key
                if (old != null) touchState.remove(old)
                touchState[id] = floatArrayOf(zone.toFloat(), event.getX(idx), event.getY(idx))
                sideOf(zone).started = true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val st = touchState[id] ?: continue
                    val zone = st[0].toInt()
                    val x = event.getX(i)
                    val dx = x - st[1]
                    if (Math.abs(dx) > 40f) {
                        val s = sideOf(zone)
                        if (fits(s, s.cells, s.x + (if (dx > 0) 1 else -1), s.y)) {
                            s.x += if (dx > 0) 1 else -1
                        }
                        st[1] = x
                    }
                    // 快速下落：向下拖动
                    val y = event.getY(i)
                    val dy = y - st[2]
                    if (dy > 120f) {
                        val s = sideOf(zone)
                        while (fits(s, s.cells, s.x, s.y + 1)) s.y++
                        st[2] = y
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                touchState.remove(event.getPointerId(event.actionIndex))
            }
        }
    }

    override fun update(dt: Float) {
        stepSide(p1, dt)
        stepSide(p2, dt)
        if (p1.over || p2.over) return
        invalidate()
    }

    private fun stepSide(s: PlayerSide, dt: Float) {
        if (s.over || !s.started) return
        s.fallAcc += dt * (1f / 0.7f)
        if (s.fallAcc >= 1f) {
            s.fallAcc = 0f
            if (!tryDrop(s)) return
        }
    }

    private fun fits(s: PlayerSide, cells: List<Pair<Int, Int>>, ox: Int, oy: Int): Boolean {
        for ((cx, cy) in cells) {
            val x = cx + ox
            val y = cy + oy
            if (x < 0 || x >= COLS || y >= ROWS) return false
            if (y >= 0 && s.board[y * COLS + x] != 0) return false
        }
        return true
    }

    private fun tryDrop(s: PlayerSide): Boolean {
        if (fits(s, s.cells, s.x, s.y + 1)) {
            s.y++
            return true
        }
        lock(s)
        return false
    }

    private fun lock(s: PlayerSide) {
        for ((cx, cy) in s.cells) {
            val y = cy + s.y
            if (y >= 0 && y < ROWS) s.board[y * COLS + s.x + cx] = s.shape + 1
        }
        // 消行
        var cleared = 0
        var write = ROWS - 1
        for (read in ROWS - 1 downTo 0) {
            var full = true
            for (c in 0 until COLS) if (s.board[read * COLS + c] == 0) full = false
            if (!full) {
                for (c in 0 until COLS) s.board[write * COLS + c] = s.board[read * COLS + c]
                write--
            } else cleared++
        }
        for (r in 0..write) for (c in 0 until COLS) s.board[r * COLS + c] = 0
        if (cleared > 0) {
            s.lines += cleared
            playSound(SoundManager.EAT)
            // 给对方加垃圾行
            val other = if (s === p1) p2 else p1
            addGarbage(other, cleared - 1)
        }
        // 新方块
        s.shape = s.next
        s.next = (0 until SHAPES.size).random()
        s.cells = SHAPES[s.shape].toMutableList()
        s.x = 3
        s.y = 0
        if (!fits(s, s.cells, s.x, s.y)) {
            s.over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(0, s === p2, (if (s === p1) "玩家 2" else "玩家 1") + "获胜！"))
        }
    }

    private fun addGarbage(s: PlayerSide, rows: Int) {
        if (rows <= 0 || s.over) return
        // 上移，底部加随机行
        for (r in 0 until ROWS - rows) {
            for (c in 0 until COLS) s.board[r * COLS + c] = s.board[(r + rows) * COLS + c]
        }
        for (r in ROWS - rows until ROWS) {
            for (c in 0 until COLS) {
                s.board[r * COLS + c] = if (c == (0 until COLS).random()) 0 else (0 until 7).random() + 1
            }
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        drawSide(canvas, p1, boardTop1)
        drawSide(canvas, p2, boardTop2)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, Paint().apply { color = 0x33FFFFFF.toInt(); strokeWidth = 2f })
        textPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("玩家 1（上半屏滑动控制，消行攻击对方）", width / 2f, boardTop1 - dp(10).toFloat(), textPaint)
        canvas.drawText("玩家 2（下半屏）", width / 2f, boardTop2 + ROWS * cell + dp(18).toFloat(), textPaint)
    }

    private fun drawSide(canvas: Canvas, s: PlayerSide, top: Float) {
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val v = s.board[r * COLS + c]
            val rect = RectF(boardLeft + c * cell, top + r * cell, boardLeft + (c + 1) * cell, top + (r + 1) * cell)
            if (v == 0) {
                canvas.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, emptyPaint)
            } else {
                tilePaint.color = COLORS[v - 1]
                canvas.drawRoundRect(rect, cell * 0.1f, cell * 0.1f, tilePaint)
            }
        }
        if (s.started && !s.over) {
            tilePaint.color = COLORS[s.shape]
            for ((cx, cy) in s.cells) {
                val x = boardLeft + (s.x + cx) * cell
                val y = top + (s.y + cy) * cell
                if (s.y + cy >= 0) {
                    canvas.drawRoundRect(RectF(x + 1f, y + 1f, x + cell - 1f, y + cell - 1f), cell * 0.1f, cell * 0.1f, tilePaint)
                }
            }
        }
    }

    override fun reset() {
        for (s in arrayOf(p1, p2)) {
            for (i in s.board.indices) s.board[i] = 0
            s.shape = 0
            s.next = (0 until SHAPES.size).random()
            s.cells = SHAPES[0].toMutableList()
            s.x = 3
            s.y = 0
            s.fallAcc = 0f
            s.lines = 0
            s.started = false
            s.over = false
        }
        start()
    }
}