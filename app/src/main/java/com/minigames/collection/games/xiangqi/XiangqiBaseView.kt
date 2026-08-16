package com.minigames.collection.games.xiangqi

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
    private var topStripY = 0f
    private var bottomStripY = 0f
    /** 黑方（上方）/ 红方（下方）各自的悔棋按钮。 */
    protected val undoBtnBlack = RectF()
    protected val undoBtnRed = RectF()
    protected val resignBtn = RectF()

    /** 棋盘底板（木纹渐变，shader 在 onLayout 中赋值）。 */
    private val boardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boardFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF6B3F1D.toInt()
    }
    private val boardInnerFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF8A5A2B.toInt()
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7A4A1F.toInt()
    }
    private val riverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF7A4A1F.toInt()
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pieceShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000.toInt() }
    private val pieceRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x668A5A2B.toInt()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB02A2A.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF20242E.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val selGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33F5D547.toInt() }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5D547.toInt()
        style = Paint.Style.STROKE
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80E8EAF0.toInt() }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7A45.toInt()
        style = Paint.Style.STROKE
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = 0xFF8A93A6.toInt()
    }
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC2A3557.toInt() }
    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x406B8AFF.toInt()
    }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5252.toInt()
        style = Paint.Style.STROKE
    }
    private val lastMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val lastMarkSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66E8503A.toInt() }
    private val pieceColors = intArrayOf(0xFFFDF6E6.toInt(), 0xFFEFD9AC.toInt(), 0xFFD9B27B.toInt())
    private val pieceStops = floatArrayOf(0f, 0.55f, 1f)

    override fun onLayout(w: Int, h: Int) {
        topStripY = dp(112).toFloat()
        bottomStripY = h - topStripY
        // 棋盘竖向居中，并保证边缘棋子不被上方/下方信息条遮挡
        val maxSide = min(w * 0.9f, (h - 2 * topStripY - dp(32).toFloat()) / 1.0978f)
        cellX = maxSide / (XiangqiRules.COLS - 1)
        cellY = maxSide / (XiangqiRules.ROWS - 1)
        stoneR = min(cellX, cellY) * 0.44f
        boardLeft = (w - cellX * (XiangqiRules.COLS - 1)) / 2f
        boardTop = (h - cellY * (XiangqiRules.ROWS - 1)) / 2f
        // 黑方（上）与红方（下）各自的悔棋按钮，与棋盘左缘对齐
        val bw = dp(82).toFloat()
        val bh = dp(34).toFloat()
        undoBtnBlack.set(boardLeft, topStripY - bh / 2f, boardLeft + bw, topStripY + bh / 2f)
        undoBtnRed.set(boardLeft, bottomStripY - bh / 2f, boardLeft + bw, bottomStripY + bh / 2f)
        resignBtn.setEmpty()
        // 棋盘木纹渐变（暖色木质）
        boardBgPaint.shader = LinearGradient(
            boardLeft, boardTop, boardLeft + maxSide, boardTop + maxSide,
            intArrayOf(0xFFF6E4BD.toInt(), 0xFFEBD197.toInt(), 0xFFDBB477.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || gameOverFlag || paused) return
        val x = event.x
        val y = event.y
        if (undoBtnBlack.contains(x, y)) {
            requestUndo(XiangqiRules.BLACK)
            return
        }
        if (undoBtnRed.contains(x, y)) {
            requestUndo(XiangqiRules.RED)
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

    /** 玩家点击己方悔棋按钮（side 为请求方）。双人版在此弹出对方同意确认。 */
    protected open fun requestUndo(side: Int) {
        onUndoRequested()
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
        // 投影（向下柔化）
        canvas.drawCircle(cx + dp(2).toFloat(), cy + dp(3).toFloat(), stoneR, pieceShadow)
        // 奶油色棋子底（高光偏左上，径向渐变）
        piecePaint.shader = RadialGradient(
            cx - stoneR * 0.35f, cy - stoneR * 0.4f, stoneR * 1.25f,
            pieceColors, pieceStops, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, stoneR, piecePaint)
        // 内圈环线（传统棋子样式）
        pieceRimPaint.strokeWidth = dp(1).toFloat()
        canvas.drawCircle(cx, cy, stoneR * 0.8f, pieceRimPaint)
        // 外描边
        borderPaint.strokeWidth = dp(2).toFloat()
        borderPaint.color = if (isRed) 0xFFB3402F.toInt() else 0xFF2A2F3A.toInt()
        canvas.drawCircle(cx, cy, stoneR, borderPaint)
        // 汉字（衬线粗体 + 阴影）；黑方棋子字面朝上，与上方玩家对向
        val tp = if (isRed) redTextPaint else blackTextPaint
        tp.textSize = stoneR * 1.08f
        val facingUp = !isRed
        drawPieceText(canvas, pieceShadow, XiangqiRules.pieceName(p), cx + 1f, cy + 2f, facingUp)
        drawPieceText(canvas, tp, XiangqiRules.pieceName(p), cx, cy, facingUp)
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawColor(0xFF141A2E.toInt())
        // 木纹棋盘底板：圆角木板 + 双层描边
        val pad = dp(8).toFloat()
        val left = boardLeft - cellX * 0.5f - pad
        val top = boardTop - cellY * 0.5f - pad
        val right = boardLeft + (XiangqiRules.COLS - 1) * cellX + cellX * 0.5f + pad
        val bottom = boardTop + (XiangqiRules.ROWS - 1) * cellY + cellY * 0.5f + pad
        val radius = cellX * 0.28f
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, boardBgPaint)
        boardFramePaint.strokeWidth = dp(3).toFloat()
        canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, boardFramePaint)
        boardInnerFramePaint.strokeWidth = dp(1).toFloat()
        val inPad = dp(5).toFloat()
        canvas.drawRoundRect(
            RectF(left + inPad, top + inPad, right - inPad, bottom - inPad),
            radius * 0.8f, radius * 0.8f, boardInnerFramePaint)

        // 网格线（楚河汉界处纵向断开）
        linePaint.strokeWidth = dp(1).toFloat()
        for (r in 0 until XiangqiRules.ROWS) {
            canvas.drawLine(boardLeft, boardTop + r * cellY, boardLeft + (XiangqiRules.COLS - 1) * cellX, boardTop + r * cellY, linePaint)
        }
        for (c in 0 until XiangqiRules.COLS) {
            canvas.drawLine(boardLeft + c * cellX, boardTop, boardLeft + c * cellX, boardTop + 4 * cellY, linePaint)
            canvas.drawLine(boardLeft + c * cellX, boardTop + 5 * cellY, boardLeft + c * cellX, boardTop + 9 * cellY, linePaint)
        }
        // 九宫斜线
        canvas.drawLine(boardLeft + 3 * cellX, boardTop, boardLeft + 5 * cellX, boardTop + 2 * cellY, linePaint)
        canvas.drawLine(boardLeft + 5 * cellX, boardTop, boardLeft + 3 * cellX, boardTop + 2 * cellY, linePaint)
        canvas.drawLine(boardLeft + 3 * cellX, boardTop + 7 * cellY, boardLeft + 5 * cellX, boardTop + 9 * cellY, linePaint)
        canvas.drawLine(boardLeft + 5 * cellX, boardTop + 7 * cellY, boardLeft + 3 * cellX, boardTop + 9 * cellY, linePaint)
        // 楚河汉界
        riverPaint.textSize = cellY * 0.42f
        canvas.drawText("楚  河", boardLeft + 2 * cellX, boardTop + 4.5f * cellY - cellY * 0.12f, riverPaint)
        canvas.drawText("汉  界", boardLeft + 6 * cellX, boardTop + 4.5f * cellY - cellY * 0.12f, riverPaint)

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

        // 选中棋子：柔光 + 亮边
        if (selected >= 0) {
            canvas.drawCircle(px(selected), py(selected), stoneR + dp(8).toFloat(), selGlowPaint)
            selPaint.strokeWidth = dp(2).toFloat()
            canvas.drawCircle(px(selected), py(selected), stoneR + dp(3).toFloat(), selPaint)
        }
        // 合法走法提示（空位圆点 / 吃子红圈）
        capPaint.strokeWidth = dp(2).toFloat()
        for (t in legalTargets) {
            if (board[t] == XiangqiRules.EMPTY) {
                canvas.drawCircle(px(t), py(t), stoneR * 0.18f, hintPaint)
            } else {
                canvas.drawCircle(px(t), py(t), stoneR, capPaint)
            }
        }
        // 最后一步标记
        if (lastFrom >= 0) {
            canvas.drawCircle(px(lastFrom), py(lastFrom), cellX * 0.06f, lastMarkSoftPaint)
            canvas.drawCircle(px(lastTo), py(lastTo), cellX * 0.06f, lastMarkPaint)
        }
        // 将军警示（脉冲红圈）
        if (!gameOverFlag && XiangqiRules.isInCheck(board, turn)) {
            val king = if (turn == XiangqiRules.RED) XiangqiRules.R_KING else XiangqiRules.B_KING
            val ki = board.indexOfFirst { it == king }
            if (ki >= 0) {
                val pulse = (Math.sin(System.currentTimeMillis() / 280.0) * 0.5 + 0.5).toFloat()
                warnPaint.alpha = (90 + 165 * pulse).toInt()
                warnPaint.strokeWidth = dp(3).toFloat()
                canvas.drawCircle(px(ki), py(ki), stoneR + 4f, warnPaint)
            }
        }

        // 中央回合状态行（如"黑方回合 · 将军！"）
        statusPaint.textAlign = Paint.Align.CENTER
        statusPaint.color = 0xFFE8EAF0.toInt()
        statusPaint.textSize = dp(15).toFloat()
        canvas.drawText(getStatusLine(), width / 2f, boardTop - cellY * 0.6f, statusPaint)

        // 双方信息条：悔棋按钮 + 本侧吃子列表 + 侧名
        drawPlayerStrip(canvas, XiangqiRules.BLACK, topStripY, undoBtnBlack)
        drawPlayerStrip(canvas, XiangqiRules.RED, bottomStripY, undoBtnRed)
    }

    /** 玩家信息条：左侧悔棋按钮，中部本侧吃掉的对方棋子，右侧侧名与回合指示点。 */
    private fun drawPlayerStrip(canvas: Canvas, side: Int, stripY: Float, undoBtn: RectF) {
        // 悔棋按钮
        canvas.drawRoundRect(undoBtn, dp(13).toFloat(), dp(13).toFloat(), btnBgPaint)
        btnBorderPaint.strokeWidth = dp(1).toFloat()
        canvas.drawRoundRect(undoBtn, dp(13).toFloat(), dp(13).toFloat(), btnBorderPaint)
        canvas.drawText(
            "↶ 悔棋", undoBtn.centerX(),
            undoBtn.centerY() - (btnTextPaint.descent() + btnTextPaint.ascent()) / 2f, btnTextPaint)

        // 吃子列表（本侧吃掉的对方棋子，按吃子先后排列，居中于按钮与侧名之间）
        val captured = if (side == XiangqiRules.RED) capturedRed else capturedBlack
        val leftBound = undoBtn.right + dp(6).toFloat()
        val rightBound = boardLeft + (XiangqiRules.COLS - 1) * cellX - dp(64).toFloat()
        val availW = rightBound - leftBound
        if (availW > 0 && captured.isNotEmpty()) {
            val gap = minOf(cellY * 0.6f, availW / captured.size)
            val miniR = minOf(cellY * 0.26f, gap * 0.5f)
            val totalW = (captured.size - 1) * gap
            var sx = (leftBound + rightBound) / 2f - totalW / 2f
            for (i in captured.indices) {
                drawMini(canvas, captured[i], sx, stripY, miniR, facingUp = side == XiangqiRules.BLACK)
                sx += gap
            }
        }

        // 右侧侧名 + 回合指示点
        val isTurn = (turn == side) && !gameOverFlag
        val inCheck = !gameOverFlag && XiangqiRules.isInCheck(board, side)
        statusPaint.textAlign = Paint.Align.RIGHT
        statusPaint.textSize = dp(14).toFloat()
        statusPaint.color = when {
            inCheck -> 0xFFFF5252.toInt()
            isTurn -> 0xFFE8EAF0.toInt()
            else -> 0xFF8A93A6.toInt()
        }
        val label = XiangqiRules.sideName(side) + if (inCheck) " · 将军" else ""
        val tx = boardLeft + (XiangqiRules.COLS - 1) * cellX - dp(8).toFloat()
        canvas.drawText(label, tx, stripY - (statusPaint.descent() + statusPaint.ascent()) / 2f, statusPaint)
        if (isTurn) {
            val dotR = dp(4).toFloat()
            val dotX = tx - statusPaint.measureText(label) - dp(10).toFloat()
            canvas.drawCircle(dotX, stripY, dotR, statusPaint)
        }
    }

    private fun drawMini(canvas: Canvas, p: Int, cx: Float, cy: Float, r: Float, facingUp: Boolean) {
        val isRed = XiangqiRules.sideOf(p) == XiangqiRules.RED
        canvas.drawCircle(cx + dp(1).toFloat(), cy + dp(1).toFloat(), r, pieceShadow)
        piecePaint.shader = RadialGradient(cx, cy - r * 0.3f, r * 1.3f, pieceColors, pieceStops, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, piecePaint)
        pieceRimPaint.strokeWidth = dp(1).toFloat()
        canvas.drawCircle(cx, cy, r * 0.75f, pieceRimPaint)
        borderPaint.strokeWidth = dp(1).toFloat()
        borderPaint.color = if (isRed) 0xFFA93232.toInt() else 0xFF2A2F3A.toInt()
        canvas.drawCircle(cx, cy, r, borderPaint)
        val tp = if (isRed) redTextPaint else blackTextPaint
        tp.textSize = r * 1.35f
        drawPieceText(canvas, tp, XiangqiRules.pieceName(p), cx, cy, facingUp)
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