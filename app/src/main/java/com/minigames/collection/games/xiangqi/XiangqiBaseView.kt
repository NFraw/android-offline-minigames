package com.minigames.collection.games.xiangqi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 象棋公共视图：棋盘绘制、点选走子、胜负检测。 */
abstract class XiangqiBaseView(context: Context) : GameView(context) {

    protected var board = XiangqiRules.initialBoard()
    protected var turn = XiangqiRules.RED
    protected var selected = -1
    protected var legalTargets = ArrayList<Int>()
    protected var lastFrom = -1
    protected var lastTo = -1
    protected var gameOverFlag = false
    protected var statusText = ""
    protected var aiThinking = false

    private var cellX = 0f
    private var cellY = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var stoneR = 0f

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0B577.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A5A2B.toInt()
        strokeWidth = 2f
    }
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF0E3C8.toInt() }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD64545.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1C2333.toInt()
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
        textSize = 24f
        color = 0xFF8A93A6.toInt()
    }
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f
        color = 0xFFE8EAF0.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w * 0.97f, h * 0.78f)
        cellX = side / (XiangqiRules.COLS - 1)
        cellY = side / (XiangqiRules.ROWS - 1)
        stoneR = min(cellX, cellY) * 0.43f
        boardLeft = (w - cellX * (XiangqiRules.COLS - 1)) / 2f
        boardTop = (h - cellY * (XiangqiRules.ROWS - 1)) / 2f + cellY * 0.5f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || gameOverFlag || paused || aiThinking) return
        val c = Math.round((event.x - boardLeft) / cellX)
        val r = Math.round((event.y - boardTop) / cellY)
        if (r !in 0 until XiangqiRules.ROWS || c !in 0 until XiangqiRules.COLS) return
        val dx = event.x - (boardLeft + c * cellX)
        val dy = event.y - (boardTop + r * cellY)
        if (Math.hypot(dx.toDouble(), dy.toDouble()) > min(cellX, cellY) * 0.5) return
        val idx = r * XiangqiRules.COLS + c
        onIntersectionTap(idx)
    }

    /** 子类决定点击响应（己方棋子/目标点等）。 */
    protected abstract fun onIntersectionTap(idx: Int)

    protected fun isMyPiece(idx: Int): Boolean {
        val p = board[idx]
        return p != XiangqiRules.EMPTY && XiangqiRules.sideOf(p) == turn
    }

    protected fun doMove(move: XiangqiRules.Move) {
        val captured = board[move.to]
        if (captured != XiangqiRules.EMPTY) {
            playSound(SoundManager.EAT)
            buzz()
        } else {
            playSound(SoundManager.MOVE)
        }
        board[move.to] = board[move.from]
        board[move.from] = XiangqiRules.EMPTY
        lastFrom = move.from
        lastTo = move.to
        selected = -1
        legalTargets.clear()
        onMoveCompleted(move)
    }

    /** 走子完成后回调：换手 / 触发 AI / 胜负检测。 */
    protected abstract fun onMoveCompleted(move: XiangqiRules.Move)

    /** 检测 side 是否无子可走；无则结束游戏（对方获胜）。返回是否已结束。 */
    protected fun checkGameOver(side: Int): Boolean {
        if (!XiangqiRules.hasAnyMove(board, side)) {
            gameOverFlag = true
            val winner = 1 - side
            val reason = if (XiangqiRules.isInCheck(board, side)) "被将死" else "无子可走"
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, XiangqiRules.sideName(winner) + "获胜！" + XiangqiRules.sideName(side) + reason))
            return true
        }
        return false
    }

    protected fun updateStatus() {
        statusText = if (XiangqiRules.isInCheck(board, turn)) "将军！" else ""
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawColor(0xFF141A2E.toInt())
        val left = boardLeft - cellX * 0.5f
        val top = boardTop - cellY * 0.5f
        val right = boardLeft + (XiangqiRules.COLS - 1) * cellX + cellX * 0.5f
        val bottom = boardTop + (XiangqiRules.ROWS - 1) * cellY + cellY * 0.5f
        canvas.drawRoundRect(RectF(left, top, right, bottom), cellX * 0.35f, cellX * 0.35f, boardPaint)
        // 横线
        for (r in 0 until XiangqiRules.ROWS) {
            canvas.drawLine(boardLeft, boardTop + r * cellY, boardLeft + (XiangqiRules.COLS - 1) * cellX, boardTop + r * cellY, linePaint)
        }
        // 竖线（河界断线）
        for (c in 0 until XiangqiRules.COLS) {
            canvas.drawLine(boardLeft + c * cellX, boardTop, boardLeft + c * cellX, boardTop + 4 * cellY, linePaint)
            canvas.drawLine(boardLeft + c * cellX, boardTop + 5 * cellY, boardLeft + c * cellX, boardTop + 9 * cellY, linePaint)
        }
        // 九宫斜线
        canvas.drawLine(boardLeft + 3 * cellX, boardTop, boardLeft + 5 * cellX, boardTop + 2 * cellY, linePaint)
        canvas.drawLine(boardLeft + 5 * cellX, boardTop, boardLeft + 3 * cellX, boardTop + 2 * cellY, linePaint)
        canvas.drawLine(boardLeft + 3 * cellX, boardTop + 7 * cellY, boardLeft + 5 * cellX, boardTop + 9 * cellY, linePaint)
        canvas.drawLine(boardLeft + 5 * cellX, boardTop + 7 * cellY, boardLeft + 3 * cellX, boardTop + 9 * cellY, linePaint)
        // 河界文字
        linePaint.textAlign = Paint.Align.CENTER
        linePaint.textSize = cellY * 0.42f
        canvas.drawText("楚  河", boardLeft + 2 * cellX, boardTop + 4.5f * cellY - cellY * 0.1f, linePaint)
        canvas.drawText("汉  界", boardLeft + 6 * cellX, boardTop + 4.5f * cellY - cellY * 0.1f, linePaint)
        // 棋子
        for (r in 0 until XiangqiRules.ROWS) for (c in 0 until XiangqiRules.COLS) {
            val idx = r * XiangqiRules.COLS + c
            val p = board[idx]
            if (p == XiangqiRules.EMPTY) continue
            val cx = boardLeft + c * cellX
            val cy = boardTop + r * cellY
            canvas.drawCircle(cx, cy, stoneR, piecePaint)
            val isRed = XiangqiRules.sideOf(p) == XiangqiRules.RED
            val paint = if (isRed) redPaint else blackPaint
            canvas.drawCircle(cx, cy, stoneR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isRed) 0xFFD64545.toInt() else 0xFF1C2333.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            })
            paint.textSize = stoneR * 1.15f
            canvas.drawText(XiangqiRules.pieceName(p), cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
            if (idx == selected) {
                canvas.drawCircle(cx, cy, stoneR + dp(3).toFloat(), selPaint)
            }
        }
        // 合法目标提示
        for (t in legalTargets) {
            val cx = boardLeft + (t % XiangqiRules.COLS) * cellX
            val cy = boardTop + (t / XiangqiRules.COLS) * cellY
            if (board[t] == XiangqiRules.EMPTY) {
                canvas.drawCircle(cx, cy, stoneR * 0.22f, hintPaint)
            } else {
                canvas.drawCircle(cx, cy, stoneR, capPaint)
            }
        }
        // 最后一步标记
        if (lastFrom >= 0) {
            val fx = boardLeft + (lastFrom % XiangqiRules.COLS) * cellX
            val fy = boardTop + (lastFrom / XiangqiRules.COLS) * cellY
            canvas.drawCircle(fx, fy, cellX * 0.08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66E8503A.toInt() })
            val tx = boardLeft + (lastTo % XiangqiRules.COLS) * cellX
            val ty = boardTop + (lastTo / XiangqiRules.COLS) * cellY
            canvas.drawCircle(tx, ty, cellX * 0.08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() })
        }
        // 状态文字
        canvas.drawText(statusText, width / 2f, boardTop - cellY * 1.3f, topPaint)
        canvas.drawText(getStatusLine(), width / 2f, boardTop - cellY * 0.55f, statusPaint)
    }

    /** 子类提供状态行文案（回合/思考中…）。 */
    protected abstract fun getStatusLine(): String

    override fun reset() {
        board = XiangqiRules.initialBoard()
        turn = XiangqiRules.RED
        selected = -1
        legalTargets.clear()
        lastFrom = -1
        lastTo = -1
        gameOverFlag = false
        statusText = ""
        aiThinking = false
        start()
    }
}
