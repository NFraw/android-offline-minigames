package com.minigames.collection.games.bounce

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 弹一弹：挡板反弹小球击碎上方方块，全部击碎获胜。 */
class BounceView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 8
        private const val ROWS = 5
    }

    private val blocks = BooleanArray(COLS * ROWS)
    private var ballX = 0f
    private var ballY = 0f
    private var vx = 0f
    private var vy = 0f
    private var ballR = 0f
    private var paddleX = 0f
    private var paddleW = 0f
    private var paddleH = 0f
    private var blockW = 0f
    private var blockH = 0f
    private var score = 0
    private var over = false
    private var speed = 0f
    private var launched = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        paddleW = w * 0.2f
        paddleH = dp(20).toFloat()
        paddleX = w / 2f
        ballR = dp(10).toFloat()
        blockW = w / COLS.toFloat()
        blockH = h * 0.05f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        if (!launched && event.actionMasked == MotionEvent.ACTION_DOWN) {
            // 发球方向 = 点击位置相对挡板中心的偏移：点挡板左半边往左上飞，右半边往右上飞。
            // 旧实现是"先把挡板瞬移到手指处、再用 (ballX - paddleX) 算偏移"，而 ballX 还停在
            // 上一帧的挡板位置，于是 off 实际等于"手指这一下跳了多远 / 半个挡板宽"，
            // 与瞄准意图无关：在屏幕最右点一下发球，球会以最高 3.2 倍设定速度朝反方向横飞
            // （第 69 行原有 vx 也没有上限），同时挡板还会瞬间跳位。
            launch(((event.x - paddleX) / (paddleW / 2f)).coerceIn(-1f, 1f))
            return  // 发球这一下不移动挡板
        }
        paddleX = event.x.coerceIn(paddleW / 2f, width - paddleW / 2f)
    }

    private fun launch(off: Float) {
        launched = true
        // 发球带最小水平分量：避免球纯垂直上下落，玩家几乎无反应时间
        vx = (off * speed * 0.4f).coerceIn(-speed * 0.4f, speed * 0.4f)
        if (abs(vx) < speed * 0.18f) vx = if (off >= 0f) speed * 0.18f else -speed * 0.18f
        vy = -speed * 0.72f
        playSound(SoundManager.MOVE)
    }

    override fun update(dt: Float) {
        if (over) return
        if (!launched) {
            // 未发射：球粘在挡板上跟随移动
            ballX = paddleX
            ballY = height - paddleH - ballR - dp(6).toFloat()
            invalidate()
            return
        }
        ballX += vx * dt
        ballY += vy * dt
        if (ballX - ballR < 0) {
            ballX = ballR
            vx = abs(vx)
        }
        if (ballX + ballR > width) {
            ballX = width - ballR
            vx = -abs(vx)
        }
        if (ballY - ballR < 0) {
            ballY = ballR
            vy = abs(vy)
        }
        // 挡板：窗口判定（与打砖块一致，已运行时验证）。球下落（vy>0）、球底已进入挡板顶平面、
        // 球顶未超出挡板高度窗口内即可反弹；比「上一帧在上、本帧穿到下」的穿越判定更宽容，
        // 快速下落时也必然命中，不会出现"穿过去"漏接。
        if (vy > 0 && ballY + ballR >= height - paddleH && ballY - ballR <= height - paddleH + dp(12).toFloat() &&
            abs(ballX - paddleX) < paddleW / 2f + ballR
        ) {
            ballY = height - paddleH - ballR
            vy = -abs(vy)
            val off = (ballX - paddleX) / (paddleW / 2f)
            vx = off * speed * 0.65f
            if (abs(vx) < speed * 0.15f) vx = if (off >= 0) speed * 0.15f else -speed * 0.15f
            playSound(SoundManager.MOVE)
        }
        // 砖块碰撞：圆形球与矩形砖块真实碰撞箱，命中后按接触面反射并击碎
        if (hitBlock()) {
            if (blocks.none { it }) {
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(score, true, "全部击碎！得分 " + score))
                return
            }
        }
        // 漏球
        if (ballY > height + ballR) {
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(score, false, "漏球了！得分 " + score))
            return
        }
        invalidate()
    }

    /**
     * 圆形球与砖块矩形碰撞检测：命中则击碎该砖并返回 true。
     * 用真实圆-矩形相交判断，且只对真正与球接触的砖生效（不会把球下方的区域错误钳到最后一排）。
     */
    private fun hitBlock(): Boolean {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (!blocks[r * COLS + c]) continue
                val left = c * blockW
                val top = r * blockH
                val right = left + blockW
                val bottom = top + blockH
                val cx = ballX.coerceIn(left, right)
                val cy = ballY.coerceIn(top, bottom)
                val dx = ballX - cx
                val dy = ballY - cy
                if (dx * dx + dy * dy > ballR * ballR) continue
                blocks[r * COLS + c] = false
                score += 10
                setScore(score)
                playSound(SoundManager.EAT)
                reflect(left, top, right, bottom, dx, dy)
                return true
            }
        }
        return false
    }

    /** 按圆心相对砖块的位置决定反射轴，并把球推出砖块，避免连续多帧重复命中。 */
    private fun reflect(left: Float, top: Float, right: Float, bottom: Float, dx: Float, dy: Float) {
        val penX = ballR - abs(dx)
        val penY = ballR - abs(dy)
        when {
            penX < penY && abs(dx) > 1f -> {
                vx = if (ballX < left) -abs(vx) else abs(vx)
                ballX = if (ballX < left) left - ballR else right + ballR
            }
            abs(dy) > 1f -> {
                vy = if (ballY < top) -abs(vy) else abs(vy)
                ballY = if (ballY < top) top - ballR else bottom + ballR
            }
            else -> {
                // 球心已经落在砖块内部（dx、dy 都接近 0）：此时既无法按接触面选轴，
                // 也必须把球推出去。旧实现只翻转速度、不修正位置，球会嵌在砖块里，
                // 之后每帧都满足命中条件，表现为"贴着方块墙逐帧连续击碎"、
                // 一块 3 血砖一帧掉多点耐久并多记分。
                if (abs(vx) > abs(vy)) {
                    vx = -vx
                    ballX = if (ballX < (left + right) / 2f) left - ballR else right + ballR
                } else {
                    vy = -vy
                    ballY = if (ballY < (top + bottom) / 2f) top - ballR else bottom + ballR
                }
            }
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            if (blocks[r * COLS + c]) {
                val rect = RectF(c * blockW + 2f, r * blockH + 2f, (c + 1) * blockW - 2f, (r + 1) * blockH - 2f)
                blockPaint.color = 0xFF4A6CF7.toInt() - r * 0x00101010
                canvas.drawRoundRect(rect, dp(6).toFloat(), dp(6).toFloat(), blockPaint)
            }
        }
        canvas.drawCircle(ballX, ballY, ballR, ballPaint)
        canvas.drawRoundRect(RectF(paddleX - paddleW / 2f, height - paddleH, paddleX + paddleW / 2f, height.toFloat()), dp(10).toFloat(), dp(10).toFloat(), paddlePaint)
        canvas.drawText(
            if (launched) "滑动挡板接球 · 得分 " + score else "滑动挡板移动 · 点击发射",
            width / 2f, height - dp(60).toFloat(), textPaint)
    }

    override fun reset() {
        for (i in blocks.indices) blocks[i] = true
        speed = height * 0.85f
        paddleX = width / 2f
        ballX = paddleX
        ballY = height - paddleH - ballR - dp(6).toFloat()
        launched = false
        score = 0
        over = false
        setScore(0)
        start()
    }
}
