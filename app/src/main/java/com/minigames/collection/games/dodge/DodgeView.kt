package com.minigames.collection.games.dodge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 小球躲避：移动底部挡板接住下落的小球，漏球失去一条命。 */
class DodgeView(context: Context) : GameView(context) {

    private data class Ball(var x: Float, var y: Float, var vy: Float, val r: Float, val color: Int)

    private val balls = ArrayList<Ball>()
    private var spawnTimer = 0.8f
    private var paddleX = 0f
    private var paddleW = 0f
    private var paddleH = 0f
    private var score = 0
    private var lives = 3
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF6B6B.toInt() }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5D547.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = 0xFFE8EAF0.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        paddleW = w * 0.22f
        paddleH = dp(22).toFloat()
        paddleX = w / 2f
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
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = (0.55f - score * 0.004f).coerceAtLeast(0.22f)
            val gold = Math.random() < 0.15
            balls.add(Ball(
                width * (0.1f + Math.random().toFloat() * 0.8f),
                -dp(20).toFloat(),
                height * (0.5f + Math.random().toFloat() * 0.45f),
                dp(20).toFloat(),
                if (gold) 0xFFF5D547.toInt() else 0xFFFF6B6B.toInt()
            ))
        }
        val it = balls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.y += b.vy * dt
            // 挡板接球
            if (b.y + b.r >= height - paddleH && b.y - b.r <= height && abs(b.x - paddleX) < paddleW / 2f + b.r) {
                it.remove()
                val isGold = b.color == 0xFFF5D547.toInt()
                score += if (isGold) 20 else 10
                setScore(score)
                playSound(SoundManager.EAT)
                continue
            }
            if (b.y > height + b.r) {
                it.remove()
                lives--
                playSound(SoundManager.LOSE)
                if (lives <= 0) {
                    over = true
                    playSound(SoundManager.LOSE)
                    gameOver(GameResult(score, false, "漏了 3 个球！得分 " + score))
                    return
                }
            }
        }
        invalidate()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (b in balls) {
            ballPaint.color = b.color
            canvas.drawCircle(b.x, b.y, b.r, ballPaint)
        }
        canvas.drawRoundRect(RectF(paddleX - paddleW / 2f, height - paddleH, paddleX + paddleW / 2f, height.toFloat()), dp(10).toFloat(), dp(10).toFloat(), paddlePaint)
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("得分 " + score, width / 2f, dp(56).toFloat(), textPaint)
        textPaint.color = 0xFFFF6B6B.toInt()
        canvas.drawText("❤ ".repeat(lives.coerceAtLeast(0)), width / 2f, dp(92).toFloat(), textPaint)
        hintPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("左右滑动接住小球，金色球 +20", width / 2f, height - dp(52).toFloat(), hintPaint)
    }

    override fun reset() {
        balls.clear()
        spawnTimer = 0.8f
        score = 0
        lives = 3
        over = false
        setScore(0)
        start()
    }
}
