package com.minigames.collection.games.piano

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 别踩白块：黑块从底部冒出向上滚动，点击最下方黑块得分，踩白/漏块失败。 */
class PianoView(context: Context) : GameView(context) {

    private val COLS = 4
    private val rows = ArrayList<Int>()
    private var offset = 0f
    private var rowH = 0f
    private var colW = 0f
    private var score = 0
    private var speed = 1f
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2333.toInt() }
    private val linePaint = Paint().apply { color = 0xFF3A4668.toInt() }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 28f
        color = 0xFFE8EAF0.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        colW = w / COLS.toFloat()
        rowH = h / 6.5f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val c = (event.x / colW).toInt()
        val r = (event.y / rowH).toInt()
        val target = rows.getOrNull(r) ?: return
        if (c == target) {
            score++
            setScore(score)
            playSound(SoundManager.EAT)
            if (score % 10 == 0) speed += 0.25f
        } else {
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(score, false, "踩到白块了！得分 " + score))
        }
    }

    override fun update(dt: Float) {
        if (over) return
        // 黑块向上滚动
        offset -= dt * rowH * speed * 0.9f
        // 漏块检测：顶部行完全滚出屏幕即失败
        if (rows.isNotEmpty()) {
            val topY = height - rows.size * rowH + offset
            if (topY + rowH < 0f) {
                over = true
                playSound(SoundManager.LOSE)
                gameOver(GameResult(score, false, "漏掉黑块了！得分 " + score))
                return
            }
        }
        while (offset <= -rowH) {
            offset += rowH
            rows.removeAt(rows.size - 1)
            rows.add(0, (0 until COLS).random())
        }
        invalidate()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (i in rows.indices) {
            val y = height - (i + 1) * rowH + offset
            if (y > height || y + rowH < 0) continue
            canvas.drawRect(RectF(0f, y, width.toFloat(), y + rowH), whitePaint)
            val blackCol = rows[i]
            canvas.drawRect(RectF(blackCol * colW, y, (blackCol + 1) * colW, y + rowH), blackPaint)
        }
        for (c in 1 until COLS) {
            canvas.drawLine(c * colW, 0f, c * colW, height.toFloat(), linePaint)
        }
        canvas.drawText("得分 " + score, width / 2f, dp(56).toFloat(), scorePaint)
        canvas.drawText("点击最下方黑色方块，别踩白色", width / 2f, height - dp(30).toFloat(), hintPaint)
    }

    override fun reset() {
        rows.clear()
        for (i in 0 until 12) rows.add((0 until COLS).random())
        offset = 0f
        score = 0
        speed = 1f
        over = false
        setScore(0)
        start()
    }
}