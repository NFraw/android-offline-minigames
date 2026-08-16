package com.minigames.collection.games.watermelon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 合成大西瓜：掉落水果，相同水果合并升级，堆出底线失败。 */
class WatermelonView(context: Context) : GameView(context) {

    companion object {
        private const val TYPES = 5
        private const val LINE_Y_RATIO = 0.16f
    }

    private data class Fruit(var x: Float, var y: Float, var vy: Float, var type: Int, var merged: Boolean = false)

    private val fruits = ArrayList<Fruit>()
    private var dropX = 0f
    private var dropTimer = 0.5f
    private var score = 0
    private var over = false

    private val colors = intArrayOf(
        0xFFFF6B6B.toInt(), 0xFFFFA04A.toInt(), 0xFFF5D547.toInt(),
        0xFF3ED98A.toInt(), 0xFF37C2F0.toInt()
    )
    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val fruitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FF5252.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        dropX = w / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            dropX = event.x.coerceIn(width * 0.1f, width * 0.9f)
        }
    }

    private fun radiusFor(type: Int): Float = dp(22).toFloat() + type * dp(10).toFloat()

    override fun update(dt: Float) {
        if (over) return
        dropTimer -= dt
        if (dropTimer <= 0f) {
            dropTimer = 0.55f
            fruits.add(Fruit(dropX, -dp(20).toFloat(), 0f, (0 until TYPES).random()))
        }
        // 重力 + 落地
        for (f in fruits) {
            f.vy += dt * height * 1.6f
            f.y += f.vy * dt
            if (f.y + radiusFor(f.type) > height - dp(30).toFloat()) {
                f.y = height - dp(30).toFloat() - radiusFor(f.type)
                f.vy = 0f
            }
        }
        // 碰撞合并（简单：两两检查，相同类型且相交）
        var merged = false
        for (i in fruits.indices) {
            for (j in i + 1 until fruits.size) {
                val a = fruits[i]
                val b = fruits[j]
                if (a.merged || b.merged) continue
                if (a.type != b.type) continue
                val ra = radiusFor(a.type)
                val d = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
                if (d < ra * 1.6f) {
                    val nt = (a.type + 1).coerceAtMost(TYPES - 1)
                    a.type = nt
                    a.x = (a.x + b.x) / 2f
                    a.y = (a.y + b.y) / 2f
                    a.vy = 0f
                    // 只删除 b，a 保留并升级
                    b.merged = true
                    score += (nt + 1) * 10
                    setScore(score)
                    playSound(SoundManager.EAT)
                    merged = true
                    break
                }
            }
            if (merged) break
        }
        if (merged) {
            fruits.removeAll { it.merged }
        }
        // 越线失败
        for (f in fruits) {
            if (f.y - radiusFor(f.type) < height * LINE_Y_RATIO) {
                over = true
                playSound(SoundManager.LOSE)
                gameOver(GameResult(score, false, "水果堆出底线！得分 " + score))
                return
            }
        }
        invalidate()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawLine(0f, height * LINE_Y_RATIO, width.toFloat(), height * LINE_Y_RATIO, linePaint)
        for (f in fruits) {
            fruitPaint.color = colors[f.type]
            canvas.drawCircle(f.x, f.y, radiusFor(f.type), fruitPaint)
        }
        canvas.drawText("移动手指选择落点 · 相同水果会合并升级", width / 2f, height - dp(16).toFloat(), textPaint)
    }

    override fun reset() {
        fruits.clear()
        dropTimer = 0.5f
        score = 0
        over = false
        setScore(0)
        start()
    }
}