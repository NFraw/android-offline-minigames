package com.minigames.collection.games.game2048

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import com.minigames.collection.common.SwipeHelper
import kotlin.math.min

/** 2048：滑动合并数字，合成 2048 即胜利。 */
class Game2048View(context: Context) : GameView(context) {

    private val size = 4
    private val board = IntArray(size * size)
    private var score = 0
    private var won = false
    private var over = false
    private var cellSize = 0f
    private var gap = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val swipe = SwipeHelper(context) { dx, dy -> move(dx, dy) }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A2E.toInt() }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF232C47.toInt() }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = 0xFFF5B971.toInt()
    }

    private val tileColors = mapOf(
        2 to 0xFFE8EAF0.toInt(), 4 to 0xFFD3D9E8.toInt(),
        8 to 0xFFF5B971.toInt(), 16 to 0xFFF59A52.toInt(),
        32 to 0xFFF57C4A.toInt(), 64 to 0xFFF0603E.toInt(),
        128 to 0xFFE8C94A.toInt(), 256 to 0xFFE8B84A.toInt(),
        512 to 0xFFE8A23A.toInt(), 1024 to 0xFFE88C2E.toInt(),
        2048 to 0xFFF0562E.toInt()
    )

    override fun onLayout(w: Int, h: Int) {
        val side = min(w, h)
        gap = dp(6).toFloat()
        cellSize = (side - gap * (size + 1)) / size
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f
        reset()
    }

    override fun onTouch(event: android.view.MotionEvent) {
        swipe.onTouch(event)
    }

    override fun update(dt: Float) {}

    private fun move(dx: Int, dy: Int) {
        if (over || paused) return
        var moved = false
        var gained = 0
        for (i in 0 until size) {
            val seq = if (dx != 0) {
                (0 until size).map { j -> if (dx > 0) i * size + (size - 1 - j) else i * size + j }
            } else {
                (0 until size).map { j -> if (dy > 0) (size - 1 - j) * size + i else j * size + i }
            }
            val line = IntArray(size) { board[seq[it]] }
            val (slid, g) = slideLine(line)
            gained += g
            for (j in 0 until size) {
                if (board[seq[j]] != slid[j]) moved = true
                board[seq[j]] = slid[j]
            }
        }
        if (!moved) return
        score += gained
        setScore(score)
        playSound(SoundManager.MOVE)
        if (!won && board.any { it == 2048 }) {
            won = true
            playSound(SoundManager.WIN)
        }
        spawn()
        if (noMoves()) {
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(score, won, if (won) "成功合成 2048！" else "没有可移动的格子了"))
        }
    }

    private fun slideLine(line: IntArray): Pair<IntArray, Int> {
        val values = line.filter { it != 0 }
        val out = ArrayList<Int>()
        var gained = 0
        var i = 0
        while (i < values.size) {
            if (i + 1 < values.size && values[i] == values[i + 1]) {
                val v = values[i] * 2
                out.add(v)
                gained += v
                i += 2
            } else {
                out.add(values[i])
                i++
            }
        }
        while (out.size < size) out.add(0)
        return Pair(out.toIntArray(), gained)
    }

    private fun spawn() {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return
        val idx = empty.random()
        board[idx] = if (Math.random() < 0.9) 2 else 4
    }

    private fun noMoves(): Boolean {
        if (board.any { it == 0 }) return false
        for (i in 0 until size) for (j in 0 until size) {
            val v = board[i * size + j]
            if (j + 1 < size && board[i * size + j + 1] == v) return false
            if (i + 1 < size && board[(i + 1) * size + j] == v) return false
        }
        return true
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (i in 0 until size) for (j in 0 until size) {
            val v = board[i * size + j]
            val r = RectF(
                boardLeft + gap + j * (cellSize + gap),
                boardTop + gap + i * (cellSize + gap),
                boardLeft + gap + j * (cellSize + gap) + cellSize,
                boardTop + gap + i * (cellSize + gap) + cellSize)
            if (v == 0) {
                canvas.drawRoundRect(r, dp(8).toFloat(), dp(8).toFloat(), emptyPaint)
            } else {
                tilePaint.color = tileColors[v] ?: 0xFFE8EAF0.toInt()
                canvas.drawRoundRect(r, dp(8).toFloat(), dp(8).toFloat(), tilePaint)
                textPaint.color = if (v <= 4) 0xFF1C2333.toInt() else 0xFFFFFFFF.toInt()
                textPaint.textSize = if (v < 100) cellSize * 0.42f else cellSize * 0.34f
                val y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(v.toString(), r.centerX(), y, textPaint)
            }
        }
        if (won && !over) {
            canvas.drawText("🎉 已合成 2048，继续刷高分！", width / 2f, boardTop - dp(14).toFloat(), hintPaint)
        }
    }

    override fun reset() {
        for (i in board.indices) board[i] = 0
        score = 0
        won = false
        over = false
        setScore(0)
        spawn()
        spawn()
        start()
    }
}
