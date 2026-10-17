package com.minigames.collection.games.plane

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 飞机大战：拖动飞机躲避子弹并射击敌机。 */
class PlaneView(context: Context) : GameView(context) {

    private data class Bullet(var x: Float, var y: Float, val enemy: Boolean)

    private data class Enemy(var x: Float, var y: Float, val speed: Float)

    private val bullets = ArrayList<Bullet>()
    private val enemies = ArrayList<Enemy>()
    private var planeX = 0f
    private var planeY = 0f
    private var planeR = 0f
    private var fireTimer = 0f
    private var spawnTimer = 1.2f
    private var score = 0
    private var lives = 3
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val planePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val enemyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5D547.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 41f
        color = 0xFFE8EAF0.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 36f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        planeR = dp(22).toFloat()
        planeX = w / 2f
        planeY = h * 0.8f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            planeX = event.x.coerceIn(planeR, width - planeR)
            planeY = event.y.coerceIn(height * 0.4f, height - planeR)
        }
    }

    override fun update(dt: Float) {
        if (over) return
        // 自动射击
        fireTimer -= dt
        if (fireTimer <= 0f) {
            fireTimer = 0.28f
            bullets.add(Bullet(planeX, planeY - planeR, false))
        }
        // 生成敌机
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = (1.2f - score * 0.004f).coerceAtLeast(0.4f)
            enemies.add(Enemy(width * (0.1f + Math.random().toFloat() * 0.8f), -dp(20).toFloat(), height * (0.35f + Math.random().toFloat() * 0.3f)))
        }
        val bit = bullets.iterator()
        while (bit.hasNext()) {
            val b = bit.next()
            b.y += if (b.enemy) height * 0.7f * dt else -height * 0.9f * dt
            if (b.y < -20 || b.y > height + 20) bit.remove()
        }
        val eit = enemies.iterator()
        while (eit.hasNext()) {
            val e = eit.next()
            e.y += e.speed * dt
            if (e.y > height + dp(30).toFloat()) {
                eit.remove()
                lives--
                if (lives <= 0) {
                    over = true
                    playSound(SoundManager.LOSE)
                    gameOver(GameResult(score, false, "敌机突破防线！得分 " + score))
                    return
                }
                playSound(SoundManager.LOSE)  // 只扣命时播一次，最后一条命不再重复播
            }
        }
        // 子弹命中敌机
        for (b in bullets) {
            if (b.enemy) continue
            val eit2 = enemies.iterator()
            while (eit2.hasNext()) {
                val e = eit2.next()
                if (Math.hypot((b.x - e.x).toDouble(), (b.y - e.y).toDouble()) < dp(30).toFloat()) {
                    eit2.remove()
                    b.y = -999f
                    score += 10
                    setScore(score)
                    playSound(SoundManager.EAT)
                }
            }
        }
        // 敌机撞玩家
        for (e in enemies) {
            if (Math.hypot((e.x - planeX).toDouble(), (e.y - planeY).toDouble()) < planeR + dp(24).toFloat()) {
                over = true
                playSound(SoundManager.LOSE)
                gameOver(GameResult(score, false, "被敌机撞毁！得分 " + score))
                return
            }
        }
        invalidate()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (b in bullets) {
            canvas.drawCircle(b.x, b.y, dp(5).toFloat(), bulletPaint)
        }
        for (e in enemies) {
            canvas.drawCircle(e.x, e.y, dp(24).toFloat(), enemyPaint)
        }
        // 玩家飞机（三角形）
        val path = android.graphics.Path()
        path.moveTo(planeX, planeY - planeR)
        path.lineTo(planeX - planeR, planeY + planeR * 0.8f)
        path.lineTo(planeX + planeR, planeY + planeR * 0.8f)
        path.close()
        canvas.drawPath(path, planePaint)
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("得分 " + score, width / 2f, dp(56).toFloat(), textPaint)
        textPaint.color = 0xFFFF6B6B.toInt()
        canvas.drawText("❤ ".repeat(lives.coerceAtLeast(0)), width / 2f, dp(92).toFloat(), textPaint)
        hintPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("拖动飞机自动开火 · 击落敌机", width / 2f, height - dp(66).toFloat(), hintPaint)
    }

    override fun reset() {
        bullets.clear()
        enemies.clear()
        fireTimer = 0f
        spawnTimer = 1.2f
        score = 0
        lives = 3
        over = false
        // 复位飞机位置：否则"重开/再来一局"后飞机停在上一局结束的位置
        // （撞机时通常贴着屏幕边缘），开局就在角落里很别扭。
        planeX = width / 2f
        planeY = height * 0.8f
        setScore(0)
        start()
    }
}
