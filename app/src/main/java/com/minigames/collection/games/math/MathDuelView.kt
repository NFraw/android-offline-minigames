package com.minigames.collection.games.math

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.Toast
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/**
 * 抢答算术（双人）：题目展示后保持不变，直到有人提交正确答案才换新题。
 * 每位玩家有独立数字键盘（0-9 + 删除 + 提交）；答错则该玩家本题锁定，先到 5 分获胜。
 */
class MathDuelView(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 5
        private const val MAX_INPUT = 4
    }

    private data class Key(val rect: RectF, val label: String, val action: Int)

    private var a = 0
    private var b = 0
    private var op = '+'
    private var answer = 0
    private var s1 = 0
    private var s2 = 0
    private var input1 = ""
    private var input2 = ""
    private var locked1 = false
    private var locked2 = false
    private var flash = -1
    private var flashTimer = 0f
    private var over = false

    private val keys1 = ArrayList<Key>()
    private val keys2 = ArrayList<Key>()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val p1Tint = Paint().apply { color = 0x334A6CF7.toInt() }
    private val p2Tint = Paint().apply { color = 0x33FF7A45.toInt() }
    private val questionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 88f
        color = 0xFFE8EAF0.toInt()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFFE8EAF0.toInt()
    }
    private val inputPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 64f
        color = 0xFFFFFFFF.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 30f
        color = 0xFF8A93A6.toInt()
    }
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 30f
        color = 0xFFFF5252.toInt()
    }
    private val key1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val key1Stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A6CF7.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val key2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A2A25.toInt() }
    private val key2Stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF7A45.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 48f
        color = 0xFFFFFFFF.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val halfH = h / 2f
        val padTop = dp(140).toFloat()
        val padBottom = dp(150).toFloat()
        val keyW = w * 0.9f / 3f
        val keyH = (halfH - padTop - padBottom) / 4f
        val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        fun build(list: ArrayList<Key>, top: Float) {
            list.clear()
            for (i in labels.indices) {
                val r = i / 3
                val c = i % 3
                val x = (w - keyW * 3) / 2f + c * keyW
                list.add(Key(
                    RectF(x + 4f, top + r * keyH + 4f, x + keyW - 4f, top + (r + 1) * keyH - 4f),
                    labels[i], i))
            }
        }
        build(keys1, padTop)
        build(keys2, halfH + padTop)
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (paused || over) return
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        val tap = if (event.y < height / 2f) 0 else 1
        val keys = if (tap == 0) keys1 else keys2
        // 玩家 1 的上半屏整体旋转 180°，触摸点需按同样的旋转反变换回去做命中测试
        val px = if (tap == 0) width - event.x else event.x
        val py = if (tap == 0) height / 2f - event.y else event.y
        for (k in keys) {
            if (k.rect.contains(px, py)) {
                onKey(tap, k)
                return
            }
        }
    }

    private fun onKey(tap: Int, k: Key) {
        val locked = if (tap == 0) locked1 else locked2
        if (locked) {
            Toast.makeText(context, "本题答错已锁定，等下一题", Toast.LENGTH_SHORT).show()
            return
        }
        when {
            k.action <= 9 -> {
                val cur = if (tap == 0) input1 else input2
                if (cur.length < MAX_INPUT) {
                    if (tap == 0) input1 += k.label else input2 += k.label
                    playSound(SoundManager.CLICK)
                }
            }
            k.action == 10 -> {
                if (tap == 0) input1 = input1.dropLast(1) else input2 = input2.dropLast(1)
                playSound(SoundManager.MOVE)
            }
            else -> submit(tap)
        }
        invalidate()
    }

    private fun submit(tap: Int) {
        val input = if (tap == 0) input1 else input2
        val v = input.toIntOrNull() ?: return
        if (v == answer) {
            if (tap == 0) s1++ else s2++
            flash = tap
            flashTimer = 0.8f
            playSound(SoundManager.EAT)
            if (s1 >= WIN_SCORE || s2 >= WIN_SCORE) {
                over = true
                playSound(SoundManager.WIN)
                val p1Win = s1 > s2
                gameOver(GameResult(0, p1Win, "玩家 " + (if (p1Win) 1 else 2) + " 率先拿到 " + WIN_SCORE + " 分获胜！"))
            } else {
                newQuestion()
            }
        } else {
            if (tap == 0) locked1 = true else locked2 = true
            Toast.makeText(context, "答错了，本题不能再答", Toast.LENGTH_SHORT).show()
            playSound(SoundManager.LOSE)
        }
        invalidate()
    }

    override fun update(dt: Float) {
        if (over) return
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
        input1 = ""
        input2 = ""
        locked1 = false
        locked2 = false
    }

    private fun drawPad(canvas: Canvas, keys: List<Key>, fill: Paint, stroke: Paint) {
        for (k in keys) {
            canvas.drawRoundRect(k.rect, dp(12).toFloat(), dp(12).toFloat(), fill)
            canvas.drawRoundRect(k.rect, dp(12).toFloat(), dp(12).toFloat(), stroke)
            keyTextPaint.textSize = if (k.action <= 9) 52f else 46f
            canvas.drawText(k.label, k.rect.centerX(), k.rect.centerY() - (keyTextPaint.ascent() + keyTextPaint.descent()) / 2f, keyTextPaint)
        }
    }

    private fun drawInput(canvas: Canvas, input: String, locked: Boolean, y: Float, tap: Int) {
        if (locked) {
            canvas.drawText("本题已锁定", width / 2f, y, lockPaint)
        } else {
            inputPaint.color = if (flash == tap) 0xFF3ED98A.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawText(if (input.isEmpty()) "_" else input, width / 2f, y, inputPaint)
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height / 2f), p1Tint)
        canvas.drawRect(RectF(0f, height / 2f, width.toFloat(), height.toFloat()), p2Tint)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, Paint().apply {
            color = 0x44FFFFFF.toInt()
            strokeWidth = 2f
        })

        // 题目：固定显示，直到有人提交正确答案才换新题
        questionPaint.color = if (flash == 0 || flash == 1) 0xFF3ED98A.toInt() else 0xFFE8EAF0.toInt()
        canvas.drawText(a.toString() + " " + op + " " + b + " = ?", width / 2f, height / 2f + dp(10).toFloat(), questionPaint)
        canvas.drawText("算好后用下方键盘输入并提交 · 先到 " + WIN_SCORE + " 分胜", width / 2f, height / 2f + dp(58).toFloat(), hintPaint)

        // 玩家 1（上半屏，对向视角：整半屏绕中心旋转 180°，坐在上方的玩家 1 可正常阅读自己的数字）
        canvas.save()
        canvas.translate(width / 2f, height / 4f)
        canvas.rotate(180f)
        canvas.translate(-width / 2f, -height / 4f)
        scorePaint.color = 0xFF6C8BFF.toInt()
        canvas.drawText("玩家 1：" + s1, width / 2f, dp(56).toFloat(), scorePaint)
        drawPad(canvas, keys1, key1Paint, key1Stroke)
        drawInput(canvas, input1, locked1, height / 2f - dp(96).toFloat(), 0)
        canvas.restore()

        // 玩家 2（下半屏）
        scorePaint.color = 0xFFFFA04A.toInt()
        canvas.drawText("玩家 2：" + s2, width / 2f, height - dp(52).toFloat(), scorePaint)
        drawPad(canvas, keys2, key2Paint, key2Stroke)
        drawInput(canvas, input2, locked2, height / 2f + dp(96).toFloat(), 1)
    }

    override fun reset() {
        s1 = 0
        s2 = 0
        over = false
        flash = -1
        flashTimer = 0f
        newQuestion()
        setScore(0)
        start()
    }
}
