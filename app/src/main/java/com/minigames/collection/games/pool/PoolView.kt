package com.minigames.collection.games.pool

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
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 台球（双人清球）：标准 2:1 球桌、6 个袋口（四角 + 两中袋）。
 * 按住拖动反向瞄准、松手击球；球间弹性碰撞、台面阻尼、库边回弹；
 * 打进己方球可续击，打进对方球算给对方，母球落袋换人并把母球放回发球区；
 * 先清光己方 4 球者获胜。
 */
class PoolView(context: Context) : GameView(context) {

    private data class Ball(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val r: Float, val isCue: Boolean, val player: Int, val num: Int, var pocketed: Boolean)

    private class Pocket(
        val x: Float, val y: Float, val r: Float,
        val top: Boolean, val bottom: Boolean, val left: Boolean, val right: Boolean)

    companion object {
        private const val RESTITUTION = 0.82f   // 库边回弹系数
        private const val FRICTION = 1.6f       // 台面阻尼（指数衰减）
        private const val STOP_SPEED = 3f       // 低于此速度视为静止
        private const val RACK_COUNT = 8
    }

    private val balls = ArrayList<Ball>()
    private val pockets = ArrayList<Pocket>()
    private var tableLeft = 0f
    private var tableTop = 0f
    private var tableW = 0f
    private var tableH = 0f
    private var ballR = 0f
    private var pocketR = 0f
    private var cushionW = 0f

    private var aimX = 0f
    private var aimY = 0f
    private var dragging = false
    private var turn = 0
    private var pocket1 = 0
    private var pocket2 = 0
    private var scratch = false
    private var pocketedOwn = false
    private var anyMoving = false
    private var wasMoving = false
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cushionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A5A2B.toInt() }
    private val cushionEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB08555.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val feltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F8A4C.toInt() }
    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0E18.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF.toInt()
        strokeWidth = 2f
    }
    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val ballStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt() }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF.toInt()
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 40f
        color = 0xFF8A93A6.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 38f
        color = 0xFF8A93A6.toInt()
    }
    private val spanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = 46f
        color = 0xFFE8EAF0.toInt()
    }
    private val goldPaint = Paint(spanPaint).apply { color = 0xFFFFD54F.toInt() }
    private val redText = Paint(spanPaint).apply { color = 0xFFFF8A80.toInt() }
    private val blueText = Paint(spanPaint).apply { color = 0xFFAFC3FF.toInt() }

    override fun onLayout(w: Int, h: Int) {
        val topZone = dp(88).toFloat()
        val bottomZone = dp(58).toFloat()
        val m = dp(24).toFloat()
        val availW = w - m * 2f
        val availH = h - topZone - bottomZone
        var tw = availW
        var th = tw / 2f
        if (th > availH) {
            th = availH
            tw = th * 2f
        }
        tableW = tw
        tableH = th
        tableLeft = (w - tw) / 2f
        tableTop = topZone + (availH - th) / 2f
        ballR = (min(tw, th) / 20f).coerceIn(dp(7).toFloat(), dp(16).toFloat())
        pocketR = ballR * 1.65f
        // 木框厚度不超过台面两侧可用的屏幕空间，避免框被裁出屏幕
        cushionW = min(max(dp(12).toFloat(), ballR * 1.7f), (w - tableW) / 2f)
        buildPockets()
        reset()
    }

    private fun buildPockets() {
        pockets.clear()
        val l = tableLeft
        val t = tableTop
        val rt = tableLeft + tableW
        val bt = tableTop + tableH
        val mid = tableLeft + tableW / 2f
        pockets.add(Pocket(l, t, pocketR, true, false, true, false))
        pockets.add(Pocket(mid, t, pocketR, true, false, false, false))
        pockets.add(Pocket(rt, t, pocketR, true, false, false, true))
        pockets.add(Pocket(l, bt, pocketR, false, true, true, false))
        pockets.add(Pocket(mid, bt, pocketR, false, true, false, false))
        pockets.add(Pocket(rt, bt, pocketR, false, true, false, true))
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (anyMoving) return
                aimX = event.x
                aimY = event.y
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    aimX = event.x
                    aimY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    shoot()
                    dragging = false
                }
            }
        }
    }

    private fun shoot() {
        val cue = balls.firstOrNull { it.isCue } ?: return
        if (cue.pocketed) return
        val dx = aimX - cue.x
        val dy = aimY - cue.y
        val len = hypot(dx, dy)
        if (len < dp(14).toFloat()) return
        // 手机屏上横向拖拽最大约半个台宽，以半个台宽为满力刻度，保证一杆能破开球堆
        val power = (len / (tableW * 0.5f)).coerceIn(0.15f, 1f)
        val speed = tableH * 3f * power
        cue.vx = -dx / len * speed
        cue.vy = -dy / len * speed
        scratch = false
        pocketedOwn = false
        playSound(SoundManager.MOVE)
    }

    override fun update(dt: Float) {
        if (over) return
        // 两次子步进，高速时更稳定
        val sub = 2
        val sdt = dt / sub
        repeat(sub) {
            for (b in balls) {
                if (b.pocketed) continue
                b.x += b.vx * sdt
                b.y += b.vy * sdt
                if (captureIfInPocket(b)) continue
                reflectCushion(b)
                val damp = exp(-FRICTION * sdt)
                b.vx *= damp
                b.vy *= damp
                if (hypot(b.vx, b.vy) < STOP_SPEED) {
                    b.vx = 0f
                    b.vy = 0f
                }
            }
            collideBalls()
        }
        anyMoving = balls.any { !it.pocketed && (it.vx != 0f || it.vy != 0f) }
        if (anyMoving) {
            wasMoving = true
        } else if (wasMoving) {
            wasMoving = false
            resolveTurn()
        }
    }

    /** 球心进入袋口圆（且贴近袋口所在库边）即落袋。 */
    private fun captureIfInPocket(b: Ball): Boolean {
        for (p in pockets) {
            val d = hypot(b.x - p.x, b.y - p.y)
            if (d >= p.r) continue
            val out = b.x < tableLeft || b.x > tableLeft + tableW ||
                b.y < tableTop || b.y > tableTop + tableH
            val toward = (p.x - b.x) * b.vx + (p.y - b.y) * b.vy >= 0f
            val nearWall = (p.top && abs(b.y - tableTop) < p.r) ||
                (p.bottom && abs(b.y - (tableTop + tableH)) < p.r) ||
                (p.left && abs(b.x - tableLeft) < p.r) ||
                (p.right && abs(b.x - (tableLeft + tableW)) < p.r)
            if ((toward || out) && nearWall) {
                b.pocketed = true
                onPocket(b)
                return true
            }
        }
        return false
    }

    private fun reflectCushion(b: Ball) {
        if (b.x - b.r < tableLeft) {
            b.x = tableLeft + b.r
            b.vx = abs(b.vx) * RESTITUTION
        } else if (b.x + b.r > tableLeft + tableW) {
            b.x = tableLeft + tableW - b.r
            b.vx = -abs(b.vx) * RESTITUTION
        }
        if (b.y - b.r < tableTop) {
            b.y = tableTop + b.r
            b.vy = abs(b.vy) * RESTITUTION
        } else if (b.y + b.r > tableTop + tableH) {
            b.y = tableTop + tableH - b.r
            b.vy = -abs(b.vy) * RESTITUTION
        }
    }

    /** 等质量弹性碰撞：沿法线交换速度分量。 */
    private fun collideBalls() {
        for (i in balls.indices) {
            for (j in i + 1 until balls.size) {
                val a = balls[i]
                val b = balls[j]
                if (a.pocketed || b.pocketed) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val d = hypot(dx, dy)
                val minD = a.r + b.r
                if (d >= minD || d < 0.001f) continue
                val nx = dx / d
                val ny = dy / d
                val overlap = minD - d
                a.x -= nx * overlap / 2f
                a.y -= ny * overlap / 2f
                b.x += nx * overlap / 2f
                b.y += ny * overlap / 2f
                val vn = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if (vn < 0f) {
                    a.vx += nx * vn
                    a.vy += ny * vn
                    b.vx -= nx * vn
                    b.vy -= ny * vn
                    if (abs(vn) > 60f) playSound(SoundManager.CLICK)
                }
            }
        }
    }

    private fun onPocket(b: Ball) {
        playSound(SoundManager.EAT)
        if (b.isCue) {
            scratch = true
        } else {
            if (b.player == 0) pocket1++ else pocket2++
            if (b.player == turn) pocketedOwn = true
            checkEnd()
        }
    }

    private fun resolveTurn() {
        if (scratch) {
            placeCueBall()
            scratch = false
            pocketedOwn = false
            turn = 1 - turn
            playSound(SoundManager.LOSE)
            Toast.makeText(context, "母球落袋！轮到 玩家 " + (turn + 1) + " 击球", Toast.LENGTH_SHORT).show()
        } else if (!pocketedOwn) {
            turn = 1 - turn
        }
    }

    private fun placeCueBall() {
        val cue = balls.first { it.isCue }
        cue.pocketed = false
        cue.vx = 0f
        cue.vy = 0f
        cue.x = tableLeft + tableW * 0.22f
        cue.y = tableTop + tableH / 2f
        var tries = 0
        while (tries < 40) {
            var clear = true
            for (o in balls) {
                if (o.isCue || o.pocketed) continue
                if (hypot(o.x - cue.x, o.y - cue.y) < o.r + cue.r + 2f) {
                    clear = false
                    break
                }
            }
            if (clear) return
            cue.x = tableLeft + tableW * (0.15f + Math.random().toFloat() * 0.3f)
            cue.y = tableTop + tableH * (0.2f + Math.random().toFloat() * 0.6f)
            tries++
        }
    }

    private fun remaining(p: Int): Int =
        balls.count { !it.isCue && !it.pocketed && it.player == p }

    private fun checkEnd() {
        val rem1 = remaining(0)
        val rem2 = remaining(1)
        if (rem1 == 0 || rem2 == 0) {
            over = true
            val p1Win = rem1 == 0
            playSound(SoundManager.WIN)
            gameOver(GameResult(
                0, p1Win,
                "玩家1（红）" + pocket1 + " : " + pocket2 + " 玩家2（蓝），" +
                    (if (p1Win) "红方清台获胜！" else "蓝方清台获胜！")))
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        drawTable(canvas)
        for (b in balls) {
            if (b.pocketed) continue
            drawBall(canvas, b)
        }
        if (dragging) {
            val cue = balls.firstOrNull { it.isCue }
            if (cue != null && !cue.pocketed) {
                val dx = cue.x - aimX
                val dy = cue.y - aimY
                val len = hypot(dx, dy)
                if (len > 0.1f) {
                    val scale = tableW * 0.6f / len
                    canvas.drawLine(cue.x, cue.y, cue.x + dx * scale, cue.y + dy * scale, aimPaint)
                }
            }
        }
        // 标题
        canvas.drawText("🎱 台球 · 先清光己方球者胜", width / 2f, dp(30).toFloat(), titlePaint)
        // 回合与比分（彩色分段，居中）
        val turnMark = if (turn == 0) "▶ " else "   "
        val pieces = listOf(
            (turnMark to (if (turn == 0) goldPaint else spanPaint)),
            ("玩家1（红）" to redText),
            (" 进" + pocket1 + "/4" to spanPaint),
            ("  vs  " to spanPaint),
            ("玩家2（蓝）" to blueText),
            (" 进" + pocket2 + "/4" to spanPaint))
        drawSpans(canvas, pieces, tableTop - dp(26).toFloat())
        // 底部提示
        val hint = when {
            over -> ""
            anyMoving -> "球在滚动中，请等待停止…"
            else -> "按住拖动反向瞄准 · 松手击球 · 打进己方球可续击"
        }
        canvas.drawText(hint, width / 2f, tableTop + tableH + dp(34).toFloat(), hintPaint)
    }

    private fun drawTable(canvas: Canvas) {
        val l = tableLeft
        val t = tableTop
        val r = tableLeft + tableW
        val b = tableTop + tableH
        // 木框
        val frame = RectF(l - cushionW, t - cushionW, r + cushionW, b + cushionW)
        canvas.drawRoundRect(frame, dp(14).toFloat(), dp(14).toFloat(), cushionPaint)
        canvas.drawRoundRect(frame, dp(14).toFloat(), dp(14).toFloat(), cushionEdge)
        // 绿呢
        canvas.drawRoundRect(RectF(l, t, r, b), dp(5).toFloat(), dp(5).toFloat(), feltPaint)
        // 发球线
        val bl = l + tableW * 0.22f
        canvas.drawLine(bl, t + dp(4).toFloat(), bl, b - dp(4).toFloat(), linePaint)
        // 袋口
        for (p in pockets) {
            canvas.drawCircle(p.x, p.y, p.r, pocketPaint)
        }
    }

    private fun drawBall(canvas: Canvas, b: Ball) {
        val fill = when {
            b.isCue -> cuePaint
            b.player == 0 -> redPaint
            else -> bluePaint
        }
        canvas.drawCircle(b.x, b.y, b.r, fill)
        canvas.drawCircle(b.x, b.y, b.r, ballStroke)
        if (!b.isCue) {
            numPaint.textSize = b.r * 0.85f
            canvas.drawText(
                b.num.toString(), b.x,
                b.y - (numPaint.ascent() + numPaint.descent()) / 2f, numPaint)
        }
        canvas.drawCircle(b.x - b.r * 0.3f, b.y - b.r * 0.35f, b.r * 0.3f, highlightPaint)
    }

    /** 绘制一组左右拼起来的文字，整体居中，各段可自定义颜色。 */
    private fun drawSpans(canvas: Canvas, pieces: List<Pair<String, Paint>>, y: Float) {
        var total = 0f
        for ((s, p) in pieces) total += p.measureText(s)
        var x = width / 2f - total / 2f
        for ((s, p) in pieces) {
            canvas.drawText(s, x, y, p)
            x += p.measureText(s)
        }
    }

    override fun reset() {
        balls.clear()
        balls.add(Ball(tableLeft + tableW * 0.22f, tableTop + tableH / 2f, 0f, 0f, ballR, true, -1, 0, false))
        // 三角摆球：顶点朝母球（左），4 行共 8 颗，红蓝交替
        val apexX = tableLeft + tableW * 0.72f
        val apexY = tableTop + tableH / 2f
        val dx = ballR * 1.732f
        val dy = ballR * 2f + 1f
        var idx = 0
        for (row in 0..3) {
            val count = if (row < 3) row + 1 else 2
            for (c in 0 until count) {
                if (idx >= RACK_COUNT) break
                val x = apexX + row * dx
                val y = apexY + (c - (count - 1) / 2f) * dy
                balls.add(Ball(x, y, 0f, 0f, ballR, false, idx % 2, idx + 1, false))
                idx++
            }
        }
        pocket1 = 0
        pocket2 = 0
        turn = 0
        scratch = false
        pocketedOwn = false
        anyMoving = false
        wasMoving = false
        over = false
        start()
    }
}
