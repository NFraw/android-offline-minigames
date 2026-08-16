package com.minigames.collection.games.bowling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 保龄球：滑动瞄准，松手滚球击倒 10 个瓶子，两球一局。 */
class BowlingView(context: Context) : GameView(context) {

    companion object {
        private const val PINS = 10
    }

    private val pins = BooleanArray(PINS)
    private var pinX = FloatArray(PINS)
    private var pinY = FloatArray(PINS)
    private var ballX = 0f
    private var ballY = 0f
    private var ballR = 0f
    private var rolling = false
    private var rollSpeed = 0f
    private var aimX = 0f
    private var ballLeft = 2
    private var score = 0
    private var over = false
    private var pinBaseY = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB06CF5.toInt() }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2333.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        ballR = w * 0.045f
        pinBaseY = h * 0.28f
        // 三角排列瓶子
        val spacing = w * 0.11f
        var idx = 0
        for (row in 0..3) {
            val y = pinBaseY + row * spacing * 0.9f
            val x0 = w / 2f - row * spacing / 2f
            for (c in 0..row) {
                if (idx < PINS) {
                    pinX[idx] = x0 + c * spacing
                    pinY[idx] = y
                    idx++
                }
            }
        }
        ballX = w / 2f
        ballY = h * 0.85f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused || rolling) return
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                aimX = event.x.coerceIn(width * 0.15f, width * 0.85f)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                rolling = true
                ballX = aimX
                ballY = h * 0.85f
                rollSpeed = h * 1.4f
                playSound(SoundManager.MOVE)
            }
        }
    }

    override fun update(dt: Float) {
        if (!rolling) return
        ballY -= rollSpeed * dt
        // 击倒检测
        for (i in 0 until PINS) {
            if (!pins[i]) {
                val d = Math.hypot((ballX - pinX[i]).toDouble(), (ballY - pinY[i]).toDouble()).toFloat()
                if (d < ballR + w * 0.035f) {
                    pins[i] = true
                    playSound(SoundManager.EAT)
                }
            }
        }
        if (ballY < -ballR) {
            rolling = false
            ballLeft--
            val knocked = pins.count { it }
            score += knocked * 10
            setScore(score)
            if (ballLeft <= 0 || knocked == PINS) {
                over = true
                val strike = knocked == PINS && ballLeft == 0
                playSound(if (knocked == PINS) SoundManager.WIN else SoundManager.LOSE)
                gameOver(GameResult(score, knocked == PINS, "击倒 " + knocked + " 瓶" + (if (knocked == PINS) "！全中！" else "，得分 " + score)))
            } else {
                resetPinsForNext()
            }
        }
        invalidate()
    }

    private fun resetPinsForNext() {
        for (i in 0 until PINS) pins[i] = false
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 球道
        canvas.drawRoundRect(android.graphics.RectF(w * 0.12f, h * 0.15f, w * 0.88f, h * 0.92f), dp(20).toFloat(), dp(20).toFloat(), lanePaint)
        // 瓶子
        for (i in 0 until PINS) {
            if (!pins[i]) {
                canvas.drawCircle(pinX[i], pinY[i], w * 0.035f, pinPaint)
            }
        }
        // 球
        if (!rolling) {
            canvas.drawCircle(aimX, h * 0.85f, ballR, ballPaint)
        } else {
            canvas.drawCircle(ballX, ballY, ballR, ballPaint)
        }
        canvas.drawText("第 " + (3 - ballLeft) + " 球 · 滑动瞄准，松手投球 · 剩余 " + ballLeft + " 球", width / 2f, h * 0.94f, textPaint)
    }

    private val w: Int get() = width
    private val h: Int get() = height

    override fun reset() {
        for (i in 0 until PINS) pins[i] = false
        rolling = false
        ballLeft = 2
        score = 0
        over = false
        aimX = width / 2f
        setScore(0)
        start()
    }
}
