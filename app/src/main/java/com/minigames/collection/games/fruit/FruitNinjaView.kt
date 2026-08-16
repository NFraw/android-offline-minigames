package com.minigames.collection.games.fruit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.hypot

/** 切水果：滑动切割抛起的水果，水果落地则失去一条命。 */
class FruitNinjaView(context: Context) : GameView(context) {

    private data class Fruit(var x: Float, var y: Float, val vx: Float, var vy: Float, val r: Float, var cut: Boolean)

    private val fruits = ArrayList<Fruit>()
    private var spawnTimer = 0.5f
    private var score = 0
    private var lives = 3
    private var over = false
    private var lastX = 0f
    private var lastY = 0f
    private var hasLast = false
    private val slash = Path()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val fruitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF6B6B.toInt() }
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
    private val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        strokeWidth = dp(6).toFloat()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = 0xFFE8EAF0.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                hasLast = true
                slash.rewind()
                slash.moveTo(lastX, lastY)
            }
            MotionEvent.ACTION_MOVE -> {
                if (hasLast) {
                    slash.lineTo(event.x, event.y)
                    // 切割检测
                    for (f in fruits) {
                        if (!f.cut && lineHitsCircle(lastX, lastY, event.x, event.y, f.x, f.y, f.r)) {
                            f.cut = true
                            score += 5
                            setScore(score)
                            playSound(SoundManager.EAT)
                        }
                    }
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> slash.rewind()
        }
    }

    private fun lineHitsCircle(x1: Float, y1: Float, x2: Float, y2: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy)
        if (len < 1f) return hypot(cx - x1, cy - y1) < r
        val t = ((cx - x1) * dx + (cy - y1) * dy) / (len * len)
        val tx = x1 + t.coerceIn(0f, 1f) * dx
        val ty = y1 + t.coerceIn(0f, 1f) * dy
        return hypot(cx - tx, cy - ty) < r
    }

    override fun update(dt: Float) {
        if (over) return
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = 0.6f + Math.random().toFloat() * 0.7f
            val x = width * (0.15f + Math.random().toFloat() * 0.7f)
            fruits.add(Fruit(x, height * 1.05f, (Math.random().toFloat() - 0.5f) * 180f, -(height * 0.85f + Math.random().toFloat() * 200f), dp(26).toFloat() + Math.random().toFloat() * dp(8), false))
        }
        val it = fruits.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.vy += dt * height * 1.4f
            f.x += f.vx * dt
            f.y += f.vy * dt
            if (f.y > height * 1.1f) {
                it.remove()
                if (!f.cut) {
                    lives--
                    playSound(SoundManager.CLICK)
                    if (lives <= 0) {
                        over = true
                        playSound(SoundManager.LOSE)
                        gameOver(GameResult(score, false, "漏掉 3 个水果！得分 " + score))
                        return
                    }
                }
            }
        }
        invalidate()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (f in fruits) {
            if (f.cut) continue
            canvas.drawCircle(f.x, f.y, f.r, fruitPaint)
            canvas.drawCircle(f.x + f.r * 0.35f, f.y - f.r * 0.45f, f.r * 0.4f, leafPaint)
        }
        if (!slash.isEmpty) {
            canvas.drawPath(slash, slashPaint)
        }
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("得分 " + score, width / 2f, dp(56).toFloat(), textPaint)
        textPaint.color = 0xFFFF6B6B.toInt()
        canvas.drawText("❤ ".repeat(lives.coerceAtLeast(0)), width / 2f, dp(92).toFloat(), textPaint)
        textPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("滑动切割水果，漏掉 3 个结束", width / 2f, height - dp(30).toFloat(), textPaint)
    }

    override fun reset() {
        fruits.clear()
        spawnTimer = 0.5f
        score = 0
        lives = 3
        over = false
        slash.rewind()
        setScore(0)
        start()
    }
}
