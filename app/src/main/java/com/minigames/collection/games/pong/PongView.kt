package com.minigames.collection.games.pong

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 乒乓 Pong：玩家1（上）与玩家2（下）各自在自己的半屏拖动球拍，先到 5 分获胜。 */
class PongView(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 5
    }

    private var p1y = 0f
    private var p2y = 0f
    private var ballX = 0f
    private var ballY = 0f
    private var vx = 0f
    private var vy = 0f
    private var s1 = 0
    private var s2 = 0
    private var over = false
    private var speed = 0f
    private var pointerP1 = -1
    private var pointerP2 = -1

    private var paddleW = 0f
    private var paddleH = 0f
    private var ballR = 0f
    private var topY = 0f
    private var bottomY = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val paddle2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF7A45.toInt() }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt()
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 30f
        color = 0xFFE8EAF0.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        paddleW = w * 0.16f
        paddleH = h * 0.05f
        ballR = h * 0.018f
        topY = h * 0.07f
        bottomY = h * 0.93f - paddleH
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                if (event.getY(idx) < height / 2f) {
                    pointerP1 = id
                    p1y = clampX(event.getX(idx))
                } else {
                    pointerP2 = id
                    p2y = clampX(event.getX(idx))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id == pointerP1) p1y = clampX(event.getX(i))
                    else if (id == pointerP2) p2y = clampX(event.getX(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = event.getPointerId(event.actionIndex)
                if (id == pointerP1) pointerP1 = -1
                if (id == pointerP2) pointerP2 = -1
            }
        }
    }

    private fun clampX(x: Float): Float = x.coerceIn(paddleW / 2f, width - paddleW / 2f)

    private fun resetBall() {
        ballX = width / 2f
        ballY = height / 2f
        val dir = if (Math.random() < 0.5) 1f else -1f
        vx = dir * speed * 0.6f
        vy = (if (Math.random() < 0.5) 1f else -1f) * speed * 0.8f
    }

    override fun update(dt: Float) {
        if (over) return
        ballX += vx * dt
        ballY += vy * dt
        // 左右墙
        if (ballX - ballR < 0) {
            ballX = ballR
            vx = abs(vx)
        }
        if (ballX + ballR > width) {
            ballX = width - ballR
            vx = -abs(vx)
        }
        // 球拍
        val p1Rect = RectF(p1y - paddleW / 2f, topY, p1y + paddleW / 2f, topY + paddleH)
        val p2Rect = RectF(p2y - paddleW / 2f, bottomY, p2y + paddleW / 2f, bottomY + paddleH)
        if (vy < 0 && ballY - ballR <= topY + paddleH && ballY + ballR >= topY &&
            ballX >= p1Rect.left - ballR && ballX <= p1Rect.right + ballR
        ) {
            ballY = topY + paddleH + ballR
            vy = abs(vy)
            vx = abs(vx) * 0.92f + (ballX - p1y) / (paddleW / 2f) * speed * 0.7f
            speed = minOf(speed * 1.06f, 1500f)
            playSound(SoundManager.MOVE)
        }
        if (vy > 0 && ballY + ballR >= bottomY && ballY - ballR <= bottomY + paddleH &&
            ballX >= p2Rect.left - ballR && ballX <= p2Rect.right + ballR
        ) {
            ballY = bottomY - ballR
            vy = -abs(vy)
            vx = abs(vx) * 0.92f + (ballX - p2y) / (paddleW / 2f) * speed * 0.7f
            speed = minOf(speed * 1.06f, 1500f)
            playSound(SoundManager.MOVE)
        }
        // 出界得分
        if (ballY < -ballR * 2) {
            s2++
            playSound(SoundManager.EAT)
            checkWin()
        } else if (ballY > height + ballR * 2) {
            s1++
            playSound(SoundManager.EAT)
            checkWin()
        }
    }

    private fun checkWin() {
        if (s1 >= WIN_SCORE || s2 >= WIN_SCORE) {
            over = true
            val p1Win = s1 > s2
            playSound(if (p1Win) SoundManager.WIN else SoundManager.LOSE)
            gameOver(GameResult(0, p1Win, (if (p1Win) "玩家 1 获胜！" else "玩家 2 获胜！") + "（" + s1 + " : " + s2 + "）"))
        } else {
            resetBall()
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, linePaint)
        canvas.drawRoundRect(RectF(p1y - paddleW / 2f, topY, p1y + paddleW / 2f, topY + paddleH), dp(10).toFloat(), dp(10).toFloat(), paddlePaint)
        canvas.drawRoundRect(RectF(p2y - paddleW / 2f, bottomY, p2y + paddleW / 2f, bottomY + paddleH), dp(10).toFloat(), dp(10).toFloat(), paddle2Paint)
        canvas.drawCircle(ballX, ballY, ballR, ballPaint)
        canvas.drawText("玩家1  " + s1 + " : " + s2 + "  玩家2", width / 2f, dp(40).toFloat(), scorePaint)
        canvas.drawText("先到 " + WIN_SCORE + " 分获胜 · 各自在自己半屏拖动控制", width / 2f, height - dp(18).toFloat(), scorePaint)
    }

    override fun reset() {
        s1 = 0
        s2 = 0
        over = false
        speed = 420f
        p1y = width / 2f
        p2y = width / 2f
        resetBall()
        start()
    }
}
