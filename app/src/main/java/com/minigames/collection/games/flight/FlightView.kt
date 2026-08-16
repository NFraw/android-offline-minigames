package com.minigames.collection.games.flight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 飞行棋（2~4 人）：掷骰子沿环形路径前进，到达终点获胜，踩到同格触发跳格。 */
class FlightView(context: Context) : GameView(context) {

    companion object {
        private const val TRACK = 28
        private const val FINISH = TRACK + 4
    }

    private val pos = IntArray(4)
    private val finished = BooleanArray(4)
    private var turn = 0
    private var dice = 1
    private var rolling = false
    private var rollTimer = 0f
    private var players = 4
    private var over = false
    private val diceBtn = RectF()
    private val colors = intArrayOf(0xFFE8503A.toInt(), 0xFF37C2F0.toInt(), 0xFF3ED98A.toInt(), 0xFFF5A54A.toInt())
    private var trackPts = ArrayList<Pair<Float, Float>>()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val dicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
        color = 0xFFE8EAF0.toInt()
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 18f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        // 环形路径
        trackPts.clear()
        val cx = w / 2f
        val cy = h * 0.48f
        val rx = w * 0.34f
        val ry = h * 0.28f
        for (i in 0 until TRACK) {
            val ang = Math.PI * 2 * i / TRACK - Math.PI / 2
            trackPts.add(Pair(cx + Math.cos(ang).toFloat() * rx, cy + Math.sin(ang).toFloat() * ry))
        }
        val dw = w * 0.3f
        val dh = dp(52).toFloat()
        diceBtn.set(w / 2f - dw / 2f, h * 0.85f, w / 2f + dw / 2f, h * 0.85f + dh)
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused || rolling) return
        if (diceBtn.contains(event.x, event.y)) {
            rolling = true
            rollTimer = 0f
            dice = (1..6).random()
            playSound(SoundManager.MOVE)
            invalidate()
        }
    }

    override fun update(dt: Float) {
        if (over || !rolling) return
        rollTimer += dt
        if (rollTimer >= 0.4f) {
            rolling = false
            var p = pos[turn]
            // 起点外起飞
            if (p == 0 && dice != 6) {
                // 未起飞
            } else {
                if (p == 0) p = 1
                p += dice
                // 踩到同格：前进 4 格（简化飞行）
                for (other in 0 until 4) {
                    if (other != turn && !finished[other] && pos[other] == p) {
                        p += 4
                        playSound(SoundManager.EAT)
                        break
                    }
                }
                if (p > FINISH) {
                    // 超过终点：弹回
                    p = FINISH - (p - FINISH)
                }
                pos[turn] = p
                if (p >= FINISH) {
                    finished[turn] = true
                    playSound(SoundManager.WIN)
                }
                // 掷到 6 再来一次
                if (dice != 6 && !finished[turn]) turn = (turn + 1) % players
            }
            if (dice == 6 && !finished[turn]) {
                // 掷到 6 再来
            }
            // 检查胜负
            val winner = finished.indexOfFirst { it }
            if (winner >= 0 && finished.count { it } == 1) {
                over = true
                playSound(SoundManager.WIN)
                gameOver(GameResult(0, true, "玩家 " + (winner + 1) + " 到达终点获胜！"))
            }
            invalidate()
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawRoundRect(RectF(dp(10).toFloat(), dp(10).toFloat(), width - dp(10).toFloat(), height * 0.78f), dp(20).toFloat(), dp(20).toFloat(), boardPaint)
        // 路径点
        for (i in 0 until TRACK) {
            canvas.drawCircle(trackPts[i].first, trackPts[i].second, dp(10).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4668.toInt() })
        }
        // 飞机
        for (pl in 0 until 4) {
            if (finished[pl]) {
                canvas.drawCircle(width / 2f, height * 0.72f - pl * dp(18).toFloat(), dp(12).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[pl] })
            } else if (pos[pl] > 0) {
                val idx = (pos[pl] - 1).coerceAtMost(TRACK - 1)
                canvas.drawCircle(trackPts[idx].first, trackPts[idx].second, dp(13).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[pl] })
                smallPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawText((pl + 1).toString(), trackPts[idx].first, trackPts[idx].second + dp(5).toFloat(), smallPaint)
            } else {
                canvas.drawCircle(width * 0.08f + pl * dp(26).toFloat(), height * 0.74f, dp(12).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[pl] })
            }
        }
        // 骰子
        canvas.drawRoundRect(diceBtn, dp(14).toFloat(), dp(14).toFloat(), dicePaint)
        textPaint.textSize = 40f
        textPaint.color = 0xFF1C2333.toInt()
        canvas.drawText(dice.toString(), diceBtn.centerX(), diceBtn.centerY() + dp(6).toFloat(), textPaint)
        smallPaint.color = 0xFFE8EAF0.toInt()
        smallPaint.textSize = 20f
        canvas.drawText("玩家 " + (turn + 1) + " 点击掷骰 · 掷到 6 起飞/再掷一次 · 踩到别人前进 4 格", width / 2f, diceBtn.bottom + dp(26).toFloat(), smallPaint)
    }

    override fun reset() {
        for (i in 0 until 4) {
            pos[i] = 0
            finished[i] = false
        }
        turn = 0
        dice = 1
        rolling = false
        over = false
        start()
    }
}
