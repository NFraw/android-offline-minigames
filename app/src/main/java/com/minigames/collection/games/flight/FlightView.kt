package com.minigames.collection.games.flight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/**
 * 飞行棋（2~4 人同屏）：开局选人数；掷到 6 起飞并可再掷；
 * 踩到其他玩家前进 4 格；绕场一圈回到起点前一格即获胜，先到者胜。
 */
class FlightView(context: Context) : GameView(context) {

    companion object {
        private const val TRACK = 28
    }

    private enum class Phase { SELECT, PLAY, OVER }

    // pos: -1 表示未起飞（在停机坪）
    private val pos = IntArray(4) { -1 }
    private var turn = 0
    private var dice = 1
    private var rolling = false
    private var rollTimer = 0f
    private var players = 0
    private var sixStreak = 0
    private var phase = Phase.SELECT
    private val diceBtn = RectF()
    private val selBtns = ArrayList<Pair<RectF, Int>>()
    private val colors = intArrayOf(0xFFE8503A.toInt(), 0xFF37C2F0.toInt(), 0xFF3ED98A.toInt(), 0xFFF5A54A.toInt())
    private val names = arrayOf("红", "蓝", "绿", "金")
    private var trackPts = ArrayList<Pair<Float, Float>>()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4668.toInt() }
    private val dicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val maskPaint = Paint().apply { color = 0xCC141A2E.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFFE8EAF0.toInt()
    }
    private val textDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFF1C2333.toInt()
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 32f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        // 环形路径（28 格）
        trackPts.clear()
        val cx = w / 2f
        val cy = h * 0.44f
        val rx = w * 0.33f
        val ry = h * 0.26f
        for (i in 0 until TRACK) {
            val ang = Math.PI * 2 * i / TRACK - Math.PI / 2
            trackPts.add(Pair(cx + Math.cos(ang).toFloat() * rx, cy + Math.sin(ang).toFloat() * ry))
        }
        // 骰子按钮
        val dw = w * 0.3f
        val dh = dp(56).toFloat()
        diceBtn.set(w / 2f - dw / 2f, h * 0.82f, w / 2f + dw / 2f, h * 0.82f + dh)
        // 选人数按钮
        selBtns.clear()
        val bw = dp(96).toFloat()
        val bh = dp(64).toFloat()
        val gap = dp(24).toFloat()
        val totalW = bw * 3 + gap * 2
        for (i in 0..2) {
            selBtns.add(Pair(RectF(w / 2f - totalW / 2f + i * (bw + gap), h * 0.56f, w / 2f - totalW / 2f + i * (bw + gap) + bw, h * 0.56f + bh), i + 2))
        }
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || paused) return
        when (phase) {
            Phase.SELECT -> {
                for ((rect, n) in selBtns) {
                    if (rect.contains(event.x, event.y)) {
                        players = n
                        phase = Phase.PLAY
                        turn = 0
                        for (i in 0 until 4) pos[i] = -1
                        dice = 1
                        rolling = false
                        sixStreak = 0
                        playSound(SoundManager.CLICK)
                        invalidate()
                        return
                    }
                }
            }
            Phase.PLAY -> {
                if (!rolling && diceBtn.contains(event.x, event.y)) {
                    rolling = true
                    rollTimer = 0f
                    playSound(SoundManager.MOVE)
                    invalidate()
                }
            }
            else -> {}
        }
    }

    override fun update(dt: Float) {
        if (phase != Phase.PLAY || !rolling) return
        rollTimer += dt
        if (rollTimer < 0.45f) {
            // 骰子滚动动画
            dice = (1..6).random()
            invalidate()
            return
        }
        rolling = false
        resolveTurn()
        invalidate()
    }

    private fun resolveTurn() {
        val pl = turn
        if (pos[pl] == -1) {
            // 未起飞：必须掷到 6
            if (dice == 6) {
                pos[pl] = startOf(pl)
                playSound(SoundManager.EAT)
                extraRoll(pl)
            } else {
                nextTurn()
            }
            return
        }
        // 在轨道上：前进
        val finished = advance(pl, dice)
        if (finished) {
            phase = Phase.OVER
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, true, names[pl] + "方（玩家 " + (pl + 1) + "）到达终点获胜！"))
            return
        }
        if (dice == 6) extraRoll(pl) else nextTurn()
    }

    /** 前进 steps 格；超过终点弹回；返回是否正好到达终点。 */
    private fun advance(pl: Int, steps: Int): Boolean {
        val start = startOf(pl)
        var off = (pos[pl] - start + TRACK) % TRACK
        off += steps
        if (off >= 27) {
            if (off == 27) {
                pos[pl] = (start + 27) % TRACK // 停在终点格
                return true
            }
            off = 27 - (off - 27)
        }
        pos[pl] = (start + off) % TRACK
        // 踩到其他玩家：前进 4 格
        for (other in 0 until 4) {
            if (other == pl || pos[other] < 0) continue
            if (pos[other] == pos[pl]) {
                playSound(SoundManager.EAT)
                return advance(pl, 4)
            }
        }
        return false
    }

    private fun extraRoll(pl: Int) {
        sixStreak++
        if (sixStreak >= 3) {
            // 连续三次 6：停一轮
            sixStreak = 0
            nextTurn()
        }
        // 否则保持当前玩家再掷
    }

    private fun nextTurn() {
        sixStreak = 0
        turn = (turn + 1) % players
    }

    private fun startOf(pl: Int): Int = pl * 7

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawRoundRect(RectF(dp(10).toFloat(), dp(10).toFloat(), width - dp(10).toFloat(), height * 0.76f), dp(20).toFloat(), dp(20).toFloat(), boardPaint)
        // 轨道格
        for (i in 0 until TRACK) {
            val (x, y) = trackPts[i]
            dotPaint.color = 0xFF3A4668.toInt()
            canvas.drawCircle(x, y, dp(12).toFloat(), dotPaint)
        }
        // 各玩家起点（彩色小点）与终点（白色圆环）
        for (pl in 0 until players) {
            val s = startOf(pl)
            val (sx, sy) = trackPts[s]
            canvas.drawCircle(sx, sy, dp(16).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[pl] })
            val f = (s + 27) % TRACK
            val (fx, fy) = trackPts[f]
            canvas.drawCircle(fx, fy, dp(17).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 3f
            })
        }
        // 玩家飞机
        for (pl in 0 until players) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[pl] }
            if (pos[pl] >= 0) {
                val (x, y) = trackPts[pos[pl]]
                canvas.drawCircle(x, y, dp(15).toFloat(), p)
                if (pl == turn && phase == Phase.PLAY) {
                    canvas.drawCircle(x, y, dp(21).toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFFFFF.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                    })
                }
                smallPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawText((pl + 1).toString(), x, y + dp(5).toFloat(), smallPaint)
            } else {
                // 停机坪
                val bx = width * 0.08f + pl * dp(30).toFloat()
                val by = height * 0.72f
                canvas.drawCircle(bx, by, dp(13).toFloat(), p)
                smallPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawText((pl + 1).toString(), bx, by + dp(5).toFloat(), smallPaint)
            }
        }
        if (phase == Phase.PLAY) {
            // 骰子
            canvas.drawRoundRect(diceBtn, dp(14).toFloat(), dp(14).toFloat(), dicePaint)
            textDarkPaint.textSize = 59f
            canvas.drawText(dice.toString(), diceBtn.centerX(), diceBtn.centerY() + dp(8).toFloat(), textDarkPaint)
            textPaint.textSize = 41f
            canvas.drawText("玩家 " + (turn + 1) + "（" + names[turn] + "方）点击骰子 · 掷到 6 起飞并再掷", width / 2f, diceBtn.bottom + dp(30).toFloat(), textPaint)
            smallPaint.color = 0xFF8A93A6.toInt()
            smallPaint.textSize = 32f
            canvas.drawText("踩到其他玩家前进 4 格 · 绕场一圈到终点获胜", width / 2f, diceBtn.bottom + dp(52).toFloat(), smallPaint)
        }
        // 选人数界面
        if (phase == Phase.SELECT) {
            canvas.drawRect(gameRect, maskPaint)
            textPaint.textSize = 62f
            canvas.drawText("✈️ 飞行棋", width / 2f, height * 0.30f, textPaint)
            textPaint.textSize = 44f
            canvas.drawText("开局先选玩家人数", width / 2f, height * 0.30f + dp(44).toFloat(), textPaint)
            for ((rect, n) in selBtns) {
                canvas.drawRoundRect(rect, dp(16).toFloat(), dp(16).toFloat(), btnPaint)
                textPaint.textSize = 53f
                canvas.drawText(n.toString() + " 人", rect.centerX(), rect.centerY() + dp(4).toFloat(), textPaint)
            }
            smallPaint.textSize = 36f
            canvas.drawText("掷到 6 起飞 · 先到终点者获胜", width / 2f, height * 0.56f + dp(84).toFloat(), smallPaint)
        }
    }

    override fun reset() {
        for (i in 0 until 4) pos[i] = -1
        turn = 0
        dice = 1
        rolling = false
        sixStreak = 0
        phase = if (players == 0) Phase.SELECT else Phase.PLAY
        start()
    }
}