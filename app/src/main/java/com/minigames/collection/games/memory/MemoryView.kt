package com.minigames.collection.games.memory

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.max
import kotlin.math.min

/** 记忆翻牌：4x4 配对，翻开两张相同则保留，全部配对完成获胜。 */
class MemoryView(context: Context) : GameView(context) {

    companion object {
        private val EMOJIS = listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼")
        private val SIZE = 4
    }

    private val cards = IntArray(SIZE * SIZE)
    private val matched = BooleanArray(SIZE * SIZE)
    private var flipA = -1
    private var flipB = -1
    private var lockTimer = 0f
    private var moves = 0
    private var pairs = 0
    private var finished = false

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val faceDownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val faceUpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val matchedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3E9E63.toInt() }
    private val matchedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0x33FFFFFF.toInt()
    }
    private val movesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w, h) * 0.9f
        cell = side / SIZE
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f
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
            moves++
            playSound(SoundManager.MOVE)
            if (cards[flipA] == cards[flipB]) {
                matched[flipA] = true
                matched[flipB] = true
                pairs++
                setScore(pairs * 10)
                playSound(SoundManager.EAT)
                flipA = -1
                flipB = -1
                if (pairs == SIZE * SIZE / 2) {
                    finished = true
                    val finalScore = max(0, 800 - moves * 15)
                    setScore(finalScore)
                    playSound(SoundManager.WIN)
                    gameOver(GameResult(finalScore, true, "全部配对成功，共用了 " + moves + " 步"))
                }
            } else {
                lockTimer = 0.7f
            }
        }
    }

    override fun update(dt: Float) {
        if (lockTimer > 0f) {
            lockTimer -= dt
            if (lockTimer <= 0f) {
                flipA = -1
                flipB = -1
            }
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawText("步数 " + moves + " · 配对 " + pairs + "/" + SIZE * SIZE / 2, width / 2f, boardTop - dp(12).toFloat(), movesPaint)
        for (i in 0 until SIZE * SIZE) {
            val r = i / SIZE
            val c = i % SIZE
            val rect = RectF(
                boardLeft + c * cell + 2f, boardTop + r * cell + 2f,
                boardLeft + (c + 1) * cell - 2f, boardTop + (r + 1) * cell - 2f)
            val radius = cell * 0.12f
            if (matched[i]) {
                canvas.drawRoundRect(rect, radius, radius, matchedPaint)
                canvas.drawRoundRect(rect, radius, radius, matchedRingPaint)
                drawEmoji(canvas, EMOJIS[cards[i]], rect.centerX(), rect.centerY() - emojiPaint.ascent() * 0.35f, cell * 0.5f, emojiPaint)
            } else if (i == flipA || i == flipB) {
                canvas.drawRoundRect(rect, radius, radius, faceUpPaint)
                drawEmoji(canvas, EMOJIS[cards[i]], rect.centerX(), rect.centerY() - emojiPaint.ascent() * 0.35f, cell * 0.5f, emojiPaint)
            } else {
                canvas.drawRoundRect(rect, radius, radius, faceDownPaint)
                emojiPaint.color = 0xFFFFFFFF.toInt()
                emojiPaint.textSize = cell * 0.42f
                canvas.drawText("?", rect.centerX(), rect.centerY() - emojiPaint.ascent() * 0.4f, emojiPaint)
                markPaint.color = 0x14FFFFFF.toInt()
                canvas.drawText("❖", rect.centerX(), rect.centerY() + cell * 0.3f, markPaint)
            }
        }
    }

    private fun drawEmoji(canvas: Canvas, emoji: String, x: Float, y: Float, size: Float, paint: Paint) {
        paint.color = 0xFF1C2333.toInt()
        paint.textSize = size
        canvas.drawText(emoji, x, y, paint)
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
        moves = 0
        pairs = 0
        finished = false
        setScore(0)
        start()
    }
}
