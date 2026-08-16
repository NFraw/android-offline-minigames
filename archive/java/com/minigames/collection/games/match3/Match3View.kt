package com.minigames.collection.games.match3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 三消：交换相邻宝石，三连消除计分。 */
class Match3View(context: Context) : GameView(context) {

    companion object {
        private const val N = 8
        private const val TYPES = 5
        private const val TARGET = 200
    }

    private val board = IntArray(N * N)
    private var selA = -1
    private var selB = -1
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var score = 0
    private var over = false

    private val colors = intArrayOf(
        0xFFFF6B6B.toInt(), 0xFFF5A54A.toInt(), 0xFFF5D547.toInt(),
        0xFF3ED98A.toInt(), 0xFFB06CF5.toInt()
    )
    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val gemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w, h) * 0.9f
        cell = side / N
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (r !in 0 until N || c !in 0 until N) return
        val idx = r * N + c
        if (selA < 0) {
            selA = idx
            playSound(SoundManager.CLICK)
        } else if (selB < 0) {
            val ar = selA / N
            val ac = selA % N
            if (abs(r - ar) + abs(c - ac) == 1) {
                selB = idx
                swapAndCheck(selA, idx)
            } else {
                selA = idx
                playSound(SoundManager.CLICK)
            }
        } else {
            selA = idx
            selB = -1
            playSound(SoundManager.CLICK)
        }
        invalidate()
    }

    private fun swapAndCheck(a: Int, b: Int) {
        val tmp = board[a]
        board[a] = board[b]
        board[b] = tmp
        if (findMatches().isEmpty()) {
            // 无效交换，换回
            val t2 = board[a]
            board[a] = board[b]
            board[b] = t2
            playSound(SoundManager.LOSE)
        } else {
            playSound(SoundManager.MOVE)
            selA = -1
            selB = -1
            resolve()
        }
    }

    private fun findMatches(): HashSet<Int> {
        val matched = HashSet<Int>()
        for (r in 0 until N) for (c in 0 until N) {
            val v = board[r * N + c]
            if (v == 0) continue
            // 横向
            if (c + 2 < N && board[r * N + c + 1] == v && board[r * N + c + 2] == v) {
                matched.add(r * N + c)
                matched.add(r * N + c + 1)
                matched.add(r * N + c + 2)
            }
            // 纵向
            if (r + 2 < N && board[(r + 1) * N + c] == v && board[(r + 2) * N + c] == v) {
                matched.add(r * N + c)
                matched.add((r + 1) * N + c)
                matched.add((r + 2) * N + c)
            }
        }
        return matched
    }

    private fun resolve() {
        var guard = 0
        while (guard++ < 20) {
            val matched = findMatches()
            if (matched.isEmpty()) break
            score += matched.size * 10
            setScore(score)
            playSound(SoundManager.EAT)
            for (i in matched) board[i] = 0
            // 下落
            for (c in 0 until N) {
                var write = N - 1
                for (r in N - 1 downTo 0) {
                    if (board[r * N + c] != 0) {
                        board[write * N + c] = board[r * N + c]
                        write--
                    }
                }
                while (write >= 0) {
                    board[write * N + c] = (0 until TYPES).random()
                    write--
                }
            }
        }
        if (score >= TARGET) {
            over = true
            playSound(SoundManager.WIN)
            gameOver(GameResult(score, true, "达到 " + TARGET + " 分目标！"))
        }
        invalidate()
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until N) for (c in 0 until N) {
            val idx = r * N + c
            val rect = android.graphics.RectF(boardLeft + c * cell, boardTop + r * cell, boardLeft + (c + 1) * cell, boardTop + (r + 1) * cell)
            canvas.drawRoundRect(rect, dp(6).toFloat(), dp(6).toFloat(), cellBgPaint)
            val v = board[idx]
            if (v > 0) {
                gemPaint.color = colors[v - 1]
                canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.34f, gemPaint)
            }
            if (idx == selA || idx == selB) {
                canvas.drawRoundRect(rect, dp(6).toFloat(), dp(6).toFloat(), selPaint)
            }
        }
        canvas.drawText("交换相邻宝石三连消除 · 目标 " + TARGET + " 分 · 当前 " + score, width / 2f, boardTop + cell * N + dp(36).toFloat(), textPaint)
    }

    override fun reset() {
        do {
            for (i in board.indices) board[i] = (1..TYPES).random()
        } while (findMatches().isNotEmpty())
        selA = -1
        selB = -1
        score = 0
        over = false
        setScore(0)
        start()
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}
