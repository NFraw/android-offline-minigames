package com.minigames.collection.games.blackjack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 21 点：和庄家比点数，超过 21 爆掉，接近 21 者胜。 */
class BlackjackView(context: Context) : GameView(context) {

    companion object {
        private const val DECKS = 6
    }

    private val deck = ArrayList<Int>()
    private val player = ArrayList<Int>()
    private val dealer = ArrayList<Int>()
    private var playerTurn = true
    private var over = false
    private var result = 0
    private var w = 0f
    private var h = 0f

    private val hitBtn = RectF()
    private val standBtn = RectF()
    private val bgPaint = Paint().apply { color = 0xFF1B2A1B.toInt() }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 41f
        color = 0xFFE8EAF0.toInt()
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E9E5B.toInt() }
    private val btnRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }

    override fun onLayout(w: Int, h: Int) {
        this.w = w.toFloat()
        this.h = h.toFloat()
        val bw = w * 0.3f
        val bh = dp(56).toFloat()
        hitBtn.set(w * 0.18f, h * 0.86f, w * 0.18f + bw, h * 0.86f + bh)
        standBtn.set(w * 0.52f, h * 0.86f, w * 0.52f + bw, h * 0.86f + bh)
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        if (playerTurn && !over) {
            if (hitBtn.contains(event.x, event.y)) {
                player.add(draw())
                playSound(SoundManager.MOVE)
                if (sum(player) > 21) finish(0, "爆牌了！点数 " + sum(player))
                invalidate()
            } else if (standBtn.contains(event.x, event.y)) {
                playerTurn = false
                dealerTurn()
                invalidate()
            }
        }
    }

    private fun dealerTurn() {
        while (sum(dealer) < 17) {
            dealer.add(draw())
        }
        val ps = sum(player)
        val ds = sum(dealer)
        if (ds > 21) {
            finish(1, "庄家爆牌，你赢了！" + ps + " : " + ds)
        } else if (ps > ds) {
            finish(1, "你赢了！" + ps + " : " + ds)
        } else if (ps == ds) {
            finish(0, "平局！" + ps + " : " + ds)
        } else {
            finish(0, "庄家赢了。" + ps + " : " + ds)
        }
    }

    private fun finish(won: Int, msg: String) {
        over = true
        result = won
        playSound(if (won == 1) SoundManager.WIN else SoundManager.LOSE)
        gameOver(GameResult(if (won == 1) 100 else 0, won == 1, msg))
    }

    private fun draw(): Int {
        if (deck.isEmpty()) {
            for (i in 0 until 52 * DECKS) deck.add(i % 13 + 1)
            deck.shuffle()
        }
        return deck.removeAt(deck.size - 1)
    }

    private fun sum(cards: List<Int>): Int {
        var s = 0
        var aces = 0
        for (c in cards) {
            if (c == 1) {
                aces++
                s += 11
            } else {
                s += min(c, 10)
            }
        }
        while (s > 21 && aces > 0) {
            s -= 10
            aces--
        }
        return s
    }

    private fun cardName(c: Int): String = when (c) {
        1 -> "A"
        11 -> "J"
        12 -> "Q"
        13 -> "K"
        else -> c.toString()
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        textPaint.color = 0xFF8A93A6.toInt()
        textPaint.textSize = 37f
        canvas.drawText("庄家：" + sum(dealer), w / 2f, dp(44).toFloat(), textPaint)
        drawCards(canvas, dealer, dp(70).toFloat())
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("你：" + sum(player), w / 2f, h * 0.46f, textPaint)
        drawCards(canvas, player, h * 0.52f)
        if (playerTurn && !over) {
            canvas.drawRoundRect(hitBtn, dp(12).toFloat(), dp(12).toFloat(), btnPaint)
            textPaint.textSize = 44f
            textPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawText("要牌", hitBtn.centerX(), hitBtn.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
            canvas.drawRoundRect(standBtn, dp(12).toFloat(), dp(12).toFloat(), btnRedPaint)
            canvas.drawText("停牌", standBtn.centerX(), standBtn.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }
    }

    private fun drawCards(canvas: Canvas, cards: List<Int>, y: Float) {
        val cw = w * 0.1f
        val ch = h * 0.16f
        for (i in cards.indices) {
            val x = w / 2f - (cards.size - 1) * cw / 2f + i * cw
            canvas.drawRoundRect(RectF(x, y, x + cw, y + ch), dp(8).toFloat(), dp(8).toFloat(), cardPaint)
            textPaint.color = 0xFF1C2333.toInt()
            textPaint.textSize = 36f
            canvas.drawText(cardName(cards[i]), x + cw / 2f, y + ch / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }
    }

    override fun reset() {
        deck.clear()
        player.clear()
        dealer.clear()
        player.add(draw())
        player.add(draw())
        dealer.add(draw())
        playerTurn = true
        over = false
        setScore(0)
        start()
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}
