package com.minigames.collection.games.reversi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.BoardArt
import com.minigames.collection.common.BoardArt.Palette
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/**
 * 黑白棋（人机）：玩家执黑先手，AI 采用边角加权贪心策略。
 *
 * 【美术说明】与双人版 [ReversiView] 保持一致：木质圆角底盘（替代原绿绒底色）、
 * 玻璃棋子、金色圆环合法落点提示、底部回合状态胶囊。
 * 布局额外让出顶部 dp(SAFE_TOP_DP) 安全区，避免棋盘顶端压在悬浮控件之下。
 */
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
    /** 局面代数：每次 reset() 自增，用于作废在途 AI 线程针对旧棋盘的计算结果。 */
    private var epoch = 0

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    /** 美术工具箱：统一木板 / 玻璃棋子 / 状态胶囊。 */
    private val art = BoardArt(this)
    /** 棋盘木板矩形（含内边距），onLayout 中计算。 */
    private val slabRect = RectF()
    /** 底部状态胶囊矩形。 */
    private val statusRect = RectF()
    /** 棋盘尺寸变化时重建木纹渐变（onLayout 可能被多次调用，避免每帧 new Shader）。 */
    private var lastBoardSide = 0f

    /** 背景色（与全库统一背景一致）。 */
    private val bgPaint = Paint().apply { color = Palette.BG }
    /** 合法落点提示：金色小圆环（STROKE），比半透明白点更醒目且不与白子混淆。 */
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.GOLD
        style = Paint.Style.STROKE
    }

    override fun onLayout(w: Int, h: Int) {
        // 顶部让出悬浮控件安全区（原实现 boardTop = (h - side)/2f 会让棋盘顶压到按钮下面），
        // 底部给状态胶囊留位置
        val topPad = dp(SAFE_TOP_DP).toFloat()
        val bottomPad = dp(96).toFloat()
        val side = min(w * 0.92f, h - topPad - bottomPad)
        cell = side / N
        boardLeft = (w - side) / 2f
        boardTop = topPad + (h - topPad - bottomPad - side) / 2f

        // 木板比棋盘各外扩 0.34 格，形成一圈柔和的木框
        slabRect.set(
            boardLeft - cell * 0.34f, boardTop - cell * 0.34f,
            boardLeft + side + cell * 0.34f, boardTop + side + cell * 0.34f)
        if (side != lastBoardSide) {
            lastBoardSide = side
            art.buildWoodSlab(slabRect)
        }

        // 底部状态胶囊（放在棋盘之下）。文案较长（含比分），字号略小避免撑出胶囊
        val bw = min(w * 0.92f, dp(360).toFloat())
        val bh = dp(38).toFloat()
        val by = slabRect.bottom + dp(12).toFloat()
        statusRect.set(w / 2f - bw / 2f, by, w / 2f + bw / 2f, by + bh)

        hintPaint.strokeWidth = dp(2.2f)
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused || aiThinking || curPlayer != BLACK) return
        val fc = (event.x - boardLeft) / cell
        val fr = (event.y - boardTop) / cell
        if (fc < 0f || fr < 0f) return  // 负值 toInt() 向零截断会把棋盘外一格解析成第 0 行/列
        val c = fc.toInt()
        val r = fr.toInt()
        if (r !in 0 until N || c !in 0 until N) return
        val idx = r * N + c
        if (!isValid(idx, BLACK)) return
        board[idx] = BLACK
        flip(idx, BLACK)
        playSound(SoundManager.CLICK)
        nextTurn()
    }

    private fun aiTurn() {
        if (over) return
        val myEpoch = epoch
        aiThinking = true
        Thread {
            val best = bestMove()
            post {
                // 重开后旧线程仍在途：不校验就会把上一局算出的落点写进新棋盘，
                // 甚至在新棋盘上宣告终局。
                if (myEpoch != epoch) return@post
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
            val s = WEIGHT[i / N][i % N]
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

    /**
     * 交替换手。
     *
     * 修复要点（原实现是致命规则错误）：
     *   旧代码写 `val opp = 1 - curPlayer`，但常量是 EMPTY=0 / BLACK=1 / WHITE=2，
     *   1 - 1 = 0 得到的是 EMPTY，"对方有手吗"永远查的是空格子 → hasMove(EMPTY) 恒为 false。
     *   后果是玩家一旦没有合法落子点就直接终局结算，而电脑明明还能走；
     *   黑白棋中"某一方整手无合法手"非常常见，导致大量对局在中盘被错误判负/判胜。
     */
    private fun nextTurn() {
        if (over) return
        curPlayer = opponentOf(curPlayer)
        if (hasMove(curPlayer)) {
            passCount = 0
        } else if (hasMove(opponentOf(curPlayer))) {
            passCount++                    // 当前方无手 → 跳过，由对方继续走
            playSound(SoundManager.MOVE)
            curPlayer = opponentOf(curPlayer)
        } else {
            finishGame()                   // 双方都无手 → 真正终局
            return
        }
        // 轮到电脑且还没结束就继续走（玩家被跳过后电脑可能连走多手）
        if (curPlayer == WHITE) aiTurn()
    }

    private fun opponentOf(p: Int): Int = if (p == BLACK) WHITE else BLACK

    /** 终局结算：按棋子数判定胜负（空位不计入任何一方）。 */
    private fun finishGame() {
        over = true
        val b = board.count { it == BLACK }
        val w = board.count { it == WHITE }
        val msg = (if (b > w) "你赢了！" else if (w > b) "电脑获胜！" else "平局！") +
                "（黑 " + b + " : " + w + " 白）"
        playSound(if (b > w) SoundManager.WIN else SoundManager.LOSE)
        gameOver(GameResult(0, b > w, msg))
    }

    private fun hasMove(player: Int): Boolean {
        for (i in board.indices) if (isValid(i, player)) return true
        return false
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 木质圆角底盘（统一美术，替代原来的绿绒格子底色）
        art.drawWoodSlab(canvas, slabRect, 20f)
        // 格子分隔线：浅色圆头细线
        for (i in 0..N) {
            art.drawGridLine(canvas, boardLeft, boardTop + i * cell, boardLeft + N * cell, boardTop + i * cell, 1.2f)
            art.drawGridLine(canvas, boardLeft + i * cell, boardTop, boardLeft + i * cell, boardTop + N * cell, 1.2f)
        }
        for (r in 0 until N) for (c in 0 until N) {
            val cx = boardLeft + (c + 0.5f) * cell
            val cy = boardTop + (r + 0.5f) * cell
            val v = board[r * N + c]
            if (v != EMPTY) {
                // 棋子：哑光玻璃质感（与双人版 / 围棋 / 五子棋同一套画法）
                art.drawGlassStone(canvas, cx, cy, cell * 0.38f, dark = v == BLACK)
            } else if (curPlayer == BLACK && isValid(r * N + c, BLACK)) {
                // 玩家合法落点：金色小圆环
                canvas.drawCircle(cx, cy, cell * 0.17f, hintPaint)
            }
        }
        // 底部回合状态胶囊（原实现画在棋盘正下方一行文字，现在收进胶囊）
        val b = board.count { it == BLACK }
        val w = board.count { it == WHITE }
        val isBlack = curPlayer == BLACK
        art.drawStatusBar(
            canvas, statusRect,
            (if (aiThinking) "电脑思考中…" else if (isBlack) "你（黑）落子" else "电脑（白）回合") + " · " + b + " : " + w,
            if (isBlack) Palette.STONE_DARK else Palette.STONE_LIGHT_RIM, sizeSp = 14f)
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
        epoch++   // 作废在途的 AI 线程计算结果
        start()
    }
}
