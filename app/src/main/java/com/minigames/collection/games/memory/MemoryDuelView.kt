package com.minigames.collection.games.memory

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 记忆对决：双人轮流翻牌配对，配对成功继续，全部配对后分数高者胜。 */
class MemoryDuelView(context: Context) : GameView(context) {

    companion object {
        private val EMOJIS = listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼")
        private val SIZE = 4
    }

    private val cards = IntArray(SIZE * SIZE)
    private val matched = BooleanArray(SIZE * SIZE)
    private var flipA = -1
    private var flipB = -1
    private var lockTimer = 0f
    private var turn = 0
    private var s1 = 0
    private var s2 = 0
    private var finished = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val faceDownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val faceUpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 41f
        color = 0xFFE8EAF0.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 36f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w, h) * 0.72f
        cell = side / SIZE
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f + cell * 0.6f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN) return
        if (lockTimer > 0f || finished) return
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (r !in 0 until SIZE || c !in 0 until SIZE) return
        val idx = r * SIZE + c
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
                playSound(SoundManager.EAT)
                flipA = -1
                flipB = -1
                if (matched.all { it }) {
                    finished = true
                    val p1Win = s1 > s2
                    playSound(SoundManager.WIN)
                    gameOver(GameResult(0, p1Win, "玩家 1 " + s1 + " : " + s2 + " 玩家 2" + (if (s1 == s2) "（平局）" else if (p1Win) "，玩家 1 获胜！" else "，玩家 2 获胜！")))
                }
            } else {
                lockTimer = 0.7f
            }
        }
        invalidate()
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
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("玩家 1：" + s1 + "  ·  玩家 2：" + s2, width / 2f, boardTop - cell * 0.7f, textPaint)
        hintPaint.color = 0xFFFFD54F.toInt()
        canvas.drawText("轮到 玩家 " + (turn + 1) + " 翻牌", width / 2f, boardTop - cell * 0.2f, hintPaint)
        for (i in 0 until SIZE * SIZE) {
            val r = i / SIZE
            val c = i % SIZE
            val rect = RectF(
                boardLeft + c * cell + 2f, boardTop + r * cell + 2f,
                boardLeft + (c + 1) * cell - 2f, boardTop + (r + 1) * cell - 2f)
            if (matched[i]) {
                canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, faceUpPaint)
                drawEmoji(canvas, EMOJIS[cards[i]], rect.centerX(), rect.centerY(), cell * 0.5f)
            } else if (i == flipA || i == flipB) {
                canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, faceUpPaint)
                drawEmoji(canvas, EMOJIS[cards[i]], rect.centerX(), rect.centerY(), cell * 0.5f)
            } else {
                canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, faceDownPaint)
                emojiPaint.color = 0xFFFFFFFF.toInt()
                emojiPaint.textSize = cell * 0.42f
                canvas.drawText("?", rect.centerX(), rect.centerY() - emojiPaint.ascent() * 0.4f, emojiPaint)
            }
        }
    }

    private fun drawEmoji(canvas: Canvas, emoji: String, x: Float, y: Float, size: Float) {
        emojiPaint.color = 0xFF1C2333.toInt()
        emojiPaint.textSize = size
        canvas.drawText(emoji, x, y - emojiPaint.ascent() * 0.35f, emojiPaint)
    }

    override fun reset() {
        val deck = ArrayList<Int>()
        for (p in 0 until SIZE * SIZE / 2) {
            deck.add(p)
            deck.add(p)
        }
        deck.shuffle()
        for (i in cards.indices) cards[i] = deck[i]
        for (i in matched.indices) matched[i] = false
        flipA = -1
        flipB = -1
        lockTimer = 0f
        turn = 0
        s1 = 0
        s2 = 0
        finished = false
        setScore(0)
        start()
    }
}
