package com.minigames.collection.games.gomoku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 五子棋（人机）：玩家执黑先手，AI 评估攻防后落子。 */
class GomokuAiView(context: Context) : GameView(context) {

    companion object {
        private const val N = 15
        private const val EMPTY = 0
        private const val BLACK = 1
        private const val WHITE = 2
        private val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))
    }

    private val board = IntArray(N * N)
    private var curPlayer = BLACK
    private var lastX = -1
    private var lastY = -1
    private var over = false
    private var aiThinking = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var stoneR = 0f

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0B577.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A5A2B.toInt()
        strokeWidth = 2f
    }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2333.toInt() }
    private val blackHiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A5568.toInt() }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB9C0CE.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
        color = 0xFF8A93A6.toInt()
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = 0xFFE8EAF0.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w * 0.96f, h * 0.72f)
        cell = side / (N - 1)
        stoneR = cell * 0.42f
        boardLeft = (w - cell * (N - 1)) / 2f
        boardTop = (h - cell * (N - 1)) / 2f + cell * 0.7f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused || aiThinking || curPlayer != BLACK) return
        val c = Math.round((event.x - boardLeft) / cell)
        val r = Math.round((event.y - boardTop) / cell)
        if (r !in 0 until N || c !in 0 until N) return
        val d = Math.hypot((event.x - (boardLeft + c * cell)).toDouble(), (event.y - (boardTop + r * cell)).toDouble())
        if (d > cell * 0.45) return
        if (board[r * N + c] != EMPTY) return
        board[r * N + c] = BLACK
        lastX = c
        lastY = r
        playSound(SoundManager.CLICK)
        if (checkWin(c, r, BLACK)) {
            finish("你赢了！黑方五连")
            return
        }
        aiThinking = true
        Thread {
            val best = bestMove()
            post {
                aiThinking = false
                if (over) return@post
                if (best != null) {
                    board[best] = WHITE
                    lastX = best % N
                    lastY = best / N
                    playSound(SoundManager.EAT)
                    if (checkWin(lastX, lastY, WHITE)) {
                        finish("电脑获胜！白方五连")
                    }
                }
            }
        }.start()
        invalidate()
    }

    private fun finish(msg: String) {
        if (over) return
        over = true
        playSound(SoundManager.WIN)
        gameOver(GameResult(0, true, msg))
    }

    private fun checkWin(x: Int, y: Int, player: Int): Boolean {
        for (dir in DIRS) {
            var count = 1
            for (s in intArrayOf(1, -1)) {
                var i = 1
                while (true) {
                    val nx = x + dir[0] * i * s
                    val ny = y + dir[1] * i * s
                    if (nx in 0 until N && ny in 0 until N && board[ny * N + nx] == player) count++ else break
                    i++
                }
            }
            if (count >= 5) return true
        }
        return false
    }

    /** 评估：对每个空位打分（进攻 + 防守），取最高。 */
    private fun bestMove(): Int? {
        var bestScore = -1
        var best = -1
        for (i in board.indices) {
            if (board[i] != EMPTY) continue
            var score = 0
            for (player in intArrayOf(WHITE, BLACK)) {
                board[i] = player
                val s = evaluateAt(i % N, i / N, player)
                board[i] = EMPTY
                score += if (player == WHITE) s * 2 else s
            }
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return if (best >= 0) best else null
    }

    private fun evaluateAt(x: Int, y: Int, player: Int): Int {
        var total = 0
        for (dir in DIRS) {
            var count = 1
            var openEnds = 0
            for (s in intArrayOf(1, -1)) {
                var i = 1
                while (true) {
                    val nx = x + dir[0] * i * s
                    val ny = y + dir[1] * i * s
                    if (nx !in 0 until N || ny !in 0 until N) break
                    val v = board[ny * N + nx]
                    if (v == player) {
                        count++
                        i++
                    } else if (v == EMPTY) {
                        openEnds++
                        break
                    } else {
                        break
                    }
                }
            }
            total += when {
                count >= 5 -> 100000
                count == 4 && openEnds == 2 -> 10000
                count == 4 && openEnds == 1 -> 5000
                count == 3 && openEnds == 2 -> 1000
                count == 3 && openEnds == 1 -> 300
                count == 2 && openEnds == 2 -> 100
                count == 2 && openEnds == 1 -> 30
                else -> 0
            }
        }
        return total
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawColor(0xFF141A2E.toInt())
        val left = boardLeft - cell * 0.5f
        val top = boardTop - cell * 0.5f
        val right = boardLeft + (N - 1) * cell + cell * 0.5f
        val bottom = boardTop + (N - 1) * cell + cell * 0.5f
        canvas.drawRoundRect(RectF(left, top, right, bottom), cell * 0.4f, cell * 0.4f, boardPaint)
        for (i in 0 until N) {
            canvas.drawLine(boardLeft, boardTop + i * cell, boardLeft + (N - 1) * cell, boardTop + i * cell, linePaint)
            canvas.drawLine(boardLeft + i * cell, boardTop, boardLeft + i * cell, boardTop + (N - 1) * cell, linePaint)
        }
        for (r in 0 until N) for (c in 0 until N) {
            val v = board[r * N + c]
            if (v == EMPTY) continue
            val cx = boardLeft + c * cell
            val cy = boardTop + r * cell
            if (v == BLACK) {
                canvas.drawCircle(cx, cy, stoneR, blackHiPaint)
                canvas.drawCircle(cx, cy, stoneR * 0.85f, blackPaint)
            } else {
                canvas.drawCircle(cx, cy, stoneR, whiteBorderPaint)
                canvas.drawCircle(cx, cy, stoneR * 0.95f, whitePaint)
            }
        }
        if (lastX >= 0) {
            canvas.drawCircle(boardLeft + lastX * cell, boardTop + lastY * cell, cell * 0.13f, markPaint)
        }
        statusPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText(if (aiThinking) "🤖 电脑思考中…" else "你（黑）落子", width / 2f, boardTop - cell * 1.4f, statusPaint)
        hintPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("黑方先手，先连成五子者胜", width / 2f, boardTop - cell * 0.7f, hintPaint)
    }

    override fun reset() {
        for (i in board.indices) board[i] = EMPTY
        curPlayer = BLACK
        lastX = -1
        lastY = -1
        over = false
        aiThinking = false
        start()
    }
}
