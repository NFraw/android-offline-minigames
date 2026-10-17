package com.minigames.collection.games.xiangqi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.minigames.collection.common.BoardArt
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import com.minigames.collection.common.VoiceManager
import kotlin.math.min

/**
 * 象棋公共视图（打磨版）：渐变棋子、走子动画、悔棋、吃子列表、将军警示。
 *
 * 【美术说明】木板、网格线、棋子、信息条全部收敛到 BoardArt（扁平柔和卡通风）：
 *   - 棋盘：art.buildWoodSlab() + drawWoodSlab()，替代原先手写的 LinearGradient + 深棕硬描边；
 *   - 棋子：art.drawCreamPiece()，软投影 + 奶油球面渐变 + 内圈细环 + 暖色外描边（选中加金环）；
 *   - 网格/斜线：art.drawGridLine()；悔棋按钮：art.drawSoftButton()；吃子小棋子同样走 drawCreamPiece()。
 * 布局同时让出了悬浮控件安全区 dp(SAFE_TOP_DP)，上方信息条不再被顶部按钮压住。
 */
abstract class XiangqiBaseView(context: Context) : GameView(context) {

    protected class HistoryEntry(val from: Int, val to: Int, val captured: Int)

    companion object {
        /** 每方每局最多悔棋次数。 */
        const val UNDO_LIMIT = 3
    }

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
    /** 每方每局可用悔棋次数（默认 3 次）。 */
    protected val undoLeft = IntArray(2) { UNDO_LIMIT }

    // 走子动画
    private var animFrom = -1
    private var animTo = -1
    private var animProgress = 0f
    private var animating = false

    /** 横向格宽（onLayout 计算）。 */
    private var cellX = 0f
    private var cellY = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var stoneR = 0f
    /** 上方（黑方）信息条中心 y。 */
    private var topStripY = 0f
    /** 下方（红方）信息条中心 y。 */
    private var bottomStripY = 0f
    /** 黑方（上方）/ 红方（下方）各自的悔棋按钮。 */
    protected val undoBtnBlack = RectF()
    protected val undoBtnRed = RectF()
    protected val resignBtn = RectF()

    /** 美术工具箱：统一木板 / 奶油棋子 / 柔和按钮。 */
    private val art = BoardArt(this)
    /** 棋盘木板矩形（比交叉点各外扩半格 + 内边距），onLayout 中计算。 */
    private val slabRect = RectF()
    /** 棋盘木板尺寸变化时重建木纹渐变（避免每帧 new Shader）。 */
    private var lastSlabW = 0f
    private var lastSlabH = 0f

    /** 楚河汉界的文字（暖棕衬线，压在木板上）。 */
    private val riverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = BoardArt.Palette.WOOD_MARK
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    /** 红方棋子文字（偏红）。 */
    private val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB02A2A.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    /** 黑方棋子文字（偏深）。 */
    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF20242E.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
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

    /** 背景色（与全库统一背景 Palette.BG 一致）。 */
    private val bgPaint = Paint().apply { color = BoardArt.Palette.BG }

    override fun onLayout(w: Int, h: Int) {
        // 顶部让出悬浮控件安全区（100dp）：信息条与棋盘都从安全区之下开始。
        // 再额外下移 40dp：上下两条信息条都带「悔棋」按钮，而悬浮控件（退出/暂停/重开/帮助）
        // 占着四角，信息条贴到安全区边缘时右上角悔棋按钮会被「?」帮助按钮压住、点不到。
        val topPad = dp(SAFE_TOP_DP).toFloat()
        topStripY = topPad + dp(64).toFloat()
        bottomStripY = h - dp(96).toFloat()
        // 棋盘可用的竖直区间：上信息条之下 → 下信息条之上。
        // 注意不能再按整屏高度居中（旧写法 boardTop = (h - 棋盘高)/2），
        // 否则棋盘顶边会伸进上信息条，顶部黑方的悔棋按钮与棋盘边缘棋子互相压住。
        val zoneTop = topStripY + dp(24).toFloat()
        val zoneBottom = bottomStripY - dp(24).toFloat()
        val maxSide = minOf(w * 0.9f, (zoneBottom - zoneTop) / 1.0978f)
        cellX = maxSide / (XiangqiRules.COLS - 1)
        cellY = maxSide / (XiangqiRules.ROWS - 1)
        stoneR = min(cellX, cellY) * 0.44f
        boardLeft = (w - cellX * (XiangqiRules.COLS - 1)) / 2f
        // 在两条信息条之间竖直居中，保证上下边缘棋子都不被信息条遮挡
        boardTop = zoneTop + ((zoneBottom - zoneTop) - cellY * (XiangqiRules.ROWS - 1)) / 2f
        // 黑方（上）与红方（下）各自的悔棋按钮，与棋盘左缘对齐（加大便于点击）
        val bw = dp(118).toFloat()
        val bh = dp(48).toFloat()
        undoBtnBlack.set(boardLeft, topStripY - bh / 2f, boardLeft + bw, topStripY + bh / 2f)
        undoBtnRed.set(boardLeft, bottomStripY - bh / 2f, boardLeft + bw, bottomStripY + bh / 2f)
        btnTextPaint.textSize = dp(16).toFloat()
        resignBtn.setEmpty()
        // 木质棋盘底盘（比交叉点各外扩半格，圆角 20dp 由 drawWoodSlab 统一处理）
        slabRect.set(
            boardLeft - cellX * 0.5f - dp(8).toFloat(),
            boardTop - cellY * 0.5f - dp(8).toFloat(),
            boardLeft + (XiangqiRules.COLS - 1) * cellX + cellX * 0.5f + dp(8).toFloat(),
            boardTop + (XiangqiRules.ROWS - 1) * cellY + cellY * 0.5f + dp(8).toFloat())
        if (slabRect.width() != lastSlabW || slabRect.height() != lastSlabH) {
            lastSlabW = slabRect.width()
            lastSlabH = slabRect.height()
            art.buildWoodSlab(slabRect)
        }
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
        // 若这一手直接结束了对局（将死/困毙/认输），帧循环会立即停止，
        // 走子动画将永远停在 t=0：drawGame 会跳过 animTo 并把棋子画在起点格，
        // 玩家看到的是"制胜的那一手好像没走出去"。这里直接把动画推进到终点。
        if (gameOverFlag) {
            animProgress = 1f
            animating = false
        }
    }

    /** 撤销一步（双人版悔棋）。返回是否真正撤销成功。 */
    protected fun undo(): Boolean {
        if (history.isEmpty() || gameOverFlag || animating) return false
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
        return true
    }

    /** 撤销到指定一方回合（人机版：撤到玩家回合）。 */
    protected fun undoUntil(side: Int) {
        while (history.isNotEmpty() && !gameOverFlag) {
            val last = history.last()
            // 用 last.to 而不是 last.from 判断走子方：撤销之前 board[last.from] 已经被清空，
            // sideOf(EMPTY) 会走 else 分支恒判为黑方，导致 undoUntil(RED) 一路撤到历史清空。
            val mover = XiangqiRules.sideOf(board[last.to])
            // undo() 可能因动画中/已结束等原因返回 false；此时历史长度不变，
            // 旧实现 while 条件恒真 → 死循环 ANR。
            if (!undo()) break
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

    private fun drawPieceAt(canvas: Canvas, p: Int, cx: Float, cy: Float, selected: Boolean = false) {
        val isRed = XiangqiRules.sideOf(p) == XiangqiRules.RED
        // 奶油色圆棋子由美术工具箱统一绘制：软投影 + 球面渐变 + 内圈细环 + 暖色外描边，
        // selected=true 时额外加一圈金色柔光（替代原来手绘的柔光圆 + 亮边）。
        // 黑方（上方玩家）棋子字面朝上（facingUp=true），红方正常朝向——语义与旧实现一致。
        art.drawCreamPiece(
            canvas, cx, cy, stoneR, XiangqiRules.pieceName(p),
            if (isRed) redTextPaint.color else blackTextPaint.color,
            facingUp = !isRed, selected = selected)
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 木质棋盘底盘（统一美术：大圆角 + 柔和渐变 + 浅色内描边，替代旧的双层深棕描边）
        art.drawWoodSlab(canvas, slabRect, 20f)

        // 网格线（楚河汉界处纵向断开），统一用工具箱的半透明圆头细线
        val gridRight = boardLeft + (XiangqiRules.COLS - 1) * cellX
        for (r in 0 until XiangqiRules.ROWS) {
            art.drawGridLine(canvas, boardLeft, boardTop + r * cellY, gridRight, boardTop + r * cellY, 1.2f)
        }
        for (c in 0 until XiangqiRules.COLS) {
            art.drawGridLine(canvas, boardLeft + c * cellX, boardTop, boardLeft + c * cellX, boardTop + 4 * cellY, 1.2f)
            art.drawGridLine(canvas, boardLeft + c * cellX, boardTop + 5 * cellY, boardLeft + c * cellX, boardTop + 9 * cellY, 1.2f)
        }
        // 九宫斜线
        art.drawGridLine(canvas, boardLeft + 3 * cellX, boardTop, boardLeft + 5 * cellX, boardTop + 2 * cellY, 1.2f)
        art.drawGridLine(canvas, boardLeft + 5 * cellX, boardTop, boardLeft + 3 * cellX, boardTop + 2 * cellY, 1.2f)
        art.drawGridLine(canvas, boardLeft + 3 * cellX, boardTop + 7 * cellY, boardLeft + 5 * cellX, boardTop + 9 * cellY, 1.2f)
        art.drawGridLine(canvas, boardLeft + 5 * cellX, boardTop + 7 * cellY, boardLeft + 3 * cellX, boardTop + 9 * cellY, 1.2f)
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
            drawPieceAt(canvas, p, px(idx), py(idx), selected = idx == selected)
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

        // 双方信息条：悔棋按钮 + 本侧吃子列表 + 侧名。
        // 上方（黑方）整块需要绕信息条自身中心旋转 180°——文字、吃子列表、悔棋按钮
        // 全部正对上方玩家；旧实现只把棋子字面转向了，信息条仍是倒的。
        canvas.save()
        canvas.translate(width / 2f, topStripY)
        canvas.rotate(180f)
        canvas.translate(-width / 2f, -topStripY)
        drawPlayerStrip(canvas, XiangqiRules.BLACK, topStripY, undoBtnBlack, stripRotated = true)
        canvas.restore()
        drawPlayerStrip(canvas, XiangqiRules.RED, bottomStripY, undoBtnRed, stripRotated = false)
    }

    /**
     * 玩家信息条：左侧悔棋按钮，中部本侧吃掉的对方棋子，右侧侧名与回合指示点。
     * @param stripRotated 调用方是否已经把画布整体旋转了 180°（黑方信息条）；
     *        此时内部的小棋子不能再自行翻转，否则两次 180° 抵消又变回正视下方玩家。
     */
    private fun drawPlayerStrip(canvas: Canvas, side: Int, stripY: Float, undoBtn: RectF, stripRotated: Boolean) {
        // 悔棋按钮：统一柔和按钮（填充 + 顶部内高光 + 软投影）
        art.drawSoftButton(canvas, undoBtn, 0xE62A3557.toInt(), 13f)
        val undoText = "↶ 悔棋 ×" + undoLeft[side]
        canvas.drawText(
            undoText, undoBtn.centerX(),
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
                // stripRotated=true 表示外层已经整体转过 180°（黑方信息条），
                // 这里不能再传 facingUp=true，否则两次旋转抵消、字面又朝下。
                drawMini(canvas, captured[i], sx, stripY, miniR,
                    facingUp = if (stripRotated) false else side == XiangqiRules.BLACK)
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

    /** 吃子列表里的迷你棋子：与棋盘上的棋子同一套奶油色画法，字号随半径自适应。 */
    private fun drawMini(canvas: Canvas, p: Int, cx: Float, cy: Float, r: Float, facingUp: Boolean) {
        val isRed = XiangqiRules.sideOf(p) == XiangqiRules.RED
        art.drawCreamPiece(
            canvas, cx, cy, r, XiangqiRules.pieceName(p),
            if (isRed) redTextPaint.color else blackTextPaint.color,
            facingUp = facingUp)
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
        undoLeft[XiangqiRules.RED] = UNDO_LIMIT
        undoLeft[XiangqiRules.BLACK] = UNDO_LIMIT
        animFrom = -1
        animTo = -1
        animProgress = 0f
        animating = false
        start()
    }
}