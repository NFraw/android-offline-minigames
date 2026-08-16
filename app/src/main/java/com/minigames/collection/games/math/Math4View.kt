package com.minigames.collection.games.math

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 四人抢答算术：四分区抢答算术题，先到 3 分获胜。 */
class Math4View(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 3
    }

    private var a = 0
    private var b = 0
    private var op = '+'
    private var answer = 0
    private var questionTimer = 0f
    private val scores = IntArray(4)
    private var flash = -1
    private var flashTimer = 0f
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val questionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 90f
        color = 0xFFE8EAF0.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 36f
        color = 0xFF8A93A6.toInt()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFFE8EAF0.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        reset()
    }

    private fun zoneOf(x: Float, y: Float): Int {
        val left = x < width / 2f
        val top = y < height / 2f
        return if (left && top) 0 else if (!left && top) 1 else if (left) 2 else 3
    }

    override fun onTouch(event: MotionEvent) {
        if (paused) return
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN
        ) return
        val idx = event.actionIndex
        val zone = zoneOf(event.getX(idx), event.getY(idx))
        scores[zone]++
        flash = zone
        flashTimer = 0.6f
        playSound(SoundManager.EAT)
        if (scores[zone] >= WIN_SCORE) {
            over = true
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, "玩家 " + (zone + 1) + " 率先拿到 " + WIN_SCORE + " 分获胜！"))
        }
        invalidate()
    }

    override fun update(dt: Float) {
        if (over) return
        questionTimer -= dt
        if (questionTimer <= 0f) {
            newQuestion()
            questionTimer = 3f
        }
        if (flashTimer > 0f) flashTimer -= dt
        invalidate()
    }

    private fun newQuestion() {
        op = listOf('+', '-', '×').random()
        when (op) {
            '+' -> {
                a = (1..15).random()
                b = (1..15).random()
                answer = a + b
            }
            '-' -> {
                a = (5..15).random()
                b = (1..a).random()
                answer = a - b
            }
            else -> {
                a = (2..8).random()
                b = (2..8).random()
                answer = a * b
            }
        }
        questionTimer = 3f
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (z in 0 until 4) {
            val left = if (z % 2 == 0) 0f else width / 2f
            val top = if (z < 2) 0f else height / 2f
            val qw = width / 2f
            val qh = height / 2f
            val tint = if (flash == z && flashTimer > 0f) 0x663ED98A.toInt() else 0x0AFFFFFF.toInt()
            canvas.drawRect(android.graphics.RectF(left, top, left + qw, top + qh), Paint().apply { color = tint })
            scorePaint.color = colors[z]
            canvas.drawText("P" + (z + 1) + "：" + scores[z], left + qw / 2f, top + dp(28).toFloat(), scorePaint)
        }
        questionPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText(a.toString() + " " + op + " " + b + " = ?", width / 2f, height / 2f + dp(12).toFloat(), questionPaint)
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), Paint().apply { color = 0x33FFFFFF.toInt(); strokeWidth = 2f })
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, Paint().apply { color = 0x33FFFFFF.toInt(); strokeWidth = 2f })
        hintPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("先算出答案就点自己分区抢答 · 先到 " + WIN_SCORE + " 分胜", width / 2f, height - dp(14).toFloat(), hintPaint)
    }

    private val colors = intArrayOf(0xFFE8503A.toInt(), 0xFF37C2F0.toInt(), 0xFF3ED98A.toInt(), 0xFFF5A54A.toInt())

    override fun reset() {
        for (i in 0 until 4) scores[i] = 0
        over = false
        flash = -1
        flashTimer = 0f
        questionTimer = 0.3f
        newQuestion()
        setScore(0)
        start()
    }
}
