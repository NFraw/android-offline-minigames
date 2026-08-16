package com.minigames.collection.games.reaction

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 反应力对决：屏幕变绿后先点击自己半屏者得分，抢拍（未变绿就点）判对方得分，先到 10 分获胜。 */
class ReactionView(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 10
        private const val WAIT = 0
        private const val READY = 1
        private const val SETTLED = 2
    }

    private var state = WAIT
    private var waitTimer = 0f
    private var settleTimer = 0f
    private var s1 = 0
    private var s2 = 0
    private var earlyFlash1 = false
    private var earlyFlash2 = false
    private var lastWinner = -1

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val waitPaint = Paint().apply { color = 0xFF2A3557.toInt() }
    private val readyPaint = Paint().apply { color = 0xFF2E9E5B.toInt() }
    private val flashPaint = Paint().apply { color = 0xFFB3392B.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 62f
        color = 0xFFE8EAF0.toInt()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFF8A93A6.toInt()
    }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt()
        strokeWidth = 2f
    }

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
        if (state == READY) {
            state = SETTLED
            settleTimer = 1.0f
            lastWinner = tap
            if (tap == 0) s1++ else s2++
            playSound(SoundManager.EAT)
            if (s1 >= WIN_SCORE || s2 >= WIN_SCORE) {
                val p1Win = s1 > s2
                playSound(if (p1Win) SoundManager.WIN else SoundManager.LOSE)
                gameOver(GameResult(0, p1Win, "玩家 " + (if (p1Win) 1 else 2) + " 率先拿到 " + WIN_SCORE + " 分获胜！"))
            }
        } else if (state == WAIT) {
            // 抢拍：对方得分
            state = SETTLED
            settleTimer = 1.0f
            lastWinner = 1 - tap
            if (tap == 0) earlyFlash1 = true else earlyFlash2 = true
            if (lastWinner == 0) s1++ else s2++
            playSound(SoundManager.LOSE)
        }
    }

    override fun update(dt: Float) {
        if (state == WAIT) {
            waitTimer -= dt
            if (waitTimer <= 0f) {
                state = READY
                playSound(SoundManager.MOVE)
            }
        } else if (state == SETTLED) {
            settleTimer -= dt
            if (settleTimer <= 0f) newRound()
        }
    }

    private fun newRound() {
        state = WAIT
        waitTimer = 0.8f + Math.random().toFloat() * 1.8f
        earlyFlash1 = false
        earlyFlash2 = false
        lastWinner = -1
    }

    private fun drawHalf(canvas: Canvas, top: Boolean, color: Int, label: String) {
        val r = if (top) RectF(0f, 0f, width.toFloat(), height / 2f)
        else RectF(0f, height / 2f, width.toFloat(), height.toFloat())
        val halfPaint = Paint()
        halfPaint.color = color
        canvas.drawRect(r, halfPaint)
        canvas.drawText(label, width / 2f, if (top) height * 0.42f else height * 0.92f, textPaint)
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        val label1: String
        val label2: String
        val c1: Int
        val c2: Int
        when (state) {
            READY -> {
                label1 = "点击！"
                label2 = "点击！"
                c1 = readyPaint.color
                c2 = readyPaint.color
            }
            SETTLED -> {
                label1 = if (lastWinner == 0) "+1" else ""
                label2 = if (lastWinner == 1) "+1" else ""
                c1 = if (earlyFlash1) flashPaint.color else if (lastWinner == 0) readyPaint.color else waitPaint.color
                c2 = if (earlyFlash2) flashPaint.color else if (lastWinner == 1) readyPaint.color else waitPaint.color
            }
            else -> {
                label1 = "等待…"
                label2 = "等待…"
                c1 = waitPaint.color
                c2 = waitPaint.color
            }
        }
        drawHalf(canvas, true, c1, label1)
        drawHalf(canvas, false, c2, label2)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, midPaint)
        canvas.drawText("玩家1  " + s1 + " : " + s2 + "  玩家2 · 先到 " + WIN_SCORE + " 分获胜", width / 2f, dp(32).toFloat(), scorePaint)
        canvas.drawText("变绿后抢先点击自己半屏 · 抢拍判负", width / 2f, height - dp(16).toFloat(), scorePaint)
    }

    override fun reset() {
        state = WAIT
        waitTimer = 1.2f
        settleTimer = 0f
        s1 = 0
        s2 = 0
        earlyFlash1 = false
        earlyFlash2 = false
        lastWinner = -1
        start()
    }
}
