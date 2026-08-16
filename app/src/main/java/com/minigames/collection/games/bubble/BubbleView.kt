package com.minigames.collection.games.bubble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 泡泡龙（简化）：底部发射泡泡，三连以上同色消除，清空所有泡泡获胜。 */
class BubbleView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 10
        private const val TYPES = 4
    }

    private data class Bubble(var x: Float, var y: Float, val col: Int)

    private val bubbles = ArrayList<Bubble>()
    private var shooterX = 0f
    private var shooterY = 0f
    private var shootAngle = 0f
    private var flying: Bubble? = null
    private var flyVx = 0f
    private var flyVy = 0f
    private var score = 0
    private var over = false
    private var bubbleR = 0f

    private val colors = intArrayOf(
        0xFFFF6B6B.toInt(), 0xFFF5A54A.toInt(), 0xFF3ED98A.toInt(), 0xFF37C2F0.toInt()
    )
    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF.toInt()
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        bubbleR = w / (COLS * 2f)
        shooterX = w / 2f
        shooterY = h * 0.93f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            val dx = event.x - shooterX
            val dy = event.y - shooterY
            shootAngle = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
        }
        if (event.action == MotionEvent.ACTION_UP && flying == null) {
            val current = (0 until TYPES).random()
            flying = Bubble(shooterX, shooterY, current)
            val len = Math.hypot(shootAngle.let { Math.cos(it.toDouble()) }, shootAngle.let { Math.sin(it.toDouble()) })
            flyVx = Math.cos(shootAngle.toDouble()).toFloat() / len.toFloat() * height * 1.1f
            flyVy = Math.sin(shootAngle.toDouble()).toFloat() / len.toFloat() * height * 1.1f
            playSound(SoundManager.MOVE)
        }
    }

    override fun update(dt: Float) {
        if (over) return
        val f = flying
        if (f != null) {
            f.x += flyVx * dt
            f.y += flyVy * dt
            if (f.x < bubbleR) {
                f.x = bubbleR
                flyVx = -flyVx
            }
            if (f.x > width - bubbleR) {
                f.x = width - bubbleR
                flyVx = -flyVx
            }
            // 碰到顶部或其他泡泡
            var hit = f.y - bubbleR <= dp(20).toFloat()
            for (b in bubbles) {
                if (Math.hypot((f.x - b.x).toDouble(), (f.y - b.y).toDouble()) < bubbleR * 1.8f) {
                    hit = true
                    break
                }
            }
            if (hit) {
                bubbles.add(Bubble(f.x, f.y, f.col))
                flying = null
                playSound(SoundManager.CLICK)
                removeGroups()
            }
        }
        invalidate()
    }

    private fun removeGroups() {
        // 简化：找 3 连以上同色（水平/近邻），删除并计分
        var removed = true
        while (removed) {
            removed = false
            val toRemove = HashSet<Bubble>()
            for (i in bubbles.indices) {
                val b = bubbles[i]
                val same = bubbles.filter { it.col == b.col && Math.hypot((it.x - b.x).toDouble(), (it.y - b.y).toDouble()) < bubbleR * 2.1f }
                if (same.size >= 3) {
                    toRemove.addAll(same)
                }
            }
            if (toRemove.isNotEmpty()) {
                bubbles.removeAll(toRemove)
                score += toRemove.size * 10
                setScore(score)
                playSound(SoundManager.EAT)
                removed = true
            }
        }
        if (bubbles.isEmpty()) {
            over = true
            playSound(SoundManager.WIN)
            gameOver(GameResult(score, true, "泡泡全部消除！得分 " + score))
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (b in bubbles) {
            bubblePaint.color = colors[b.col]
            canvas.drawCircle(b.x, b.y, bubbleR, bubblePaint)
        }
        val f = flying
        if (f != null) {
            bubblePaint.color = colors[f.col]
            canvas.drawCircle(f.x, f.y, bubbleR, bubblePaint)
        }
        // 瞄准线
        canvas.drawLine(shooterX, shooterY, shooterX + Math.cos(shootAngle.toDouble()).toFloat() * dp(120).toFloat(), shooterY + Math.sin(shootAngle.toDouble()).toFloat() * dp(120).toFloat(), aimPaint)
        canvas.drawCircle(shooterX, shooterY, bubbleR, bubblePaint)
        canvas.drawText("拖动瞄准，松手发射 · 三连消除", width / 2f, height - dp(14).toFloat(), textPaint)
    }

    override fun reset() {
        bubbles.clear()
        // 初始两行
        for (r in 0 until 2) for (c in 0 until COLS) {
            bubbles.add(Bubble(c * bubbleR * 2 + bubbleR, dp(40).toFloat() + r * bubbleR * 1.8f, (0 until TYPES).random()))
        }
        flying = null
        score = 0
        over = false
        setScore(0)
        start()
    }
}
