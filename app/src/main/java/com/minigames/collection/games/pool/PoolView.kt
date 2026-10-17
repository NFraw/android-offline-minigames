package com.minigames.collection.games.pool

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.widget.Toast
import com.minigames.collection.common.BoardArt
import com.minigames.collection.common.BoardArt.Palette
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 台球（传统 8 球，双人同屏）：球桌长边跟随设备方向（竖屏竖放、横屏横放）。
 * 规则：
 * - 1-7 实球、9-15 花球、8 号黑球，三角架开球；
 * - 开球后桌面开放，首次合法进球方确定球组（实球/花球），之后必须先碰己方球组；
 * - 己方球组清光后才能打 8 号球，且每杆前必须先选袋口（叫袋）；
 * - 犯规（母球落袋 / 没碰到任何球 / 先碰对方球组）对方获得"球在手中"
 *   （开球犯规限定在发球区放置，其余可任意摆放）；
 * - 合法打进 8 号球获胜；未清光球组就进 8 号、犯规时进 8 号、或 8 号未进所叫袋口均判负；
 * - 开球时 8 号进袋不算负，摆回脚点继续。
 * 击球前按住反向拖动瞄准，画面绘制球杆，并可用右上角按钮开关虚线辅助线（指向首个目标球）。
 */
class PoolView(context: Context) : GameView(context) {

    private data class Ball(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val r: Float, val num: Int, var pocketed: Boolean)

    private class Pocket(
        val x: Float, val y: Float, val r: Float,
        val top: Boolean, val bottom: Boolean, val left: Boolean, val right: Boolean)

    companion object {
        private const val RESTITUTION = 0.82f   // 库边回弹系数
        private const val FRICTION = 1.6f       // 台面阻尼（指数衰减）
        private const val STOP_SPEED = 3f       // 低于此速度视为静止
        private const val PHASE_AIM = 0
        private const val PHASE_PLACE = 1       // 球在手中：点台面放置母球
        private const val PHASE_CALL8 = 2       // 打 8 号前选袋口
        private const val PHASE_MOVE = 3
        // 传统配色：1-7 实球、8 黑、9-15 花球（白底 + 色带）
        private val BALL_COLORS = intArrayOf(
            0xFFF1C40F.toInt(), 0xFF2E5EFF.toInt(), 0xFFE74C3C.toInt(), 0xFF8E44AD.toInt(),
            0xFFE67E22.toInt(), 0xFF27AE60.toInt(), 0xFF8B2E2E.toInt(), 0xFF15161A.toInt(),
            0xFFF1C40F.toInt(), 0xFF2E5EFF.toInt(), 0xFFE74C3C.toInt(), 0xFF8E44AD.toInt(),
            0xFFE67E22.toInt(), 0xFF27AE60.toInt(), 0xFF8B2E2E.toInt())
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
    private var verticalTable = true
    private val guideBtn = RectF()

    private var phase = PHASE_AIM
    private var turn = 0
    private var group1 = 0          // 0=未定 1=实球 2=花球
    private var breakShot = true
    private var ballInHand = false
    private var kitchenOnly = false // 球在手中是否限定发球区（仅开球犯规）
    private var callPocket = -1     // 8 号球叫袋
    private var firstContact = -1   // 本杆母球首先碰到的球号
    private var need8Shot = false   // 本杆是否处于打 8 号阶段（清光己方球组后）
    private var shotEvents = ArrayList<Pair<Int, Int>>()  // 本杆进球序列 (球号, 袋口序号)
    private var aimX = 0f
    private var aimY = 0f
    private var dragging = false
    private var showGuide = true
    private var anyMoving = false
    private var wasMoving = false
    private var over = false

    /** 美术工具箱：统一卡片/胶囊/圆角绘制。 */
    private val art = BoardArt(this)

    private val bgPaint = Paint().apply { color = Palette.BG }
    private val cushionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB08956.toInt() }
    private val cushionEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val feltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E8B57.toInt() }
    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0E18.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF.toInt()
        strokeWidth = 2f
    }
    private val cueWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val blackBallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF15161A.toInt() }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt() }
    private val whiteCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF111111.toInt()
        typeface = Typeface.DEFAULT_BOLD
    }
    private val cueStickBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB08D57.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val cueStickLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8D9B0.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt()
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val guideHitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44F5D547.toInt() }
    private val callFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33F5D547.toInt() }
    private val callStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF5D547.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val placeDimPaint = Paint().apply { color = 0x2E141A2E.toInt() }

    private fun groupOf(p: Int): Int = if (p == 0) group1 else 3 - group1

    private fun ballGroup(num: Int): Int = when {
        num == 0 -> 0
        num == 8 -> 3
        num <= 7 -> 1
        else -> 2
    }

    private fun groupName(p: Int): String =
        if (group1 == 0) "球组待定" else if (groupOf(p) == 1) "实球" else "花球"

    private fun ownGroupCleared(p: Int): Boolean {
        if (group1 == 0) return false
        val g = groupOf(p)
        return balls.none { !it.pocketed && ballGroup(it.num) == g }
    }

    override fun onLayout(w: Int, h: Int) {
        verticalTable = h >= w
        val topZone = dp(128).toFloat()
        val bottomZone = dp(78).toFloat()
        val m = dp(16).toFloat()
        val availW = w - m * 2f
        val availH = h - topZone - bottomZone
        if (verticalTable) {
            tableH = min(availH, availW * 2f)
            tableW = tableH / 2f
        } else {
            tableW = min(availW, availH * 2f)
            tableH = tableW / 2f
        }
        tableLeft = (w - tableW) / 2f
        tableTop = topZone + (availH - tableH) / 2f
        ballR = (min(tableW, tableH) / 22f).coerceIn(dp(7).toFloat(), dp(15).toFloat())
        pocketR = ballR * 1.75f
        // 木框厚度不超过台面两侧可用的屏幕空间
        cushionW = min(max(dp(10).toFloat(), ballR * 1.6f), (w - tableW) / 2f - dp(4).toFloat())
            .coerceAtLeast(dp(4).toFloat())
        // 辅助线开关放在右下角得分胶囊的**上方**：得分胶囊由 GameActivity 叠在最上层
        // （右下角贴边 12dp、高约 43dp），旧位置 h-52..h-14 与它几乎完全重叠，
        // 按钮文字被盖住、点击也被胶囊区域吃掉，玩家根本看不到/按不到这个开关。
        guideBtn.set(w - dp(150).toFloat(), h - dp(112).toFloat(), w - dp(12).toFloat(), h - dp(74).toFloat())
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

    // 发球区（竖屏为底部 22%，横屏为左侧 22%）
    private fun headX(): Float = tableLeft + tableW * 0.22f
    private fun headY(): Float = tableTop + tableH * 0.78f
    // 架球点（脚点：远离发球区一侧）
    private fun footX(): Float = if (verticalTable) tableLeft + tableW / 2f else tableLeft + tableW * 0.82f
    private fun footY(): Float = if (verticalTable) tableTop + tableH * 0.18f else tableTop + tableH / 2f
    private fun cueStartX(): Float = if (verticalTable) tableLeft + tableW / 2f else tableLeft + tableW * 0.1f
    private fun cueStartY(): Float = if (verticalTable) tableTop + tableH * 0.9f else tableTop + tableH / 2f

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (guideBtn.contains(event.x, event.y)) {
                    showGuide = !showGuide
                    playSound(SoundManager.CLICK)
                    invalidate()
                    return
                }
                when (phase) {
                    PHASE_PLACE -> placeCueAt(event.x, event.y)
                    PHASE_CALL8 -> {
                        val pi = pocketAt(event.x, event.y)
                        if (pi >= 0) {
                            callPocket = pi
                            phase = PHASE_AIM
                            playSound(SoundManager.CLICK)
                            invalidate()
                        }
                    }
                    PHASE_AIM -> {
                        if (anyMoving) return
                        aimX = event.x
                        aimY = event.y
                        dragging = true
                    }
                }
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

    private fun pocketAt(x: Float, y: Float): Int {
        for ((i, p) in pockets.withIndex()) {
            if (hypot(x - p.x, y - p.y) < pocketR + dp(18).toFloat()) return i
        }
        return -1
    }

    private fun shoot() {
        val cue = balls.first { it.num == 0 }
        if (cue.pocketed) return
        val dx = aimX - cue.x
        val dy = aimY - cue.y
        val len = hypot(dx, dy)
        if (len < dp(12).toFloat()) return
        val power = (len / (tableW * 0.5f)).coerceIn(0.15f, 1f)
        val speed = tableH * 2.8f * power
        cue.vx = -dx / len * speed
        cue.vy = -dy / len * speed
        need8Shot = group1 != 0 && ownGroupCleared(turn)
        firstContact = -1
        shotEvents.clear()
        phase = PHASE_MOVE
        playSound(SoundManager.MOVE)
    }

    override fun update(dt: Float) {
        if (over) return
        // 自适应子步：球速上限是 tableH*2.8（power=1），若固定 2 子步，在帧间隔被基类钳到
        // 0.05s（≈20fps，低端机常见）时单步位移会超过一个球直径 → 母球直接"穿过"目标球，
        // firstContact 保持 -1，玩家被判"没碰到任何球"犯规。按最大位移自适应分段即可消除。
        var maxMove = 0f
        for (b in balls) {
            if (b.pocketed) continue
            val d = hypot(b.vx, b.vy) * dt
            if (d > maxMove) maxMove = d
        }
        val ballR = balls.first().r
        val sub = (maxMove / (ballR * 0.5f)).toInt().coerceIn(2, 16)
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
            resolveShot()
        }
    }

    private fun captureIfInPocket(b: Ball): Boolean {
        for ((i, p) in pockets.withIndex()) {
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
                if (!shotEvents.any { it.first == b.num }) shotEvents.add(b.num to i)
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
                if (d >= minD) continue  // 未接触
                // 圆心重合时 dx=dy=0，法向 nx=dx/d 会变成 NaN。旧实现直接 continue 跳过，
                // 两颗球会永远叠在同一个点上、之后再也不能互相弹开。这里改用固定法向按重叠量分离。
                val coincident = d < 0.001f
                val nx = if (coincident) 1f else dx / d
                val ny = if (coincident) 0f else dy / d
                val overlap = if (coincident) minD else minD - d
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
                    // 记录母球首先碰到的球（用于判犯规）
                    if (firstContact < 0) {
                        if (a.num == 0) firstContact = b.num
                        else if (b.num == 0) firstContact = a.num
                    }
                    if (abs(vn) > 60f) playSound(SoundManager.CLICK)
                }
            }
        }
    }

    private fun onPocket(b: Ball) {
        playSound(if (b.num == 0) SoundManager.CLICK else SoundManager.EAT)
    }

    private fun isFoul(): Boolean {
        val cue = balls.first { it.num == 0 }
        if (cue.pocketed) return true
        if (firstContact == -1) return true
        if (group1 != 0) {
            if (need8Shot) {
                if (firstContact != 8) return true
            } else if (ballGroup(firstContact) != groupOf(turn)) {
                return true
            }
        } else {
            // 桌面开放（球组未定）时先碰 8 号球同样属于犯规，标准 8 球规则如此。
            // 旧实现只在球组已确定后才校验首碰对象，开放台面先碰 8 号被漏判。
            if (firstContact == 8) return true
        }
        return false
    }

    /** 一杆结束后结算：判胜负 / 组别 / 犯规，并决定下一家与下一阶段。 */
    private fun resolveShot() {
        val cue = balls.first { it.num == 0 }
        val scratch = cue.pocketed
        val fouled = isFoul()
        var eightPos = shotEvents.indexOfFirst { it.first == 8 }
        var eightIn = eightPos >= 0
        val eightPocket = if (eightIn) shotEvents[eightPos].second else -1

        // 开球打进 8 号：摆回脚点，不算胜负。
        // 必须同时把 eightIn 置回 false —— 旧实现只 respot 了球，紧接着的 if (eightIn)
        // 仍会执行：此时 ownGroupCleared(turn) 为 false（球组还没定）且没叫袋，
        // 于是开球方在开球瞬间就被判负，与"开球进 8 不算负"的规则完全相反。
        if (eightIn && breakShot) {
            shotEvents.removeAt(eightPos)
            respotEight()
            eightIn = false
            eightPos = -1
        }

        if (eightIn) {
            val legal = !fouled && ownGroupCleared(turn) && callPocket == eightPocket
            over = true
            if (legal) {
                playSound(SoundManager.WIN)
                gameOver(GameResult(0, turn == 0, "玩家 " + (turn + 1) + "（" + groupName(turn) + "）合法打进 8 号球获胜！"))
            } else {
                val winner = 1 - turn
                playSound(SoundManager.LOSE)
                gameOver(GameResult(0, winner == 0, "玩家 " + (turn + 1) + " 犯规/违例打进 8 号球，玩家 " + (winner + 1) + " 获胜！"))
            }
            return
        }

        if (breakShot) {
            breakShot = false
            if (scratch || fouled) {
                ballInHand = true
                kitchenOnly = true
                turn = 1 - turn
                playSound(SoundManager.LOSE)
                Toast.makeText(context, "开球犯规，对方在发球区球在手中", Toast.LENGTH_SHORT).show()
            } else if (shotEvents.isNotEmpty()) {
                Toast.makeText(context, "开球进球，继续击球（桌面保持开放）", Toast.LENGTH_SHORT).show()
            } else {
                turn = 1 - turn
            }
            startTurn()
            return
        }

        // 桌面开放时首次合法进球确定球组
        if (group1 == 0 && !fouled) {
            val first = shotEvents.firstOrNull { it.first != 8 }
            if (first != null) {
                group1 = ballGroup(first.first)
                val gName = if (group1 == 1) "实球（1-7）" else "花球（9-15）"
                Toast.makeText(context, "玩家 " + (turn + 1) + " 确定为" + gName + "！", Toast.LENGTH_SHORT).show()
            }
        }

        if (fouled) {
            ballInHand = true
            kitchenOnly = false
            turn = 1 - turn
            playSound(SoundManager.LOSE)
            Toast.makeText(context, "犯规！对方球在手中", Toast.LENGTH_SHORT).show()
        } else {
            val pocketedOwn = shotEvents.any { ballGroup(it.first) == groupOf(turn) }
            if (!pocketedOwn) turn = 1 - turn
        }
        startTurn()
    }

    private fun startTurn() {
        dragging = false
        anyMoving = false
        callPocket = -1
        if (ballInHand) {
            phase = PHASE_PLACE
        } else if (group1 != 0 && ownGroupCleared(turn)) {
            phase = PHASE_CALL8
        } else {
            phase = PHASE_AIM
        }
        invalidate()
    }

    private fun placeCueAt(tx: Float, ty: Float) {
        val cue = balls.first { it.num == 0 }
        cue.pocketed = false
        cue.vx = 0f
        cue.vy = 0f
        var px = tx.coerceIn(tableLeft + cue.r, tableLeft + tableW - cue.r)
        var py = ty.coerceIn(tableTop + cue.r, tableTop + tableH - cue.r)
        if (kitchenOnly) {
            if (verticalTable) py = py.coerceAtLeast(headY() + cue.r)
            else px = px.coerceAtMost(headX() - cue.r)
        }
        // 就近寻找不与任何球重叠、也不落在袋口内的落点
        var placed = false
        var radius = 0f
        while (!placed && radius < tableW) {
            for (a in 0 until 360 step 15) {
                val rad = Math.toRadians(a.toDouble())
                val cx = (px + cos(rad).toFloat() * radius).coerceIn(tableLeft + cue.r, tableLeft + tableW - cue.r)
                val cy = (py + sin(rad).toFloat() * radius).coerceIn(tableTop + cue.r, tableTop + tableH - cue.r)
                if (kitchenOnly && verticalTable && cy < headY() + cue.r) continue
                if (kitchenOnly && !verticalTable && cx > headX() - cue.r) continue
                // 排除袋口：母球若被放在袋口圆内，下一杆只要朝袋口方向就"凭空"落袋判犯规
                if (pockets.any { hypot(it.x - cx, it.y - cy) < it.r + cue.r + 1f }) continue
                if (balls.none { !it.pocketed && it.num != 0 && hypot(it.x - cx, it.y - cy) < it.r + cue.r + 1f }) {
                    px = cx
                    py = cy
                    placed = true
                    break
                }
            }
            radius += ballR
        }
        if (!placed) {
            // 极端拥挤（球几乎摆满台面）时找不到完全不重叠的空位。
            // 旧实现会静默把母球留在与其他球重叠的位置，导致下一帧被撞开、甚至卡在一起。
            // 这里退化为"离最近一颗球刚好相切"的落点，保证至少不重叠。
            var worst = 0f
            for (o in balls) {
                if (o.pocketed || o.num == 0) continue
                val need = o.r + cue.r + 2f - hypot(o.x - px, o.y - py)
                if (need > worst) {
                    worst = need
                    val dx = px - o.x
                    val dy = py - o.y
                    val len = hypot(dx, dy)
                    if (len > 0.001f) {
                        px = (o.x + dx / len * (o.r + cue.r + 2f))
                            .coerceIn(tableLeft + cue.r, tableLeft + tableW - cue.r)
                        py = (o.y + dy / len * (o.r + cue.r + 2f))
                            .coerceIn(tableTop + cue.r, tableTop + tableH - cue.r)
                    }
                }
            }
        }
        cue.x = px
        cue.y = py
        ballInHand = false
        kitchenOnly = false
        if (group1 != 0 && ownGroupCleared(turn)) {
            phase = PHASE_CALL8
        } else {
            phase = PHASE_AIM
        }
        playSound(SoundManager.CLICK)
        invalidate()
    }

    private fun respotEight() {
        val eight = balls.first { it.num == 8 }
        eight.pocketed = false
        eight.vx = 0f
        eight.vy = 0f
        eight.x = footX()
        eight.y = footY()
        var tries = 0
        while (tries < 60) {
            if (balls.none { !it.pocketed && it.num != 8 && hypot(it.x - eight.x, it.y - eight.y) < it.r + eight.r + 2f }) return
            eight.x = tableLeft + tableW * (0.3f + Math.random().toFloat() * 0.4f)
            eight.y = tableTop + tableH * (0.3f + Math.random().toFloat() * 0.4f)
            tries++
        }
    }

    /** 本杆击球方向（母球反向拖动）。 */
    private fun shootDir(): Pair<Float, Float> {
        val cue = balls.first { it.num == 0 }
        val dx = cue.x - aimX
        val dy = cue.y - aimY
        val len = hypot(dx, dy)
        if (len < 0.01f) return 0f to -1f
        return dx / len to dy / len
    }

    /** 辅助线长度：母球中心到首个目标球边缘或库边。 */
    private fun guideLen(): Float {
        val cue = balls.first { it.num == 0 }
        val (dx, dy) = shootDir()
        var best = Float.MAX_VALUE
        for (b in balls) {
            if (b.pocketed || b.num == 0) continue
            val rx = b.x - cue.x
            val ry = b.y - cue.y
            val proj = rx * dx + ry * dy
            if (proj < 0f) continue
            val perp2 = rx * rx + ry * ry - proj * proj
            val rr = b.r + cue.r
            if (perp2 > rr * rr) continue
            val t = proj - sqrt(rr * rr - perp2)
            if (t < best) best = t
        }
        var edge = Float.MAX_VALUE
        if (dx > 0f) edge = min(edge, (tableLeft + tableW - cue.x - cue.r) / dx)
        if (dx < 0f) edge = min(edge, (cue.x - cue.r - tableLeft) / -dx)
        if (dy > 0f) edge = min(edge, (tableTop + tableH - cue.y - cue.r) / dy)
        if (dy < 0f) edge = min(edge, (cue.y - cue.r - tableTop) / -dy)
        return min(best, edge).coerceAtLeast(0f)
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        drawTable(canvas)
        if (phase == PHASE_PLACE) canvas.drawRect(tableRect(), placeDimPaint)
        for (b in balls) if (!b.pocketed) drawBall(canvas, b)
        if (phase == PHASE_AIM && dragging) drawAim(canvas)
        drawTopBar(canvas)
        if (phase == PHASE_CALL8) drawCallPockets(canvas)
        drawControls(canvas)
    }

    private fun tableRect() = RectF(tableLeft, tableTop, tableLeft + tableW, tableTop + tableH)

    private fun drawTable(canvas: Canvas) {
        val l = tableLeft
        val t = tableTop
        val r = tableLeft + tableW
        val b = tableTop + tableH
        val corner = dp(18f)
        // 木质外框：软投影 + 渐变木色 + 浅色内描边（不再用两段生硬的棕色描边）
        val frame = RectF(l - cushionW, t - cushionW, r + cushionW, b + cushionW)
        canvas.drawRoundRect(RectF(frame.left, frame.top + dp(4f), frame.right, frame.bottom + dp(5f)),
            corner, corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x44000000 })
        art.drawWoodSlab(canvas, frame, 18f)
        // 绒面台布
        canvas.drawRoundRect(RectF(l, t, r, b), dp(6f), dp(6f), feltPaint)
        // 台布内阴影：从木框向内一圈柔和的暗部，让台面有凹陷感
        val innerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33000000
            style = Paint.Style.STROKE
            strokeWidth = dp(6f)
        }
        canvas.drawRoundRect(RectF(l + 3f, t + 3f, r - 3f, b - 3f), dp(5f), dp(5f), innerShadow)
        // 发球线
        linePaint.strokeWidth = dp(2f)
        if (verticalTable) {
            canvas.drawLine(l, headY(), r, headY(), linePaint)
        } else {
            canvas.drawLine(headX(), t, headX(), b, linePaint)
        }
        // 袋口：深色圆 + 内侧柔和高光，形成"洞"的立体感
        for (p in pockets) {
            canvas.drawCircle(p.x, p.y, p.r, pocketPaint)
            canvas.drawCircle(p.x, p.y, p.r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x55FFFFFF; style = Paint.Style.STROKE; strokeWidth = dp(1.5f)
            })
        }
    }

    private fun drawBall(canvas: Canvas, b: Ball) {
        if (b.num == 0) {
            canvas.drawCircle(b.x, b.y, b.r, cueWhite)
            canvas.drawCircle(b.x, b.y, b.r, ballStroke)
            canvas.drawCircle(b.x - b.r * 0.3f, b.y - b.r * 0.35f, b.r * 0.3f, highlightPaint)
            return
        }
        val color = BALL_COLORS[b.num - 1]
        when {
            b.num <= 7 -> {
                fillPaint.color = color
                canvas.drawCircle(b.x, b.y, b.r, fillPaint)
                canvas.drawCircle(b.x, b.y, b.r, ballStroke)
            }
            b.num == 8 -> {
                canvas.drawCircle(b.x, b.y, b.r, blackBallPaint)
                canvas.drawCircle(b.x, b.y, b.r, ballStroke)
            }
            else -> {
                canvas.drawCircle(b.x, b.y, b.r, cueWhite)
                val clip = Path().apply { addCircle(b.x, b.y, b.r, Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(clip)
                bandPaint.color = color
                canvas.drawRect(RectF(b.x - b.r, b.y - b.r * 0.36f, b.x + b.r, b.y + b.r * 0.36f), bandPaint)
                canvas.restore()
                canvas.drawCircle(b.x, b.y, b.r, ballStroke)
            }
        }
        // 号码圆 + 数字
        canvas.drawCircle(b.x, b.y, b.r * 0.42f, whiteCirclePaint)
        numPaint.textSize = b.r * 0.62f
        numPaint.color = 0xFF111111.toInt()
        canvas.drawText(b.num.toString(), b.x, b.y - (numPaint.ascent() + numPaint.descent()) / 2f, numPaint)
        canvas.drawCircle(b.x - b.r * 0.3f, b.y - b.r * 0.35f, b.r * 0.22f, highlightPaint)
    }

    /** 球杆 + 可开关的虚线辅助线。 */
    private fun drawAim(canvas: Canvas) {
        val cue = balls.first { it.num == 0 }
        val (dx, dy) = shootDir()
        // 球杆：位于母球后方（击球方向的反侧）
        val stickLen = tableW * 0.45f
        val bx = cue.x - dx * (cue.r + dp(6).toFloat())
        val by = cue.y - dy * (cue.r + dp(6).toFloat())
        val ex = bx - dx * stickLen
        val ey = by - dy * stickLen
        cueStickBody.strokeWidth = dp(9).toFloat()
        canvas.drawLine(bx, by, ex, ey, cueStickBody)
        cueStickLine.strokeWidth = dp(3).toFloat()
        canvas.drawLine(bx, by, ex, ey, cueStickLine)
        if (showGuide) {
            val len = guideLen()
            val gx = cue.x + dx * len
            val gy = cue.y + dy * len
            guidePaint.pathEffect = DashPathEffect(floatArrayOf(dp(10).toFloat(), dp(8).toFloat()), 0f)
            canvas.drawLine(cue.x, cue.y, gx, gy, guidePaint)
            guidePaint.pathEffect = null
            // 标记首个目标球
            for (b in balls) {
                if (b.pocketed || b.num == 0) continue
                val rx = b.x - cue.x
                val ry = b.y - cue.y
                val proj = rx * dx + ry * dy
                if (proj < 0f) continue
                val perp2 = rx * rx + ry * ry - proj * proj
                val rr = b.r + cue.r
                if (perp2 > rr * rr) continue
                val t = proj - sqrt(rr * rr - perp2)
                if (abs(t - len) < dp(2).toFloat()) {
                    canvas.drawCircle(b.x, b.y, b.r + dp(3).toFloat(), guideHitPaint)
                    break
                }
            }
        }
    }

    private fun drawTopBar(canvas: Canvas) {
        val isP1 = turn == 0
        val bw = min(tableW, width - dp(120f))
        val bh = dp(44f)
        val bx = width / 2f - bw / 2f
        // 顶部状态条：放在悬浮控件安全区之下
        val by = dp(SAFE_TOP_DP - 30).toFloat()
        val barRect = RectF(bx, by, bx + bw, by + bh)
        val status = when (phase) {
            PHASE_PLACE -> "球在手中，请放置"
            PHASE_CALL8 -> "请选择 8 号球袋口"
            PHASE_MOVE -> "球在滚动…"
            else -> "拖动瞄准 · 松手击球"
        }
        art.drawStatusBar(canvas, barRect,
            "玩家 " + (turn + 1) + "（" + groupName(turn) + "） · " + status,
            if (isP1) 0xFFE8503A.toInt() else 0xFF3E8BF0.toInt())

        // 球组剩余情况
        val sRem = balls.count { !it.pocketed && ballGroup(it.num) == 1 }
        val hRem = balls.count { !it.pocketed && ballGroup(it.num) == 2 }
        val e8 = balls.any { it.num == 8 && !it.pocketed }
        art.drawCenterText(canvas,
            "实球剩余 " + sRem + " · 8 号" + (if (e8) "在场" else "已进") + " · 花球剩余 " + hRem,
            width / 2f, by + bh + dp(18f), 11.5f, Palette.DIM, bold = false)
        if (group1 == 0 && phase != PHASE_PLACE && phase != PHASE_CALL8) {
            art.drawCenterText(canvas, "桌面开放 · 首杆合法进球定球组（实球/花球）",
                width / 2f, by + bh + dp(34f), 11.5f, Palette.GOLD, bold = false)
        }
    }

    private fun drawCallPockets(canvas: Canvas) {
        for ((i, p) in pockets.withIndex()) {
            canvas.drawCircle(p.x, p.y, pocketR + dp(14).toFloat(), callFill)
            canvas.drawCircle(p.x, p.y, pocketR + dp(14).toFloat(), callStroke)
            numPaint.textSize = sp(17f)
            numPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawText((i + 1).toString(), p.x, centerBaseline(p.y, numPaint), numPaint)
        }
    }

    private fun drawControls(canvas: Canvas) {
        // 辅助线开关：柔光按钮（开=绿色实底，关=深色半透明）
        art.drawSoftButton(canvas, guideBtn,
            if (showGuide) 0xFF2E9E5B.toInt() else 0xCC2A3557.toInt(), 16f)
        art.drawCenterText(canvas, "辅助线：" + (if (showGuide) "开" else "关"),
            guideBtn.centerX(), guideBtn.centerY(), 12f, Palette.TEXT_LIGHT)
        // 底部提示：与球桌保持距离，且避开右下角得分胶囊区间
        val hint = when (phase) {
            PHASE_PLACE ->
                if (kitchenOnly) "开球犯规：母球须放回发球区，点击台面放置"
                else "犯规球在手中：点击台面任意位置放置母球"
            PHASE_CALL8 -> "点击高亮的袋口，指定 8 号球进哪个袋"
            PHASE_MOVE -> "球在滚动中，请等待停止…"
            else -> "按住反向拖动瞄准 · 松手击球 · 打进己方球可续击"
        }
        art.drawCenterText(canvas, hint, width / 2f, guideBtn.top - dp(16f), 11.5f, Palette.DIM, bold = false)
    }

    private fun buildRack() {
        // 三角架：8 号居中，后两角一实一花，其余随机
        val solids = (1..7).shuffled().toMutableList()
        val stripes = (9..15).shuffled().toMutableList()
        val cornerSolid = solids.removeAt(0)
        val cornerStripe = stripes.removeAt(0)
        val rest = (solids + stripes).shuffled()
        val layout = IntArray(15)
        layout[4] = 8
        layout[10] = cornerSolid
        layout[14] = cornerStripe
        var ri = 0
        for (i in layout.indices) {
            if (i == 4 || i == 10 || i == 14) continue
            layout[i] = rest[ri++]
        }
        val apexX = footX()
        val apexY = footY()
        val dy = ballR * 2f + 1f
        val dx = ballR * 1.732f
        var idx = 0
        for (r in 0 until 5) {
            val count = r + 1
            for (c in 0 until count) {
                val x: Float
                val y: Float
                if (verticalTable) {
                    x = apexX + (c - (count - 1) / 2f) * dx
                    y = apexY + r * dy
                } else {
                    x = apexX - r * dx
                    y = apexY + (c - (count - 1) / 2f) * dy
                }
                balls.add(Ball(x, y, 0f, 0f, ballR, layout[idx], false))
                idx++
            }
        }
    }

    override fun reset() {
        balls.clear()
        balls.add(Ball(cueStartX(), cueStartY(), 0f, 0f, ballR, 0, false))
        buildRack()
        turn = 0
        group1 = 0
        breakShot = true
        ballInHand = false
        kitchenOnly = false
        callPocket = -1
        firstContact = -1
        need8Shot = false
        shotEvents.clear()
        anyMoving = false
        wasMoving = false
        over = false
        phase = PHASE_AIM
        start()
    }
}
