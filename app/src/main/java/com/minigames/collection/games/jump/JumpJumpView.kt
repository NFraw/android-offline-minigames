package com.minigames.collection.games.jump

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.abs

/** 跳一跳：按住蓄力，松手跳跃，精准落在方块中心得高分。 */
class JumpJumpView(context: Context) : GameView(context) {

    private var curX = 0f      // 当前所在方块 x
    private var nextX = 0f     // 目标方块 x
    private var blockY = 0f
    private var blockW = 0f
    private var blockH = 0f
    private var power = 0f     // 蓄力
    private var jumping = false
    private var jumpX = 0f
    private var jumpY = 0f
    private var jumpT = 0f
    private var score = 0
    private var over = false
    private var pressing = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val nextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB74D.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        color = 0xFF8A93A6.toInt()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 30f
        color = 0xFFE8EAF0.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        blockH = dp(34).toFloat()
        blockW = dp(90).toFloat()
        blockY = h * 0.62f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressing = true
                power = 0f
            }
            MotionEvent.ACTION_UP -> {
                pressing = false
                if (!jumping) {
                    jumping = true
                    jumpX = curX
                    jumpY = blockY - blockH
                    jumpT = 0f
                    playSound(SoundManager.MOVE)
                }
            }
        }
    }

    override fun update(dt: Float) {
        if (over || paused) return
        if (jumping) {
            jumpT += dt
            // 跳跃距离由蓄力决定
            val dist = power * 340f
            val t = jumpT / 0.32f
            if (t >= 1f) {
                val landed = curX + dist
                val center = nextX + blockW / 2f
                if (abs(landed + blockW / 2f - center) < blockW * 0.45f) {
                    // 落在方块上
                    score++
                    val perfect = abs(landed + blockW / 2f - center) < blockW * 0.12f
                    if (perfect) score++
                    setScore(score)
                    playSound(if (perfect) SoundManager.EAT else SoundManager.CLICK)
                    curX = landed
                    // 生成下一个方块（向右）
                    nextX = curX + blockW * (1.1f + Math.random().toFloat() * 1.6f)
                    if (nextX + blockW > width) nextX = curX - blockW * (1.1f + Math.random().toFloat() * 1.2f)
                } else {
                    over = true
                    playSound(SoundManager.LOSE)
                    gameOver(GameResult(score, false, "没跳上去！得分 " + score))
                    return
                }
                jumping = false
                power = 0f
            } else {
                // 空中抛物线
                jumpX = curX + power * 340f * t
                jumpY = blockY - blockH - (blockH * 2.2f) * 4f * t * (1f - t)
            }
            invalidate()
        } else if (pressing) {
            // 蓄力（按住屏幕）
            power = (power + dt * 1.2f).coerceAtMost(1f)
            invalidate()
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 地面
        canvas.drawLine(0f, blockY + blockH, width.toFloat(), blockY + blockH, textPaint)
        // 当前方块
        canvas.drawRoundRect(android.graphics.RectF(curX, blockY, curX + blockW, blockY + blockH), dp(10).toFloat(), dp(10).toFloat(), blockPaint)
        // 目标方块
        canvas.drawRoundRect(android.graphics.RectF(nextX, blockY, nextX + blockW, blockY + blockH), dp(10).toFloat(), dp(10).toFloat(), nextPaint)
        // 玩家
        val px: Float
        val py: Float
        if (jumping) {
            px = jumpX
            py = jumpY
        } else {
            px = curX
            py = blockY - blockH
        }
        canvas.drawRoundRect(android.graphics.RectF(px + blockW * 0.18f, py, px + blockW * 0.82f, py + blockH), dp(10).toFloat(), dp(10).toFloat(), playerPaint)
        // 蓄力条
        if (!jumping && power > 0.02f) {
            textPaint.color = 0xFFF5D547.toInt()
            canvas.drawText("蓄力 " + (power * 100).toInt() + "%", width / 2f, blockY - blockH * 2.6f, textPaint)
        }
        scorePaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("得分 " + score, width / 2f, dp(60).toFloat(), scorePaint)
        textPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("按住蓄力，松手跳跃", width / 2f, blockY + blockH + dp(40).toFloat(), textPaint)
    }

    override fun reset() {
        curX = width * 0.15f
        nextX = curX + blockW * 2.4f
        power = 0f
        jumping = false
        score = 0
        over = false
        setScore(0)
        start()
    }
}