package com.minigames.collection.games.curling

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.BoardArt
import com.minigames.collection.common.BoardArt.Palette
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * 冰壶（2~4 人同屏轮流）：开局先选人数。
 * 按住蓄力（蓄力条来回摆动），左右滑动调整投出位置与瞄准角度，松手投壶；
 * 冰壶之间会真实碰撞推挤（反弹克制、不夸张）；离圆心最近的一方按壶计分。
 * 局数随人数：2 人 3 局、3 人 4 局、4 人 5 局；下一局先投者为上一局赢家，首局按玩家顺序。
 *
 * 【美术与排版说明】统一到 BoardArt 的「扁平柔和卡通风」：
 *   - 冰面改为带柔和圆角与内描边的整块浅色冰道，营垒同心圆配色更柔和（不再用纯红纯蓝硬圆）。
 *   - 冰壶改为「软投影 + 球面渐变 + 顶部高光 + 中心白色圆盘」的立体壶形，
 *     替代原来「纯色圆 + 半透明小圆」的扁平画法。
 *   - 蓄力条改为圆角胶囊 + 渐变填充 + 百分比文字，出发点和瞄准线更清晰。
 *   - 顶部状态与记分板改为圆角胶囊，当前玩家高亮；选人界面改为卡片式。
 *   - 所有文字避开顶部悬浮控件安全区（SAFE_TOP_DP）与右下角得分胶囊区间。
 */
class CurlingView(context: Context) : GameView(context) {

    companion object {
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
    private var rounds = 3             // 总局数：2 人 3 局、3 人 4 局、4 人 5 局
    private var lastWinner = 0         // 上一局赢家，作为下一局先投者
    private val score = IntArray(4)
    private val thrownCount = IntArray(4)
    private var throwLeft = STONES_PER_PLAYER
    private var roundEndT = 0f
    private var roundResult = ""
    private var ranking = ""
    /** 是否已经完成首次布局（用于区分"首次初始化"与"尺寸变化"，避免中途清空比赛）。 */
    private var firstLayout = true

    // 蓄力 / 瞄准
    private var charging = false
    private var chargeT = 0f
    private var power = 0f

    /**
     * 瞄准几何（按下点为固定支点，瞄准线延长到底线求交点）：
     *   - [pivotX]/[pivotY]：按下那一刻的**固定支点** P，整个手势期间不动；
     *   - [aimCurX]/[aimCurY]：当前触点 F，随手指移动；
     *   - [launchX]：抛出点 L 的 x 坐标 —— 直线 P-F 延长后与发射底线 y 的交点，
     *     由 [solveAim] 每帧重算，因此会随瞄准线角度沿底线滑动（不再是按下点的垂直投影）；
     *   - [dirX]/[dirY]：投出方向（单位向量），从 L 指向 P，即沿瞄准线朝场地内。
     */
    private var pivotX = 0f
    private var pivotY = 0f
    private var launchX = 0f
    private var dirX = 0f
    private var dirY = -1f
    private var aimCurX = 0f
    private var aimCurY = 0f
    /** 本次手势的瞄准是否已解算成功（未拖出有效距离时为 false，画面不画虚壶与球路）。 */
    private var aimValid = false

    // 选择界面按钮：2 / 3 / 4 人
    private val selBtns = ArrayList<Pair<RectF, Int>>()

    /** 美术工具箱：统一木盘/胶囊/文字绘制。 */
    private val art = BoardArt(this)

    private val bgPaint = Paint().apply { color = Palette.BG }
    private val icePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8F4FB.toInt() }
    private val iceLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFC9DEEE.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8503A.toInt()
        style = Paint.Style.FILL
    }
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.GOLD
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.GOLD
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(dp(12f), dp(8f)), 0f)
    }
    private val aimDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.GOLD
        style = Paint.Style.FILL
    }
    /** 抛出点外环 / 支点标记（半透明金色描边）。 */
    private val aimRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88F5C542.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    /** 支点十字线。 */
    private val aimCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAAF5C542.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
    }
    /** 发射底线（抛出点必须落在这条线上）。 */
    private val aimBaselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66F5C542.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(dp(6f), dp(6f)), 0f)
    }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val maskPaint = Paint().apply { color = 0xCC141A2E.toInt() }
    /** 冰壶配色（红/蓝/绿/金），与飞行棋等其他桌游的阵营色一致。 */
    private val stoneColors = intArrayOf(
        0xFFE8503A.toInt(), 0xFF3E8BF0.toInt(), 0xFF3ED98A.toInt(), 0xFFF5A54A.toInt())
    private val playerNames = arrayOf("红方", "蓝方", "绿方", "金方")

    override fun onLayout(w: Int, h: Int) {
        // 顶部让出悬浮控件安全区，底部给蓄力条与提示留位置
        centerY = dp(SAFE_TOP_DP).toFloat() + (h - dp(SAFE_TOP_DP).toFloat()) * 0.16f
        centerX = w / 2f
        ringR = min(w, h) * 0.19f
        stoneR = dp(19f)
        sheet.set(dp(20f), dp(SAFE_TOP_DP + 62).toFloat(), w - dp(20f), h - dp(56f))
        if (!::physics.isInitialized) physics = CurlingPhysics(stoneR)
        physics.left = sheet.left
        physics.top = sheet.top
        physics.right = sheet.right
        physics.bottom = sheet.bottom
        // 选择卡片：横排三张
        selBtns.clear()
        val cardW = min((w - dp(70f)) / 3.6f, dp(108f))
        val cardH = cardW * 1.2f
        val gap = dp(14f)
        val totalW = cardW * 3 + gap * 2
        val cardY = height * 0.5f - cardH / 2f
        for (i in 0..2) {
            selBtns.add(Pair(
                RectF(w / 2f - totalW / 2f + i * (cardW + gap), cardY,
                    w / 2f - totalW / 2f + i * (cardW + gap) + cardW, cardY + cardH),
                i + 2))
        }
        // 只在首次布局时开局。onLayout 在尺寸变化（分屏、系统栏内缩）时也会被调用，
        // 旧实现每次都 reset()，会把进行中的比分、局数与投壶记录全部清空、回到第 1 局。
        if (firstLayout) {
            firstLayout = false
            reset()
        }
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
                    // 按下点即本次投壶的**固定支点** P，整个手势期间不再改变。
                    // 抛出点由 P 与手指连线延长到底线求交得到（见 solveAim）。
                    pivotX = event.x
                    pivotY = event.y
                    aimCurX = event.x
                    aimCurY = event.y
                    // 未拖动前先给一个默认抛出点（支点的垂直投影），让虚壶有个初始位置
                    launchX = event.x.coerceIn(sheet.left + stoneR, sheet.right - stoneR)
                    aimValid = false
                }
                MotionEvent.ACTION_MOVE -> {
                    // 只更新手指位置，支点保持不动；瞄准线与抛出点每帧重新解算
                    aimCurX = event.x
                    aimCurY = event.y
                    solveAim()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (charging) {
                        charging = false
                        if (aimValid && power > 0.08f) {
                            throwStone()
                        } else {
                            // 没有有效瞄准（手指没拖开）或力度太小：不算一次投壶
                            playSound(SoundManager.CLICK)
                            aimValid = false
                        }
                    }
                }
            }
            else -> {}
        }
        invalidate()
    }

    /**
     * 解算瞄准几何：**按下点为固定支点 P**，手指当前位置为 F。
     *
     * 直线 P-F 延长后与发射底线（y = sheet.bottom - stoneR）的交点，就是本次的**抛出点 L**。
     * 因此 L 会随瞄准线角度的变化沿底线滑动，而不是固定为按下点的垂直投影。
     * 投出方向 = 从 L 指向 P 并继续向前（即朝场地内、指向营垒一侧）。
     *
     * 以下情况判定为「无效瞄准」（aimValid=false，画面不画球路、松手也不投壶）：
     *   - 手指几乎没动（拖动距离过小，方向无从确定）；
     *   - 连线与底线平行（P 与 F 等高，永不相交）；
     *   - 支点落在底线之下（方向会朝场外）。
     */
    private fun solveAim() {
        val baselineY = sheet.bottom - stoneR
        val dxp = aimCurX - pivotX
        val dyp = aimCurY - pivotY
        // 拖动距离过小：方向不可靠
        if (hypot(dxp, dyp) < dp(12f)) {
            aimValid = false
            return
        }
        // P 与 F 等高 → 连线与底线平行，没有交点
        if (abs(dyp) < 0.5f) {
            aimValid = false
            return
        }
        // 参数方程 P + s·(F − P)，令 y = baselineY 解出 s，再求交点 x
        val s = (baselineY - pivotY) / dyp
        var lx = pivotX + s * dxp
        // 抛出点必须落在冰道内
        lx = lx.coerceIn(sheet.left + stoneR, sheet.right - stoneR)

        // 方向：从抛出点 L 指向支点 P（继续延伸即球路）
        val vx = pivotX - lx
        val vy = pivotY - baselineY
        val len = hypot(vx, vy)
        // 方向必须朝场地内（vy < 0 表示向上）；否则说明支点在底线之下
        if (len < 1f || vy >= 0f) {
            aimValid = false
            return
        }
        launchX = lx
        dirX = vx / len
        dirY = vy / len
        aimValid = true
    }

    /**
     * 按已解算好的抛出点与方向投壶。
     * 方向来自 [solveAim]（瞄准线延长线与底线的交点 → 支点），不再用「当前触点 − 抛出点」现算：
     * 抛出点本身是从这条线解出来的，若在这里再拿触点减一次，等于把同一个几何关系重复施加一遍，
     * 球路会偏离玩家看到的瞄准线。
     */
    private fun throwStone() {
        if (!aimValid) return
        val sx = launchX
        val sy = sheet.bottom - stoneR
        val v = power * sheet.height() * 1.35f
        physics.addStone(sx, sy, dirX * v, dirY * v, turn)
        playSound(SoundManager.MOVE)
        phase = Phase.MOVING
        aimValid = false
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
                val anyMoving = physics.step(dt, 18f)
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
                    if (round >= rounds) {
                        finishMatch()
                    } else {
                        round++
                        physics.clear()
                        for (i in thrownCount.indices) thrownCount[i] = 0
                        throwLeft = players * STONES_PER_PLAYER
                        // 下一局先投者 = 上一局赢家；首局先投者按玩家顺序（startMatch 里 turn=0）
                        turn = lastWinner
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

    /**
     * 离圆心最近的一方计分：只有停在营垒（大本营）范围内的壶才参与比较，
     * 比对方最近壶更近的己方壶每壶计 1 分。
     *
     * 修复点：旧实现不对范围做限制，停在场边远端的壶照样可能是"最近壶"并计分，
     * 与真实冰壶"营垒外的壶不得分"不符，而且永远不可能出现 0:0 的空局。
     */
    private fun scoreRound() {
        var bestP = -1
        var bestD = Float.MAX_VALUE
        val bestOf = FloatArray(players) { Float.MAX_VALUE }
        for (s in physics.stones) {
            val d = hypot(s.x - centerX, s.y - centerY)
            if (d > ringR) continue   // 营垒外的壶不计分
            if (d < bestOf[s.player]) bestOf[s.player] = d
            if (d < bestD) { bestD = d; bestP = s.player }
        }
        if (bestP < 0) {
            // 本局无人把壶投进营垒 → 空局，双方 0 分，先手顺延
            roundResult = "本局无人进营垒，0 : 0 空局"
            return
        }
        var second = Float.MAX_VALUE
        for (p in 0 until players) {
            if (p != bestP && bestOf[p] < second) second = bestOf[p]
        }
        var points = 0
        for (s in physics.stones) {
            val d = hypot(s.x - centerX, s.y - centerY)
            if (s.player == bestP && d <= ringR && d < second) points++
        }
        score[bestP] += points
        lastWinner = bestP
        roundResult = playerNames[bestP] + " 本轮得 " + points + " 分，下局先投"
        playSound(SoundManager.EAT)
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
        gameOver(GameResult(0, true, "$rounds 局结束：\n" + ranking + "\n\n" + title))
    }

    // ---------------- 绘制 ----------------

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 冰面：大圆角冰道 + 内描边
        canvas.drawRoundRect(sheet, dp(20f), dp(20f), icePaint)
        canvas.drawRoundRect(RectF(sheet.left + 3f, sheet.top + 3f, sheet.right - 3f, sheet.bottom - 3f),
            dp(18f), dp(18f), iceLinePaint)
        // 同心圆营垒（蓝-白-红-白）：改用填充色块，视觉更柔和
        val ringFills = intArrayOf(0xFF4A6CF7.toInt(), 0xFFFFFFFF.toInt(), 0xFFE8503A.toInt(), 0xFFFFFFFF.toInt())
        val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0..3) {
            fillP.color = ringFills[i]
            canvas.drawCircle(centerX, centerY, ringR * (4 - i) / 4f, fillP)
        }
        canvas.drawCircle(centerX, centerY, dp(8f), centerPaint)
        // 中线与投壶区提示线
        canvas.drawLine(sheet.left + dp(12f), centerY, sheet.right - dp(12f), centerY, iceLinePaint)
        canvas.drawLine(sheet.left + dp(12f), sheet.bottom - dp(34f), sheet.right - dp(12f), sheet.bottom - dp(34f), iceLinePaint)

        // 冰壶：软投影 + 球面渐变 + 顶部高光 + 中心白盘
        for (s in physics.stones) {
            drawStone(canvas, s.x, s.y, stoneColors[s.player], (s.player + 1).toString())
        }

        // 蓄力瞄准预览
        if (phase == Phase.THROW && charging) {
            val sy = sheet.bottom - stoneR
            // 发射底线：把抛出点所在的这条线画出来，玩家才能看懂"交点即抛出点"
            canvas.drawLine(sheet.left + dp(12f), sy, sheet.right - dp(12f), sy, aimBaselinePaint)
            if (aimValid) {
                // 球路：从抛出点沿瞄准线一直延长到支点，并再往场地内延伸一段（示意出球方向）
                val far = hypot(sheet.width(), sheet.height())
                val ex = pivotX + dirX * far
                val ey = pivotY + dirY * far
                canvas.drawLine(launchX, sy, ex, ey, dashPaint)
                // 抛出点：底部实心圆点 + 外环，强调"球从这里出发"
                canvas.drawCircle(launchX, sy, dp(8f), aimDotPaint)
                canvas.drawCircle(launchX, sy, dp(13f), aimRingPaint)
                // 支点：按下点（固定不动），用小十字标出
                canvas.drawCircle(pivotX, pivotY, dp(7f), aimRingPaint)
                canvas.drawLine(pivotX - dp(12f), pivotY, pivotX + dp(12f), pivotY, aimCrossPaint)
                canvas.drawLine(pivotX, pivotY - dp(12f), pivotX, pivotY + dp(12f), aimCrossPaint)
                // 手指当前位置
                canvas.drawCircle(aimCurX, aimCurY, dp(6f), aimPaint)
            }
            // 虚拟冰壶（画在解算出的抛出点上，随瞄准线滑动）
            ghostPaint.color = Palette.GOLD
            canvas.drawCircle(launchX, sy, stoneR, ghostPaint)
            // 蓄力条：圆角胶囊 + 渐变填充 + 百分比
            val barW = sheet.width() * 0.62f
            val barH = dp(18f)
            val barX = centerX - barW / 2f
            val barY = sheet.bottom + dp(6f)
            val barRect = RectF(barX, barY, barX + barW, barY + barH)
            art.drawPill(canvas, barRect, 0xCC1C2333.toInt(), 0x33FFFFFF, 1f)
            if (power > 0.02f) {
                art.drawPill(canvas, RectF(barX, barY, barX + barW * power, barY + barH), Palette.GOLD)
            }
            val tip = if (aimValid) "力度 " + (power * 100).toInt() + "% · 松手投壶"
                      else "按住并拖动手指，瞄准线延长到底线即为抛出点"
            art.drawCenterText(canvas, tip, centerX, barY + barH + dp(20f), 12f, Palette.TEXT_LIGHT)
        }

        // 顶部状态胶囊
        when (phase) {
            Phase.THROW -> {
                val bar = RectF(width * 0.07f, dp(SAFE_TOP_DP + 6).toFloat(),
                    width * 0.93f, dp(SAFE_TOP_DP + 44).toFloat())
                art.drawStatusBar(canvas, bar,
                    "第 " + round + " 局 · " + playerNames[turn] + " 投壶（剩 " + (STONES_PER_PLAYER - thrownCount[turn]) + " 壶）",
                    stoneColors[turn])
                art.drawCenterText(canvas, "按住蓄力 · 拖动手指定方向（瞄准线延长到底线的交点即抛出点）",
                    width / 2f, bar.bottom + dp(18f), 11.5f, Palette.DIM, bold = false)
            }
            Phase.MOVING -> {
                val bar = RectF(width * 0.07f, dp(SAFE_TOP_DP + 6).toFloat(),
                    width * 0.93f, dp(SAFE_TOP_DP + 44).toFloat())
                art.drawStatusBar(canvas, bar, "第 " + round + " 局 · 冰壶滑行中…", stoneColors[turn])
            }
            Phase.ROUND_END -> {
                // 回合结束信息绘制在浅色冰面上，必须用深色文字才看得清
                art.drawCenterTextShadow(canvas, roundResult, width / 2f, height * 0.45f, 16f, Palette.TEXT)
                art.drawCenterTextShadow(canvas,
                    (2.6f - roundEndT).toInt().coerceAtLeast(0).toString() + " 秒后进入下一局",
                    width / 2f, height * 0.45f + dp(32f), 13f, Palette.DIM)
            }
            else -> {}
        }

        // 记分板
        drawScoreboard(canvas)

        // 开局选人数：卡片式布局
        if (phase == Phase.SELECT) {
            canvas.drawRect(gameRect, maskPaint)
            val titleY = dp(SAFE_TOP_DP).toFloat() + dp(56f)
            art.drawCenterTextShadow(canvas, "🥌 冰壶多人对战", width / 2f, titleY, 28f, Palette.TEXT_LIGHT)
            art.drawCenterTextShadow(canvas, "开局先选玩家人数", width / 2f, titleY + dp(32f), 13f, Palette.DIM)
            for ((rect, n) in selBtns) {
                art.drawCard(canvas, rect, 0xFFFDFCF7.toInt(), 18f)
                // 顶部多色条
                val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                val stripTop = rect.top + dp(4f)
                val segW = (rect.width() - dp(8f)) / n
                for (k in 0 until n) {
                    stripPaint.color = stoneColors[k]
                    val sx = rect.left + dp(4f) + k * segW
                    canvas.drawRect(sx, stripTop, sx + segW + 0.5f, stripTop + dp(8f), stripPaint)
                }
                // 冰壶示意图标
                drawStone(canvas, rect.centerX(), rect.top + rect.height() * 0.46f,
                    stoneColors[0], "1", radiusOverride = rect.width() * 0.26f)
                art.drawCenterText(canvas, n.toString() + " 人", rect.centerX(),
                    rect.bottom - dp(24f), 18f, Palette.TEXT)
            }
            val hintY = (selBtns.lastOrNull()?.first?.bottom ?: height * 0.7f) + dp(28f)
            art.drawCenterTextShadow(canvas, "支持 2 / 3 / 4 人同屏轮流投壶", width / 2f, hintY, 12f, Palette.DIM)
        }
    }

    /**
     * 绘制一枚冰壶：软投影 + 球面径向渐变 + 左上高光 + 中心白色圆盘与号码。
     * 旧实现是「纯色圆 + 半透明小圆」，在浅色冰面上缺乏体积感；这里补上球面光影。
     *
     * @param radiusOverride 非空时覆盖默认 stoneR（用于选人界面的示意图标）
     */
    private fun drawStone(canvas: Canvas, cx: Float, cy: Float, color: Int, num: String, radiusOverride: Float? = null) {
        val r = radiusOverride ?: stoneR
        // 软投影
        canvas.drawCircle(cx + dp(1.5f), cy + dp(3f), r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x33000000 })
        // 球面渐变（高光偏心左上）
        val base = color and 0x00FFFFFF
        val shader = android.graphics.RadialGradient(
            cx - r * 0.34f, cy - r * 0.38f, r * 1.35f,
            intArrayOf(
                (base or 0xFF000000.toInt()),
                (base or 0xFF000000.toInt()),
                blendDark(base)),
            floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawCircle(cx, cy, r, p)
        // 细描边（柔化边缘，不用纯黑）
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x33000000; style = Paint.Style.STROKE; strokeWidth = dp(1.5f)
        })
        // 顶部高光小弧
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.34f, r * 0.26f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x55FFFFFF })
        // 中心白色圆盘 + 号码
        canvas.drawCircle(cx, cy, r * 0.42f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xEEFFFFFF.toInt() })
        art.drawCenterText(canvas, num, cx, cy, if (r > dp(30f)) 15f else 11f, Palette.TEXT)
    }

    /** 把颜色压暗一档（用于球面渐变的暗部）。 */
    private fun blendDark(rgb: Int): Int {
        val r = ((rgb shr 16) and 0xFF) * 0.62f
        val g = ((rgb shr 8) and 0xFF) * 0.62f
        val b = (rgb and 0xFF) * 0.62f
        return (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    private fun drawScoreboard(canvas: Canvas) {
        if (phase == Phase.SELECT || phase == Phase.OVER) return
        val chipW = dp(72f)
        val chipH = dp(32f)
        val gap = dp(8f)
        val totalW = players * chipW + (players - 1) * gap
        val x0 = width / 2f - totalW / 2f
        // 记分板放在冰道上方，避免压住冰面与营垒
        val y0 = sheet.top + dp(10f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (pl in 0 until players) {
            val x = x0 + pl * (chipW + gap)
            val active = pl == turn && (phase == Phase.THROW || phase == Phase.MOVING)
            val rect = RectF(x, y0, x + chipW, y0 + chipH)
            art.drawPill(canvas, rect,
                if (active) 0xEE1C2333.toInt() else 0xCCFFFFFF.toInt(),
                if (active) stoneColors[pl] else 0x33000000, if (active) 2f else 1f)
            // 左侧色点
            p.color = stoneColors[pl]
            canvas.drawCircle(x + dp(14f), y0 + chipH / 2f, dp(7f), p)
            art.drawCenterText(canvas, score[pl].toString(), x + chipW - dp(18f), y0 + chipH / 2f,
                13f, if (active) Palette.TEXT_LIGHT else Palette.TEXT)
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
        aimValid = false
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
        rounds = players + 1 // 2 人 3 局、3 人 4 局、4 人 5 局
        lastWinner = 0
        throwLeft = players * STONES_PER_PLAYER
        turn = 0
        charging = false
        power = 0f
        aimValid = false
        roundResult = ""
        phase = Phase.THROW
        invalidate()
    }
}
