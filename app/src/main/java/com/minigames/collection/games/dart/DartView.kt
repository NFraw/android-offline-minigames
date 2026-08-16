package com.minigames.collection.games.dart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 飞镖：点击发射飞镖，三镖一局，按靶环计分。 */
class DartView(context: Context) : GameView(context) {

    private data class Dart(val x: Float, val y: Float)

    private val darts = ArrayList<Dart>()
    private var dartsLeft = 3
    private var score = 0
    private var over = false
    private var centerX = 0f
    private var centerY = 0f
    private var boardR = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val creamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val bullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5D547.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        boardR = min(w, h) * 0.32f
        centerX = w / 2f
        centerY = h * 0.48f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        if (dartsLeft <= 0) return
        // 落点：靶盘中心附近带随机散布（模拟投掷误差）
        val spread = boardR * 0.8f
        val x = centerX + (Math.random().toFloat() - 0.5f) * spread * 2f
        val y = centerY + (Math.random().toFloat() - 0.5f) * spread * 2f
        darts.add(Dart(x, y))
        dartsLeft--
        // 计分：按环
        val d = Math.hypot((x - centerX).toDouble(), (y - centerY).toDouble()).toFloat()
        val pts = when {
            d < boardR * 0.1f -> 50
            d < boardR * 0.24f -> 25
            d < boardR * 0.42f -> 10
            d < boardR * 0.6f -> 5
            d < boardR * 0.82f -> 2
            else -> 0
        }
        score += pts
        setScore(score)
        playSound(if (pts > 0) SoundManager.EAT else SoundManager.CLICK)
        if (dartsLeft == 0) {
            over = true
            playSound(SoundManager.WIN)
            gameOver(GameResult(score, true, "三镖得分 " + score + "！靶心 50 分"))
        }
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 靶环
        canvas.drawCircle(centerX, centerY, boardR, creamPaint)
        canvas.drawCircle(centerX, centerY, boardR * 0.82f, redPaint)
        canvas.drawCircle(centerX, centerY, boardR * 0.6f, creamPaint)
        canvas.drawCircle(centerX, centerY, boardR * 0.42f, redPaint)
        canvas.drawCircle(centerX, centerY, boardR * 0.24f, creamPaint)
        canvas.drawCircle(centerX, centerY, boardR * 0.1f, bullPaint)
        // 飞镖
        for (d in darts) {
            canvas.drawLine(d.x, d.y - dp(30).toFloat(), d.x, d.y + dp(6).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); strokeWidth = dp(3).toFloat() })
            canvas.drawCircle(d.x, d.y - dp(30).toFloat(), dp(5).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF37C2F0.toInt() })
        }
        canvas.drawText("剩余飞镖 " + dartsLeft + " · 点击发射", width / 2f, centerY + boardR + dp(44).toFloat(), textPaint)
    }

    override fun reset() {
        darts.clear()
        dartsLeft = 3
        score = 0
        over = false
        setScore(0)
        start()
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}
