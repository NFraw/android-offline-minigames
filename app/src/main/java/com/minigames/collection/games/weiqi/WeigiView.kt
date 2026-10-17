package com.minigames.collection.games.weiqi

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
 * 围棋（15×15 简易规则）：
 * 落子提子、禁自杀；双方连续虚手（点击「虚手」按钮）后终局，
 * 按棋子数 + 围空判定胜负（不实现打劫与贴目）。
 *
 * 【美术说明】棋盘改用 BoardArt 统一木质圆角底盘（与五子棋/象棋/黑白棋同源），
 * 网格线改为半透明圆头细线，星位为柔和暖棕圆点，棋子改用哑光玻璃质感
 * （左上柔光高光 + 向下软投影），虚手按钮与状态提示分别收敛为柔和按钮与居中文字。
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
    /** 上一手落子之前的局面快照（打劫/禁全同判定用），首手为空。 */
    private var prevState: IntArray? = null
    /** 上一次落子失败是否因为打劫（用于给玩家不同的提示文案）。 */
    private var koBlocked = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var stoneR = 0f
    private val passBtn = RectF()

    /** 美术工具箱：统一木板 / 玻璃棋子 / 胶囊 / 柔和按钮。 */
    private val art = BoardArt(this)
    /** 棋盘木板矩形（含内边距），onLayout 中计算。 */
    private val slabRect = RectF()
    /** 顶部回合状态胶囊矩形。 */
    private val statusRect = RectF()
    /** 棋盘尺寸变化时重建木纹渐变（onLayout 可能被多次调用，避免每帧 new Shader）。 */
    private var lastBoardSide = 0f

    /** 背景色（与全库统一背景一致）。 */
    private val bgPaint = Paint().apply { color = Palette.BG }

    override fun onLayout(w: Int, h: Int) {
        // 顶部让出悬浮控件安全区（退出/暂停/重开/帮助按钮），底部给「虚手」按钮与提示文字留位置
        val topPad = dp(SAFE_TOP_DP).toFloat()
        val bottomPad = dp(112).toFloat()
        val statusH = dp(38).toFloat()
        // 头部占用：安全区 + 回合胶囊 + 提子信息行。
        // 提子信息原来画在 statusRect.bottom + 20dp，正好落在木板顶边上、压住棋盘边框，
        // 这里显式给它留出一条 band，棋盘从 band 之下开始画。
        val headerBottom = topPad + dp(4).toFloat() + statusH + dp(24).toFloat()
        val side = min(w * 0.94f, h - headerBottom - bottomPad).coerceAtLeast(dp(120).toFloat())
        cell = side / (N - 1)
        stoneR = cell * 0.44f
        boardLeft = (w - cell * (N - 1)) / 2f
        // 棋盘紧贴头部信息之下（余量留在底部，避免棋盘沉到屏幕下半部）
        boardTop = headerBottom + dp(16).toFloat() + cell * 0.5f

        // 木板比网格线各外扩半格，保证边缘交叉点不贴边
        slabRect.set(
            boardLeft - cell * 0.62f, boardTop - cell * 0.62f,
            boardLeft + cell * (N - 1) + cell * 0.62f, boardTop + cell * (N - 1) + cell * 0.62f)
        if (side != lastBoardSide) {
            lastBoardSide = side
            art.buildWoodSlab(slabRect)
        }

        // 顶部回合状态胶囊（放在安全区之下）
        val bw = min(w * 0.72f, dp(300).toFloat())
        val by = topPad + dp(4).toFloat()
        statusRect.set(w / 2f - bw / 2f, by, w / 2f + bw / 2f, by + statusH)

        // 「虚手」按钮：棋盘之下，底部提示文字之上
        val pw = min(cell * 4.4f, w * 0.6f)
        val ph = dp(46).toFloat()
        val py = slabRect.bottom + dp(12).toFloat()
        passBtn.set(w / 2f - pw / 2f, py, w / 2f + pw / 2f, py + ph)

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

    /** 落子并处理提子；自杀禁着或打劫违规返回 false。 */
    private fun place(x: Int, y: Int): Boolean {
        val idx = y * N + x
        if (board[idx] != EMPTY) return false
        // 保存落子前的局面，用于打劫（禁全同）判定
        val before = board.copyOf()
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
        // 打劫（禁全同）：落子后的局面不能与"上一手之前的局面"完全相同。
        // 典型场景是白提一子后黑立即回提——旧实现没有这条判定，双方可以无限循环互提，
        // 劫争完全失效并直接影响终局目数。
        if (prevState != null && board.contentEquals(prevState!!)) {
            board[idx] = EMPTY
            koBlocked = true
            return false
        }
        prevState = before
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
            prevState = null  // 虚手解消劫争限制（标准规则）
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
            koBlocked = false
        } else {
            playSound(SoundManager.LOSE)
            if (koBlocked) {
                android.widget.Toast.makeText(context, "打劫：不能立即提回，请先在别处找劫材",
                    android.widget.Toast.LENGTH_SHORT).show()
                koBlocked = false
            }
        }
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 木质圆角底盘（统一美术）
        art.drawWoodSlab(canvas, slabRect, 20f)
        // 网格线：细、半透明、圆头
        for (i in 0 until N) {
            art.drawGridLine(canvas, boardLeft, boardTop + i * cell, boardLeft + (N - 1) * cell, boardTop + i * cell, 1.2f)
            art.drawGridLine(canvas, boardLeft + i * cell, boardTop, boardLeft + i * cell, boardTop + (N - 1) * cell, 1.2f)
        }
        // 星位（15 路盘的九个传统星位）
        for (sx in intArrayOf(3, 7, 11)) for (sy in intArrayOf(3, 7, 11)) {
            art.drawStar(canvas, boardLeft + sx * cell, boardTop + sy * cell, cell * 0.09f)
        }
        // 棋子：哑光玻璃质感，最后一手用柔和标记点
        for (r in 0 until N) for (c in 0 until N) {
            val v = board[r * N + c]
            if (v == EMPTY) continue
            art.drawGlassStone(
                canvas, boardLeft + c * cell, boardTop + r * cell, stoneR,
                dark = v == BLACK, lastMark = (r * N + c) == lastMove)
        }
        // 顶部回合状态胶囊：文字直接写明当前落子方（不再用 ●○ 字形导致黑子隐形）
        val isBlack = curPlayer == BLACK
        art.drawStatusBar(
            canvas, statusRect, (if (isBlack) "黑方落子" else "白方落子"),
            if (isBlack) Palette.STONE_DARK else Palette.STONE_LIGHT_RIM)
        // 提子信息：胶囊左侧画一枚真实小棋子，棋子颜色一目了然
        val infoY = statusRect.bottom + dp(20).toFloat()
        art.drawGlassStone(canvas, statusRect.left + dp(16).toFloat(), infoY, stoneR * 0.5f, dark = isBlack)
        art.drawCenterText(
            canvas, (if (isBlack) "黑方" else "白方") + "提子 黑提 " + capturedW + " · 白提 " + capturedB,
            statusRect.centerX() + dp(12).toFloat(), infoY, 13f, Palette.DIM)
        // 「虚手」按钮：标注当前落子方，双方轮流使用
        art.drawSoftButton(canvas, passBtn, Palette.ACCENT, 14f)
        art.drawCenterText(
            canvas, (if (isBlack) "黑方" else "白方") + "虚手",
            passBtn.centerX(), passBtn.centerY(), 17f, Palette.TEXT_LIGHT)
        // 底部提示：避开右下角得分胶囊所在的 dp(55..12) 区间
        art.drawCenterText(
            canvas, "点击落子 · 虚手跳过 · 双方连续虚手两次后终局",
            width / 2f, passBtn.bottom + dp(16).toFloat(), 12f, Palette.DIM)
    }

    override fun reset() {
        for (i in board.indices) board[i] = EMPTY
        curPlayer = BLACK
        passCount = 0
        lastMove = -1
        over = false
        capturedW = 0
        capturedB = 0
        prevState = null
        koBlocked = false
        start()
    }
}
