package com.minigames.collection.games.gomoku

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
 * 五子棋（人机对战）实现。
 *
 * 游戏规则：玩家执黑先手，AI 执白后手，先连成五子者胜。
 *
 * AI 实现策略（评估函数法）：
 *   对每个空位计算"进攻得分 + 防守得分"，取最高分位置落子。
 *   进攻得分：假设 AI 在此落子后形成的连子模式（活四、冲三等）
 *   防守得分：假设玩家在此落子后形成的连子模式（阻止玩家形成活四等）
 *   权重：进攻得分 × 2（AI 更倾向进攻而非纯防守）
 *
 * 多线程说明：
 *   AI 计算在后台线程（Thread{...}）执行，避免阻塞主线程（UI 线程）导致界面卡顿。
 *   计算完成后通过 post{...} 回到主线程更新棋盘（Android 要求 UI 操作必须在主线程）。
 *   aiThinking 标志防止计算期间玩家继续落子。
 *
 * 学习要点：
 *   - Android 多线程：Thread + post{} 是最简单的方式（更高级的有协程/Handler/AsyncTask）
 *   - 评估函数：棋类 AI 的基础方法，比穷举搜索更高效
 *   - 棋盘表示：一维 IntArray（N*N）代替二维数组，index = y*N+x
 */
class GomokuAiView(context: Context) : GameView(context) {

    companion object {
        private const val N = 15
        private const val EMPTY = 0
        private const val BLACK = 1
        private const val WHITE = 2
        private val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))
    }

    private val board = IntArray(N * N)
    private var curPlayer = BLACK
    private var lastX = -1
    private var lastY = -1
    private var over = false
    private var aiThinking = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var stoneR = 0f
    /** 局面代数：每次 reset() 自增，用于作废在途 AI 线程针对旧局面的计算结果。 */
    private var epoch = 0

    /** 美术工具箱：与双人版 GomokuView 同一套木板 / 玻璃棋子 / 状态胶囊。 */
    private val art = BoardArt(this)
    private val slabRect = RectF()
    private val statusRect = RectF()
    private val hintRect = RectF()
    private var lastBoardSide = 0f
    private val bgPaint = Paint().apply { color = Palette.BG }

    override fun onLayout(w: Int, h: Int) {
        // 与双人版一致的排版：安全区之下、底部状态条之上，棋盘竖直居中
        val topPad = dp(SAFE_TOP_DP).toFloat()
        val statusH = dp(38).toFloat()
        val hintH = dp(26).toFloat()
        val headerBottom = topPad + dp(4).toFloat() + statusH + hintH
        // 棋盘是正方形且受屏宽限制；竖屏手机上 w*0.94 通常远小于屏高，
        // 若把余量全放在棋盘上方会显得"棋盘沉在屏幕下半部"。
        // 这里让棋盘紧贴头部信息之下开始，余量留在底部（与双人版观感一致）。
        val side = min(w * 0.94f, h - headerBottom - dp(24).toFloat()).coerceAtLeast(dp(120).toFloat())
        cell = side / (N - 1)
        stoneR = cell * 0.44f
        boardLeft = (w - cell * (N - 1)) / 2f
        boardTop = headerBottom + dp(20).toFloat() + cell * 0.5f

        slabRect.set(
            boardLeft - cell * 0.62f, boardTop - cell * 0.62f,
            boardLeft + cell * (N - 1) + cell * 0.62f, boardTop + cell * (N - 1) + cell * 0.62f)
        if (side != lastBoardSide) {
            lastBoardSide = side
            art.buildWoodSlab(slabRect)
        }

        // 顶部状态胶囊 + 其下方一行小字提示
        val bw = min(w * 0.78f, dp(320).toFloat())
        val by = topPad + dp(4).toFloat()
        statusRect.set(w / 2f - bw / 2f, by, w / 2f + bw / 2f, by + statusH)
        hintRect.set(w / 2f - bw / 2f, statusRect.bottom + dp(4).toFloat(),
            w / 2f + bw / 2f, statusRect.bottom + dp(4).toFloat() + hintH)

        reset()
    }

    /**
     * 触摸事件处理：玩家落子 → AI 后台计算 → AI 落子。
     * 多线程模式：
     *   主线程（UI 线程）：玩家落子 + 更新棋盘 + 重绘
     *   后台线程（Thread{...}）：AI 计算最佳落子（可能耗时较长，不能阻塞 UI）
     *   post{...}：后台线程完成后回到主线程更新 UI（Android 要求 UI 操作在主线程）
     */
    override fun onTouch(event: MotionEvent) {
        // 守卫条件：非按下事件/游戏结束/暂停/AI 思考中/非玩家回合 → 忽略
        if (event.action != MotionEvent.ACTION_DOWN || over || paused || aiThinking || curPlayer != BLACK) return
        // 触摸坐标 → 棋盘格子坐标（四舍五入到最近交叉点）
        val c = Math.round((event.x - boardLeft) / cell)
        val r = Math.round((event.y - boardTop) / cell)
        if (r !in 0 until N || c !in 0 until N) return
        // 距离检测：触摸点离交叉点中心超过 0.45 格半径则忽略（防止误触相邻格）
        val d = Math.hypot((event.x - (boardLeft + c * cell)).toDouble(), (event.y - (boardTop + r * cell)).toDouble())
        if (d > cell * 0.45) return
        if (board[r * N + c] != EMPTY) return  // 已有棋子的位置不能落子
        // 玩家落子
        board[r * N + c] = BLACK
        lastX = c
        lastY = r
        playSound(SoundManager.CLICK)
        // 检查玩家是否获胜
        if (checkWin(c, r, BLACK)) {
            finish("你赢了！黑方五连", true)
            return
        }
        // 满盘且无人五连 → 平局（旧实现没有这条：棋盘下满后 AI 返回 null，
        // 既不落子也不结算，此后所有点击都被拒绝，局面永久卡死只能退出）
        if (board.none { it == EMPTY }) {
            finish("棋盘已满，平局！", false)
            return
        }
        // AI 回合：在后台线程计算（不阻塞 UI）
        val myEpoch = epoch
        aiThinking = true
        Thread {
            val best = bestMove()   // 在后台线程执行 AI 计算
            post {                  // 回到主线程更新 UI
                // 重开后旧线程仍在途，不校验会把上一局算出的落点写进新棋盘
                if (myEpoch != epoch) return@post
                aiThinking = false
                if (over) return@post  // 计算期间游戏已结束（如玩家退出）
                if (best != null) {
                    board[best] = WHITE   // AI 落子
                    lastX = best % N
                    lastY = best / N
                    playSound(SoundManager.EAT)
                    if (checkWin(lastX, lastY, WHITE)) {
                        finish("电脑获胜！白方五连", false)
                    } else if (board.none { it == EMPTY }) {
                        finish("棋盘已满，平局！", false)
                    }
                }
            }
        }.start()
        invalidate()  // 立即重绘显示玩家的落子（不等 AI 计算完成）
    }

    /**
     * 结算。
     * @param msg 结果摘要
     * @param won 玩家（黑方）是否获胜——决定结算弹窗标题是"🎉 胜利"还是"游戏结束"。
     *        旧实现恒定传 true，电脑五连时也会显示"🎉 胜利 / 电脑获胜"，音效也是胜利音。
     */
    private fun finish(msg: String, won: Boolean) {
        if (over) return
        over = true
        playSound(if (won) SoundManager.WIN else SoundManager.LOSE)
        gameOver(GameResult(0, won, msg))
    }

    private fun checkWin(x: Int, y: Int, player: Int): Boolean {
        for (dir in DIRS) {
            var count = 1
            for (s in intArrayOf(1, -1)) {
                var i = 1
                while (true) {
                    val nx = x + dir[0] * i * s
                    val ny = y + dir[1] * i * s
                    if (nx in 0 until N && ny in 0 until N && board[ny * N + nx] == player) count++ else break
                    i++
                }
            }
            if (count >= 5) return true
        }
        return false
    }

    /**
     * AI 计算最佳落子位置（在后台线程调用）。
     * 算法：遍历所有空位，对每个空位计算"进攻得分 × 2 + 防守得分"，取最高分位置。
     * 注意：使用 board.clone() 副本计算，避免修改主线程正在绘制的棋盘。
     * @return 最佳位置的索引（y*N+x），无可用位置返回 null
     */
    private fun bestMove(): Int? {
        var bestScore = -1
        var bestDef = -1
        var best = -1
        val b = board.clone()  // 克隆棋盘用于评估，不污染原始棋盘
        for (i in b.indices) {
            if (b[i] != EMPTY) continue
            var score = 0
            var def = 0
            // 评估两个角色：AI（WHITE）和玩家（BLACK）
            for (player in intArrayOf(WHITE, BLACK)) {
                b[i] = player                               // 临时落子
                val s = evaluateAt(b, i % N, i / N, player)  // 评估该位置的价值
                b[i] = EMPTY                                 // 撤销临时落子
                if (player == WHITE) score += s * 2 else { score += s; def = s }  // AI 进攻权重 × 2
            }
            // 同分时优先防守：进攻权重 ×2 会让 AI 自己的冲四(10000)与封堵对手活三(10000)打平，
            // 若只比总分，先被遍历到的那个胜出——AI 可能放着对手活三不封、去下自己的冲四，
            // 随后被对手活四直接击溃。加上防守分作为次级比较即可避免。
            if (score > bestScore || (score == bestScore && def > bestDef)) {
                bestScore = score
                bestDef = def
                best = i
            }
        }
        return if (best >= 0) best else null
    }

    /**
     * 评估指定位置对指定玩家的价值。
     * 从 (x,y) 出发，沿四个方向（横/竖/正斜/反斜）统计连子数和开放端数。
     *
     * 连子模式评分：
     *   五连（count≥5）= 100000（必胜）
     *   活四（4连+两端开放）= 10000（下一步必胜）
     *   冲四（4连+一端开放）= 5000（对手必须防守）
     *   活三（3连+两端开放）= 1000（可能形成活四）
     *   冲三（3连+一端开放）= 300
     *   活二（2连+两端开放）= 100
     *   冲二（2连+一端开放）= 30
     *
     * @param b 棋盘数组（可能是 clone 副本）
     * @param x 评估位置的列号
     * @param y 评估位置的行号
     * @param player 评估的玩家（BLACK 或 WHITE）
     * @return 该位置的总评分
     */
    private fun evaluateAt(b: IntArray, x: Int, y: Int, player: Int): Int {
        var total = 0
        for (dir in DIRS) {  // 遍历四个方向
            var count = 1     // 连子数（从 1 开始，包含 (x,y) 自身）
            var openEnds = 0  // 开放端数（两端为空格则为活，被对手棋子或边界堵住则为冲）
            for (s in intArrayOf(1, -1)) {  // 正反两个方向各扫描一次
                var i = 1
                while (true) {
                    val nx = x + dir[0] * i * s
                    val ny = y + dir[1] * i * s
                    if (nx !in 0 until N || ny !in 0 until N) break  // 越界
                    val v = b[ny * N + nx]
                    if (v == player) {
                        count++   // 同色棋子，连子数+1
                        i++
                    } else if (v == EMPTY) {
                        openEnds++  // 空格，开放端+1
                        break
                    } else {
                        break  // 对手棋子，堵住了
                    }
                }
            }
            // 根据连子数和开放端数评分
            total += when {
                count >= 5 -> 100000                        // 五连必胜
                count == 4 && openEnds == 2 -> 10000        // 活四（两步内必胜）
                count == 4 && openEnds == 1 -> 5000         // 冲四（对手必须防守）
                count == 3 && openEnds == 2 -> 1000         // 活三（可能发展为活四）
                count == 3 && openEnds == 1 -> 300          // 冲三
                count == 2 && openEnds == 2 -> 100          // 活二
                count == 2 && openEnds == 1 -> 30           // 冲二
                else -> 0
            }
        }
        return total
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 木质圆角底盘（与双人版 GomokuView 同源）
        art.drawWoodSlab(canvas, slabRect, 20f)
        // 网格线 + 星位
        for (i in 0 until N) {
            art.drawGridLine(canvas, boardLeft, boardTop + i * cell,
                boardLeft + (N - 1) * cell, boardTop + i * cell, 1.2f)
            art.drawGridLine(canvas, boardLeft + i * cell, boardTop,
                boardLeft + i * cell, boardTop + (N - 1) * cell, 1.2f)
        }
        for ((sx, sy) in listOf(Pair(3, 3), Pair(11, 3), Pair(3, 11), Pair(11, 11), Pair(7, 7))) {
            art.drawStar(canvas, boardLeft + sx * cell, boardTop + sy * cell, cell * 0.09f)
        }
        // 棋子：哑光玻璃质感
        for (r in 0 until N) for (c in 0 until N) {
            val v = board[r * N + c]
            if (v == EMPTY) continue
            art.drawGlassStone(canvas, boardLeft + c * cell, boardTop + r * cell, stoneR, dark = v == BLACK)
        }
        // 最后一手：金色圆环
        if (lastX >= 0) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Palette.GOLD
                style = Paint.Style.STROKE
                strokeWidth = dp(2.5f)
            }
            canvas.drawCircle(boardLeft + lastX * cell, boardTop + lastY * cell, stoneR * 0.46f, ring)
        }
        // 状态胶囊 + 小字提示
        art.drawStatusBar(canvas, statusRect,
            if (aiThinking) "🤖 电脑思考中…" else "你（黑）落子", Palette.STONE_DARK)
        art.drawCenterText(canvas, "黑方先手，先连成五子者胜",
            hintRect.centerX(), hintRect.centerY(), 11.5f, Palette.DIM, bold = false)
    }

    override fun reset() {
        for (i in board.indices) board[i] = EMPTY
        curPlayer = BLACK
        lastX = -1
        lastY = -1
        over = false
        aiThinking = false
        epoch++   // 作废在途 AI 线程针对旧局面的计算结果
        start()
    }
}
