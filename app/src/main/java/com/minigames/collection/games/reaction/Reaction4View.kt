package com.minigames.collection.games.reaction

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 四人反应力：四个分区同时变绿，抢点得分，先到 5 分获胜。 */
class Reaction4View(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 5
        private const val WAIT = 0
        private const val READY = 1
        private const val SETTLED = 2
    }

    private var state = WAIT
    private var waitTimer = 0f
    private var settleTimer = 0f
    private val scores = IntArray(4)
    private val flashes = BooleanArray(4)
    private var lastWinner = -1

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val waitPaint = Paint().apply { color = 0xFF2A3557.toInt() }
    private val readyPaint = Paint().apply { color = 0xFF2E9E5B.toInt() }
    private val flashPaint = Paint().apply { color = 0xFFB3392B.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 34f
        color = 0xFFE8EAF0.toInt()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f
        color = 0xFF8A93A6.toInt()
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
        if (state == READY) {
            state = SETTLED
            settleTimer = 1.0f
            lastWinner = zone
            scores[zone]++
            playSound(SoundManager.EAT)
            if (scores[zone] >= WIN_SCORE) {
                playSound(SoundManager.WIN)
                gameOver(GameResult(0, true, "玩家 " + (zone + 1) + " 率先拿到 " + WIN_SCORE + " 分获胜！"))
            }
        } else if (state == WAIT) {
            state = SETTLED
            settleTimer = 1.0f
            flashes[zone] = true
            playSound(SoundManager.LOSE)
        }
        invalidate()
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
        for (i in 0 until 4) flashes[i] = false
        lastWinner = -1
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (z in 0 until 4) {
            val left = if (z % 2 == 0) 0f else width / 2f
            val top = if (z < 2) 0f else height / 2f
            val rect = android.graphics.RectF(left, top, left + width / 2f, top + height / 2f)
            val color = when {
                flashes[z] -> flashPaint.color
                state == READY -> readyPaint.color
                else -> waitPaint.color
            }
            val zonePaint = Paint()
            zonePaint.color = color
            canvas.drawRect(rect, zonePaint)
            val label = when {
                flashes[z] -> "抢拍！"
                state == READY -> "点击！"
                state == SETTLED && lastWinner == z -> "+1"
                else -> "等待…"
            }
            canvas.drawText(label, left + width / 4f, top + height / 4f + dp(10).toFloat(), textPaint)
            scorePaint.color = 0xAAE8EAF0.toInt()
            canvas.drawText("玩家 " + (z + 1) + "：" + scores[z], left + width / 4f, top + height / 4f + dp(44).toFloat(), scorePaint)
        }
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), Paint().apply { color = 0x33FFFFFF.toInt(); strokeWidth = 2f })
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, Paint().apply { color = 0x33FFFFFF.toInt(); strokeWidth = 2f })
        scorePaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("变绿后抢先点击自己分区 · 抢拍无分 · 先到 " + WIN_SCORE + " 分胜", width / 2f, height - dp(14).toFloat(), scorePaint)
    }

    override fun reset() {
        state = WAIT
        waitTimer = 1.2f
        settleTimer = 0f
        for (i in 0 until 4) {
            scores[i] = 0
            flashes[i] = false
        }
        lastWinner = -1
        start()
    }
}
