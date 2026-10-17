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
import kotlin.math.hypot

/** 乒乓 Pong：玩家1（下）与玩家2（上）各自在自己的半屏拖动球拍，先到 5 分获胜。 */
class PongView(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 5
        /** 初始球速（更快开局，避免拖拍拉锯） */
        private const val BASE_SPEED = 620f
        /** 碰撞加速的临界值 */
        private const val MAX_SPEED = 2400f
        /** 每次碰撞的加速系数 */
        private const val ACCEL = 1.07f
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
        textSize = 46f
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
                // 玩家 1 在下半屏、玩家 2 在上半屏（与全库"玩家 1 朝下"的座位约定一致）。
                if (event.getY(idx) < height / 2f) {
                    // 同一半屏若已有手指在控拍就不要覆盖标记：旧实现无条件覆盖，
                    // 随手多按一根再抬起它就会把标记清成 -1，原来按住的那根手指从此推不动球拍
                    // （表现为球拍"锁死"，必须完全重新按一次）。
                    if (pointerP2 < 0) {
                        pointerP2 = id
                        p2y = clampX(event.getX(idx))
                    }
                } else {
                    if (pointerP1 < 0) {
                        pointerP1 = id
                        p1y = clampX(event.getX(idx))
                    }
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
        // 按位移自适应分段：球速上限 2400px/s，dt 被基类钳到 0.05s 时单帧位移可达 120px，
        // 而球拍判定带只有 paddleH + 2*ballR（小屏约 68px）。不分子步的话，掉帧或高速球
        // 会直接穿过球拍被误判成对方得分，直接改变胜负。
        val moveDist = hypot(vx, vy) * dt
        val steps = (moveDist / (ballR * 1.5f)).toInt().coerceIn(1, 8)
        val sdt = dt / steps
        repeat(steps) { if (!over) stepOnce(sdt) }
    }

    private fun stepOnce(dt: Float) {
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
        val p1Rect = RectF(p1y - paddleW / 2f, bottomY, p1y + paddleW / 2f, bottomY + paddleH)
        val p2Rect = RectF(p2y - paddleW / 2f, topY, p2y + paddleW / 2f, topY + paddleH)
        if (vy < 0 && ballY - ballR <= topY + paddleH && ballY + ballR >= topY &&
            ballX >= p2Rect.left - ballR && ballX <= p2Rect.right + ballR
        ) {
            ballY = topY + paddleH + ballR
            vy = abs(vy)
            vx = abs(vx) * 0.92f + (ballX - p2y) / (paddleW / 2f) * speed * 0.7f
            boostVelocity()
            playSound(SoundManager.MOVE)
        }
        if (vy > 0 && ballY + ballR >= bottomY && ballY - ballR <= bottomY + paddleH &&
            ballX >= p1Rect.left - ballR && ballX <= p1Rect.right + ballR
        ) {
            ballY = bottomY - ballR
            vy = -abs(vy)
            vx = abs(vx) * 0.92f + (ballX - p1y) / (paddleW / 2f) * speed * 0.7f
            boostVelocity()
            playSound(SoundManager.MOVE)
        }
        // 出界得分：球从上方出界是上方玩家（玩家 2）漏球，玩家 1 得分；反之亦然
        if (ballY < -ballR * 2) {
            s1++
            playSound(SoundManager.EAT)
            checkWin()
        } else if (ballY > height + ballR * 2) {
            s2++
            playSound(SoundManager.EAT)
            checkWin()
        }
    }

    /** 每次碰撞后球速整体提升，直到临界值 MAX_SPEED。 */
    private fun boostVelocity() {
        val cur = hypot(vx, vy)
        if (cur <= 0f) return
        val scale = minOf(cur * ACCEL, MAX_SPEED) / cur
        vx *= scale
        vy *= scale
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
        // 上方是玩家 2 的球拍（橙色），下方是玩家 1 的球拍（蓝色）
        canvas.drawRoundRect(RectF(p2y - paddleW / 2f, topY, p2y + paddleW / 2f, topY + paddleH), dp(10).toFloat(), dp(10).toFloat(), paddle2Paint)
        canvas.drawRoundRect(RectF(p1y - paddleW / 2f, bottomY, p1y + paddleW / 2f, bottomY + paddleH), dp(10).toFloat(), dp(10).toFloat(), paddlePaint)
        canvas.drawCircle(ballX, ballY, ballR, ballPaint)
        val scoreText = "玩家1  " + s1 + " : " + s2 + "  玩家2"
        // 下方（玩家 1）正常绘制；上方（玩家 2）必须旋转 180°，否则上方玩家看到的是倒字
        canvas.drawText(scoreText, width / 2f, height - dp(22).toFloat(), scorePaint)
        drawPieceText(canvas, scorePaint, scoreText, width / 2f, dp(46).toFloat(), true)
    }

    override fun reset() {
        s1 = 0
        s2 = 0
        over = false
        speed = BASE_SPEED
        p1y = width / 2f
        p2y = width / 2f
        resetBall()
        start()
    }
}
