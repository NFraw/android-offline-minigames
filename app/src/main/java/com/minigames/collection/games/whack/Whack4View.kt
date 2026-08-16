package com.minigames.collection.games.whack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 四人打地鼠：四分区各有一只地鼠，限时 30 秒各打各的，分高者胜。 */
class Whack4View(context: Context) : GameView(context) {

    companion object {
        private const val GAME_SECONDS = 30
    }

    private val moleTimer = FloatArray(4)
    private val moleShown = BooleanArray(4)
    private val scores = IntArray(4)
    private var timeLeft = GAME_SECONDS.toFloat()
    private var started = false
    private var over = false
    private val startBtn = RectF()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E1424.toInt() }
    private val molePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8865A.toInt() }
    private val moleDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A5A38.toInt() }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A2E.toInt() }
    private val greenBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 30f
        color = 0xFFE8EAF0.toInt()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val bw = w * 0.55f
        val bh = dp(68).toFloat()
        startBtn.set(w / 2f - bw / 2f, h * 0.55f - bh / 2f, w / 2f + bw / 2f, h * 0.55f + bh / 2f)
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        if (!started) {
            if (startBtn.contains(event.x, event.y)) {
                started = true
                timeLeft = GAME_SECONDS.toFloat()
                playSound(SoundManager.MOVE)
            }
            return
        }
        val zone = if (event.x < width / 2f) if (event.y < height / 2f) 0 else 2 else if (event.y < height / 2f) 1 else 3
        if (moleShown[zone]) {
            moleShown[zone] = false
            moleTimer[zone] = 0.5f + Math.random().toFloat() * 0.7f
            scores[zone]++
            playSound(SoundManager.EAT)
        }
        invalidate()
    }

    override fun update(dt: Float) {
        if (over || !started) return
        timeLeft -= dt
        if (timeLeft <= 0f) {
            over = true
            val best = scores.max()
            val winners = scores.indices.filter { scores[it] == best }
            val names = winners.joinToString("、") { "玩家 " + (it + 1) }
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, "时间到！" + names + " 以 " + best + " 分获胜！(" + scores.joinToString(":") + ")"))
            return
        }
        for (z in 0 until 4) {
            if (moleShown[z]) {
                moleTimer[z] -= dt
                if (moleTimer[z] <= 0f) {
                    moleShown[z] = false
                    moleTimer[z] = 0.4f + Math.random().toFloat() * 0.6f
                }
            } else {
                moleTimer[z] -= dt
                if (moleTimer[z] <= 0f) {
                    moleShown[z] = true
                    moleTimer[z] = 0.9f
                }
            }
        }
        invalidate()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        if (!started) {
            textPaint.textSize = 44f
            canvas.drawText("🔨 四人打地鼠", width / 2f, height * 0.36f, textPaint)
            textPaint.textSize = 24f
            textPaint.color = 0xFF8A93A6.toInt()
            canvas.drawText("四个分区各打各的地鼠 · 限时 " + GAME_SECONDS + " 秒", width / 2f, height * 0.44f, textPaint)
            canvas.drawRoundRect(startBtn, dp(18).toFloat(), dp(18).toFloat(), greenBtnPaint)
            textPaint.textSize = 30f
            textPaint.color = 0xFF141A2E.toInt()
            canvas.drawText("开始游戏", width / 2f, startBtn.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
            textPaint.color = 0xFFE8EAF0.toInt()
            return
        }
        for (z in 0 until 4) {
            val left = if (z % 2 == 0) 0f else width / 2f
            val top = if (z < 2) 0f else height / 2f
            val qw = width / 2f
            val qh = height / 2f
            val cx = left + qw / 2f
            val cy = top + qh / 2f
            val r = min(qw, qh) * 0.3f
            canvas.drawOval(RectF(cx - r, cy - r, cx + r, cy + r), holePaint)
            if (moleShown[z]) {
                canvas.drawCircle(cx - r * 0.28f, cy - r * 0.35f, r * 0.3f, molePaint)
                canvas.drawCircle(cx + r * 0.28f, cy - r * 0.35f, r * 0.3f, molePaint)
                canvas.drawCircle(cx, cy - r * 0.08f, r * 0.34f, molePaint)
                canvas.drawCircle(cx - r * 0.12f, cy - r * 0.16f, r * 0.05f, eyePaint)
                canvas.drawCircle(cx + r * 0.12f, cy - r * 0.16f, r * 0.05f, eyePaint)
                canvas.drawCircle(cx, cy + r * 0.12f, r * 0.1f, moleDarkPaint)
            }
            scorePaint.color = 0xFFE8EAF0.toInt()
            scorePaint.textSize = 22f
            canvas.drawText("P" + (z + 1) + "：" + scores[z], cx, top + dp(28).toFloat(), scorePaint)
        }
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), Paint().apply { color = 0x22FFFFFF.toInt(); strokeWidth = 2f })
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, Paint().apply { color = 0x22FFFFFF.toInt(); strokeWidth = 2f })
        textPaint.textSize = 22f
        textPaint.color = if (timeLeft <= 10) 0xFFFF5252.toInt() else 0xFFE8EAF0.toInt()
        canvas.drawText("⏱ " + Math.ceil(timeLeft.toDouble()).toInt() + " 秒 · 点击自己分区的地鼠", width / 2f, dp(30).toFloat(), textPaint)
    }

    override fun reset() {
        started = false
        timeLeft = GAME_SECONDS.toFloat()
        for (i in 0 until 4) {
            moleShown[i] = false
            moleTimer[i] = 0.6f
            scores[i] = 0
        }
        over = false
        setScore(0)
        start()
    }
}
