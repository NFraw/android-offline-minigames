package com.minigames.collection.games.memory

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.Toast
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/**
 * 记忆对决：双人轮流翻牌配对，配对成功继续，配错换人。
 * 共 4 关（4×4 → 8×8 → 10×10 → 16×16），分数累计，全部通关后分高者胜。
 * 卡片图案 = emoji × 颜色 组合（16×9 互质，共 144 种不同视觉），保证 16×16 的 128 对也互不相同。
 */
class MemoryDuelView(context: Context) : GameView(context) {

    companion object {
        private val EMOJIS = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔")
        private val COLORS = intArrayOf(
            0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF3949AB.toInt(),
            0xFF1E88E5.toInt(), 0xFF00897B.toInt(), 0xFF43A047.toInt(), 0xFFF4511E.toInt(),
            0xFFFDD835.toInt())
        private val LEVELS = intArrayOf(4, 8, 10, 16)
    }

    private var cards = IntArray(16)
    private var matched = BooleanArray(16)
    private var size = 4
    private var flipA = -1
    private var flipB = -1
    private var lockTimer = 0f
    private var turn = 0
    private var s1 = 0
    private var s2 = 0
    private var score = 0
    private var level = 0
    private var finished = false

    private var lastW = 0
    private var lastH = 0
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val faceDownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val faceUpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFE8EAF0.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFD54F.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        lastW = w
        lastH = h
        recomputeLayout()
        reset()
    }

    private fun recomputeLayout() {
        val statusH = dp(150).toFloat()
        val availH = lastH - statusH
        val side = min(lastW.toFloat(), availH) * 0.94f
        cell = side / size
        boardLeft = (lastW - side) / 2f
        boardTop = statusH + (availH - side) / 2f
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN) return
        if (lockTimer > 0f || finished) return
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (r !in 0 until size || c !in 0 until size) return
        val idx = r * size + c
        if (matched[idx] || idx == flipA || idx == flipB) return
        if (flipA < 0) {
            flipA = idx
            playSound(SoundManager.CLICK)
        } else {
            flipB = idx
            if (cards[flipA] == cards[flipB]) {
                matched[flipA] = true
                matched[flipB] = true
                if (turn == 0) s1++ else s2++
                score += 10
                setScore(score)
                playSound(SoundManager.EAT)
                flipA = -1
                flipB = -1
                if (matched.all { it }) levelCleared()
            } else {
                lockTimer = 0.7f
            }
        }
        invalidate()
    }

    private fun levelCleared() {
        if (level == LEVELS.lastIndex) {
            finished = true
            playSound(SoundManager.WIN)
            val p1Win = s1 > s2
            val verdict = if (s1 == s2) "（平局）" else if (p1Win) "，玩家 1 获胜！" else "，玩家 2 获胜！"
            gameOver(GameResult(score, p1Win, "全部通关！玩家 1 " + s1 + " : " + s2 + " 玩家 2" + verdict))
        } else {
            level++
            recomputeLayout()
            deal()
            playSound(SoundManager.WIN)
            Toast.makeText(context, "第 " + (level + 1) + "/" + LEVELS.size + " 关：" + size + "×" + size, Toast.LENGTH_SHORT).show()
            start()
        }
    }

    override fun update(dt: Float) {
        if (lockTimer > 0f) {
            lockTimer -= dt
            if (lockTimer <= 0f) {
                flipA = -1
                flipB = -1
                turn = 1 - turn
            }
            invalidate()
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        textPaint.textSize = sp(17f)
        canvas.drawText(
            "第 " + (level + 1) + "/" + LEVELS.size + " 关   玩家 1：" + s1 + "   玩家 2：" + s2,
            width / 2f, boardTop - dp(58).toFloat(), textPaint)
        hintPaint.textSize = sp(15f)
        canvas.drawText("轮到 玩家 " + (turn + 1) + " 翻牌", width / 2f, boardTop - dp(22).toFloat(), hintPaint)
        for (i in 0 until size * size) {
            val r = i / size
            val c = i % size
            val rect = RectF(
                boardLeft + c * cell + 1.5f, boardTop + r * cell + 1.5f,
                boardLeft + (c + 1) * cell - 1.5f, boardTop + (r + 1) * cell - 1.5f)
            if (matched[i] || i == flipA || i == flipB) {
                canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, faceUpPaint)
                drawCardFace(canvas, i, rect.centerX(), rect.centerY())
            } else {
                canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, faceDownPaint)
                emojiPaint.color = 0xFFFFFFFF.toInt()
                emojiPaint.textSize = cell * 0.42f
                canvas.drawText("?", rect.centerX(), rect.centerY() - emojiPaint.ascent() * 0.4f, emojiPaint)
            }
        }
    }

    /** 卡面 = 颜色圆 + emoji，两者组合全局唯一（16 与 9 互质，144 种）。 */
    private fun drawCardFace(canvas: Canvas, i: Int, x: Float, y: Float) {
        val p = cards[i]
        emojiPaint.color = COLORS[p % COLORS.size]
        canvas.drawCircle(x, y, cell * 0.3f, emojiPaint)
        emojiPaint.color = 0xFF1C2333.toInt()
        emojiPaint.textSize = cell * 0.42f
        canvas.drawText(EMOJIS[p % EMOJIS.size], x, y - emojiPaint.ascent() * 0.35f, emojiPaint)
    }

    /** 按当前 size 重新发牌，保留局外状态（level/score/双人比分）。 */
    private fun deal() {
        cards = IntArray(size * size)
        matched = BooleanArray(size * size)
        val deck = ArrayList<Int>()
        for (p in 0 until size * size / 2) {
            deck.add(p)
            deck.add(p)
        }
        deck.shuffle()
        for (i in cards.indices) cards[i] = deck[i]
        flipA = -1
        flipB = -1
        lockTimer = 0f
        turn = 0
        finished = false
    }

    override fun reset() {
        level = 0
        size = LEVELS[0]
        s1 = 0
        s2 = 0
        score = 0
        recomputeLayout()
        deal()
        setScore(0)
        start()
    }
}
