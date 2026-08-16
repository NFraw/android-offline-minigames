package com.minigames.collection.games.weiqi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/**
 * 围棋（15×15 简易规则）：
 * 落子提子、禁自杀；双方连续虚手（点击「虚手」按钮）后终局，
 * 按棋子数 + 围空判定胜负（不实现打劫与贴目）。
 */
class WeigiView(context: Context) : GameView(context) {

    companion object {
        private const val N = 15
        private const val EMPTY = 0
        private const val BLACK = 1
        private const val WHITE = 2
    }

    private val board = IntArray(N * N)
    private var curPlayer = BLACK
    private var passCount = 0
    private var lastMove = -1
    private var over = false
    private var capturedW = 0
    private var capturedB = 0

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var stoneR = 0f
    private val passBtn = RectF()

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
    private val turnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 41f
        color = 0xFFE8EAF0.toInt()
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w * 0.96f, h * 0.7f)
        cell = side / (N - 1)
        stoneR = cell * 0.44f
        boardLeft = (w - cell * (N - 1)) / 2f
        boardTop = (h - cell * (N - 1)) / 2f + cell * 0.9f
        val bw = cell * 4.2f
        val bh = dp(52).toFloat()
        passBtn.set(w / 2f - bw / 2f, boardTop + cell * (N - 1) + cell * 0.9f - bh / 2f, w / 2f + bw / 2f, boardTop + cell * (N - 1) + cell * 0.9f + bh / 2f)
        reset()
    }

    private fun neighbors(i: Int): List<Int> {
        val r = i / N
        val c = i % N
        val out = ArrayList<Int>()
        if (r > 0) out.add(i - N)
        if (r < N - 1) out.add(i + N)
        if (c > 0) out.add(i - 1)
        if (c < N - 1) out.add(i + 1)
        return out
    }

    private fun groupAt(idx: Int, player: Int): List<Int> {
        val group = ArrayList<Int>()
        val seen = BooleanArray(N * N)
        val stack = ArrayDeque<Int>()
        stack.add(idx)
        seen[idx] = true
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            group.add(cur)
            for (nb in neighbors(cur)) {
                if (!seen[nb] && board[nb] == player) {
                    seen[nb] = true
                    stack.add(nb)
                }
            }
        }
        return group
    }

    private fun groupHasLiberty(group: List<Int>): Boolean {
        for (i in group) {
            for (nb in neighbors(i)) {
                if (board[nb] == EMPTY) return true
            }
        }
        return false
    }

    /** 落子并处理提子；自杀禁着返回 false。 */
    private fun place(x: Int, y: Int): Boolean {
        val idx = y * N + x
        if (board[idx] != EMPTY) return false
        val opp = if (curPlayer == BLACK) WHITE else BLACK
        board[idx] = curPlayer
        var captured = 0
        for (nb in neighbors(idx)) {
            if (board[nb] == opp) {
                val g = groupAt(nb, opp)
                if (!groupHasLiberty(g)) {
                    for (gi in g) board[gi] = EMPTY
                    captured += g.size
                }
            }
        }
        if (captured == 0 && !groupHasLiberty(groupAt(idx, curPlayer))) {
            board[idx] = EMPTY
            return false
        }
        if (curPlayer == BLACK) capturedW += captured else capturedB += captured
        lastMove = idx
        passCount = 0
        playSound(SoundManager.CLICK)
        return true
    }

    private fun finish() {
        over = true
        // 数子：子数 + 围空
        var blackStones = 0
        var whiteStones = 0
        for (i in board.indices) {
            when (board[i]) {
                BLACK -> blackStones++
                WHITE -> whiteStones++
            }
        }
        val visited = BooleanArray(N * N)
        var blackTerr = 0
        var whiteTerr = 0
        for (i in board.indices) {
            if (board[i] != EMPTY || visited[i]) continue
            // flood fill 空区域
            val stack = ArrayDeque<Int>()
            stack.add(i)
            visited[i] = true
            var size = 0
            var touchesBlack = false
            var touchesWhite = false
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                size++
                for (nb in neighbors(cur)) {
                    when (board[nb]) {
                        BLACK -> touchesBlack = true
                        WHITE -> touchesWhite = true
                        EMPTY -> if (!visited[nb]) {
                            visited[nb] = true
                            stack.add(nb)
                        }
                    }
                }
            }
            if (touchesBlack && !touchesWhite) blackTerr += size
            else if (touchesWhite && !touchesBlack) whiteTerr += size
        }
        val bTotal = blackStones + blackTerr
        val wTotal = whiteStones + whiteTerr
        val msg = "黑 " + bTotal + " : " + wTotal + " 白（黑子 " + blackStones + " + 围空 " + blackTerr +
            "，白子 " + whiteStones + " + 围空 " + whiteTerr + "）"
        val blackWin = bTotal > wTotal
        playSound(if (blackWin) SoundManager.WIN else SoundManager.LOSE)
        gameOver(GameResult(0, blackWin, (if (blackWin) "黑方获胜！" else if (wTotal > bTotal) "白方获胜！" else "平局！") + msg))
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val x = event.x
        val y = event.y
        if (passBtn.contains(x, y)) {
            passCount++
            playSound(SoundManager.MOVE)
            if (passCount >= 2) {
                finish()
            } else {
                curPlayer = if (curPlayer == BLACK) WHITE else BLACK
            }
            return
        }
        val c = Math.round((x - boardLeft) / cell)
        val r = Math.round((y - boardTop) / cell)
        if (r !in 0 until N || c !in 0 until N) return
        val d = Math.hypot((x - (boardLeft + c * cell)).toDouble(), (y - (boardTop + r * cell)).toDouble())
        if (d > cell * 0.45) return
        if (place(c, r)) {
            curPlayer = if (curPlayer == BLACK) WHITE else BLACK
        } else {
            playSound(SoundManager.LOSE)
        }
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
        // 星位
        for (sx in intArrayOf(3, 7, 11)) for (sy in intArrayOf(3, 7, 11)) {
            canvas.drawCircle(boardLeft + sx * cell, boardTop + sy * cell, cell * 0.09f, linePaint)
        }
        // 棋子
        for (r in 0 until N) for (c in 0 until N) {
            val v = board[r * N + c]
            if (v == EMPTY) continue
            val cx = boardLeft + c * cell
            val cy = boardTop + r * cell
            if (v == BLACK) {
                canvas.drawCircle(cx, cy, stoneR, blackHiPaint)
                canvas.drawCircle(cx, cy, stoneR * 0.86f, blackPaint)
            } else {
                canvas.drawCircle(cx, cy, stoneR, whiteBorderPaint)
                canvas.drawCircle(cx, cy, stoneR * 0.95f, whitePaint)
            }
        }
        if (lastMove >= 0) {
            canvas.drawCircle(boardLeft + (lastMove % N) * cell, boardTop + (lastMove / N) * cell, cell * 0.12f, markPaint)
        }
        // 回合 + 提子信息
        turnPaint.color = if (curPlayer == BLACK) 0xFFE8EAF0.toInt() else 0xFFB9C0CE.toInt()
        val name = if (curPlayer == BLACK) "● 黑方落子" else "○ 白方落子"
        canvas.drawText(name, width / 2f, boardTop - cell * 1.6f, turnPaint)
        canvas.drawText("提子：黑提 " + capturedW + " · 白提 " + capturedB, width / 2f, boardTop - cell * 0.95f, infoPaint)
        // 虚手按钮
        canvas.drawRoundRect(passBtn, dp(14).toFloat(), dp(14).toFloat(), btnPaint)
        canvas.drawText("虚手", passBtn.centerX(), passBtn.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f, btnTextPaint)
    }

    override fun reset() {
        for (i in board.indices) board[i] = EMPTY
        curPlayer = BLACK
        passCount = 0
        lastMove = -1
        over = false
        capturedW = 0
        capturedB = 0
        start()
    }
}
