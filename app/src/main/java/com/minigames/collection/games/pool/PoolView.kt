package com.minigames.collection.games.pool

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 台球（简化双人）：轮流击球，把己方球打进袋，先清光己方球者胜。 */
class PoolView(context: Context) : GameView(context) {

    private data class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float, val isCue: Boolean, val player: Int, var pocketed: Boolean)

    private val balls = ArrayList<Ball>()
    private var aimX = 0f
    private var aimY = 0f
    private var dragging = false
    private var turn = 0
    private var pocket1 = 0
    private var pocket2 = 0
    private var over = false
    private var tableW = 0f
    private var tableH = 0f
    private var tableLeft = 0f
    private var tableTop = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val tablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E9E5B.toInt() }
    private val cushionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A5A2B.toInt() }
    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5F6FA.toInt() }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt()
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 22f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        tableW = w * 0.9f
        tableH = h * 0.62f
        tableLeft = (w - tableW) / 2f
        tableTop = (h - tableH) / 2f + dp(30).toFloat()
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                aimX = event.x
                aimY = event.y
                dragging = true
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    shoot()
                    dragging = false
                }
            }
        }
    }

    private fun shoot() {
        val cue = balls.first { it.isCue }
        if (cue.pocketed) return
        val dx = aimX - cue.x
        val dy = aimY - cue.y
        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 10f) return
        // 反方向击球（瞄准点=击球方向的反向延长线）
        cue.vx = -dx / len * tableH * 1.6f
        cue.vy = -dy / len * tableH * 1.6f
        playSound(SoundManager.MOVE)
        invalidate()
    }

    override fun update(dt: Float) {
        if (over) return
        var anyMoving = false
        for (b in balls) {
            if (b.pocketed) continue
            b.x += b.vx * dt
            b.y += b.vy * dt
            // 库边反弹
            if (b.x - b.r < tableLeft) {
                b.x = tableLeft + b.r
                b.vx = abs(b.vx)
            }
            if (b.x + b.r > tableLeft + tableW) {
                b.x = tableLeft + tableW - b.r
                b.vx = -abs(b.vx)
            }
            if (b.y - b.r < tableTop) {
                b.y = tableTop + b.r
                b.vy = abs(b.vy)
            }
            if (b.y + b.r > tableTop + tableH) {
                b.y = tableTop + tableH - b.r
                b.vy = -abs(b.vy)
            }
            if (abs(b.vx) < 2f && abs(b.vy) < 2f) {
                b.vx = 0f
                b.vy = 0f
            }
            // 角袋（简化：四角接球）
            val inPocket = (abs(b.x - tableLeft) < b.r && abs(b.y - tableTop) < b.r) ||
                (abs(b.x - (tableLeft + tableW)) < b.r && abs(b.y - tableTop) < b.r) ||
                (abs(b.x - tableLeft) < b.r && abs(b.y - (tableTop + tableH)) < b.r) ||
                (abs(b.x - (tableLeft + tableW)) < b.r && abs(b.y - (tableTop + tableH)) < b.r)
            if (inPocket) {
                b.pocketed = true
                if (b.isCue) {
                    // 母球落袋：罚球，放回中心
                    b.pocketed = false
                    b.x = tableLeft + tableW / 2f
                    b.y = tableTop + tableH * 0.75f
                    b.vx = 0f
                    b.vy = 0f
                    turn = 1 - turn
                    playSound(SoundManager.LOSE)
                    checkEnd()
                    return
                }
                playSound(SoundManager.EAT)
                if (b.player == 0) pocket1++ else pocket2++
                checkEnd()
            }
            if (b.vx != 0f || b.vy != 0f) anyMoving = true
        }
        // 碰撞（简化：两两弹性交换速度）
        for (i in balls.indices) {
            for (j in i + 1 until balls.size) {
                val a = balls[i]
                val b = balls[j]
                if (a.pocketed || b.pocketed) continue
                val d = Math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
                if (d < a.r + b.r && d > 0.1f) {
                    val nx = (a.x - b.x) / d
                    val ny = (a.y - b.y) / d
                    val overlap = a.r + b.r - d
                    a.x += nx * overlap / 2f
                    a.y += ny * overlap / 2f
                    b.x -= nx * overlap / 2f
                    b.y -= ny * overlap / 2f
                    val av = a.vx * nx + a.vy * ny
                    val bv = b.vx * nx + b.vy * ny
                    a.vx += (bv - av) * nx
                    a.vy += (bv - av) * ny
                    b.vx += (av - bv) * nx
                    b.vy += (av - bv) * ny
                    playSound(SoundManager.CLICK)
                }
            }
        }
        if (!anyMoving && !over) {
            // 回合结束：换人
            val cue = balls.first { it.isCue }
            cue.vx = 0f
            cue.vy = 0f
            turn = 1 - turn
        }
        invalidate()
    }

    private fun checkEnd() {
        val p1Balls = balls.count { !it.isCue && !it.pocketed && it.player == 0 }
        val p2Balls = balls.count { !it.isCue && !it.pocketed && it.player == 1 }
        if (p1Balls == 0 || p2Balls == 0) {
            over = true
            val p1Win = p1Balls == 0
            playSound(SoundManager.WIN)
            gameOver(GameResult(0, p1Win, "玩家 1 " + pocket1 + " : " + pocket2 + " 玩家 2，" + (if (p1Win) "玩家 1 清台获胜！" else "玩家 2 清台获胜！")))
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        canvas.drawRoundRect(android.graphics.RectF(tableLeft - dp(14).toFloat(), tableTop - dp(14).toFloat(), tableLeft + tableW + dp(14).toFloat(), tableTop + tableH + dp(14).toFloat()), dp(12).toFloat(), dp(12).toFloat(), cushionPaint)
        canvas.drawRoundRect(android.graphics.RectF(tableLeft, tableTop, tableLeft + tableW, tableTop + tableH), dp(8).toFloat(), dp(8).toFloat(), tablePaint)
        for (b in balls) {
            if (b.pocketed) continue
            val p = if (b.isCue) cuePaint else if (b.player == 0) redPaint else bluePaint
            canvas.drawCircle(b.x, b.y, b.r, p)
        }
        if (dragging) {
            val cue = balls.first { it.isCue }
            canvas.drawLine(cue.x, cue.y, aimX, aimY, aimPaint)
        }
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("玩家 " + (turn + 1) + " 击球 · 拖动瞄准反方向击球 · 红方 " + pocket1 + " : " + pocket2 + " 蓝方", width / 2f, tableTop + tableH + dp(30).toFloat(), textPaint)
    }

    override fun reset() {
        balls.clear()
        balls.add(Ball(tableLeft + tableW / 2f, tableTop + tableH * 0.75f, 0f, 0f, dp(13).toFloat(), true, -1, false))
        // 红 4 蓝 4 三角排列
        val startX = tableLeft + tableW * 0.78f
        val startY = tableTop + tableH / 2f
        var idx = 0
        for (row in 0 until 3) {
            for (c in 0..row) {
                if (idx >= 8) break
                val x = startX + row * dp(24).toFloat()
                val y = startY + (c - row / 2f) * dp(26).toFloat()
                balls.add(Ball(x, y, 0f, 0f, dp(13).toFloat(), false, idx % 2, false))
                idx++
            }
        }
        pocket1 = 0
        pocket2 = 0
        turn = 0
        over = false
        start()
    }
}
