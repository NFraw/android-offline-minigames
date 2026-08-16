package com.minigames.collection.games.sheep

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 羊了个羊（简化）：点击收集卡片，槽位凑齐 3 张相同即可消除，7 槽满则失败。 */
class SheepView(context: Context) : GameView(context) {

    companion object {
        private const val TYPES = 6
        private const val SLOT = 7
        private const val ROWS = 4
        private const val COLS = 6
    }

    private val deck = ArrayList<Int>()
    private val slots = ArrayList<Int>()
    private var score = 0
    private var over = false
    private var cardW = 0f
    private var cardH = 0f
    private var boardTop = 0f
    private val cardRects = ArrayList<RectF>()

    private val colors = intArrayOf(
        0xFFFF6B6B.toInt(), 0xFFF5A54A.toInt(), 0xFFF5D547.toInt(),
        0xFF3ED98A.toInt(), 0xFF37C2F0.toInt(), 0xFFB06CF5.toInt()
    )
    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slotBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        cardW = w / (COLS + 1.2f)
        cardH = cardW * 0.7f
        boardTop = h * 0.16f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        if (deck.isEmpty()) return
        val idx = ((event.x - (width - cardW * COLS) / 2f) / cardW).toInt()
        val r = ((event.y - boardTop) / cardH).toInt()
        if (r in 0 until ROWS && idx in 0 until COLS && r * COLS + idx < deck.size) {
            val card = deck.removeAt(0)
            slots.add(card)
            playSound(SoundManager.CLICK)
            // 三连消除
            val same = slots.count { it == card }
            if (same >= 3) {
                val it = slots.iterator()
                var removed = 0
                while (it.hasNext() && removed < 3) {
                    if (it.next() == card) {
                        it.remove()
                        removed++
                    }
                }
                score += 30
                setScore(score)
                playSound(SoundManager.EAT)
            }
            if (slots.size >= SLOT) {
                over = true
                playSound(SoundManager.LOSE)
                gameOver(GameResult(score, false, "槽位满了！消除 " + (score / 30) + " 组"))
            } else if (deck.isEmpty() && slots.isEmpty()) {
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(score, true, "全部消除！得分 " + score))
            }
        }
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 卡片堆（自上而下移除）
        val left = (width - cardW * COLS) / 2f
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val idx = r * COLS + c
            if (idx >= deck.size) continue
            val rect = RectF(left + c * cardW, boardTop + r * cardH, left + (c + 1) * cardW, boardTop + (r + 1) * cardH)
            cardPaint.color = colors[deck[idx] % TYPES]
            canvas.drawRoundRect(rect, dp(8).toFloat(), dp(8).toFloat(), cardPaint)
        }
        // 槽位
        val slotW = width / (SLOT + 1.5f)
        val slotY = height * 0.72f
        for (i in 0 until SLOT) {
            val rect = RectF(width * 0.12f + i * slotW, slotY, width * 0.12f + (i + 1) * slotW, slotY + cardH)
            canvas.drawRoundRect(rect, dp(8).toFloat(), dp(8).toFloat(), slotBgPaint)
            if (i < slots.size) {
                cardPaint.color = colors[slots[i] % TYPES]
                canvas.drawRoundRect(RectF(rect.left + 2f, rect.top + 2f, rect.right - 2f, rect.bottom - 2f), dp(6).toFloat(), dp(6).toFloat(), cardPaint)
            }
        }
        canvas.drawText("剩余 " + deck.size + " 张 · 点击卡片收集，3 张相同消除 · 槽位 " + slots.size + "/" + SLOT, width / 2f, height * 0.7f - dp(14).toFloat(), textPaint)
    }

    override fun reset() {
        deck.clear()
        slots.clear()
        val cards = ArrayList<Int>()
        // 保证 3 的倍数
        for (t in 0 until TYPES) {
            repeat(6) { cards.add(t) }
        }
        cards.shuffle()
        deck.addAll(cards)
        score = 0
        over = false
        setScore(0)
        start()
    }
}
