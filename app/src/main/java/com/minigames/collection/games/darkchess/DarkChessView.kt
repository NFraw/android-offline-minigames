package com.minigames.collection.games.darkchess

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 暗棋：32 子随机暗置，翻开后可走相邻格吃子，吃光对方或对方无子可动获胜。 */
class DarkChessView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 4
        private const val ROWS = 8
        // 标准暗棋配子：每方 16 子 = 帅(将)1 + 仕(士)2 + 相(象)2 + 车2 + 马2 + 炮2 + 兵(卒)5
        private val RED_PIECES = intArrayOf(
            11,
            12, 12,
            13, 13,
            14, 14,
            15, 15,
            16, 16,
            17, 17, 17, 17, 17
        )
        private val NAMES = mapOf(
            11 to "帅", 12 to "仕", 13 to "相", 14 to "车", 15 to "马", 16 to "炮", 17 to "兵",
            -11 to "将", -12 to "士", -13 to "象", -14 to "车", -15 to "马", -16 to "炮", -17 to "卒"
        )
    }

    private val board = IntArray(COLS * ROWS)
    private val revealed = BooleanArray(COLS * ROWS)
    private var selected = -1
    private var turn = 0
    private var over = false
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2333.toInt() }
    private val pieceBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8C68F.toInt() }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5D547.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        cell = kotlin.math.min(w / COLS.toFloat(), h / ROWS.toFloat()) * 0.96f
        boardLeft = (w - cell * COLS) / 2f
        boardTop = (h - cell * ROWS) / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (r !in 0 until ROWS || c !in 0 until COLS) return
        val idx = r * COLS + c
        if (!revealed[idx]) {
            // 翻开
            revealed[idx] = true
            playSound(SoundManager.CLICK)
            selected = -1
            checkEnd()
            if (!over) turn = 1 - turn
            if (!over) checkStalemate()
            invalidate()
            return
        }
        val p = board[idx]
        if (p == 0) return
        if (selected < 0) {
            if (side(p) == turn) {
                selected = idx
                playSound(SoundManager.CLICK)
            }
            return
        }
        if (idx == selected) {
            selected = -1
            return
        }
        val sp = board[selected]
        if (side(sp) == turn && side(p) == turn) {
            selected = idx
            playSound(SoundManager.CLICK)
            return
        }
        // 移动/吃子
        val sx = selected % COLS
        val sy = selected / COLS
        if (abs(c - sx) + abs(r - sy) == 1) {
            if (p == 0 || canCapture(sp, p)) {
                board[idx] = sp
                board[selected] = 0
                revealed[idx] = true
                selected = -1
                playSound(if (p != 0) SoundManager.EAT else SoundManager.MOVE)
                checkEnd()
                if (!over) turn = 1 - turn
                if (!over) checkStalemate()
            } else {
                selected = -1
                playSound(SoundManager.LOSE)
            }
            invalidate()
        }
    }

    private fun side(p: Int): Int = if (p > 0) 0 else 1

    private fun rank(v: Int): Int {
        val a = if (v > 0) v else -v
        return when (a) {
            11 -> 7
            12 -> 6
            13 -> 5
            14 -> 4
            15 -> 3
            16 -> 2
            else -> 1
        }
    }

    private fun canCapture(attacker: Int, target: Int): Boolean {
        val ar = rank(attacker)
        val tr = rank(target)
        // 兵/卒吃帅/将
        if (ar == 1 && tr == 7) return true
        // 帅/将不能吃兵/卒
        if (ar == 7 && tr == 1) return false
        return ar >= tr
    }

    /** 吃光判定：统计棋盘上所有存活子（含未翻开的暗子），任一方为 0 即对方吃光获胜。 */
    private fun checkEnd() {
        var redCount = 0
        var blackCount = 0
        for (i in board.indices) {
            if (board[i] > 0) redCount++
            else if (board[i] < 0) blackCount++
        }
        if (redCount == 0 || blackCount == 0) {
            over = true
            val redWin = redCount > 0
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, redWin, (if (redWin) "红方" else "黑方") + "吃光对方获胜！"))
        }
    }

    /** 当前回合方若既无暗子可翻、又无明子可走，则判负。 */
    private fun checkStalemate() {
        if (hasAnyMove(turn)) return
        over = true
        val winner = 1 - turn
        playSound(SoundManager.WIN)
        gameOver(GameResult(0, winner == 0, (if (winner == 0) "红方" else "黑方") + "获胜！" + (if (turn == 0) "红方" else "黑方") + "无子可动"))
    }

    /** 某方是否还能走棋：有暗子可翻，或有明子可走到相邻空格/可吃子。 */
    private fun hasAnyMove(side: Int): Boolean {
        for (i in board.indices) {
            if (!revealed[i]) return true
        }
        for (i in board.indices) {
            if (!revealed[i] || board[i] == 0 || side(board[i]) != side) continue
            val x = i % COLS
            val y = i / COLS
            if (x > 0 && canMoveFrom(i, i - 1)) return true
            if (x < COLS - 1 && canMoveFrom(i, i + 1)) return true
            if (y > 0 && canMoveFrom(i, i - COLS)) return true
            if (y < ROWS - 1 && canMoveFrom(i, i + COLS)) return true
        }
        return false
    }

    private fun canMoveFrom(from: Int, to: Int): Boolean {
        val t = board[to]
        return t == 0 || (revealed[to] && side(t) != side(board[from]) && canCapture(board[from], t))
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val idx = r * COLS + c
            val x = boardLeft + c * cell
            val y = boardTop + r * cell
            val rect = RectF(x + 1f, y + 1f, x + cell - 1f, y + cell - 1f)
            if (!revealed[idx]) {
                canvas.drawRoundRect(rect, dp(6).toFloat(), dp(6).toFloat(), cellPaint)
            } else {
                val p = board[idx]
                canvas.drawRoundRect(rect, dp(6).toFloat(), dp(6).toFloat(), pieceBgPaint)
                if (p != 0) {
                    textPaint.color = if (p > 0) redPaint.color else blackPaint.color
                    textPaint.textSize = cell * 0.5f
                    drawPieceText(canvas, textPaint, NAMES[p] ?: "", x + cell / 2f, y + cell / 2f, p < 0)
                }
            }
            if (idx == selected) {
                canvas.drawRoundRect(rect, dp(6).toFloat(), dp(6).toFloat(), selPaint)
            }
        }
        // 顶部回合横幅：明确显示当前轮到谁
        val bannerW = dp(150).toFloat()
        val bannerH = dp(48).toFloat()
        val bannerY = dp(30).toFloat()
        val isRed = turn == 0
        roundRect(canvas, RectF(width / 2f - bannerW / 2f, bannerY, width / 2f + bannerW / 2f, bannerY + bannerH),
            if (isRed) 0xFFE8503A.toInt() else 0xFF1C2333.toInt(), 24)
        textPaint.color = 0xFFFFFFFF.toInt()
        textPaint.textSize = sp(26f)
        textPaint.isFakeBoldText = true
        canvas.drawText(if (isRed) "红方回合" else "黑方回合", width / 2f, centerBaseline(bannerY + bannerH / 2f, textPaint), textPaint)
        textPaint.isFakeBoldText = false
        // 底部提示：拆成两行，避免超长单行被裁掉
        textPaint.color = 0xFF8A93A6.toInt()
        textPaint.textSize = sp(17f)
        val hintY = boardTop + cell * ROWS + dp(24).toFloat()
        canvas.drawText("点击翻开暗子，或走相邻格吃子", width / 2f, hintY, textPaint)
        canvas.drawText("帅将>仕士>相象>车>马>炮>兵卒 · 兵卒吃帅将", width / 2f, hintY + dp(32).toFloat(), textPaint)
    }

    override fun reset() {
        val all = ArrayList<Int>()
        for (v in RED_PIECES) {
            all.add(v)
            all.add(-v)
        }
        all.shuffle()
        for (i in board.indices) {
            board[i] = all[i]
            revealed[i] = false
        }
        selected = -1
        turn = 0
        over = false
        start()
    }

    private fun abs(v: Int): Int = if (v < 0) -v else v
}