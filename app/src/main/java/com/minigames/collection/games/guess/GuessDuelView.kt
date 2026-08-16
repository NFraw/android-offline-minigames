package com.minigames.collection.games.guess

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 猜数字对决：电脑出一个 4 位不重复数字，双方轮流猜，先猜中者胜。 */
class GuessDuelView(context: Context) : GameView(context) {

    private data class Guess(val player: Int, val text: String, val a: Int, val b: Int)

    private val secret = IntArray(4)
    private val input = ArrayList<Int>()
    private val history = ArrayList<Guess>()
    private var turn = 0
    private var over = false
    private var resultMsg = ""
    private val keyRects = ArrayList<RectF>()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val okPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
    private val delPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = 0xFFE8EAF0.toInt()
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        // 数字键盘 3x4 底部
        keyRects.clear()
        val kw = w * 0.22f
        val kh = dp(52).toFloat()
        val startX = w / 2f - kw * 1.5f
        val startY = h * 0.62f
        var i = 0
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                keyRects.add(RectF(startX + col * kw, startY + row * kh, startX + (col + 1) * kw, startY + (row + 1) * kh))
                i++
            }
        }
        keyRects.add(RectF(startX, startY + 3 * kh, startX + kw, startY + 4 * kh))          // 删除
        keyRects.add(RectF(startX + kw, startY + 3 * kh, startX + 2 * kw, startY + 4 * kh))  // 0
        keyRects.add(RectF(startX + 2 * kw, startY + 3 * kh, startX + 3 * kw, startY + 4 * kh)) // 确定
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        for (i in keyRects.indices) {
            if (keyRects[i].contains(event.x, event.y)) {
                when {
                    i < 9 -> {
                        val n = i + 1
                        if (input.size < 4 && !input.contains(n)) {
                            input.add(n)
                            playSound(SoundManager.CLICK)
                        }
                    }
                    i == 9 -> {
                        if (input.isNotEmpty()) {
                            input.removeAt(input.size - 1)
                            playSound(SoundManager.CLICK)
                        }
                    }
                    i == 10 -> {
                        if (input.size < 4 && !input.contains(0)) {
                            input.add(0)
                            playSound(SoundManager.CLICK)
                        }
                    }
                    i == 11 -> submit()
                }
                invalidate()
                return
            }
        }
    }

    private fun submit() {
        if (input.size < 4) return
        var a = 0
        var b = 0
        for (i in 0 until 4) {
            if (input[i] == secret[i]) {
                a++
            } else if (secret.contains(input[i])) {
                b++
            }
        }
        val text = input.joinToString("")
        history.add(Guess(turn, text, a, b))
        playSound(if (a == 4) SoundManager.WIN else SoundManager.MOVE)
        if (a == 4) {
            over = true
            resultMsg = "玩家 " + (turn + 1) + " 猜中 " + text + " 获胜！共用 " + history.size + " 次猜测"
            gameOver(GameResult(0, true, resultMsg))
        } else {
            turn = 1 - turn
        }
        input.clear()
        invalidate()
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("玩家 " + (turn + 1) + " 输入你的猜测", width / 2f, dp(48).toFloat(), textPaint)
        // 输入显示
        val inputText = input.joinToString("") { it.toString() } + "_".repeat((4 - input.size).coerceAtLeast(0))
        textPaint.color = 0xFFF5D547.toInt()
        canvas.drawText(inputText, width / 2f, dp(96).toFloat(), textPaint)
        // 历史
        smallPaint.textSize = 18f
        smallPaint.color = 0xFF8A93A6.toInt()
        var y = dp(130).toFloat()
        for (g in history.takeLast(8)) {
            canvas.drawText("P" + (g.player + 1) + " 猜 " + g.text + "  →  " + g.a + "A" + g.b + "B", width / 2f, y, smallPaint)
            y += dp(28).toFloat()
        }
        // 键盘
        for (i in 0 until 9) {
            canvas.drawRoundRect(keyRects[i], dp(10).toFloat(), dp(10).toFloat(), keyPaint)
            textPaint.textSize = 24f
            textPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawText((i + 1).toString(), keyRects[i].centerX(), keyRects[i].centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }
        canvas.drawRoundRect(keyRects[9], dp(10).toFloat(), dp(10).toFloat(), delPaint)
        canvas.drawText("删", keyRects[9].centerX(), keyRects[9].centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        canvas.drawRoundRect(keyRects[10], dp(10).toFloat(), dp(10).toFloat(), keyPaint)
        canvas.drawText("0", keyRects[10].centerX(), keyRects[10].centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        canvas.drawRoundRect(keyRects[11], dp(10).toFloat(), dp(10).toFloat(), okPaint)
        canvas.drawText("确定", keyRects[11].centerX(), keyRects[11].centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        smallPaint.textSize = 16f
        smallPaint.color = 0xFF6A7284.toInt()
        canvas.drawText("A=数字和位置都对 B=数字对位置错 · 4 位数字不重复", width / 2f, keyRects[11].bottom + dp(22).toFloat(), smallPaint)
    }

    override fun reset() {
        val nums = (0..9).toMutableList()
        nums.shuffle()
        for (i in 0 until 4) secret[i] = nums[i]
        input.clear()
        history.clear()
        turn = 0
        over = false
        setScore(0)
        start()
    }
}
