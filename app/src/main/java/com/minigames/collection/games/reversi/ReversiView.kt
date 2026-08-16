package com.minigames.collection.games.reversi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 黑白棋（奥赛罗）：8x8，双方夹住对方棋子即可翻转，无子可下时跳过，最终子多者胜。 */
class ReversiView(context: Context) : GameView(context) {

    companion object {
        private const val N = 8
        private const val EMPTY = 0
        private const val BLACK = 1
        private const val WHITE = 2
    }

    private val board = IntArray(N * N)
    private var curPlayer = BLACK
    private var over = false
    private var passCount = 0

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val validMoves = ArrayList<Int>()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D4F.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E5C3A.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A2E.toInt() }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF.toInt() }
    private val turnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFF8A93A6.toInt()
    }

    private val dirs = listOf(
        Pair(-1, -1), Pair(0, -1), Pair(1, -1),
        Pair(-1, 0), Pair(1, 0),
        Pair(-1, 1), Pair(0, 1), Pair(1, 1)
    )

    override fun onLayout(w: Int, h: Int) {
        val side = min(w * 0.92f, h * 0.66f)
        cell = side / N
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f + cell * 0.6f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (r !in 0 until N || c !in 0 until N) return
        val idx = r * N + c
        if (board[idx] != EMPTY || !isValid(idx, curPlayer)) return
        board[idx] = curPlayer
        flip(idx, curPlayer)
        playSound(SoundManager.CLICK)
        nextTurn()
    }

    private fun isValid(idx: Int, player: Int): Boolean {
        if (board[idx] != EMPTY) return false
        val r = idx / N
        val c = idx % N
        for ((dr, dc) in dirs) {
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
        for ((dr, dc) in dirs) {
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
        val opp = if (curPlayer == BLACK) WHITE else BLACK
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
            playSound(if (b > w) SoundManager.WIN else SoundManager.LOSE)
            val msg = if (b > w) "黑方获胜！" else if (w > b) "白方获胜！" else "平局！"
            gameOver(GameResult(0, b > w, msg + "（黑 " + b + " : " + w + " 白）"))
        }
    }

    private fun hasMove(player: Int): Boolean {
        for (i in board.indices) {
            if (isValid(i, player)) return true
        }
        return false
    }

    private fun computeValidMoves() {
        validMoves.clear()
        for (i in board.indices) {
            if (isValid(i, curPlayer)) validMoves.add(i)
        }
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        val name = if (curPlayer == BLACK) "● 黑方回合" else "○ 白方回合"
        canvas.drawText(name + "（" + board.count { it == BLACK } + " : " + board.count { it == WHITE } + "）", width / 2f, boardTop - cell * 0.5f, turnPaint)
        computeValidMoves()
        for (r in 0 until N) for (c in 0 until N) {
            val rect = android.graphics.RectF(
                boardLeft + c * cell, boardTop + r * cell,
                boardLeft + (c + 1) * cell, boardTop + (r + 1) * cell)
            canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), cellBgPaint)
            canvas.drawRect(rect, linePaint)
            val v = board[r * N + c]
            if (v != EMPTY) {
                canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.38f, if (v == BLACK) blackPaint else whitePaint)
                if (v == WHITE) {
                    canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFB9C0CE.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                    })
                }
            } else if (validMoves.contains(r * N + c)) {
                canvas.drawCircle(rect.centerX(), rect.centerY(), cell * 0.12f, hintPaint)
            }
        }
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
        start()
    }
}
