package com.minigames.collection.games.curling

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.hypot

/**
 * 冰壶（2~4 人同屏轮流）：开局先选人数。
 * 按住蓄力（蓄力条来回摆动），左右滑动调整投出位置与瞄准角度，松手投壶；
 * 冰壶之间会真实碰撞推挤（反弹克制、不夸张）；离圆心最近的一方按壶计分，三局定胜负。
 */
class CurlingView(context: Context) : GameView(context) {

    companion object {
        private const val ROUNDS = 3             // 总局数
        private const val STONES_PER_PLAYER = 2  // 每人每局投壶数
    }

    private enum class Phase { SELECT, THROW, MOVING, ROUND_END, OVER }

    private lateinit var physics: CurlingPhysics
    private var centerX = 0f
    private var centerY = 0f
    private var ringR = 0f
    private var stoneR = 0f
    private val sheet = RectF()

    // 状态
    private var phase = Phase.SELECT
    private var players = 0
    private var turn = 0
    private var round = 1
    private val score = IntArray(4)
    private val thrownCount = IntArray(4)
    private var throwLeft = STONES_PER_PLAYER
    private var roundEndT = 0f
    private var roundResult = ""
    private var ranking = ""

    // 蓄力 / 瞄准
    private var charging = false
    private var chargeT = 0f
    private var power = 0f
    private var launchX = 0f
    private var aimOffset = 0f
    // 辅助虚线：第一触点 → 当前拖拽点
    private var aimStartX = 0f
    private var aimStartY = 0f
    private var aimCurX = 0f
    private var aimCurY = 0f

    // 选择界面按钮：2 / 3 / 4 人
    private val selBtns = ArrayList<Pair<RectF, Int>>()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val icePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8F4FB.toInt() }
    private val iceLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC9DEEE.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8503A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8503A.toInt()
        style = Paint.Style.FILL
    }
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD54F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD54F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(dp(12).toFloat(), dp(8).toFloat()), 0f)
    }
    private val aimDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD54F.toInt()
        style = Paint.Style.FILL
    }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x332A3557.toInt() }
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD54F.toInt() }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val btnActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val maskPaint = Paint().apply { color = 0xCC141A2E.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
        color = 0xFF1C2333.toInt()
    }
    private val textLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFFE8EAF0.toInt()
    }
    private val smallLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 36f
        color = 0xFF8A93A6.toInt()
    }
    private val stonePaints = ArrayList<Paint>().apply {
        add(Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() })   // 红
        add(Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() })   // 蓝
        add(Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() })   // 绿
        add(Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5A54A.toInt() })   // 金
    }
    private val playerNames = arrayOf("红方", "蓝方", "绿方", "金方")

    override fun onLayout(w: Int, h: Int) {
        centerX = w / 2f
        centerY = h * 0.28f
        ringR = min(w, h) * 0.19f
        stoneR = dp(18).toFloat()
        sheet.set(dp(24).toFloat(), dp(84).toFloat(), w - dp(24).toFloat(), h - dp(24).toFloat())
        if (!::physics.isInitialized) physics = CurlingPhysics(stoneR)
        physics.left = sheet.left
        physics.top = sheet.top
        physics.right = sheet.right
        physics.bottom = sheet.bottom
        // 选择按钮
        selBtns.clear()
        val bw = dp(96).toFloat()
        val bh = dp(64).toFloat()
        val gap = dp(24).toFloat()
        val totalW = bw * 3 + gap * 2
        for (i in 0..2) {
            selBtns.add(Pair(
                RectF(w / 2f - totalW / 2f + i * (bw + gap), h * 0.56f, w / 2f - totalW / 2f + i * (bw + gap) + bw, h * 0.56f + bh),
                i + 2
            ))
        }
        reset()
    }

    // ---------------- 触摸 ----------------

    override fun onTouch(event: MotionEvent) {
        if (paused) return
        when (phase) {
            Phase.SELECT -> {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    for ((rect, n) in selBtns) {
                        if (rect.contains(event.x, event.y)) {
                            players = n
                            playSound(SoundManager.CLICK)
                            startMatch()
                            invalidate()
                            return
                        }
                    }
                }
            }
            Phase.THROW -> when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    charging = true
                    chargeT = 0f
                    power = 0f
                    aimStartX = event.x
                    aimStartY = event.y
                    aimCurX = event.x
                    aimCurY = event.y
                    launchX = event.x.coerceIn(sheet.left + stoneR, sheet.right - stoneR)
                    aimOffset = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    aimCurX = event.x
                    aimCurY = event.y
                    launchX = event.x.coerceIn(sheet.left + stoneR, sheet.right - stoneR)
                    aimOffset = (event.x - launchX) * 1.4f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (charging) {
                        charging = false
                        if (power > 0.08f) {
                            throwStone()
                        } else {
                            playSound(SoundManager.CLICK)
                        }
                    }
                }
            }
            else -> {}
        }
        invalidate()
    }

    private fun throwStone() {
        val sx = launchX
        val sy = sheet.bottom - stoneR
        val tx = centerX + aimOffset
        val ty = centerY
        val dx = tx - sx
        val dy = ty - sy
        val len = hypot(dx, dy)
        if (len < 1f) return
        val v = power * sheet.height() * 1.35f
        physics.addStone(sx, sy, dx / len * v, dy / len * v, turn)
        playSound(SoundManager.MOVE)
        phase = Phase.MOVING
        invalidate()
    }

    // ---------------- 逻辑 ----------------

    override fun update(dt: Float) {
        when (phase) {
            Phase.THROW -> {
                if (charging) {
                    chargeT += dt
                    power = 0.5f + 0.5f * Math.sin(chargeT * 3.0).toFloat() // 0..1 摆动
                    invalidate()
                }
            }
            Phase.MOVING -> {
                val anyMoving = physics.step(dt, 7f)
                // 显著碰撞：音效 + 震动（冲击速度阈值过滤轻微刮擦）
                if (physics.lastImpactSpeed >= 90f) playSound(SoundManager.CLICK)
                if (physics.lastImpactSpeed >= 220f) buzz()
                if (!anyMoving) {
                    throwLeft--
                    thrownCount[turn]++
                    if (throwLeft > 0) {
                        turn = nextPlayer(turn)
                        phase = Phase.THROW
                    } else {
                        scoreRound()
                        phase = Phase.ROUND_END
                        roundEndT = 0f
                        playSound(SoundManager.EAT)
                    }
                }
                invalidate()
            }
            Phase.ROUND_END -> {
                roundEndT += dt
                if (roundEndT >= 2.6f) {
                    if (round >= ROUNDS) {
                        finishMatch()
                    } else {
                        round++
                        physics.clear()
                        for (i in thrownCount.indices) thrownCount[i] = 0
                        throwLeft = players * STONES_PER_PLAYER
                        turn = (round - 1) % players
                        phase = Phase.THROW
                    }
                    invalidate()
                }
            }
            else -> {}
        }
    }

    private fun nextPlayer(from: Int): Int {
        var p = (from + 1) % players
        // 每人每局投 STONES_PER_PLAYER 壶，按 turn 顺序轮转即可（无跳过条件）
        return p
    }

    /** 离圆心最近的一方，比对方最近壶更近的己方壶每壶计 1 分。 */
    private fun scoreRound() {
        var bestP = -1
        var bestD = Float.MAX_VALUE
        val bestOf = FloatArray(players) { Float.MAX_VALUE }
        for (s in physics.stones) {
            val d = hypot(s.x - centerX, s.y - centerY)
            if (d < bestOf[s.player]) bestOf[s.player] = d
            if (d < bestD) { bestD = d; bestP = s.player }
        }
        if (bestP < 0) return
        var second = Float.MAX_VALUE
        for (p in 0 until players) {
            if (p != bestP && bestOf[p] < second) second = bestOf[p]
        }
        var points = 0
        for (s in physics.stones) {
            if (s.player == bestP && hypot(s.x - centerX, s.y - centerY) < second) points++
        }
        score[bestP] += points
        roundResult = playerNames[bestP] + " 本轮得 " + points + " 分"
        speakVoice("player" + (bestP + 1))
    }

    private fun finishMatch() {
        phase = Phase.OVER
        // 排名
        val order = (0 until players).sortedWith(compareByDescending<Int> { score[it] }.thenBy { it })
        val sb = StringBuilder()
        for ((i, p) in order.withIndex()) {
            sb.append(if (i > 0) "，" else "").append("第 ").append(i + 1).append(" 名 ").append(playerNames[p]).append(" ").append(score[p]).append(" 分")
        }
        ranking = sb.toString()
        val top = score[order[0]]
        val champs = order.filter { score[it] == top }
        playSound(SoundManager.WIN)
        val title = if (champs.size > 1) {
            champs.joinToString("、") { playerNames[it] } + " 并列冠军！"
        } else {
            playerNames[champs[0]] + " 获胜！"
        }
        gameOver(GameResult(0, true, "三局结束：\n" + ranking + "\n\n" + title))
    }

    // ---------------- 绘制 ----------------

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 冰面
        canvas.drawRoundRect(sheet, dp(18).toFloat(), dp(18).toFloat(), icePaint)
        canvas.drawRoundRect(RectF(sheet.left + 2, sheet.top + 2, sheet.right - 2, sheet.bottom - 2), dp(17).toFloat(), dp(17).toFloat(), iceLinePaint)
        // 同心圆（蓝-白-红-白）
        val ringColors = intArrayOf(0xFF4A6CF7.toInt(), 0xFFFFFFFF.toInt(), 0xFFE8503A.toInt(), 0xFFFFFFFF.toInt())
        for (i in 0..3) {
            ringPaint.color = ringColors[i]
            canvas.drawCircle(centerX, centerY, ringR * (4 - i) / 4f, ringPaint)
        }
        canvas.drawCircle(centerX, centerY, dp(9).toFloat(), centerPaint)
        // 中线
        canvas.drawLine(sheet.left, centerY, sheet.right, centerY, iceLinePaint)

        // 投壶区提示线
        canvas.drawLine(sheet.left + dp(10).toFloat(), sheet.bottom - dp(30).toFloat(), sheet.right - dp(10).toFloat(), sheet.bottom - dp(30).toFloat(), iceLinePaint)

        // 冰壶
        for (s in physics.stones) {
            val p = stonePaints[s.player]
            canvas.drawCircle(s.x, s.y, stoneR, p)
            canvas.drawCircle(s.x, s.y, stoneR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x33000000.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f
            })
            canvas.drawCircle(s.x, s.y, stoneR * 0.28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF.toInt() })
            textPaint.color = 0xFFFFFFFF.toInt()
            textPaint.textSize = stoneR * 1.05f
            canvas.drawText((s.player + 1).toString(), s.x, s.y - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
        }

        // 蓄力瞄准预览
        if (phase == Phase.THROW && charging) {
            val sy = sheet.bottom - stoneR
            val tx = centerX + aimOffset
            val ty = centerY
            // 辅助虚线：第一触点 → 拖拽点，两点一线；长度不超过起点到圆心距离的 1/4
            val dragDx = aimCurX - aimStartX
            val dragDy = aimCurY - aimStartY
            val dragLen = hypot(dragDx, dragDy)
            if (dragLen > 4f) {
                val maxLen = hypot(aimStartX - centerX, aimStartY - centerY) * 0.25f
                val len = Math.min(dragLen, maxLen)
                val ex = aimStartX + dragDx / dragLen * len
                val ey = aimStartY + dragDy / dragLen * len
                canvas.drawLine(aimStartX, aimStartY, ex, ey, dashPaint)
                canvas.drawCircle(aimStartX, aimStartY, dp(6).toFloat(), aimDotPaint)
            }
            // 目标点
            canvas.drawCircle(tx, ty, dp(6).toFloat(), aimPaint)
            // 虚拟冰壶
            ghostPaint.color = 0xFFFFD54F.toInt()
            canvas.drawCircle(launchX, sy, stoneR, ghostPaint)
            // 蓄力条
            val barW = sheet.width() * 0.6f
            val barH = dp(16).toFloat()
            val barX = centerX - barW / 2f
            val barY = sheet.bottom + dp(14).toFloat()
            canvas.drawRoundRect(RectF(barX, barY, barX + barW, barY + barH), barH / 2f, barH / 2f, barBgPaint)
            canvas.drawRoundRect(RectF(barX, barY, barX + barW * power, barY + barH), barH / 2f, barH / 2f, barFillPaint)
            textLightPaint.textSize = 37f
            canvas.drawText("力度 " + (power * 100).toInt() + "% · 松手投壶", centerX, barY + barH + dp(26).toFloat(), textLightPaint)
        }

        // 顶部状态
        when (phase) {
            Phase.THROW -> {
                textLightPaint.textSize = 44f
                canvas.drawText("第 " + round + " 局 · " + playerNames[turn] + " 投壶（剩 " + (STONES_PER_PLAYER - thrownCount[turn]) + " 壶）", width / 2f, dp(52).toFloat(), textLightPaint)
                smallLightPaint.textSize = 36f
                canvas.drawText("按住蓄力 · 左右滑动选投出位置 · 松手投出", width / 2f, dp(76).toFloat(), smallLightPaint)
            }
            Phase.MOVING -> {
                textLightPaint.textSize = 44f
                canvas.drawText("第 " + round + " 局 · 冰壶滑行中…", width / 2f, dp(52).toFloat(), textLightPaint)
            }
            Phase.ROUND_END -> {
                textLightPaint.textSize = 46f
                canvas.drawText(roundResult, width / 2f, height * 0.45f, textLightPaint)
                textLightPaint.textSize = 41f
                canvas.drawText((2.6f - roundEndT).toInt().coerceAtLeast(0).toString() + " 秒后进入下一局", width / 2f, height * 0.45f + dp(40).toFloat(), textLightPaint)
            }
            else -> {}
        }

        // 记分板
        drawScoreboard(canvas)

        // 开局选人数
        if (phase == Phase.SELECT) {
            canvas.drawRect(gameRect, maskPaint)
            textLightPaint.textSize = 62f
            canvas.drawText("🥌 冰壶多人对战", width / 2f, height * 0.30f, textLightPaint)
            textLightPaint.textSize = 44f
            canvas.drawText("开局先选玩家人数", width / 2f, height * 0.30f + dp(44).toFloat(), textLightPaint)
            for ((rect, n) in selBtns) {
                canvas.drawRoundRect(rect, dp(16).toFloat(), dp(16).toFloat(), btnPaint)
                textLightPaint.textSize = 53f
                canvas.drawText(n.toString() + " 人", rect.centerX(), rect.centerY() + dp(4).toFloat(), textLightPaint)
            }
            smallLightPaint.textSize = 36f
            canvas.drawText("支持 2 / 3 / 4 人同屏轮流投壶", width / 2f, height * 0.56f + dp(84).toFloat(), smallLightPaint)
        }
    }

    private fun drawScoreboard(canvas: Canvas) {
        if (phase == Phase.SELECT || phase == Phase.OVER) return
        val chipW = dp(74).toFloat()
        val chipH = dp(34).toFloat()
        val gap = dp(8).toFloat()
        val totalW = players * chipW + (players - 1) * gap
        val x0 = width / 2f - totalW / 2f
        val y0 = dp(96).toFloat()
        for (p in 0 until players) {
            val x = x0 + p * (chipW + gap)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (p == turn && (phase == Phase.THROW || phase == Phase.MOVING)) {
                    (stonePaints[p].color and 0x00FFFFFF.toInt()) or 0xFF000000.toInt()
                } else 0x552A3557.toInt()
            }
            canvas.drawRoundRect(RectF(x, y0, x + chipW, y0 + chipH), dp(10).toFloat(), dp(10).toFloat(), fill)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = stonePaints[p].color }
            canvas.drawCircle(x + dp(14).toFloat(), y0 + chipH / 2f, dp(8).toFloat(), dot)
            textLightPaint.textSize = 37f
            canvas.drawText(score[p].toString(), x + chipW - dp(16).toFloat(), y0 + chipH / 2f + dp(3).toFloat(), textLightPaint)
        }
    }

    override fun reset() {
        physics.clear()
        for (i in score.indices) score[i] = 0
        for (i in thrownCount.indices) thrownCount[i] = 0
        round = 1
        throwLeft = players * STONES_PER_PLAYER
        turn = 0
        charging = false
        power = 0f
        roundResult = ""
        ranking = ""
        phase = if (players == 0) Phase.SELECT else Phase.THROW
        start()
    }

    private fun startMatch() {
        physics.clear()
        for (i in score.indices) score[i] = 0
        for (i in thrownCount.indices) thrownCount[i] = 0
        round = 1
        throwLeft = players * STONES_PER_PLAYER
        turn = 0
        charging = false
        power = 0f
        roundResult = ""
        phase = Phase.THROW
        invalidate()
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}
