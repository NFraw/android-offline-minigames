package com.minigames.collection.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View

/**
 * 所有小游戏的基类：
 * Choreographer 帧循环 + 暂停/恢复 + 触摸分发 + 得分/结束回调。
 * 子类只需实现 update / drawGame / reset / onLayout。
 */
abstract class GameView(context: Context) : View(context) {

    interface Callbacks {
        fun onScoreChanged(score: Int)
        fun onGameOver(result: GameResult)
    }

    var callbacks: Callbacks? = null
    var sound: SoundManager? = null
    var haptic: HapticManager? = null

    var paused = true
    protected val gameRect = RectF()

    private var running = false
    private var lastFrame = 0L
    private var showPauseOverlay = false

    private val pausePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f * resources.displayMetrics.scaledDensity
        color = 0xFFFFFFFF.toInt()
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || paused) return
            val dt = if (lastFrame > 0) (frameTimeNanos - lastFrame) / 1e9f else 0.016f
            lastFrame = frameTimeNanos
            update(dt.coerceIn(0f, 0.05f))
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gameRect.set(0f, 0f, w.toFloat(), h.toFloat())
        onLayout(w, h)
    }

    /** 布局尺寸确定后调用，用于初始化棋盘/网格等。 */
    protected open fun onLayout(w: Int, h: Int) {}

    /** 游戏逻辑步进，dt 为秒。 */
    protected abstract fun update(dt: Float)

    /** 绘制游戏画面。 */
    protected abstract fun drawGame(canvas: Canvas)

    /** 重置状态并自动开始。 */
    abstract fun reset()

    protected open fun onGameStart() {}

    protected open fun onGamePause() {}

    protected fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    final override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGame(canvas)
        if (paused && showPauseOverlay) {
            canvas.drawColor(0x66000000.toInt())
            canvas.drawText("⏸ 已暂停", width / 2f, height / 2f, pausePaint)
        }
    }

    /** 开始（首次或重置后）。 */
    fun start() {
        paused = false
        showPauseOverlay = false
        running = true
        lastFrame = 0
        onGameStart()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    /** 暂停（顶部按钮或切后台）。 */
    fun pause() {
        if (paused) return
        paused = true
        showPauseOverlay = true
        onGamePause()
    }

    /** 继续。 */
    fun resume() {
        if (!paused) return
        paused = false
        showPauseOverlay = false
        lastFrame = 0
        onGameStart()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onTouch(event)
        return true
    }

    protected open fun onTouch(event: MotionEvent) {}

    protected fun setScore(score: Int) {
        callbacks?.onScoreChanged(score)
    }

    protected fun playSound(rawId: Int) {
        sound?.play(rawId)
    }

    protected fun buzz() {
        haptic?.buzz()
    }

    /** 游戏结束：冻结画面并通知 Activity 弹结算框。 */
    protected fun gameOver(result: GameResult) {
        paused = true
        running = false
        callbacks?.onGameOver(result)
    }
}

data class GameResult(val score: Int, val won: Boolean, val summary: String)
