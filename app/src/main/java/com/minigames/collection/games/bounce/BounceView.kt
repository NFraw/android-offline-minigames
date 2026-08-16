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
        paddleX = event.x.coerceIn(paddleW / 2f, width - paddleW / 2f)
        if (!launched && event.actionMasked == MotionEvent.ACTION_DOWN) {
            launch()
        }
    }

    private fun launch() {
        launched = true
        val off = (ballX - paddleX) / (paddleW / 2f)
        vx = off * speed * 0.5f
        if (abs(vx) < speed * 0.15f) vx = if (off >= 0) speed * 0.15f else -speed * 0.15f
        vy = -speed * 0.8f
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
        val prevY = ballY
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
        // 挡板：用上一帧位置判断球是否穿越挡板顶平面，避免高速穿档
        if (vy > 0 && prevY + ballR >= height - paddleH && ballY + ballR <= height - paddleH &&
            abs(ballX - paddleX) < paddleW / 2f + ballR
        ) {
            ballY = height - paddleH - ballR
            vy = -abs(vy)
            val off = (ballX - paddleX) / (paddleW / 2f)
            vx = off * speed * 0.7f
            if (abs(vx) < speed * 0.15f) vx = if (off >= 0) speed * 0.15f else -speed * 0.15f
            playSound(SoundManager.MOVE)
        }
        // 砖块碰撞
        if (vy < 0) {
            val c = (ballX / blockW).toInt().coerceIn(0, COLS - 1)
            val r = ((ballY - ballR) / blockH).toInt().coerceIn(0, ROWS - 1)
            if (blocks[r * COLS + c]) {
                blocks[r * COLS + c] = false
                vy = abs(vy)
                score += 10
                setScore(score)
                playSound(SoundManager.EAT)
                if (blocks.none { it }) {
                    over = true
                    playSound(SoundManager.WIN)
                    gameOver(GameResult(score, true, "全部击碎！得分 " + score))
                    return
                }
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
