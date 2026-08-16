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
            playSound(SoundManager.MOVE)
        }
        // 砖块碰撞（按球头位置判断所在砖）
        val c = (ballX / blockW).toInt().coerceIn(0, COLS - 1)
        val r = ((ballY - ballR) / blockH).toInt().coerceIn(0, ROWS - 1)
        if (blocks[r * COLS + c] > 0) {
            blocks[r * COLS + c]--
            vy = -abs(vy)
            score += 10
            setScore(score)
            playSound(SoundManager.EAT)
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
