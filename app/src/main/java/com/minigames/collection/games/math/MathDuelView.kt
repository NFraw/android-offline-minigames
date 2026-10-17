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
 * 抢答算术（双人）：开局先选难度模式，题目按模式限定范围生成，
 * 双视角显示（双方都能正常阅读题目）。题目展示后保持不变，
 * 直到有人提交正确答案才换新题。每位玩家有独立数字键盘（0-9 + 删除 + 提交）；
 * 答错则该玩家本题锁定；输入位数按模式限制，先到 5 分获胜。
 */
class MathDuelView(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 5
        private const val MODE_10 = 0
        private const val MODE_20 = 1
        private const val MODE_50 = 2
        private const val MODE_100 = 3
        private const val MODE_MIX100 = 4
        private const val MODE_MIX1000 = 5
        private val MODE_NAMES = arrayOf(
            "10 以内加减", "20 以内加减", "50 以内加减",
            "100 以内加减", "100 以内四则", "1000 以内四则")
        private val MODE_DESC = arrayOf(
            "结果 ≤ 10 的加减法", "结果 ≤ 20 的加减法", "结果 ≤ 50 的加减法",
            "结果 ≤ 100 的加减法", "结果 ≤ 100 的加减乘除", "结果 ≤ 1000 的加减乘除")

        // ---- 按键动作码 ----
        // 必须用显式动作码，不能拿"键盘下标"当动作（历史 bug）：
        // 键盘顺序是 1..9 / ⌫ / 0 / ✓，若直接用下标当动作，⌫ 的下标 9 会落进
        // 「≤9 视为数字」的分支，于是按下删除键会把 "⌫" 这个字符追加进输入框；
        // 同时 0 的下标 10 被当成删除键，按 0 反而退格。现在显式区分，杜绝下标错位。
        private const val ACT_DIGIT = 0
        private const val ACT_DELETE = 1
        private const val ACT_SUBMIT = 2

        /** 键盘标签（3 列 × 4 行，与 [KEY_ACTIONS] 一一对应）。 */
        private val KEY_LABELS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        /** 每个键的动作码，顺序与 [KEY_LABELS] 严格对应。 */
        private val KEY_ACTIONS = listOf(
            ACT_DIGIT, ACT_DIGIT, ACT_DIGIT,
            ACT_DIGIT, ACT_DIGIT, ACT_DIGIT,
            ACT_DIGIT, ACT_DIGIT, ACT_DIGIT,
            ACT_DELETE, ACT_DIGIT, ACT_SUBMIT)
    }

    /**
     * 一个按键。
     * @param label 键面文字（数字键为 "0".."9"，另有 "⌫" / "✓"）
     * @param action 动作码（[ACT_DIGIT] / [ACT_DELETE] / [ACT_SUBMIT]），与键面文字解耦，
     *        避免"按下标判断动作"再次出现错位。
     */
    private data class Key(val rect: RectF, val label: String, val action: Int)

    private var phase = 0 // 0=选模式 1=对战中
    private var mode = MODE_10
    private val modeBtns = ArrayList<Pair<RectF, Int>>()
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
    // 难度选择界面
    private val maskPaint = Paint().apply { color = 0xDD141A2E.toInt() }
    private val modeBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val modeBtnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A6CF7.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val modeNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 46f
        color = 0xFFE8EAF0.toInt()
    }
    private val modeDescPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val halfH = h / 2f
        val padTop = dp(140).toFloat()
        val padBottom = dp(150).toFloat()
        val keyW = w * 0.9f / 3f
        val keyH = (halfH - padTop - padBottom) / 4f
        fun build(list: ArrayList<Key>, top: Float) {
            list.clear()
            for (i in KEY_LABELS.indices) {
                val r = i / 3
                val c = i % 3
                val x = (w - keyW * 3) / 2f + c * keyW
                list.add(Key(
                    RectF(x + 4f, top + r * keyH + 4f, x + keyW - 4f, top + (r + 1) * keyH - 4f),
                    KEY_LABELS[i], KEY_ACTIONS[i]))
            }
        }
        build(keys1, padTop)
        build(keys2, halfH + padTop)
        // 难度模式选择按钮：2 列 × 3 行
        modeBtns.clear()
        val cols = 2
        val bw = w * 0.36f
        val bh = dp(64).toFloat()
        val gapX = dp(20).toFloat()
        val gapY = dp(18).toFloat()
        val totalW = cols * bw + (cols - 1) * gapX
        val x0 = (w - totalW) / 2f
        val y0 = h * 0.34f
        for (i in MODE_NAMES.indices) {
            val c = i % cols
            val r = i / cols
            modeBtns.add(Pair(
                RectF(x0 + c * (bw + gapX), y0 + r * (bh + gapY), x0 + c * (bw + gapX) + bw, y0 + r * (bh + gapY) + bh),
                i))
        }
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (paused || over) return
        // 必须同时接受 ACTION_POINTER_DOWN：本作的核心玩法就是"同时抢答"，
        // 只要有一根手指还搭在屏幕上，第二人的触摸就是 POINTER_DOWN，
        // 旧实现只认 ACTION_DOWN，第二人的按键会被整段丢弃（一人按住屏幕即可让对方键盘失效）。
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN) return
        val idx = event.actionIndex
        val ex = event.getX(idx)
        val ey = event.getY(idx)
        // 先选难度模式
        if (phase == 0) {
            for ((rect, m) in modeBtns) {
                if (rect.contains(ex, ey)) {
                    mode = m
                    playSound(SoundManager.CLICK)
                    phase = 1
                    newQuestion()
                    invalidate()
                    return
                }
            }
            return
        }
        val tap = if (ey < height / 2f) 0 else 1
        val keys = if (tap == 0) keys1 else keys2
        // 玩家 1 的上半屏整体旋转 180°，触摸点需按同样的旋转反变换回去做命中测试
        val px = if (tap == 0) width - ex else ex
        val py = if (tap == 0) height / 2f - ey else ey
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
        when (k.action) {
            ACT_DIGIT -> {
                // 键面文字就是数字本身，直接追加（动作码已与下标解耦，不会再串键）
                val cur = if (tap == 0) input1 else input2
                if (cur.length < maxInput()) {
                    if (tap == 0) input1 += k.label else input2 += k.label
                    playSound(SoundManager.CLICK)
                }
            }
            ACT_DELETE -> {
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
            // 两人都答错时必须换题：换题原本只发生在"答对"分支，两边都锁定后
            // 题目永远不会更新、两个键盘也永远拒绝输入——整局死锁，只能退出重开。
            if (locked1 && locked2) newQuestion()
        }
        invalidate()
    }

    override fun update(dt: Float) {
        if (over) return
        if (flashTimer > 0f) {
            flashTimer -= dt
            // 计时结束要清掉高亮，否则"本题有人答对"的绿色会让题目与输入框从第一个答对之后
            // 一直保持高亮，后续的对错反馈完全看不出来。
            if (flashTimer <= 0f) flash = -1
        }
        invalidate()
    }

    /** 当前模式允许的最大输入位数（保证答案都在题目范围内，避免乱输）。 */
    private fun maxInput(): Int = when (mode) {
        MODE_10, MODE_20, MODE_50 -> 2
        MODE_100, MODE_MIX100 -> 3
        else -> 4 // MODE_MIX1000
    }

    /** 按模式生成题目：结果严格控制在模式的范围内。 */
    private fun newQuestion() {
        input1 = ""
        input2 = ""
        locked1 = false
        locked2 = false
        when (mode) {
            MODE_10, MODE_20, MODE_50, MODE_100 -> {
                val range = when (mode) {
                    MODE_10 -> 10
                    MODE_20 -> 20
                    MODE_50 -> 50
                    else -> 100
                }
                if (Math.random() < 0.5) {
                    op = '+'
                    a = 1 + (Math.random() * (range - 1)).toInt()
                    b = 1 + (Math.random() * (range - a)).toInt()
                    answer = a + b
                } else {
                    op = '-'
                    a = 2 + (Math.random() * (range - 1)).toInt()
                    b = 1 + (Math.random() * (a - 1)).toInt()
                    answer = a - b
                }
            }
            MODE_MIX100, MODE_MIX1000 -> {
                val range = if (mode == MODE_MIX100) 100 else 1000
                op = charArrayOf('+', '-', '×', '÷')[(Math.random() * 4).toInt()]
                when (op) {
                    '+' -> {
                        a = 1 + (Math.random() * (range - 1)).toInt()
                        b = 1 + (Math.random() * (range - a)).toInt()
                        answer = a + b
                    }
                    '-' -> {
                        a = 2 + (Math.random() * (range - 1)).toInt()
                        b = 1 + (Math.random() * (a - 1)).toInt()
                        answer = a - b
                    }
                    '×' -> {
                        val maxF = if (mode == MODE_MIX100) 10 else 30
                        do {
                            a = 2 + (Math.random() * (maxF - 1)).toInt()
                            b = 2 + (Math.random() * (maxF - 1)).toInt()
                        } while (a * b > range)
                        answer = a * b
                    }
                    else -> { // ÷：a = 商 × 除数，保证整除且 ≤ range
                        val maxQ = range / 2
                        val q = 2 + (Math.random() * (maxQ - 1)).toInt()
                        val maxB = (range / q).coerceIn(2, 9)
                        b = 2 + (Math.random() * (maxB - 1)).toInt()
                        a = q * b
                        answer = q
                    }
                }
            }
        }
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
        // 难度模式选择
        if (phase == 0) {
            canvas.drawRect(gameRect, maskPaint)
            questionPaint.textSize = 62f
            questionPaint.color = 0xFFE8EAF0.toInt()
            canvas.drawText("🧮 抢答算术", width / 2f, height * 0.20f, questionPaint)
            modeDescPaint.textSize = 34f
            canvas.drawText("先选难度模式，再开始对战", width / 2f, height * 0.20f + dp(48).toFloat(), modeDescPaint)
            for ((rect, m) in modeBtns) {
                canvas.drawRoundRect(rect, dp(16).toFloat(), dp(16).toFloat(), modeBtnPaint)
                canvas.drawRoundRect(rect, dp(16).toFloat(), dp(16).toFloat(), modeBtnStroke)
                modeNamePaint.textSize = 42f
                canvas.drawText(MODE_NAMES[m], rect.centerX(), rect.centerY() + dp(2).toFloat(), modeNamePaint)
                modeDescPaint.textSize = 24f
                canvas.drawText(MODE_DESC[m], rect.centerX(), rect.bottom - dp(10).toFloat(), modeDescPaint)
            }
            questionPaint.textSize = 88f
            return
        }

        canvas.drawRect(RectF(0f, 0f, width.toFloat(), height / 2f), p1Tint)
        canvas.drawRect(RectF(0f, height / 2f, width.toFloat(), height.toFloat()), p2Tint)
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, Paint().apply {
            color = 0x44FFFFFF.toInt()
            strokeWidth = 2f
        })
        val qColor = if (flash == 0 || flash == 1) 0xFF3ED98A.toInt() else 0xFFE8EAF0.toInt()
        val questionStr = a.toString() + " " + op + " " + b + " = ?"

        // 玩家 1（上半屏，对向视角：整半屏绕中心旋转 180°，坐在上方的玩家 1 可正常阅读自己的数字）
        canvas.save()
        canvas.translate(width / 2f, height / 4f)
        canvas.rotate(180f)
        canvas.translate(-width / 2f, -height / 4f)
        scorePaint.color = 0xFF6C8BFF.toInt()
        canvas.drawText("玩家 1：" + s1, width / 2f, dp(56).toFloat(), scorePaint)
        // 题目双视角：玩家 1 的题目在其半屏内正常显示（随半屏旋转后正对玩家 1）
        questionPaint.textSize = 88f
        questionPaint.color = qColor
        canvas.drawText(questionStr, width / 2f, dp(104).toFloat(), questionPaint)
        hintPaint.textSize = 30f
        canvas.drawText("算好后用下方键盘输入并提交 · 先到 " + WIN_SCORE + " 分胜", width / 2f, dp(130).toFloat(), hintPaint)
        drawPad(canvas, keys1, key1Paint, key1Stroke)
        drawInput(canvas, input1, locked1, height / 2f - dp(96).toFloat(), 0)
        canvas.restore()

        // 玩家 2（下半屏）
        scorePaint.color = 0xFFFFA04A.toInt()
        canvas.drawText("玩家 2：" + s2, width / 2f, height - dp(52).toFloat(), scorePaint)
        // 题目双视角：玩家 2 的题目正立显示在其半屏内
        questionPaint.textSize = 88f
        questionPaint.color = qColor
        canvas.drawText(questionStr, width / 2f, height / 2f + dp(64).toFloat(), questionPaint)
        hintPaint.textSize = 30f
        canvas.drawText("算好后用下方键盘输入并提交 · 先到 " + WIN_SCORE + " 分胜", width / 2f, height / 2f + dp(112).toFloat(), hintPaint)
        drawPad(canvas, keys2, key2Paint, key2Stroke)
        drawInput(canvas, input2, locked2, height / 2f + dp(96).toFloat(), 1)
    }

    override fun reset() {
        phase = 0
        s1 = 0
        s2 = 0
        over = false
        flash = -1
        flashTimer = 0f
        setScore(0)
        start()
    }
}
