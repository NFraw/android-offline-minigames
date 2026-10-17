package com.minigames.collection.games.reaction

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/**
 * 四人反应力：四个分区同时变绿，各分区独立计分（四指同点四区都生效），先到 5 分获胜。
 * 变绿前点击该分区 = 抢拍，该分区本轮作废（不再得分），不影响其他分区。
 */
class Reaction4View(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 5
        private const val WAIT = 0
        private const val READY = 1
        private const val SETTLED = 2
    }

    private var state = WAIT
    private var waitTimer = 0f
    private var readyTimer = 0f
    private var settleTimer = 0f
    private val scores = IntArray(4)
    private val scored = BooleanArray(4)  // 本回合该分区已得分
    private val flashes = BooleanArray(4) // 本回合该分区抢拍被罚
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val waitPaint = Paint().apply { color = 0xFF2A3557.toInt() }
    private val readyPaint = Paint().apply { color = 0xFF2E9E5B.toInt() }
    private val flashPaint = Paint().apply { color = 0xFFB3392B.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 53f
        color = 0xFFE8EAF0.toInt()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 36f
        color = 0xFF8A93A6.toInt()
    }
    /** 分区底色画笔（复用，避免每帧新建 Paint 造成 GC 抖动）。 */
    private val zonePaint = Paint()
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt()
        strokeWidth = 2f
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
        if (paused || over) return
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN
        ) return
        val idx = event.actionIndex
        val zone = zoneOf(event.getX(idx), event.getY(idx))
        if (state == READY) {
            // 每区每轮只能得一次分；抢拍被罚的分区本轮不得分
            if (scored[zone] || flashes[zone]) {
                invalidate()
                return
            }
            scored[zone] = true
            scores[zone]++
            playSound(SoundManager.EAT)
            if (scores[zone] >= WIN_SCORE) {
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(0, true, "玩家 " + (zone + 1) + " 率先拿到 " + WIN_SCORE + " 分获胜！"))
                return
            }
            if (allZonesDone()) {
                state = SETTLED
                settleTimer = 0.9f
            }
        } else if (state == WAIT) {
            // 抢拍：只罚该分区本轮，不影响其他分区继续
            if (!flashes[zone]) {
                flashes[zone] = true
                playSound(SoundManager.LOSE)
            }
        }
        invalidate()
    }

    private fun allZonesDone(): Boolean {
        for (i in 0 until 4) if (!scored[i] && !flashes[i]) return false
        return true
    }

    override fun update(dt: Float) {
        if (state == WAIT) {
            waitTimer -= dt
            if (waitTimer <= 0f) {
                state = READY
                readyTimer = 2.0f
                playSound(SoundManager.MOVE)
            }
        } else if (state == READY) {
            readyTimer -= dt
            if (readyTimer <= 0f) {
                state = SETTLED
                settleTimer = 0.9f
            }
        } else if (state == SETTLED) {
            settleTimer -= dt
            if (settleTimer <= 0f) newRound()
        }
    }

    private fun newRound() {
        state = WAIT
        waitTimer = 0.8f + Math.random().toFloat() * 1.8f
        for (i in 0 until 4) {
            scored[i] = false
            flashes[i] = false
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (z in 0 until 4) {
            val left = if (z % 2 == 0) 0f else width / 2f
            val top = if (z < 2) 0f else height / 2f
            val rect = RectF(left, top, left + width / 2f, top + height / 2f)
            zonePaint.color = when {
                flashes[z] -> flashPaint.color
                state == READY -> readyPaint.color
                else -> waitPaint.color
            }
            canvas.drawRect(rect, zonePaint)
            val label = when {
                flashes[z] -> "抢拍！"
                scored[z] -> "+1"
                state == READY -> "点击！"
                else -> "等待…"
            }
            val cx = left + width / 4f
            val cy = top + height / 4f
            // 上半屏两个分区（玩家 1、玩家 2）的文字必须旋转 180°，
            // 否则坐在屏幕上方的两人看到的是倒字，无法确认自己的分区与比分。
            val faceUp = z < 2
            scorePaint.color = 0xAAE8EAF0.toInt()
            if (faceUp) {
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(180f)
                canvas.translate(-cx, -cy)
                canvas.drawText(label, cx, cy + dp(10).toFloat(), textPaint)
                canvas.drawText("玩家 " + (z + 1) + "：" + scores[z], cx, cy + dp(44).toFloat(), scorePaint)
                canvas.restore()
            } else {
                canvas.drawText(label, cx, cy + dp(10).toFloat(), textPaint)
                canvas.drawText("玩家 " + (z + 1) + "：" + scores[z], cx, cy + dp(44).toFloat(), scorePaint)
            }
        }
        canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), midPaint)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, midPaint)
        scorePaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("四分区同时变绿 · 各点各的区可同时得分 · 抢拍该区本轮作废 · 先到 " + WIN_SCORE + " 分胜", width / 2f, height - dp(14).toFloat(), scorePaint)
    }

    override fun reset() {
        state = WAIT
        waitTimer = 1.2f
        readyTimer = 0f
        settleTimer = 0f
        over = false
        for (i in 0 until 4) {
            scores[i] = 0
            scored[i] = false
            flashes[i] = false
        }
        start()
    }
}
