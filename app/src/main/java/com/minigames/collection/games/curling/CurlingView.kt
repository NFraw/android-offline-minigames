package com.minigames.collection.games.curling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 冰壶对决：拖动蓄力投壶，离圆心最近者得分，三轮定胜负。 */
class CurlingView(context: Context) : GameView(context) {

    companion object {
        private const val ROUNDS = 3
    }

    private data class Stone(var x: Float, var y: Float, var vx: Float, var vy: Float, val player: Int)

    private val stones = ArrayList<Stone>()
    private var centerX = 0f
    private var centerY = 0f
    private var ringR = 0f
    private var dragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var shotX = 0f
    private var shotY = 0f
    private var turn = 0
    private var round = 1
    private var s1 = 0
    private var s2 = 0
    private var moving = false
    private var throwLeft = 2
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFFB8D4E8.toInt() }
    private val icePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8F4FB.toInt() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8503A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f
        color = 0xFF1C2333.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        centerX = w / 2f
        centerY = h * 0.3f
        ringR = min(w, h) * 0.2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused || moving) return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                dragStartX = event.x
                dragStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                shotX = event.x
                shotY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                    val dx = dragStartX - event.x
                    val dy = dragStartY - event.y
                    val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    if (len > 30f) {
                        // 投壶：从底部滑向目标
                        stones.add(Stone(width / 2f, height - dp(40).toFloat(), dx / len * height * 1.1f, dy / len * height * 1.1f, turn))
                        moving = true
                        playSound(SoundManager.MOVE)
                    }
                }
            }
        }
        invalidate()
    }

    override fun update(dt: Float) {
        if (over) return
        if (!moving) return
        var any = false
        for (s in stones) {
            if (s.vx == 0f && s.vy == 0f) continue
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vx *= 0.985f
            s.vy *= 0.985f
            if (abs(s.vx) < 8f && abs(s.vy) < 8f) {
                s.vx = 0f
                s.vy = 0f
            }
            any = true
        }
        if (!any) {
            moving = false
            throwLeft--
            if (throwLeft > 0) {
                turn = 1 - turn
            } else {
                scoreRound()
            }
        }
        invalidate()
    }

    private fun scoreRound() {
        // 离圆心最近者得分
        var bestD = Float.MAX_VALUE
        var bestP = -1
        for (s in stones) {
            val d = Math.hypot((s.x - centerX).toDouble(), (s.y - centerY).toDouble()).toFloat()
            if (d < bestD) {
                bestD = d
                bestP = s.player
            }
        }
        if (bestP == 0) s1++ else if (bestP == 1) s2++
        round++
        playSound(SoundManager.EAT)
        if (round > ROUNDS) {
            over = true
            val p1Win = s1 > s2
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, p1Win, "玩家 1 " + s1 + " : " + s2 + " 玩家 2" + (if (s1 == s2) "（平局）" else if (p1Win) "，玩家 1 获胜！" else "，玩家 2 获胜！")))
        } else {
            // 清场进入下一轮
            stones.clear()
            throwLeft = 2
            turn = 0
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawRoundRect(RectF(dp(20).toFloat(), dp(80).toFloat(), width - dp(20).toFloat(), height - dp(20).toFloat()), dp(16).toFloat(), dp(16).toFloat(), icePaint)
        // 同心圆
        for (i in 1..4) {
            canvas.drawCircle(centerX, centerY, ringR * i / 4f, ringPaint)
        }
        canvas.drawCircle(centerX, centerY, dp(8).toFloat(), ringPaint)
        for (s in stones) {
            canvas.drawCircle(s.x, s.y, dp(16).toFloat(), if (s.player == 0) redPaint else bluePaint)
        }
        textPaint.color = 0xFF1C2333.toInt()
        canvas.drawText("第 " + round + " 轮 · 玩家 " + (turn + 1) + " 投壶（剩 " + throwLeft + " 壶）· 红 " + s1 + " : " + s2 + " 蓝", width / 2f, dp(52).toFloat(), textPaint)
        textPaint.textSize = 18f
        canvas.drawText("按住屏幕向后拖动蓄力，松手投壶", width / 2f, height - dp(8).toFloat(), textPaint)
    }

    override fun reset() {
        stones.clear()
        turn = 0
        round = 1
        s1 = 0
        s2 = 0
        moving = false
        throwLeft = 2
        over = false
        start()
    }

    private fun min(a: Int, b: Int): Int = if (a < b) a else b
}
