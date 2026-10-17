package com.minigames.collection.games.flight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import com.minigames.collection.common.BoardArt
import com.minigames.collection.common.BoardArt.Palette
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/**
 * 飞行棋（2~4 人同屏）。
 *
 * ## 与旧版的差异
 * 旧版把机库、起飞格、跑道、冲刺道、终点分别写死在四套互不相关的网格坐标里，
 * 画出来"四个色块分布完全不对应"。这一版全部改为由 [FlightBoard] **单一数据源**推导，
 * 四个阵营用旋转 90° 得到，几何上不可能再偏移。
 *
 * 规则由 [FlightRules] 承载（起飞 / 跳格 / 飞行 / 踢子 / 叠子 / 冲刺 / 三次六判罚 / 胜负），
 * 与本类的绘制完全解耦 —— 因此规则可以单独单测（见 FlightBoardTest）。
 *
 * 骰子改为 3D 立方体（见 [Dice3D]），带缓动旋转动画，掷骰更有实感。
 */
class FlightView(context: Context) : GameView(context) {

    private enum class Phase { SELECT, PLAY, OVER }

    // ---- 核心数据 ----
    private var rules: FlightRules? = null
    private val board = FlightBoard(4)          // 几何与人数无关，先建一个
    private var phase = Phase.SELECT
    private var players = 0

    // ---- 骰子 ----
    private val dice = Dice3D(resources.displayMetrics.density)
    private var diceValue = 1
    private var rolling = false
    private var rollTimer = 0f
    private var rollCooldown = 0f

    // ---- 状态提示 ----
    private var toastText = ""
    private var toastT = 0f

    // ---- 选人卡片 ----
    private val selBtns = ArrayList<Pair<RectF, Int>>()

    // ---- 美术 ----
    private val art = BoardArt(this)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.BG }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val planePath = Path()

    // 四个阵营色（柔和卡通）
    private val colors = intArrayOf(
        0xFFE8503A.toInt(),   // 红
        0xFF3E8BF0.toInt(),   // 蓝
        0xFF3ED98A.toInt(),   // 绿
        0xFFF5A54A.toInt()    // 黄
    )
    private val names = arrayOf("红", "蓝", "绿", "黄")

    // ---- 布局 ----
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f
    private val boardRect = RectF()
    private val diceRect = RectF()

    override fun onLayout(w: Int, h: Int) {
        val topPad = dp(SAFE_TOP_DP).toFloat()
        val bottomZone = dp(210).toFloat()
        val availH = h - topPad - bottomZone
        // 棋盘是正方形，取宽高中较小者
        val side = min(w * 0.96f, availH).coerceAtLeast(dp(140).toFloat())
        cell = side / FlightBoard.SIZE
        boardLeft = (w - side) / 2f
        boardTop = topPad + (availH - side) / 2f
        boardRect.set(boardLeft, boardTop, boardLeft + side, boardTop + side)

        // 骰子区
        val dw = min(w * 0.30f, dp(110).toFloat())
        diceRect.set(w / 2f - dw / 2f, h - dp(175).toFloat(), w / 2f + dw / 2f, h - dp(175).toFloat() + dw)

        // 选人卡片
        selBtns.clear()
        val cardW = min((w - dp(60).toFloat()) / 3.6f, dp(110).toFloat())
        val cardH = cardW * 1.25f
        val gap = dp(14).toFloat()
        val totalW = cardW * 3 + gap * 2
        val cardY = h * 0.52f - cardH / 2f
        for (i in 0..2) {
            val x = w / 2f - totalW / 2f + i * (cardW + gap)
            selBtns.add(Pair(RectF(x, cardY, x + cardW, cardY + cardH), i + 2))
        }
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || paused) return
        when (phase) {
            Phase.SELECT -> {
                for ((rect, n) in selBtns) {
                    if (rect.contains(event.x, event.y)) {
                        players = n
                        rules = FlightRules(n)
                        phase = Phase.PLAY
                        dice.snap(1)
                        showToast("掷骰开始！" + names[rules!!.turn] + "方先手")
                        playSound(SoundManager.CLICK)
                        invalidate()
                        return
                    }
                }
            }
            Phase.PLAY -> {
                val r = rules ?: return
                // 点骰子 → 掷
                if (diceRect.contains(event.x, event.y) && !rolling) {
                    startRoll()
                    return
                }
                // 点飞机 → 走子
                if (!rolling) {
                    val hit = hitPlane(event.x, event.y)
                    if (hit != null) {
                        applyMove(hit.first, hit.second)
                    }
                }
            }
            else -> {}
        }
        invalidate()
    }

    override fun update(dt: Float) {
        if (toastT > 0f) {
            toastT -= dt
            if (toastT <= 0f) toastText = ""
        }
        dice.step(dt)
        if (rolling) {
            rollTimer += dt
            if (rollTimer < 0.85f) {
                // 掷骰过程中随机换点数，配合旋转动画
                diceValue = (1..6).random()
                dice.animateTo(diceValue, spinTurns = 1)
            } else {
                rolling = false
                val r = rules ?: return
                val rolled = r.roll(diceValue)
                if (rolled == 0) {
                    showToast("连续三次 6，本轮作废！轮到" + names[r.turn] + "方")
                    playSound(SoundManager.LOSE)
                } else {
                    showToast(names[r.turn] + "方掷出 " + rolled + " 点")
                    playSound(SoundManager.MOVE)
                    // 若无子可走，自动换手
                    if (r.legalMoves(rolled).isEmpty() && !r.canRollAgain()) {
                        r.passTurn()
                        showToast("无子可走，轮到" + names[r.turn] + "方")
                    }
                }
            }
        }
        invalidate()
    }

    private fun startRoll() {
        val r = rules ?: return
        if (r.rolled && !r.canRollAgain()) {
            showToast("请先走子")
            return
        }
        rolling = true
        rollTimer = 0f
        playSound(SoundManager.MOVE)
    }

    /** 命中一架可移动的飞机。返回 (玩家, 飞机号)。 */
    private fun hitPlane(x: Float, y: Float): Pair<Int, Int>? {
        val r = rules ?: return null
        val moves = r.legalMoves(diceValue)
        var best: Pair<Int, Int>? = null
        var bestD = Float.MAX_VALUE
        for ((idx, _) in moves) {
            val p = r.planes[idx]
            val pos = planePosition(p)
            val d = (pos.first - x) * (pos.first - x) + (pos.second - y) * (pos.second - y)
            if (d < bestD) {
                bestD = d
                best = Pair(p.player, p.index)
            }
        }
        return if (bestD <= cell * cell * 1.1f) best else null
    }

    private fun applyMove(player: Int, index: Int) {
        val r = rules ?: return
        val plane = r.planesOf(player).firstOrNull { it.index == index } ?: return
        val mv = r.applyMove(plane, diceValue) ?: return

        if (mv.flew) showToast(names[player] + "方起飞，飞跃全场！")
        else if (mv.jumped) showToast(names[player] + "方跳到下一格！")

        if (mv.kicked.isNotEmpty()) {
            showToast(names[mv.kicked[0].player] + "方的飞机被踢回停机坪！")
            playSound(SoundManager.LOSE)
        } else {
            playSound(SoundManager.CLICK)
        }

        if (r.isWinner(player)) {
            phase = Phase.OVER
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, names[player] + "方 4 架飞机全部到达终点获胜！"))
            return
        }

        // 掷到 6 可再掷
        if (!r.canRollAgain()) {
            r.passTurn()
            showToast("轮到" + names[r.turn] + "方")
        }
    }

    private fun showToast(msg: String) {
        toastText = msg
        toastT = 2.0f
    }

    // ---------- 绘制 ----------

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        if (phase == Phase.SELECT) {
            drawSelectScreen(canvas)
            return
        }
        drawBoard(canvas)
        drawPlanes(canvas)
        drawHud(canvas)
        drawDice(canvas)
    }

    /** 棋盘：跑道 + 机库 + 冲刺道 + 中心终点。全部由 FlightBoard 推导，严格对应。 */
    private fun drawBoard(canvas: Canvas) {
        val radius = dp(18f)
        // 底板
        boardPaint.color = 0xFFF0F3F8.toInt()
        canvas.drawRoundRect(boardRect, radius, radius, boardPaint)
        // 内描边
        linePaint.color = 0x22000000
        linePaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(boardRect, radius, radius, linePaint)

        // ---- 1. 跑道底带（浅色圆环）----
        drawTrackBand(canvas)

        // ---- 2. 机库（四角）----
        for (p in 0 until players) drawHangar(canvas, p)

        // ---- 3. 冲刺道（四条）----
        for (p in 0 until players) drawHomeLane(canvas, p)

        // ---- 4. 中心终点 ----
        drawCenter(canvas)

        // ---- 5. 跑道格 ----
        for (n in 0 until FlightBoard.RING) drawRingCell(canvas, n)
    }

    /** 跑道底带：沿外圈铺一条浅色圆环，让 52 格连成闭环。 */
    private fun drawTrackBand(canvas: Canvas) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
        val bandW = cell * 0.92f
        // 用四个圆角矩形拼环（避免 Path 二次贝塞尔）
        val inset = cell * 1.0f
        val r = boardRect.left + inset
        val t = boardRect.top + inset
        val ri = boardRect.right - inset
        val b = boardRect.bottom - inset
        val outer = RectF(r - bandW, t - bandW, ri + bandW, b + bandW)
        val inner = RectF(r + bandW, t + bandW, ri - bandW, b - bandW)
        val path = Path().apply {
            addRoundRect(outer, dp(16f), dp(16f), Path.Direction.CW)
            addRoundRect(inner, dp(12f), dp(12f), Path.Direction.CCW)
        }
        canvas.drawPath(path, p)
    }

    /** 单个跑道格。 */
    private fun drawRingCell(canvas: Canvas, n: Int) {
        val pt = board.ringPoint(n)
        val cx = boardLeft + pt.cx * cell
        val cy = boardTop + pt.cy * cell
        val r = cell * 0.42f

        // 找出这格属于谁（起飞格/飞行格）
        var owner = -1
        for (p in 0 until players) {
            if (board.absoluteRing(p, 0) == n) owner = p
            if (board.flySquare(p) == n) owner = p
        }

        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        if (owner >= 0) {
            cellPaint.color = colors[owner]
            canvas.drawRoundRect(rect, dp(5f), dp(5f), cellPaint)
            // 飞行格画虚线连接到对面
            if (board.flySquare(owner) == n) {
                drawFlyLine(canvas, owner)
            }
        } else {
            cellPaint.color = 0xFFE4E9F0.toInt()
            canvas.drawRoundRect(rect, dp(5f), dp(5f), cellPaint)
        }
        linePaint.color = 0x22000000
        linePaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(rect, dp(5f), dp(5f), linePaint)
    }

    /** 飞行虚线：从飞行格连到正对面的飞行格。 */
    private fun drawFlyLine(canvas: Canvas, player: Int) {
        val a = board.ringPoint(board.flySquare(player))
        val b = board.ringPoint(board.absoluteRing(player, 20 + FlightBoard.PER_SIDE * 2))
        val ax = boardLeft + a.cx * cell
        val ay = boardTop + a.cy * cell
        val bx = boardLeft + b.cx * cell
        val by = boardTop + b.cy * cell
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
        }
        canvas.drawLine(ax, ay, bx, by, p)
    }

    /** 机库（四角）。 */
    private fun drawHangar(canvas: Canvas, player: Int) {
        val r = board.hangarRect(player)
        val rect = RectF(
            boardLeft + r[0] * cell, boardTop + r[1] * cell,
            boardLeft + r[2] * cell, boardTop + r[3] * cell)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (colors[player] and 0x00FFFFFF) or 0x33000000
        }
        canvas.drawRoundRect(rect, dp(12f), dp(12f), fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors[player]
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
        }
        canvas.drawRoundRect(rect, dp(12f), dp(12f), stroke)

        // 4 个停机位（空心圆）
        for (i in 0 until 4) {
            val slot = board.hangarPoint(player, i)
            val sx = boardLeft + slot.cx * cell
            val sy = boardTop + slot.cy * cell
            val sr = cell * 0.3f
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x55000000
                style = Paint.Style.STROKE
                strokeWidth = dp(1.5f)
            }
            canvas.drawCircle(sx, sy, sr, ring)
        }

        // 阵营名
        art.drawCenterText(canvas, names[player],
            rect.centerX(), rect.bottom - cell * 0.2f, 11f, colors[player])
    }

    /** 冲刺道（6 格）。 */
    private fun drawHomeLane(canvas: Canvas, player: Int) {
        for (k in 1..FlightBoard.HOME_LEN) {
            val pt = board.homePoint(player, k)
            val cx = boardLeft + pt.cx * cell
            val cy = boardTop + pt.cy * cell
            val r = cell * 0.42f
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            cellPaint.color = (colors[player] and 0x00FFFFFF) or 0x66000000
            canvas.drawRoundRect(rect, dp(5f), dp(5f), cellPaint)
            linePaint.color = 0x33000000
            linePaint.strokeWidth = dp(1f)
            canvas.drawRoundRect(rect, dp(5f), dp(5f), linePaint)
        }
    }

    /** 中心终点。 */
    private fun drawCenter(canvas: Canvas) {
        val pt = board.homePoint()
        val cx = boardLeft + pt.cx * cell
        val cy = boardTop + pt.cy * cell
        val r = cell * 0.72f

        // 四色扇形
        for (p in 0 until 4) {
            val path = Path().apply {
                moveTo(cx, cy)
                arcTo(RectF(cx - r, cy - r, cx + r, cy + r),
                    -90f + p * 90f, 90f, false)
                close()
            }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (colors[p] and 0x00FFFFFF) or 0x88000000.toInt()
            }
            canvas.drawPath(path, fill)
        }

        // 中心小圆
        val c = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(cx, cy, cell * 0.22f, c)

        // 描边
        val s = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
        }
        canvas.drawCircle(cx, cy, r, s)
    }

    /** 飞机（三角形，机头朝行进方向）。 */
    private fun drawPlanes(canvas: Canvas) {
        val r = rules ?: return
        // 叠子：同格多架时错开
        val counts = HashMap<String, Int>()
        for (p in r.planes) {
            if (p.inBase) continue
            val pos = planePosition(p)
            val key = "${pos.first.toInt()}_${pos.second.toInt()}"
            val idx = counts[key] ?: 0
            counts[key] = idx + 1
            val offset = (idx - 0.5f) * cell * 0.16f
            drawPlane(canvas, p.player, p.index,
                pos.first + offset, pos.second + offset,
                planeAngle(p))
        }
    }

    private fun drawPlane(canvas: Canvas, player: Int, index: Int, cx: Float, cy: Float, angle: Float) {
        val r = cell * 0.42f
        // 软投影
        val sh = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
        canvas.drawCircle(cx + dp(1f), cy + dp(2f), r * 0.92f, sh)

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle)

        // 机身
        planePath.reset()
        planePath.moveTo(r * 1.0f, 0f)
        planePath.lineTo(-r * 0.72f, -r * 0.9f)
        planePath.lineTo(-r * 0.32f, 0f)
        planePath.lineTo(-r * 0.72f, r * 0.9f)
        planePath.close()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[player] }
        canvas.drawPath(planePath, body)

        // 描边
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (colors[player] and 0x00FFFFFF) or 0x55000000
            style = Paint.Style.STROKE
            strokeWidth = dp(1.2f)
        }
        canvas.drawPath(planePath, edge)
        canvas.restore()

        // 中心圆徽章 + 机号
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(cx, cy, r * 0.32f, badge)
        art.drawCenterText(canvas, (index + 1).toString(), cx, cy, 8f, Palette.TEXT)
    }

    /** 飞机朝向（机头朝行进方向）。 */
    private fun planeAngle(p: FlightRules.Plane): Float {
        if (p.inBase) return -90f
        if (p.atHome) return -90f
        val r = rules ?: return -90f
        val board2 = FlightBoard(players)
        // 在跑道上：朝下一格方向
        if (p.progress < FlightBoard.RING) {
            val cur = board2.ringPoint(board2.absoluteRing(p.player, p.progress))
            val nxt = board2.ringPoint(board2.absoluteRing(p.player, p.progress + 1))
            val dx = nxt.cx - cur.cx
            val dy = nxt.cy - cur.cy
            return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        }
        // 在冲刺道上：朝中心
        val cur = board2.homePoint(p.player, p.progress - FlightBoard.RING + 1)
        val c = board2.homePoint()
        val dx = c.cx - cur.cx
        val dy = c.cy - cur.cy
        return Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    /** 飞机在画布上的像素坐标。 */
    private fun planePosition(p: FlightRules.Plane): Pair<Float, Float> {
        val r = rules ?: return Pair(boardLeft, boardTop)
        val b = FlightBoard(players)
        val pt = if (p.inBase) {
            b.hangarPoint(p.player, p.index)
        } else if (p.atHome) {
            b.homePoint()
        } else if (p.progress < FlightBoard.RING) {
            b.ringPoint(b.absoluteRing(p.player, p.progress))
        } else {
            b.homePoint(p.player, p.progress - FlightBoard.RING + 1)
        }
        return Pair(boardLeft + pt.cx * cell, boardTop + pt.cy * cell)
    }

    /** 底部 HUD：回合 + 进度 + 提示。 */
    private fun drawHud(canvas: Canvas) {
        val r = rules ?: return
        val w = width.toFloat()

        // 回合状态条
        val barW = min(w * 0.86f, dp(340f))
        val barH = dp(38f)
        val barY = diceRect.top - dp(64f)
        val bar = RectF(w / 2f - barW / 2f, barY, w / 2f + barW / 2f, barY + barH)
        val tip = when {
            rolling -> "掷骰中…"
            else -> names[r.turn] + "方回合 · 点击骰子掷骰"
        }
        art.drawStatusBar(canvas, bar, tip, colors[r.turn])

        // 进度点阵
        val dotR = dp(5f)
        val dotGap = dp(15f)
        val dotsW = 4 * dotGap
        val groupGap = dp(26f)
        val totalW = players * dotsW + (players - 1) * groupGap
        var x0 = w / 2f - totalW / 2f
        val dotY = bar.bottom + dp(18f)
        for (pl in 0 until players) {
            art.drawCenterText(canvas, names[pl], x0 - dp(10f), dotY, 10f,
                if (pl == r.turn) colors[pl] else Palette.DIM)
            val done = r.homeCount(pl)
            for (k in 0 until 4) {
                val px = x0 + k * dotGap + dotGap / 2f
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (k < done) colors[pl] else 0x44FFFFFF
                }
                canvas.drawCircle(px, dotY, dotR, p)
            }
            x0 += dotsW + groupGap
        }

        // 提示
        if (toastText.isNotEmpty()) {
            val tw = art.measureText(toastText, 12f) + dp(30f)
            val th = dp(30f)
            val tr = RectF(w / 2f - tw / 2f, diceRect.bottom + dp(10f),
                w / 2f + tw / 2f, diceRect.bottom + dp(10f) + th)
            art.drawPill(canvas, tr, 0xE61C2333.toInt())
            art.drawCenterText(canvas, toastText, tr.centerX(), tr.centerY(), 12f, Palette.TEXT_LIGHT)
        } else {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Palette.DIM
                textAlign = Paint.Align.CENTER
                textSize = sp(11f)
            }
            canvas.drawText("掷到 6 起飞 · 落同色格跳 4 格 · 飞行格可飞跃全场",
                w / 2f, diceRect.bottom + dp(24f), p)
        }
    }

    /** 3D 骰子。 */
    private fun drawDice(canvas: Canvas) {
        val cx = diceRect.centerX()
        val cy = diceRect.centerY()
        val size = diceRect.width() * 0.86f
        dice.draw(canvas, cx, cy, size)

        // 骰子下方"点击掷骰"提示
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.DIM
            textAlign = Paint.Align.CENTER
            textSize = sp(10f)
        }
        canvas.drawText("点击掷骰", cx, diceRect.bottom + dp(10f), p)
    }

    /** 选人界面。 */
    private fun drawSelectScreen(canvas: Canvas) {
        val w = width.toFloat()
        val titleY = dp(SAFE_TOP_DP).toFloat() + dp(46f)
        art.drawCenterTextShadow(canvas, "✈️ 飞行棋", w / 2f, titleY, 28f, Palette.TEXT_LIGHT)
        art.drawCenterText(canvas, "2~4 人 · 掷 6 起飞 · 4 架全到终点获胜",
            w / 2f, titleY + dp(32f), 12f, Palette.DIM, bold = false)

        for ((rect, n) in selBtns) {
            art.drawCard(canvas, rect, 0xFFFDFCF7.toInt(), 20f)
            // 多色顶条
            val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val stripTop = rect.top + dp(4f)
            val stripBottom = stripTop + dp(10f)
            val segW = (rect.width() - dp(8f)) / n
            for (k in 0 until n) {
                stripPaint.color = colors[k]
                val sx = rect.left + dp(4f) + k * segW
                canvas.drawRect(sx, stripTop, sx + segW + 0.5f, stripBottom, stripPaint)
            }
            // 飞机图标
            val oldCell = cell
            cell = rect.width() * 0.62f
            drawPlane(canvas, 0, 0, rect.centerX(), rect.top + rect.height() * 0.45f, -90f)
            cell = oldCell
            art.drawCenterText(canvas, n.toString() + " 人", rect.centerX(),
                rect.bottom - dp(30f), 18f, Palette.TEXT)
        }

        val hintY = (selBtns.lastOrNull()?.first?.bottom ?: height * 0.7f) + dp(30f)
        art.drawCenterTextShadow(canvas, "支持 2 / 3 / 4 人同屏轮流掷骰",
            w / 2f, hintY, 11f, Palette.DIM, bold = false)
    }

    override fun reset() {
        players = 0
        rules = null
        phase = Phase.SELECT
        rolling = false
        rollTimer = 0f
        toastText = ""
        toastT = 0f
        dice.snap(1)
        diceValue = 1
        start()
    }
}
