package com.minigames.collection.games.tank

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 坦克大战（简化）：拖动坦克左右移动并自动开炮，击毁 12 辆敌坦获胜。 */
class TankView(context: Context) : GameView(context) {

    companion object {
        private const val TARGET = 12
    }

    private data class Shell(var x: Float, var y: Float)

    private data class Enemy(var x: Float, var y: Float, val speed: Float)

    private val shells = ArrayList<Shell>()
    private val enemies = ArrayList<Enemy>()
    private var tankX = 0f
    private var tankY = 0f
    private var tankW = 0f
    private var fireTimer = 0f
    private var spawnTimer = 1.0f
    private var kills = 0
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val tankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
    private val enemyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
    private val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5D547.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
        color = 0xFFE8EAF0.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        tankW = w * 0.16f
        tankX = w / 2f
        tankY = h * 0.85f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
            tankX = event.x.coerceIn(tankW / 2f, width - tankW / 2f)
        }
    }

    override fun update(dt: Float) {
        if (over) return
        fireTimer -= dt
        if (fireTimer <= 0f) {
            fireTimer = 0.35f
            shells.add(Shell(tankX, tankY - tankW * 0.5f))
        }
        spawnTimer -= dt
        if (spawnTimer <= 0f && enemies.size < 4) {
            spawnTimer = (1.4f - kills * 0.05f).coerceAtLeast(0.5f)
            enemies.add(Enemy(width * (0.1f + Math.random().toFloat() * 0.8f), -dp(24).toFloat(), height * (0.25f + Math.random().toFloat() * 0.2f)))
        }
        val sit = shells.iterator()
        while (sit.hasNext()) {
            val s = sit.next()
            s.y -= height * 0.8f * dt
            if (s.y < -dp(20).toFloat()) sit.remove()
        }
        val eit = enemies.iterator()
        while (eit.hasNext()) {
            val e = eit.next()
            e.y += e.speed * dt
            if (e.y > tankY) {
                over = true
                playSound(SoundManager.LOSE)
                gameOver(GameResult(kills * 10, false, "敌坦突破防线！击毁 " + kills + " 辆"))
                return
            }
        }
        for (s in shells) {
            val eit2 = enemies.iterator()
            while (eit2.hasNext()) {
                val e = eit2.next()
                if (Math.hypot((s.x - e.x).toDouble(), (s.y - e.y).toDouble()) < tankW * 0.55f) {
                    eit2.remove()
                    s.y = -999f
                    kills++
                    setScore(kills * 10)
                    playSound(SoundManager.EAT)
                    if (kills >= TARGET) {
                        over = true
                        playSound(SoundManager.WIN)
                        gameOver(GameResult(kills * 10, true, "击毁全部 " + TARGET + " 辆敌坦，胜利！"))
                        return
                    }
                }
            }
        }
        invalidate()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (s in shells) {
            canvas.drawCircle(s.x, s.y, dp(6).toFloat(), shellPaint)
        }
        for (e in enemies) {
            canvas.drawRoundRect(RectF(e.x - tankW * 0.4f, e.y - tankW * 0.3f, e.x + tankW * 0.4f, e.y + tankW * 0.3f), dp(8).toFloat(), dp(8).toFloat(), enemyPaint)
        }
        // 玩家坦克
        canvas.drawRoundRect(RectF(tankX - tankW / 2f, tankY - tankW * 0.35f, tankX + tankW / 2f, tankY + tankW * 0.35f), dp(10).toFloat(), dp(10).toFloat(), tankPaint)
        canvas.drawRect(RectF(tankX - dp(4).toFloat(), tankY - tankW * 0.55f, tankX + dp(4).toFloat(), tankY - tankW * 0.3f), tankPaint)
        canvas.drawText("击毁 " + kills + " / " + TARGET + " · 拖动坦克自动开炮", width / 2f, dp(56).toFloat(), textPaint)
    }

    override fun reset() {
        shells.clear()
        enemies.clear()
        fireTimer = 0f
        spawnTimer = 1.0f
        kills = 0
        over = false
        setScore(0)
        start()
    }
}
