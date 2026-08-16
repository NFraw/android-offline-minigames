package com.minigames.collection.games.animal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 斗兽棋：象狮虎豹狼狗猫鼠互吃，鼠吃象，先进入对方兽穴或吃光对方获胜。 */
class AnimalView(context: Context) : GameView(context) {

    companion object {
        private const val N = 9
        private const val M = 7
        private const val RANK = 8
        // 棋子编码：1..8（象1 狮2 虎3 豹4 狼5 狗6 猫7 鼠8），黑方用负值，0 为空
        private const val ELEPHANT = 1
        private const val LION = 2
        private const val TIGER = 3
        private const val LEOPARD = 4
        private const val WOLF = 5
        private const val DOG = 6
        private const val CAT = 7
        private const val RAT = 8
        private val NAMES = arrayOf("", "象", "狮", "虎", "豹", "狼", "狗", "猫", "鼠")
        private val RANK_OF = intArrayOf(0, 8, 7, 6, 5, 4, 3, 2, 1)
    }

    private val board = IntArray(N * M)
    private var selected = -1
    private var turn = 0
    private var over = false
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cellRect = RectF()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9B38C.toInt() }
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF37C2F0.toInt() }
    private val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5D547.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        cell = kotlin.math.min(w / N.toFloat(), h / M.toFloat()) * 0.98f
        boardLeft = (w - cell * N) / 2f
        boardTop = (h - cell * M) / 2f
        reset()
    }

    private fun isWater(x: Int, y: Int): Boolean =
        (x == 3 || x == 4 || x == 5) && (y == 1 || y == 2 || y == 4 || y == 5)

    private fun inBounds(x: Int, y: Int) = x in 0 until N && y in 0 until M

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (!inBounds(c, r)) return
        val idx = r * N + c
        if (selected < 0) {
            val p = board[idx]
            if (p != 0 && side(p) == turn) {
                selected = idx
                playSound(SoundManager.CLICK)
            }
            return
        }
        if (idx == selected) {
            selected = -1
            return
        }
        val p = board[selected]
        if (board[idx] != 0 && side(board[idx]) == turn) {
            selected = idx
            playSound(SoundManager.CLICK)
            return
        }
        // 移动合法性
        val sx = selected % N
        val sy = selected / N
        val captured = board[idx]
        val canMove = canMoveTo(sx, sy, c, r, p)
        if (canMove && canCapture(p, captured)) {
            board[idx] = p
            board[selected] = 0
            selected = -1
            playSound(if (captured != 0) SoundManager.EAT else SoundManager.MOVE)
            // 只有对方子进入己方兽穴才获胜（己方子不能进自己兽穴，此处仅判定对方兽穴）
            val redDen = 4
            val blackDen = N * M - N + 4
            if ((idx == blackDen && turn == 0) || (idx == redDen && turn == 1)) {
                // 走子方进入对方兽穴 → 走子方获胜（黑方兽穴=idx4 被红方进入；红方兽穴=idx58 被黑方进入）
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(0, true, (if (turn == 0) "红方" else "黑方") + "进入兽穴获胜！"))
            } else if (board.count { it != 0 && side(it) == 1 - turn } == 0) {
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(0, true, (if (turn == 0) "红方" else "黑方") + "吃光对方获胜！"))
            } else {
                turn = 1 - turn
            }
        } else {
            selected = -1
        }
        invalidate()
    }

    private fun side(p: Int): Int = if (p > 0) 0 else 1

    private fun canMoveTo(sx: Int, sy: Int, tx: Int, ty: Int, p: Int): Boolean {
        val dx = abs(tx - sx)
        val dy = abs(ty - sy)
        if (dx + dy != 1) {
            // 狮虎跳河：横向跳 3 格过河（中间是水且路径上无子）
            if (rankOf(p) == 6 || rankOf(p) == 7) {
                if (dy == 0 && dx == 3 && isWater((sx + tx) / 2, sy)) {
                    val mid1 = sy * N + (sx + tx) / 2
                    val mid2 = sy * N + tx - (tx - sx) / 2
                    if (board[mid1] == 0 && board[mid2] == 0) return true
                }
                if (dx == 0 && dy == 3 && isWater(sx, (sy + ty) / 2)) {
                    val mid1 = ((sy + ty) / 2) * N + sx
                    val mid2 = (ty - (ty - sy) / 2) * N + sx
                    if (board[mid1] == 0 && board[mid2] == 0) return true
                }
            }
            return false
        }
        // 鼠不能进兽穴和对手兽穴；兽穴判定简化：跳过
        if (isWater(tx, ty) && rankOf(p) != 1) return false
        if (isWater(sx, sy) && !isWater(tx, ty)) return false
        return true
    }

    private fun canCapture(p: Int, target: Int): Boolean {
        if (target == 0) return true
        val pr = rankOf(p)
        val tr = rankOf(target)
        // 鼠吃象
        if (pr == 1 && tr == 8) return true
        // 象不能吃鼠
        if (pr == 8 && tr == 1) return false
        // 等级相同不能互吃；等级高者吃低者
        return pr > tr
    }

    private fun rankOf(p: Int): Int {
        val a = if (p > 0) p else -p
        return RANK_OF[a]
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until M) for (c in 0 until N) {
            val x = boardLeft + c * cell
            val y = boardTop + r * cell
            cellRect.set(x, y, x + cell, y + cell)
            if (isWater(c, r)) {
                canvas.drawRoundRect(cellRect, dp(4).toFloat(), dp(4).toFloat(), waterPaint)
            } else {
                canvas.drawRoundRect(cellRect, dp(4).toFloat(), dp(4).toFloat(), cellPaint)
            }
            val p = board[r * N + c]
            if (p != 0) {
                val a = if (p > 0) p else -p
                piecePaint.color = if (p > 0) 0xFFE8503A.toInt() else 0xFF1C2333.toInt()
                canvas.drawCircle(x + cell / 2f, y + cell / 2f, cell * 0.36f, piecePaint)
                textPaint.color = 0xFFFFFFFF.toInt()
                textPaint.textSize = cell * 0.5f
                canvas.drawText(NAMES[a], x + cell / 2f, y + cell / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
            }
            if (r * N + c == selected) {
                canvas.drawRoundRect(cellRect, dp(4).toFloat(), dp(4).toFloat(), selPaint)
            }
        }
        // 兽穴标记
        canvas.drawText("穴", boardLeft + 4 * cell + cell / 2f, boardTop + cell / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        canvas.drawText("穴", boardLeft + 4 * cell + cell / 2f, boardTop + 6 * cell + cell / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        textPaint.color = 0xFF8A93A6.toInt()
        textPaint.textSize = 20f
        canvas.drawText((if (turn == 0) "红方" else "黑方") + "回合 · 点击选子再点目标 · 象狮虎豹狼狗猫鼠互吃，鼠吃象", width / 2f, boardTop + cell * M + dp(28).toFloat(), textPaint)
    }

    override fun reset() {
        for (i in board.indices) board[i] = 0
        // 标准布局：两侧各 4 子，兽穴 (4,0)/(4,6) 留空
        // 红方（正数）在下 y=6，黑方（负数）在上 y=0
        val left = intArrayOf(ELEPHANT, LION, TIGER, LEOPARD)
        val right = intArrayOf(WOLF, DOG, CAT, RAT)
        for (i in 0 until 4) {
            board[(M - 1) * N + i] = left[i]
            board[0 * N + i] = -left[i]
            board[(M - 1) * N + 5 + i] = right[i]
            board[0 * N + 5 + i] = -right[i]
        }
        selected = -1
        turn = 0
        over = false
        start()
    }
}