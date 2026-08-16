package com.minigames.collection.games.mines

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

/** 扫雷：点击翻开，长按插旗，翻开所有非雷格获胜。 */
class MinesView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 9
        private const val ROWS = 12
        private const val MINES = 15
        private const val MINE = -1
    }

    private val grid = IntArray(COLS * ROWS)
    private val revealed = BooleanArray(COLS * ROWS)
    private val flagged = BooleanArray(COLS * ROWS)
    private var firstTap = true
    private var over = false
    private var time = 0f
    private var revealedCount = 0
    private var longPressed = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            longPressed = true
            toggleFlag(e.x, e.y)
        }
    })

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val hiddenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val shownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = 0xFF8A93A6.toInt()
    }
    private val bombPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val flagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val numColors = intArrayOf(
        0xFF8A93A6.toInt(), 0xFF6C8BFF.toInt(), 0xFF3ED98A.toInt(), 0xFFFF6B6B.toInt(),
        0xFF7A3BF0.toInt(), 0xFFB3541E.toInt(), 0xFF1BA1A8.toInt(), 0xFF333A48.toInt(), 0xFF7A7F8C.toInt()
    )

    override fun onLayout(w: Int, h: Int) {
        cell = min(w / (COLS + 1.2f), h / (ROWS + 2.2f))
        boardLeft = (w - COLS * cell) / 2f
        boardTop = (h - ROWS * cell) / 2f + cell * 0.8f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        gesture.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            if (!longPressed) revealAt(event.x, event.y)
            longPressed = false
        }
    }

    override fun update(dt: Float) {
        if (!over) time += dt
    }

    private fun cellAt(x: Float, y: Float): Int {
        val c = ((x - boardLeft) / cell).toInt()
        val r = ((y - boardTop) / cell).toInt()
        if (r !in 0 until ROWS || c !in 0 until COLS) return -1
        return r * COLS + c
    }

    private fun toggleFlag(x: Float, y: Float) {
        val idx = cellAt(x, y)
        if (idx < 0 || revealed[idx] || over) return
        flagged[idx] = !flagged[idx]
        playSound(SoundManager.CLICK)
    }

    private fun revealAt(x: Float, y: Float) {
        val idx = cellAt(x, y)
        if (idx < 0 || revealed[idx] || flagged[idx] || over) return
        if (firstTap) {
            firstTap = false
            // 首次点击必安全：若踩雷则把雷移走
            if (grid[idx] == MINE) {
                grid[idx] = 0
                val empty = grid.indices.filter { it != idx && grid[it] != MINE }.toMutableList()
                val newMine = empty.random()
                grid[newMine] = MINE
                computeCounts()
            }
        }
        reveal(idx)
    }

    private fun computeCounts() {
        for (i in grid.indices) {
            if (grid[i] == MINE) continue
            var n = 0
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = i / COLS + dr
                val c = i % COLS + dc
                if (r in 0 until ROWS && c in 0 until COLS && grid[r * COLS + c] == MINE) n++
            }
            grid[i] = n
        }
    }

    private fun reveal(idx: Int) {
        if (revealed[idx] || flagged[idx] || over) return
        if (grid[idx] == MINE) {
            revealed[idx] = true
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(0, false, "踩到地雷了！用时 " + time.toInt() + " 秒"))
            return
        }
        revealed[idx] = true
        revealedCount++
        playSound(SoundManager.MOVE)
        if (grid[idx] == 0) {
            val r = idx / COLS
            val c = idx % COLS
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until ROWS && nc in 0 until COLS) reveal(nr * COLS + nc)
            }
        }
        if (revealedCount == COLS * ROWS - MINES) {
            over = true
            val s = max(0, 500 - time.toInt() * 2)
            playSound(SoundManager.WIN)
            gameOver(GameResult(s, true, "排雷成功！用时 " + time.toInt() + " 秒"))
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        var flagsLeft = MINES
        for (i in 0 until COLS * ROWS) if (flagged[i]) flagsLeft--
        canvas.drawText("剩余地雷 " + flagsLeft + " · 用时 " + time.toInt() + "s", width / 2f, boardTop - dp(10).toFloat(), textPaint)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val i = r * COLS + c
            val rect = RectF(
                boardLeft + c * cell + 1f, boardTop + r * cell + 1f,
                boardLeft + (c + 1) * cell - 1f, boardTop + (r + 1) * cell - 1f)
            val radius = cell * 0.14f
            if (revealed[i]) {
                canvas.drawRoundRect(rect, radius, radius, shownPaint)
                if (grid[i] == MINE) {
                    bombPaint.color = 0xFFFF5252.toInt()
                    bombPaint.textSize = cell * 0.55f
                    canvas.drawText("💣", rect.centerX(), rect.centerY() - bombPaint.ascent() * 0.4f, bombPaint)
                } else if (grid[i] > 0) {
                    numPaint.color = numColors[grid[i]]
                    numPaint.textSize = cell * 0.5f
                    canvas.drawText(grid[i].toString(), rect.centerX(), rect.centerY() - numPaint.ascent() * 0.4f, numPaint)
                }
            } else {
                canvas.drawRoundRect(rect, radius, radius, hiddenPaint)
                if (flagged[i]) {
                    flagPaint.color = 0xFFFFB74D.toInt()
                    flagPaint.textSize = cell * 0.55f
                    canvas.drawText("🚩", rect.centerX(), rect.centerY() - flagPaint.ascent() * 0.4f, flagPaint)
                }
            }
        }
    }

    override fun reset() {
        for (i in grid.indices) grid[i] = 0
        for (i in revealed.indices) revealed[i] = false
        for (i in flagged.indices) flagged[i] = false
        var placed = 0
        while (placed < MINES) {
            val i = (0 until COLS * ROWS).random()
            if (grid[i] != MINE) {
                grid[i] = MINE
                placed++
            }
        }
        computeCounts()
        firstTap = true
        over = false
        time = 0f
        revealedCount = 0
        longPressed = false
        setScore(0)
        start()
    }
}
