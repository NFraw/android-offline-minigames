package com.minigames.collection.games.pin

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.cos
import kotlin.math.sin

/** 见缝插针：点击发射钢针插入旋转圆盘，针碰针失败。 */
class PinView(context: Context) : GameView(context) {

    companion object {
        private const val TARGET = 12
    }

    private data class Pin(val angle: Float, val radius: Float)

    private val pins = ArrayList<Pin>()
    private var diskR = 0f
    private var diskX = 0f
    private var diskY = 0f
    private var pinLen = 0f
    private var rotation = 0f
    private var speed = 1f
    private var flying = false
    private var flyY = 0f
    private var over = false
    private var count = 0

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val diskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val diskInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6C8BFF.toInt() }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 41f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        diskR = kotlin.math.min(w, h) * 0.17f
        diskX = w / 2f
        diskY = h * 0.42f
        pinLen = dp(64).toFloat()
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused || flying) return
        flying = true
        flyY = height - dp(40).toFloat()
        playSound(SoundManager.MOVE)
    }

    override fun update(dt: Float) {
        if (over) return
        rotation += dt * 2.2f * speed
        if (flying) {
            flyY -= dt * height * 1.1f
            if (flyY - pinLen <= diskY - diskR) {
                // 插到圆盘
                flying = false
                pins.add(Pin(rotation, diskR))
                count++
                playSound(SoundManager.EAT)
                speed += 0.06f
                if (count >= TARGET) {
                    over = true
                    playSound(SoundManager.WIN)
                    gameOver(GameResult(TARGET * 10, true, "插满 " + TARGET + " 根针，完美！"))
                }
            }
        }
        // 针碰针检测
        if (pins.size >= 2) {
            val last = pins.last()
            for (i in 0 until pins.size - 1) {
                if (angleDiff(pins[i].angle, last.angle) < 0.045f) {
                    over = true
                    playSound(SoundManager.LOSE)
                    gameOver(GameResult(count * 10, false, "针碰针了！插了 " + count + " 根"))
                    return
                }
            }
        }
        invalidate()
    }

    private fun angleDiff(a: Float, b: Float): Float {
        var d = a - b
        while (d > Math.PI.toFloat()) d -= Math.PI.toFloat() * 2
        while (d < -Math.PI.toFloat()) d += Math.PI.toFloat() * 2
        return Math.abs(d)
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 已插入的针（随圆盘旋转）
        for (p in pins) {
            val ang = p.angle
            val px = diskX + cos(ang) * p.radius
            val py = diskY + sin(ang) * p.radius
            drawPin(canvas, px, py, ang + Math.PI.toFloat() / 2f)
        }
        // 飞行中的针
        if (flying) {
            drawPin(canvas, diskX, flyY, Math.PI.toFloat() / 2f)
        }
        // 圆盘
        canvas.drawCircle(diskX, diskY, diskR, diskPaint)
        canvas.drawCircle(diskX, diskY, diskR * 0.55f, diskInnerPaint)
        canvas.drawCircle(diskX, diskY, diskR * 0.16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5D547.toInt() })
        // 底部发射位
        drawPin(canvas, diskX, height - dp(40).toFloat(), Math.PI.toFloat() / 2f)
        canvas.drawText("点击发射钢针 · 已插 " + count + " / " + TARGET, width / 2f, height - dp(16).toFloat(), textPaint)
    }

    private fun drawPin(canvas: Canvas, x: Float, y: Float, ang: Float) {
        val dx = cos(ang) * pinLen
        val dy = sin(ang) * pinLen
        canvas.drawLine(x, y, x - dx, y - dy, pinPaint)
        canvas.drawCircle(x - dx, y - dy, dp(7).toFloat(), headPaint)
    }

    override fun reset() {
        pins.clear()
        rotation = 0f
        speed = 1f
        flying = false
        count = 0
        over = false
        start()
    }

}
