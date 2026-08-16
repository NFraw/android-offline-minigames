package com.minigames.collection.games.xiangqi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import com.minigames.collection.common.VoiceManager
import kotlin.math.min

/**
 * 象棋公共视图（打磨版）：渐变棋子、走子动画、悔棋、吃子列表、将军警示。
 */
abstract class XiangqiBaseView(context: Context) : GameView(context) {

    protected class HistoryEntry(val from: Int, val to: Int, val captured: Int)

    protected var board = XiangqiRules.initialBoard()
    protected var turn = XiangqiRules.RED
    protected var selected = -1
    protected var legalTargets = ArrayList<Int>()
    protected var lastFrom = -1
    protected var lastTo = -1
    protected var gameOverFlag = false
    protected var statusText = ""
    protected var aiThinking = false

    protected val history = ArrayList<HistoryEntry>()
    protected val capturedRed = ArrayList<Int>()
    protected val capturedBlack = ArrayList<Int>()

    // 走子动画
    private var animFrom = -1
    private var animTo = -1
    private var animProgress = 0f
    private var animating = false

    private var cellX = 0f
    private var cellY = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var stoneR = 0f
    protected val undoBtn = RectF()
    protected val resignBtn = RectF()

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8C68F.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A5A2B.toInt()
        strokeWidth = 2f
    }
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pieceShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC63B3B.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF232833.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5D547.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF.toInt() }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8503A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = 0xFFE8EAF0.toInt()
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88000000.toInt() }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
        color = 0xFFFFFFFF.toInt()
    }
    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5252.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val pieceColors = intArrayOf(0xFFFBF1DE.toInt(), 0xFFE8CDA4.toInt(), 0xFFCFA970.toInt())
    private val pieceStops = floatArrayOf(0f, 0.72f, 1f)

    override fun onLayout(w: Int, h: Int) {
        val side = min(w * 0.97f, h * 0.72f)
        cellX = side / (XiangqiRules.COLS - 1)
        cellY = side / (XiangqiRules.ROWS - 1)
        stoneR = min(cellX, cellY) * 0.43f
        boardLeft = (w - cellX * (XiangqiRules.COLS - 1)) / 2f
        boardTop = (h - cellY * (XiangqiRules.ROWS - 1)) / 2f + cellY * 0.6f
        val bw = dp(92).toFloat()
        val bh = dp(46).toFloat()
        undoBtn.set(boardLeft, boardTop - cellY * 2.3f - bh / 2f, boardLeft + bw, boardTop - cellY * 2.3f + bh / 2f)
        resignBtn.set(boardLeft + cellX * (XiangqiRules.COLS - 1) - bw, undoBtn.top, boardLeft + cellX * (XiangqiRules.COLS - 1), undoBtn.bottom)
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || gameOverFlag || paused) return
        val x = event.x
        val y = event.y
        if (undoBtn.contains(x, y)) {
            onUndoRequested()
            return
        }
        if (resignBtn.width() > 0 && resignBtn.contains(x, y)) {
            onResignRequested()
            return
        }
        val c = Math.round((x - boardLeft) / cellX)
        val r = Math.round((y - boardTop) / cellY)
        if (r !in 0 until XiangqiRules.ROWS || c !in 0 until XiangqiRules.COLS) return
        val dx = x - (boardLeft + c * cellX)
        val dy = y - (boardTop + r * cellY)
        if (Math.hypot(dx.toDouble(), dy.toDouble()) > min(cellX, cellY) * 0.5) return
        val idx = r * XiangqiRules.COLS + c
        onIntersectionTap(idx)
    }

    protected abstract fun onIntersectionTap(idx: Int)

    protected fun isMyPiece(idx: Int): Boolean {
        val p = board[idx]
        return p != XiangqiRules.EMPTY && XiangqiRules.sideOf(p) == turn
    }

    protected fun doMove(move: XiangqiRules.Move) {
        val captured = board[move.to]
        history.add(HistoryEntry(move.from, move.to, captured))
        if (captured != XiangqiRules.EMPTY) {
            if (turn == XiangqiRules.RED) capturedRed.add(captured) else capturedBlack.add(captured)
            playSound(SoundManager.EAT)
            buzz()
            speakVoice(VoiceManager.CHI)
        } else {
            playSound(SoundManager.MOVE)
        }
        board[move.to] = board[move.from]
        board[move.from] = XiangqiRules.EMPTY
        lastFrom = move.from
        lastTo = move.to
        selected = -1
        legalTargets.clear()
        animFrom = move.from
        animTo = move.to
        animProgress = 0f
        animating = true
        onMoveCompleted(move)
    }

    /** 撤销一步（双人版悔棋）。 */
    protected fun undo() {
        if (history.isEmpty() || gameOverFlag || animating) return
        val e = history.removeLast()
        board[e.from] = board[e.to]
        board[e.to] = e.captured
        if (e.captured != XiangqiRules.EMPTY) {
            if (XiangqiRules.sideOf(e.captured) == XiangqiRules.BLACK) {
                if (capturedRed.isNotEmpty()) capturedRed.removeAt(capturedRed.size - 1)
            } else {
                if (capturedBlack.isNotEmpty()) capturedBlack.removeAt(capturedBlack.size - 1)
            }
        }
        lastFrom = e.from
        lastTo = e.to
        selected = -1
        legalTargets.clear()
        statusText = ""
        turn = XiangqiRules.sideOf(board[e.from])
        aiThinking = false
        updateStatus()
        playSound(SoundManager.CLICK)
        speakVoice(VoiceManager.HUIQI)
        invalidate()
    }

    /** 撤销到指定一方回合（人机版：撤到玩家回合）。 */
    protected fun undoUntil(side: Int) {
        while (history.isNotEmpty() && !gameOverFlag) {
            val last = history.last()
            val mover = XiangqiRules.sideOf(board[last.from])
            undo()
            if (mover == side) break
        }
        turn = side
        aiThinking = false
        updateStatus()
    }

    protected open fun onUndoRequested() {
        undo()
    }

    protected open fun onResignRequested() {
        gameOverFlag = true
        playSound(SoundManager.LOSE)
        speakVoice(VoiceManager.RENSHU)
        gameOver(GameResult(0, false, "你认输了，" + XiangqiRules.sideName(turn) + "放弃本局"))
    }

    protected abstract fun onMoveCompleted(move: XiangqiRules.Move)

    protected fun checkGameOver(side: Int): Boolean {
        if (!XiangqiRules.hasAnyMove(board, side)) {
            gameOverFlag = true
            val winner = 1 - side
            val reason = if (XiangqiRules.isInCheck(board, side)) "被将死" else "无子可走"
            playSound(SoundManager.WIN)
            if (XiangqiRules.isInCheck(board, side)) speakVoice(VoiceManager.JIANGSI)
            gameOver(GameResult(0, true, XiangqiRules.sideName(winner) + "获胜！" + XiangqiRules.sideName(side) + reason))
            return true
        }
        return false
    }

    /** 换手后若对方被将军则播报"将军"。 */
    protected fun announceCheckIfAny() {
        if (!gameOverFlag && XiangqiRules.isInCheck(board, turn)) {
            speakVoice(VoiceManager.JIANGJUN)
        }
    }

    protected fun updateStatus() {
        statusText = if (XiangqiRules.isInCheck(board, turn)) "将军！" else ""
    }

    override fun update(dt: Float) {
        if (animating) {
            animProgress += dt / 0.14f
            if (animProgress >= 1f) {
                animProgress = 1f
                animating = false
            }
            invalidate()
        }
    }

    private fun px(i: Int): Float = boardLeft + (i % XiangqiRules.COLS) * cellX
    private fun py(i: Int): Float = boardTop + (i / XiangqiRules.COLS) * cellY

    private fun drawPieceAt(canvas: Canvas, p: Int, cx: Float, cy: Float) {
        val isRed = XiangqiRules.sideOf(p) == XiangqiRules.RED
        // 阴影
        canvas.drawCircle(cx + dp(2).toFloat(), cy + dp(2).toFloat(), stoneR, pieceShadow)
        // 渐变底
        canvas.save()
        canvas.translate(cx - stoneR, cy - stoneR)
        val shader = RadialGradient(stoneR, stoneR * 0.3f, stoneR * 1.35f, pieceColors, pieceStops, Shader.TileMode.CLAMP)
        piecePaint.shader = shader
        canvas.drawCircle(stoneR, stoneR, stoneR, piecePaint)
        canvas.restore()
        // 描边
        borderPaint.color = if (isRed) 0xFFA93232.toInt() else 0xFF2A2F3A.toInt()
        canvas.drawCircle(cx, cy, stoneR, borderPaint)
        // 汉字（带一点阴影增强立体感）
        val tp = if (isRed) redTextPaint else blackTextPaint
        tp.textSize = stoneR * 1.12f
        canvas.drawText(XiangqiRules.pieceName(p), cx + 1f, cy + 1f - (tp.descent() + tp.ascent()) / 2f, pieceShadow)
        canvas.drawText(XiangqiRules.pieceName(p), cx, cy - (tp.descent() + tp.ascent()) / 2f, tp)
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawColor(0xFF141A2E.toInt())
        val left = boardLeft - cellX * 0.5f
        val top = boardTop - cellY * 0.5f
        val right = boardLeft + (XiangqiRules.COLS - 1) * cellX + cellX * 0.5f
        val bottom = boardTop + (XiangqiRules.ROWS - 1) * cellY + cellY * 0.5f
        canvas.drawRoundRect(RectF(left, top, right, bottom), cellX * 0.35f, cellX * 0.35f, boardPaint)
        for (r in 0 until XiangqiRules.ROWS) {
            canvas.drawLine(boardLeft, boardTop + r * cellY, boardLeft + (XiangqiRules.COLS - 1) * cellX, boardTop + r * cellY, linePaint)
        }
        for (c in 0 until XiangqiRules.COLS) {
            canvas.drawLine(boardLeft + c * cellX, boardTop, boardLeft + c * cellX, boardTop + 4 * cellY, linePaint)
            canvas.drawLine(boardLeft + c * cellX, boardTop + 5 * cellY, boardLeft + c * cellX, boardTop + 9 * cellY, linePaint)
        }
        canvas.drawLine(boardLeft + 3 * cellX, boardTop, boardLeft + 5 * cellX, boardTop + 2 * cellY, linePaint)
        canvas.drawLine(boardLeft + 5 * cellX, boardTop, boardLeft + 3 * cellX, boardTop + 2 * cellY, linePaint)
        canvas.drawLine(boardLeft + 3 * cellX, boardTop + 7 * cellY, boardLeft + 5 * cellX, boardTop + 9 * cellY, linePaint)
        canvas.drawLine(boardLeft + 5 * cellX, boardTop + 7 * cellY, boardLeft + 3 * cellX, boardTop + 9 * cellY, linePaint)
        linePaint.textAlign = Paint.Align.CENTER
        linePaint.textSize = cellY * 0.4f
        canvas.drawText("楚  河", boardLeft + 2 * cellX, boardTop + 4.5f * cellY - cellY * 0.1f, linePaint)
        canvas.drawText("汉  界", boardLeft + 6 * cellX, boardTop + 4.5f * cellY - cellY * 0.1f, linePaint)

        // 棋子
        for (r in 0 until XiangqiRules.ROWS) for (c in 0 until XiangqiRules.COLS) {
            val idx = r * XiangqiRules.COLS + c
            val p = board[idx]
            if (p == XiangqiRules.EMPTY) continue
            if (animating && idx == animTo) continue
            drawPieceAt(canvas, p, px(idx), py(idx))
        }
        // 动画中的棋子
        if (animating && animTo >= 0) {
            val p = board[animTo]
            if (p != XiangqiRules.EMPTY) {
                val t = animProgress * animProgress * (3f - 2f * animProgress)
                val ax = px(animFrom) + (px(animTo) - px(animFrom)) * t
                val ay = py(animFrom) + (py(animTo) - py(animFrom)) * t
                drawPieceAt(canvas, p, ax, ay)
            }
        }

        // 选中高亮 + 合法走法
        if (selected >= 0) {
            canvas.drawCircle(px(selected), py(selected), stoneR + dp(3).toFloat(), selPaint)
        }
        for (t in legalTargets) {
            if (board[t] == XiangqiRules.EMPTY) {
                canvas.drawCircle(px(t), py(t), stoneR * 0.2f, hintPaint)
            } else {
                canvas.drawCircle(px(t), py(t), stoneR, capPaint)
            }
        }
        // 最后一步标记
        if (lastFrom >= 0) {
            canvas.drawCircle(px(lastFrom), py(lastFrom), cellX * 0.07f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66E8503A.toInt() })
            canvas.drawCircle(px(lastTo), py(lastTo), cellX * 0.07f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() })
        }
        // 将军警示（脉冲红圈）
        if (!gameOverFlag && XiangqiRules.isInCheck(board, turn)) {
            val king = if (turn == XiangqiRules.RED) XiangqiRules.R_KING else XiangqiRules.B_KING
            val ki = board.indexOfFirst { it == king }
            if (ki >= 0) {
                val pulse = (Math.sin(System.currentTimeMillis() / 280.0) * 0.5 + 0.5).toFloat()
                warnPaint.alpha = (90 + 165 * pulse).toInt()
                canvas.drawCircle(px(ki), py(ki), stoneR + 4f, warnPaint)
            }
        }

        // 状态文字
        statusPaint.textSize = 26f
        canvas.drawText(statusText, width / 2f, boardTop - cellY * 1.45f, statusPaint)
        statusPaint.textSize = 22f
        statusPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText(getStatusLine(), width / 2f, boardTop - cellY * 0.95f, statusPaint)
        statusPaint.color = 0xFFE8EAF0.toInt()

        // 吃子列表（顶部一行小棋子：左黑右红）
        val miniR = cellY * 0.26f
        val gap = cellY * 0.62f
        val baseY = boardTop - cellY * 0.55f
        val totalW = maxOf(capturedRed.size, capturedBlack.size).toFloat() * gap
        var sx = width / 2f - totalW / 2f + miniR
        for (i in capturedRed.indices) {
            drawMini(canvas, capturedRed[i], sx + i * gap, baseY, miniR)
        }
        for (i in capturedBlack.indices) {
            drawMini(canvas, capturedBlack[i], sx + i * gap, baseY, miniR)
        }

        // 悔棋 / 认输按钮
        canvas.drawRoundRect(undoBtn, dp(12).toFloat(), dp(12).toFloat(), btnPaint)
        canvas.drawText("↶ 悔棋", undoBtn.centerX(), undoBtn.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f, btnTextPaint)
        if (showResignButton()) {
            canvas.drawRoundRect(resignBtn, dp(12).toFloat(), dp(12).toFloat(), btnPaint)
            canvas.drawText("认输", resignBtn.centerX(), resignBtn.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f, btnTextPaint)
        }
    }

    private fun drawMini(canvas: Canvas, p: Int, cx: Float, cy: Float, r: Float) {
        val isRed = XiangqiRules.sideOf(p) == XiangqiRules.RED
        canvas.drawCircle(cx, cy, r, pieceShadow)
        val shader = RadialGradient(cx, cy - r * 0.3f, r * 1.3f, pieceColors, pieceStops, Shader.TileMode.CLAMP)
        piecePaint.shader = shader
        canvas.drawCircle(cx, cy, r, piecePaint)
        borderPaint.color = if (isRed) 0xFFA93232.toInt() else 0xFF2A2F3A.toInt()
        canvas.drawCircle(cx, cy, r, borderPaint)
        val tp = if (isRed) redTextPaint else blackTextPaint
        tp.textSize = r * 1.5f
        canvas.drawText(XiangqiRules.pieceName(p), cx, cy - (tp.descent() + tp.ascent()) / 2f, tp)
    }

    protected open fun showResignButton(): Boolean = false

    protected abstract fun getStatusLine(): String

    override fun reset() {
        board = XiangqiRules.initialBoard()
        speakVoice(VoiceManager.START)
        turn = XiangqiRules.RED
        selected = -1
        legalTargets.clear()
        lastFrom = -1
        lastTo = -1
        gameOverFlag = false
        statusText = ""
        aiThinking = false
        history.clear()
        capturedRed.clear()
        capturedBlack.clear()
        animFrom = -1
        animTo = -1
        animProgress = 0f
        animating = false
        start()
    }
}