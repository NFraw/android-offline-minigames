package com.minigames.collection.games.flight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.Toast
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs
import kotlin.math.min

/**
 * 飞行棋（2~4 人同屏）：每人 4 架飞机。
 * 掷到 6 起飞并可再掷，连续三个 6 停一轮；踩到对方飞机整架踢回停机坪，同色可叠机；
 * 绕场一圈进入彩色冲刺道，4 架飞机全部到达中心终点者获胜。
 */
class FlightView(context: Context) : GameView(context) {

    companion object {
        private const val TRACK = 52
        private const val DASH = 5 // 冲刺道格数
        private const val HOME = 57 // 终点（中心）
    }

    private enum class Phase { SELECT, PLAY, OVER }

    // 每玩家 4 架飞机；offset=-1 停机坪，0..51 轨道，52..56 冲刺道，57 终点
    private val pos = Array(4) { IntArray(4) { -1 } }
    private var turn = 0
    private var players = 0
    private var dice = 1
    private var rolling = false
    private var rollTimer = 0f
    private var sixStreak = 0
    private var waitPick = false
    private var phase = Phase.SELECT

    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f
    private val diceBtn = RectF()
    private val selBtns = ArrayList<Pair<RectF, Int>>()

    private val colors = intArrayOf(0xFFE8503A.toInt(), 0xFF37C2F0.toInt(), 0xFF3ED98A.toInt(), 0xFFF5A54A.toInt())
    private val names = arrayOf("红", "蓝", "绿", "黄")

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4668.toInt() }
    private val dicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val maskPaint = Paint().apply { color = 0xCC141A2E.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFFE8EAF0.toInt()
    }
    private val textDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFF1C2333.toInt()
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 32f
        color = 0xFF8A93A6.toInt()
    }
    private val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    override fun onLayout(w: Int, h: Int) {
        boardLeft = w * 0.03f
        boardTop = dp(100).toFloat()
        val boardSize = min(w * 0.94f, (h - dp(100) - dp(230)).toFloat()).coerceAtLeast(1f)
        cell = boardSize / 14f
        val dw = w * 0.34f
        val dh = dp(58).toFloat()
        diceBtn.set(w / 2f - dw / 2f, h - dp(190).toFloat(), w / 2f + dw / 2f, h - dp(190).toFloat() + dh)
        selBtns.clear()
        val bw = dp(100).toFloat()
        val bh = dp(66).toFloat()
        val gap = dp(26).toFloat()
        val totalW = bw * 3 + gap * 2
        for (i in 0..2) {
            selBtns.add(Pair(RectF(w / 2f - totalW / 2f + i * (bw + gap), h * 0.5f, w / 2f - totalW / 2f + i * (bw + gap) + bw, h * 0.5f + bh), i + 2))
        }
        reset()
    }

    // ---------- 棋盘布局 ----------

    private fun startOf(pl: Int): Int = intArrayOf(6, 18, 31, 45)[pl]

    /** 轨道格索引 -> 网格坐标 (gx, gy)，网格 14×14。 */
    private fun trackPos(i: Int): Pair<Int, Int> = when {
        i <= 11 -> Pair(1 + i, 0)
        i == 12 -> Pair(13, 0)
        i <= 24 -> Pair(13, i - 12)
        i == 25 -> Pair(13, 13)
        i <= 37 -> Pair(38 - i, 13)
        i == 38 -> Pair(0, 13)
        i <= 50 -> Pair(0, 51 - i)
        else -> Pair(0, 0)
    }

    /** 冲刺道格（k=1..5，从起飞格向中心）。 */
    private fun dashPos(pl: Int, k: Int): Pair<Int, Int> = when (pl) {
        0 -> Pair(7, k)        // 红：顶边 (7,0) 向下
        1 -> Pair(13 - k, 6)   // 蓝：右边 (13,6) 向左
        2 -> Pair(7, 13 - k)   // 绿：底边 (7,13) 向上
        else -> Pair(k, 6)     // 黄：左边 (0,6) 向右
    }

    /** 停机坪格（每玩家 2×2）。 */
    private fun hangarPos(pl: Int, i: Int): Pair<Int, Int> {
        val gx0 = if (pl == 0 || pl == 3) 1 else 11
        val gy0 = if (pl == 0 || pl == 1) 1 else 11
        return Pair(gx0 + i % 2, gy0 + i / 2)
    }

    private fun cellCenterX(g: Int): Float = boardLeft + (g + 0.5f) * cell
    private fun cellCenterY(g: Int): Float = boardTop + (g + 0.5f) * cell

    // ---------- 交互 ----------

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || paused) return
        when (phase) {
            Phase.SELECT -> {
                for ((rect, n) in selBtns) {
                    if (rect.contains(event.x, event.y)) {
                        players = n
                        phase = Phase.PLAY
                        resetBoard()
                        playSound(SoundManager.CLICK)
                        invalidate()
                        return
                    }
                }
            }
            Phase.PLAY -> {
                if (rolling) return
                if (waitPick) {
                    val i = hitPlane(turn, event.x, event.y)
                    if (i >= 0) doMove(turn, i, dice)
                } else if (diceBtn.contains(event.x, event.y)) {
                    rolling = true
                    rollTimer = 0f
                    playSound(SoundManager.MOVE)
                }
                invalidate()
            }
            else -> {}
        }
    }

    override fun update(dt: Float) {
        if (phase != Phase.PLAY || !rolling) return
        rollTimer += dt
        if (rollTimer < 0.45f) {
            dice = (1..6).random()
            invalidate()
            return
        }
        rolling = false
        resolveRoll()
        invalidate()
    }

    private fun resolveRoll() {
        val n = dice
        if (n == 6) sixStreak++ else sixStreak = 0
        if (sixStreak >= 3) {
            sixStreak = 0
            Toast.makeText(context, "连续三个 6，停一轮", Toast.LENGTH_SHORT).show()
            nextTurn()
            return
        }
        val list = ArrayList<Int>()
        for (i in 0 until 4) if (canMove(turn, i, n)) list.add(i)
        if (list.isEmpty()) {
            nextTurn()
            return
        }
        if (list.size == 1) {
            doMove(turn, list[0], n)
        } else {
            waitPick = true
        }
    }

    private fun canMove(pl: Int, idx: Int, n: Int): Boolean {
        val p = pos[pl][idx]
        if (p < 0) return n == 6 // 停机坪：掷 6 才能起飞
        if (p >= HOME) return false
        return true // 轨道/冲刺道总是可前进（超步弹回）
    }

    private fun doMove(pl: Int, idx: Int, n: Int) {
        waitPick = false
        val p = pos[pl][idx]
        val target: Int = if (p < 0) 0 else {
            var t = p + n
            if (t > HOME) t = HOME - (t - HOME)
            t
        }
        pos[pl][idx] = target
        if (target in 0 until TRACK) {
            val abs = (startOf(pl) + target) % TRACK
            var kicked = false
            for (o in 0 until players) {
                if (o == pl) continue
                for (j in 0 until 4) {
                    val q = pos[o][j]
                    if (q in 0 until TRACK && (startOf(o) + q) % TRACK == abs) {
                        pos[o][j] = -1
                        kicked = true
                    }
                }
            }
            if (kicked) {
                playSound(SoundManager.LOSE)
                Toast.makeText(context, names[pl] + "方踩中对方飞机，踢回停机坪", Toast.LENGTH_SHORT).show()
            }
        }
        playSound(SoundManager.EAT)
        if (pos[pl].all { it >= HOME }) {
            phase = Phase.OVER
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, names[pl] + "方 4 架飞机全部到达终点获胜！"))
            return
        }
        if (n == 6) {
            Toast.makeText(context, "掷到 6，再掷一次", Toast.LENGTH_SHORT).show()
        } else {
            nextTurn()
        }
    }

    private fun nextTurn() {
        sixStreak = 0
        waitPick = false
        turn = (turn + 1) % players
    }

    /** 命中检测：返回被点到的该玩家飞机索引，未命中返回 -1。 */
    private fun hitPlane(pl: Int, x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0 until 4) {
            if (!canMove(pl, i, dice)) continue
            val (px, py) = planeScreenPos(pl, i)
            val dx = px - x
            val dy = py - y
            val d = dx * dx + dy * dy
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        val maxD = cell * cell * 0.45f
        return if (bestD <= maxD) best else -1
    }

    private fun planeScreenPos(pl: Int, idx: Int): Pair<Float, Float> {
        val p = pos[pl][idx]
        return when {
            p == -1 -> {
                val (gx, gy) = hangarPos(pl, idx)
                Pair(cellCenterX(gx), cellCenterY(gy))
            }
            p == HOME -> Pair(boardLeft + 7f * cell, boardTop + 7f * cell)
            p in 0 until TRACK -> {
                val (gx, gy) = trackPos(p)
                Pair(cellCenterX(gx), cellCenterY(gy))
            }
            else -> {
                val (gx, gy) = dashPos(pl, p - DASH)
                Pair(cellCenterX(gx), cellCenterY(gy))
            }
        }
    }

    /** 叠机偏移：同一格多架飞机时错开排列。 */
    private fun stackOffset(pl: Int, idx: Int): Pair<Float, Float> {
        val (x, y) = planeScreenPos(pl, idx)
        val same = ArrayList<Pair<Int, Int>>()
        for (a in 0 until players) for (j in 0 until 4) {
            if (pos[a][j] < 0) continue
            val (ax, ay) = planeScreenPos(a, j)
            if (abs(ax - x) < 0.5f && abs(ay - y) < 0.5f) same.add(Pair(a, j))
        }
        val k = same.indexOf(Pair(pl, idx))
        val d = cell * 0.17f
        return when (k % 4) {
            0 -> Pair(-d, -d)
            1 -> Pair(d, -d)
            2 -> Pair(-d, d)
            else -> Pair(d, d)
        }
    }

    // ---------- 绘制 ----------

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        val bw = cell * 14
        canvas.drawRoundRect(
            RectF(boardLeft - cell * 0.6f, boardTop - cell * 0.6f, boardLeft + bw + cell * 0.6f, boardTop + bw + cell * 0.6f),
            dp(16).toFloat(), dp(16).toFloat(), boardPaint)

        // 中心终点
        val cx = boardLeft + 7f * cell
        val cy = boardTop + 7f * cell
        canvas.drawCircle(cx, cy, cell * 1.05f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() })
        val endText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = cell * 0.85f
            color = 0xFF1C2333.toInt()
        }
        canvas.drawText("终", cx, cy + cell * 0.32f, endText)

        // 轨道格（角格稍大）
        for (i in 0 until TRACK) {
            val (gx, gy) = trackPos(i)
            val isCorner = i == 12 || i == 25 || i == 38 || i == 51
            val r = if (isCorner) cell * 0.32f else cell * 0.28f
            canvas.drawCircle(cellCenterX(gx), cellCenterY(gy), r, dotPaint)
        }

        for (pl in 0 until players) {
            // 起点格标记
            val (sgx, sgy) = trackPos(startOf(pl))
            canvas.drawCircle(cellCenterX(sgx), cellCenterY(sgy), cell * 0.34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[pl] })
            // 冲刺道
            for (k in 1..DASH) {
                val (dgx, dgy) = dashPos(pl, k)
                canvas.drawRoundRect(
                    RectF(cellCenterX(dgx) - cell * 0.36f, cellCenterY(dgy) - cell * 0.36f, cellCenterX(dgx) + cell * 0.36f, cellCenterY(dgy) + cell * 0.36f),
                    dp(5).toFloat(), dp(5).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[pl] })
            }
            // 停机坪
            drawHangar(canvas, pl)
        }

        // 飞机
        for (pl in 0 until players) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[pl] }
            for (i in 0 until 4) {
                val (px, py) = planeScreenPos(pl, i)
                val (ox, oy) = stackOffset(pl, i)
                val dx = px + ox
                val dy = py + oy
                canvas.drawCircle(dx, dy, cell * 0.3f, p)
                if (phase == Phase.PLAY && waitPick && pl == turn && canMove(pl, i, dice)) {
                    canvas.drawCircle(dx, dy, cell * 0.4f, whiteStroke)
                }
                smallPaint.color = 0xFFFFFFFF.toInt()
                smallPaint.textSize = cell * 0.34f
                canvas.drawText((i + 1).toString(), dx, dy + cell * 0.12f, smallPaint)
            }
        }

        if (phase == Phase.PLAY) {
            // 玩家进度
            val sb = StringBuilder()
            for (pl in 0 until players) {
                val done = pos[pl].count { it >= HOME }
                if (pl > 0) sb.append("   ")
                sb.append(names[pl]).append(":").append(done).append("/4")
            }
            smallPaint.textSize = 34f
            canvas.drawText(sb.toString(), width / 2f, diceBtn.top - dp(78).toFloat(), smallPaint)
            // 状态提示
            textPaint.textSize = 42f
            val tip = when {
                rolling -> "掷骰中…"
                waitPick -> "玩家 " + (turn + 1) + "（" + names[turn] + "方）请点选要移动的飞机"
                else -> "玩家 " + (turn + 1) + "（" + names[turn] + "方）点击骰子掷骰"
            }
            canvas.drawText(tip, width / 2f, diceBtn.top - dp(40).toFloat(), textPaint)
            // 骰子
            canvas.drawRoundRect(diceBtn, dp(14).toFloat(), dp(14).toFloat(), dicePaint)
            textDarkPaint.textSize = 59f
            canvas.drawText(dice.toString(), diceBtn.centerX(), diceBtn.centerY() + dp(8).toFloat(), textDarkPaint)
            smallPaint.textSize = 32f
            canvas.drawText("掷 6 起飞再掷 · 踩中对方踢回 · 4 架全到终点获胜", width / 2f, diceBtn.bottom + dp(42).toFloat(), smallPaint)
        }

        if (phase == Phase.SELECT) {
            canvas.drawRect(gameRect, maskPaint)
            textPaint.textSize = 62f
            canvas.drawText("✈️ 飞行棋", width / 2f, height * 0.3f, textPaint)
            textPaint.textSize = 42f
            canvas.drawText("每人 4 架飞机，掷 6 起飞 · 4 架全到终点获胜", width / 2f, height * 0.3f + dp(46).toFloat(), textPaint)
            for ((rect, n) in selBtns) {
                canvas.drawRoundRect(rect, dp(16).toFloat(), dp(16).toFloat(), btnPaint)
                textPaint.textSize = 53f
                canvas.drawText(n.toString() + " 人", rect.centerX(), rect.centerY() + dp(4).toFloat(), textPaint)
            }
        }
    }

    private fun drawHangar(canvas: Canvas, pl: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = (colors[pl] and 0xFFFFFF) or 0x33000000.toInt()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors[pl]
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        for (i in 0 until 4) {
            val (gx, gy) = hangarPos(pl, i)
            val x = boardLeft + gx * cell
            val y = boardTop + gy * cell
            val rect = RectF(x + 1.5f, y + 1.5f, x + cell - 1.5f, y + cell - 1.5f)
            canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), fill)
            canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), stroke)
        }
    }

    private fun resetBoard() {
        for (pl in 0 until 4) for (i in 0 until 4) pos[pl][i] = -1
        turn = 0
        dice = 1
        rolling = false
        sixStreak = 0
        waitPick = false
    }

    override fun reset() {
        resetBoard()
        phase = if (players == 0) Phase.SELECT else Phase.PLAY
        start()
    }
}
