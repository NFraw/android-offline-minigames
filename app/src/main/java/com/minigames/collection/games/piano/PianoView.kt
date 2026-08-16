package com.minigames.collection.games.piano

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.ceil

/** 别踩白块：黑块从顶部下落滚向底部，点击黑块得分，踩白/漏块失败。 */
class PianoView(context: Context) : GameView(context) {

    private data class Row(val col: Int, var y: Float, var hit: Boolean)

    private val COLS = 4
    private val rows = ArrayList<Row>()
    private var rowH = 0f
    private var colW = 0f
    private var score = 0
    private var speed = 1f
    private var over = false
    private var started = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val whiteHitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB9C2D9.toInt() }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2333.toInt() }
    private val blackHitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4668.toInt() }
    private val linePaint = Paint().apply { color = 0xFF3A4668.toInt() }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
        color = 0xFFE8EAF0.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 37f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        colW = w / COLS.toFloat()
        rowH = h / 6.5f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        started = true
        val c = (event.x / colW).toInt()
        val row = rowAt(event.y) ?: return
        if (row.hit) return // 已点过的行不再计分/判罚
        if (c == row.col) {
            row.hit = true
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

    /** 按屏幕坐标反查所在行（y 为行顶坐标，行整体向下滚动）。 */
    private fun rowAt(y: Float): Row? {
        for (r in rows) {
            if (y >= r.y && y < r.y + rowH) return r
        }
        return null
    }

    override fun update(dt: Float) {
        if (over || !started) return
        // 黑块整体向下滚动
        val step = dt * rowH * speed * 0.9f
        for (r in rows) r.y += step
        // 整行滚出屏幕底部：若从未被点击视为漏块
        val it = rows.iterator()
        while (it.hasNext()) {
            val r = it.next()
            if (r.y >= height) {
                it.remove()
                if (!r.hit) {
                    over = true
                    playSound(SoundManager.LOSE)
                    gameOver(GameResult(score, false, "漏掉黑块了！得分 " + score))
                    return
                }
            }
        }
        // 顶部补新行：保证最顶行始终在屏幕上方等待滑入
        while (rows.first().y > -rowH) {
            rows.add(0, Row((0 until COLS).random(), rows.first().y - rowH, false))
        }
        invalidate()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in rows) {
            if (r.y > height || r.y + rowH < 0) continue
            canvas.drawRect(RectF(0f, r.y, width.toFloat(), r.y + rowH), if (r.hit) whiteHitPaint else whitePaint)
            canvas.drawRect(RectF(r.col * colW, r.y, (r.col + 1) * colW, r.y + rowH), if (r.hit) blackHitPaint else blackPaint)
        }
        for (c in 1 until COLS) {
            canvas.drawLine(c * colW, 0f, c * colW, height.toFloat(), linePaint)
        }
        // 得分胶囊：深色圆角底，避免白字叠在白块上
        val pillW = dp(150).toFloat()
        val pillH = dp(46).toFloat()
        roundRect(canvas, RectF(width / 2f - pillW / 2f, dp(10).toFloat(), width / 2f + pillW / 2f, dp(10).toFloat() + pillH), 0xB31C2333.toInt(), 23)
        canvas.drawText("得分 " + score, width / 2f, centerBaseline(dp(10).toFloat() + pillH / 2f, scorePaint), scorePaint)
        canvas.drawText("点击黑色方块，别踩白色", width / 2f, height - dp(30).toFloat(), hintPaint)
    }

    override fun reset() {
        rows.clear()
        // 初始铺满屏幕可见区域，最顶一行在屏幕上方外待进入
        val n = ceil(height / rowH).toInt() + 1
        for (i in 0 until n) {
            rows.add(Row((0 until COLS).random(), i * rowH - rowH, false))
        }
        score = 0
        speed = 1f
        over = false
        started = false
        setScore(0)
        start()
    }
}
