package com.minigames.collection.games.math

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 抢答算术（双人）：先答对者得分，先到 5 分获胜。 */
class MathDuelView(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 5
    }

    private var a = 0
    private var b = 0
    private var op = '+'
    private var answer = 0
    private var questionTimer = 0f
    private var s1 = 0
    private var s2 = 0
    private var flash = -1
    private var flashTimer = 0f
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val questionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 72f
        color = 0xFFE8EAF0.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = 0xFF8A93A6.toInt()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f
        color = 0xFFE8EAF0.toInt()
    }
    private val p1Paint = Paint().apply { color = 0x334A6CF7.toInt() }
    private val p2Paint = Paint().apply { color = 0x33FF7A45.toInt() }

    override fun onLayout(w: Int, h: Int) {
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (paused) return
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN
        ) return
        val idx = event.actionIndex
        val tap = if (event.getY(idx) < height / 2f) 0 else 1
        if (tap == 0) s1++ else s2++
        flash = tap
        flashTimer = 0.6f
        playSound(SoundManager.EAT)
        if (s1 >= WIN_SCORE || s2 >= WIN_SCORE) {
            over = true
            val p1Win = s1 > s2
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, p1Win, "玩家 " + (if (p1Win) 1 else 2) + " 率先拿到 " + WIN_SCORE + " 分获胜！"))
        }
        invalidate()
    }

    override fun update(dt: Float) {
        if (over) return
        questionTimer -= dt
        if (questionTimer <= 0f) {
            newQuestion()
            questionTimer = 2.5f
        }
        if (flashTimer > 0f) flashTimer -= dt
        invalidate()
    }

    private fun newQuestion() {
        op = listOf('+', '-', '×').random()
        when (op) {
            '+' -> {
                a = (1..20).random()
                b = (1..20).random()
                answer = a + b
            }
            '-' -> {
                a = (5..20).random()
                b = (1..a).random()
                answer = a - b
            }
            else -> {
                a = (2..9).random()
                b = (2..9).random()
                answer = a * b
            }
        }
        questionTimer = 2.5f
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawRect(android.graphics.RectF(0f, 0f, width.toFloat(), height / 2f), p1Paint)
        canvas.drawRect(android.graphics.RectF(0f, height / 2f, width.toFloat(), height.toFloat()), p2Paint)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, Paint().apply { color = 0x44FFFFFF.toInt(); strokeWidth = 2f })
        questionPaint.color = if (flash == 0) 0xFF3ED98A.toInt() else if (flash == 1) 0xFF3ED98A.toInt() else 0xFFE8EAF0.toInt()
        questionPaint.textSize = 72f
        canvas.drawText(a.toString() + " " + op + " " + b + " = ?", width / 2f, height / 2f + dp(10).toFloat(), questionPaint)
        scorePaint.color = 0xFF6C8BFF.toInt()
        canvas.drawText("玩家 1：" + s1, width / 2f, dp(56).toFloat(), scorePaint)
        scorePaint.color = 0xFFFFA04A.toInt()
        canvas.drawText("玩家 2：" + s2, width / 2f, height - dp(48).toFloat(), scorePaint)
        hintPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("谁先算出答案就点自己半屏抢答 · 先到 " + WIN_SCORE + " 分胜", width / 2f, height / 2f + dp(70).toFloat(), hintPaint)
    }

    override fun reset() {
        s1 = 0
        s2 = 0
        over = false
        flash = -1
        flashTimer = 0f
        questionTimer = 0.3f
        newQuestion()
        setScore(0)
        start()
    }
}
