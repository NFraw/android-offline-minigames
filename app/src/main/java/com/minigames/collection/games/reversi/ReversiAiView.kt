package com.minigames.collection.games.reversi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 黑白棋（人机）：玩家执黑先手，AI 采用边角加权贪心策略。 */
class ReversiAiView(context: Context) : GameView(context) {

    companion object {
        private const val N = 8
        private const val EMPTY = 0
        private const val BLACK = 1
        private const val WHITE = 2
        private val DIRS = listOf(
            Pair(-1, -1), Pair(0, -1), Pair(1, -1),
            Pair(-1, 0), Pair(1, 0),
            Pair(-1, 1), Pair(0, 1), Pair(1, 1)
        )
        private val WEIGHT = arrayOf(
            intArrayOf(120, -20, 20, 5, 5, 20, -20, 120),
            intArrayOf(-20, -40, -5, -5, -5, -5, -40, -20),
            intArrayOf(20, -5, 15, 3, 3, 15, -5, 20),
            intArrayOf(5, -5, 3, 3, 3, 3, -5, 5),
            intArrayOf(5, -5, 3, 3, 3, 3, -5, 5),
            intArrayOf(20, -5, 15, 3, 3, 15, -5, 20),
            intArrayOf(-20, -40, -5, -5, -5, -5, -40, -20),
            intArrayOf(120, -20, 20, 5, 5, 20, -20, 120)
        )
    }

    private val board = IntArray(N * N)
    private var curPlayer = BLACK
    private var over = false
    private var passCount = 0
    private var aiThinking = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D4F.toInt() }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A2E.toInt() }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF.toInt() }
    private val turnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w * 0.92f, h * 0.72f)
        cell = side / N
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused || aiThinking || curPlayer != BLACK) return
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (r !in 0 until N || c !in 0 until N) return
        val idx = r * N + c
        if (!isValid(idx, BLACK)) return
        board[idx] = BLACK
        flip(idx, BLACK)
        playSound(SoundManager.CLICK)
        aiTurn()
    }

    private fun aiTurn() {
        aiThinking = true
        Thread {
            val best = bestMove()
            post {
                aiThinking = false
                if (over) return@post
                if (best != null) {
                    board[best] = WHITE
                    flip(best, WHITE)
                    playSound(SoundManager.EAT)
                }
                nextTurn()
            }
        }.start()
    }

    private fun bestMove(): Int? {
        var bestScore = Int.MIN_VALUE
        var best = -1
        for (i in board.indices) {
            if (!isValid(i, WHITE)) continue
            val copy = board.copyOf()
            copy[i] = WHITE
            // 模拟翻转计分（简化：只加权重）
            var s = WEIGHT[i / N][i % N]
            if (s > bestScore) {
                bestScore = s
                best = i
            }
        }
        return if (best >= 0) best else null
    }

    private fun isValid(idx: Int, player: Int): Boolean {
        if (board[idx] != EMPTY) return false
        val r = idx / N
        val c = idx % N
        for ((dr, dc) in DIRS) {
            var nr = r + dr
            var nc = c + dc
            var seenOpp = false
            while (nr in 0 until N && nc in 0 until N) {
                val v = board[nr * N + nc]
                if (v == EMPTY) break
                if (v == player) {
                    if (seenOpp) return true
                    break
                }
                seenOpp = true
                nr += dr
                nc += dc
            }
        }
        return false
    }

    private fun flip(idx: Int, player: Int) {
        val r = idx / N
        val c = idx % N
        val opp = if (player == BLACK) WHITE else BLACK
        for ((dr, dc) in DIRS) {
            var nr = r + dr
            var nc = c + dc
            val toFlip = ArrayList<Int>()
            while (nr in 0 until N && nc in 0 until N) {
                val v = board[nr * N + nc]
                if (v == EMPTY) {
                    toFlip.clear()
                    break
                }
                if (v == player) break
                toFlip.add(nr * N + nc)
                nr += dr
                nc += dc
            }
            if (toFlip.isNotEmpty() && nr in 0 until N && nc in 0 until N && board[nr * N + nc] == player) {
                for (f in toFlip) board[f] = player
            }
        }
    }

    private fun nextTurn() {
        if (over) return
        val opp = 1 - curPlayer
        if (hasMove(opp)) {
            curPlayer = opp
            passCount = 0
        } else if (hasMove(curPlayer)) {
            passCount++
            playSound(SoundManager.MOVE)
        } else {
            over = true
            val b = board.count { it == BLACK }
            val w = board.count { it == WHITE }
            val msg = (if (b > w) "你赢了！" else if (w > b) "电脑获胜！" else "平局！") + "（黑 " + b + " : " + w + " 白）"
            playSound(if (b > w) SoundManager.WIN else SoundManager.LOSE)
            gameOver(GameResult(0, b > w, msg))
        }
    }

    private fun hasMove(player: Int): Boolean {
        for (i in board.indices) if (isValid(i, player)) return true
        return false
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until N) for (c in 0 until N) {
            val rect = RectF(
                boardLeft + c * cell, boardTop + r * cell,
                boardLeft + (c + 1) * cell, boardTop + (r + 1) * cell)
            canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), cellBgPaint)
            val v = board[r * N + c]
            if (v != EMPTY) {
                canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.38f, if (v == BLACK) blackPaint else whitePaint)
            } else if (curPlayer == BLACK && isValid(r * N + c, BLACK)) {
                canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.1f, hintPaint)
            }
        }
        val b = board.count { it == BLACK }
        val w = board.count { it == WHITE }
        turnPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText(
            (if (aiThinking) "🤖 电脑思考中…" else if (curPlayer == BLACK) "你（黑）落子" else "电脑（白）回合") + " · " + b + " : " + w,
            width / 2f, boardTop + cell * N + dp(34).toFloat(), turnPaint)
    }

    override fun reset() {
        for (i in board.indices) board[i] = EMPTY
        val m = N / 2
        board[(m - 1) * N + m - 1] = WHITE
        board[(m - 1) * N + m] = BLACK
        board[m * N + m - 1] = BLACK
        board[m * N + m] = WHITE
        curPlayer = BLACK
        over = false
        passCount = 0
        aiThinking = false
        start()
    }
}
