package com.minigames.collection.games.breakout

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 打砖块：挡板反弹小球，消完所有砖块获胜，漏球失败。 */
class BreakoutView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 10
        private const val ROWS = 6
    }

    private val blocks = IntArray(COLS * ROWS)
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

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF37C2F0.toInt() }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        paddleW = w * 0.22f
        paddleH = dp(20).toFloat()
        paddleX = w / 2f
        ballR = dp(9).toFloat()
        blockW = w / COLS.toFloat()
        blockH = h * 0.045f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            paddleX = event.x.coerceIn(paddleW / 2f, width - paddleW / 2f)
        }
    }

    override fun update(dt: Float) {
        if (over) return
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
        if (vy > 0 && ballY + ballR >= height - paddleH && ballY - ballR <= height - paddleH + dp(12).toFloat() &&
            abs(ballX - paddleX) < paddleW / 2f + ballR
        ) {
            ballY = height - paddleH - ballR
            vy = -abs(vy)
            vx = (ballX - paddleX) / (paddleW / 2f) * speed * 0.65f
            // 最小水平分量：球正对挡板中心落下时 vx 恰好为 0，之后球心 x 永不变化，
            // 变成上下竖直往返的自持循环；若剩余砖块都在别的列，游戏既不结束也不掉分，
            // 只能靠玩家主动挪挡板才能打破（弹一弹有同样的兜底，打砖块漏了）。
            if (abs(vx) < speed * 0.15f) {
                vx = if (ballX >= paddleX) speed * 0.15f else -speed * 0.15f
            }
            playSound(SoundManager.MOVE)
        }
        // 砖块碰撞：圆形球与矩形砖块真实碰撞箱，命中后按接触面反射并扣血量
        if (hitBlock()) {
            if (blocks.none { it > 0 }) {
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(score, true, "全部击碎！得分 " + score))
                return
            }
        }
        if (ballY > height + ballR) {
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(score, false, "漏球了！得分 " + score))
            return
        }
        invalidate()
    }

    /**
     * 圆形球与砖块矩形碰撞检测：命中则扣该砖一点血量并返回 true。
     * 用真实圆-矩形相交判断，且只对真正与球接触的砖生效（不会把球下方的区域错误钳到最后一排）。
     */
    private fun hitBlock(): Boolean {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                if (blocks[r * COLS + c] <= 0) continue
                val left = c * blockW
                val top = r * blockH
                val right = left + blockW
                val bottom = top + blockH
                val cx = ballX.coerceIn(left, right)
                val cy = ballY.coerceIn(top, bottom)
                val dx = ballX - cx
                val dy = ballY - cy
                if (dx * dx + dy * dy > ballR * ballR) continue
                blocks[r * COLS + c]--
                // 计分按"击碎"计（血量归零才算一个砖块），与设计文档一致。
                // 旧实现每次命中就 +10，一块 3 血砖能记 30 分，最高分虚高最多 3 倍。
                if (blocks[r * COLS + c] <= 0) {
                    score += 10
                    setScore(score)
                } else {
                    setScore(score)
                }
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
                // 球心已落入砖块内部（dx、dy 都接近 0）：必须同时把球推出去，
                // 否则球会嵌在砖块里、之后每帧都满足命中条件，一块 3 血砖一次穿过就连掉多点耐久。
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
            val hp = blocks[r * COLS + c]
            if (hp > 0) {
                blockPaint.color = when (hp) {
                    1 -> 0xFFE8503A.toInt()
                    2 -> 0xFFF5A54A.toInt()
                    else -> 0xFF4A6CF7.toInt()
                }
                canvas.drawRoundRect(RectF(c * blockW + 2f, r * blockH + 2f, (c + 1) * blockW - 2f, (r + 1) * blockH - 2f), dp(5).toFloat(), dp(5).toFloat(), blockPaint)
            }
        }
        canvas.drawCircle(ballX, ballY, ballR, ballPaint)
        canvas.drawRoundRect(RectF(paddleX - paddleW / 2f, height - paddleH, paddleX + paddleW / 2f, height.toFloat()), dp(10).toFloat(), dp(10).toFloat(), paddlePaint)
        canvas.drawText("滑动挡板 · 得分 " + score, width / 2f, height - dp(52).toFloat(), textPaint)
    }

    override fun reset() {
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            blocks[r * COLS + c] = if (r < 2) 3 else if (r < 4) 2 else 1
        }
        ballX = width / 2f
        ballY = height * 0.75f
        speed = height * 0.9f
        vx = speed * 0.35f
        vy = -speed * 0.75f
        paddleX = width / 2f
        score = 0
        over = false
        setScore(0)
        start()
    }
}
