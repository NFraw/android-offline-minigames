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

    /** 全库统一色板（游戏内深色背景 UI 用）。 */
    companion object {
        const val C_BG = 0xFF141A2E.toInt()       // 游戏背景
        const val C_PANEL = 0xFF2A3557.toInt()    // 面板/按钮底
        const val C_TEXT = 0xFFE8EAF0.toInt()     // 主文字
        const val C_DIM = 0xFF8A93A6.toInt()      // 次要文字
        const val C_ACCENT = 0xFF4A6CF7.toInt()   // 强调蓝
        const val C_GREEN = 0xFF3ED98A.toInt()
        const val C_RED = 0xFFFF5252.toInt()
        const val C_GOLD = 0xFFFFD54F.toInt()

        /**
         * 顶部安全区（dp）：Activity 悬浮控制层（左上/右上角按钮）占用的区域。
         * 游戏内顶部标题/状态文字应从该高度之下开始绘制，避免被悬浮层遮挡。
         */
        const val SAFE_TOP_DP = 100
    }

    interface Callbacks {
        fun onScoreChanged(score: Int)
        fun onGameOver(result: GameResult)
    }

    var callbacks: Callbacks? = null
    var sound: SoundManager? = null
    var haptic: HapticManager? = null
    var voice: VoiceManager? = null

    var paused = true
    protected val gameRect = RectF()

    private var running = false
    private var lastFrame = 0L
    private var showPauseOverlay = false

    private val pausePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f * resources.displayMetrics.scaledDensity
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

    /** 字号按屏幕缩放密度换算（与系统字体设置联动）。 */
    protected fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity

    /**
     * 生成带深色描边/阴影的文字 Paint，避免深色背景下白字看不清。
     * @param sizeSp 字号（sp）
     * @param color 文字颜色
     * @param bold 是否加粗
     */
    protected fun textPaint(sizeSp: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sp(sizeSp)
            this.color = color
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xB0000000.toInt())
        }

    /** 圆角矩形（带投影），返回用于填充的 Paint，方便复用。 */
    protected fun roundRect(canvas: Canvas, rect: RectF, fill: Int, radiusDp: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
            setShadowLayer(dp(3).toFloat(), 0f, dp(2).toFloat(), 0x66000000.toInt())
            canvas.drawRoundRect(rect, dp(radiusDp).toFloat(), dp(radiusDp).toFloat(), this)
        }

    /** 文字竖直居中对齐辅助：返回绘制文本的 baseline y（中心点对齐）。 */
    protected fun centerBaseline(centerY: Float, paint: Paint): Float =
        centerY - (paint.descent() + paint.ascent()) / 2f

    /**
     * 以 (cx, cy) 为中心绘制文字；facingUp 为 true 时文字绕中心旋转 180°，
     * 使文字正对屏幕上方玩家。用于双人棋类游戏的"对向视角"：红方（下方）棋子
     * 文字朝下正常绘制，黑方（上方）棋子文字朝上，棋盘本身不翻转。
     */
    protected fun drawPieceText(canvas: Canvas, paint: Paint, text: String, cx: Float, cy: Float, facingUp: Boolean) {
        val baseline = (paint.descent() + paint.ascent()) / 2f
        if (!facingUp) {
            canvas.drawText(text, cx, cy - baseline, paint)
        } else {
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(180f)
            canvas.drawText(text, 0f, -baseline, paint)
            canvas.restore()
        }
    }

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

    protected fun speakVoice(tag: String) {
        voice?.speak(tag)
    }

    /** 游戏结束：冻结画面并通知 Activity 弹结算框。 */
    protected fun gameOver(result: GameResult) {
        paused = true
        running = false
        callbacks?.onGameOver(result)
    }
}

data class GameResult(val score: Int, val won: Boolean, val summary: String)
