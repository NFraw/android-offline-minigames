package com.minigames.collection.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View

/**
 * 所有小游戏的抽象基类。
 *
 * 功能：提供统一的游戏循环（帧驱动）、暂停/恢复、触摸分发、得分/结束回调，
 * 子类只需实现 update/drawGame/reset/onLayout 四个方法即可成为一款完整游戏。
 *
 * 架构说明（对应 C++/Java 游戏编程概念）：
 *   - 游戏循环：使用 Android Choreographer（帧回调），等价于 C++ 的 while(true) { update(); render(); }
 *     但更节能——仅在 View 可见时才回调，且自动与屏幕刷新率（60/120Hz）同步。
 *   - 渲染：使用 Canvas 绘制（2D 矢量/位图），等价于 C++ 的 GDI/OpenGL 2D 绘图。
 *     onDraw() 由 Android 框架在每帧自动调用，子类在 drawGame() 中实现绘制逻辑。
 *   - 触摸输入：onTouchEvent() 由 Android 框架在用户触摸时调用，子类在 onTouch() 中处理。
 *
 * 帧循环工作流程：
 *   1. start() 被调用 → Choreographer 注册帧回调
 *   2. 每帧到达时 → doFrame() 被调用 → 计算 dt（上一帧到本帧的时间差，秒）
 *   3. 调用 update(dt) → 子类实现游戏逻辑步进（移动、碰撞检测、状态更新等）
 *   4. 调用 invalidate() → 触发 onDraw() → 调用 drawGame() → 子类实现画面绘制
 *   5. 重新注册帧回调 → 回到步骤 2
 *   6. 暂停时 running=false → doFrame() 直接 return → 帧循环停止
 *
 * 子类实现要求：
 *   - update(dt: Float)：游戏逻辑步进。dt 为秒（通常 0.016 即 60fps），上限 0.05s 防止切后台回来大跳跃
 *   - drawGame(canvas: Canvas)：绘制游戏画面。每帧被调用，应尽量高效（避免创建对象）
 *   - reset()：重置游戏状态并调用 start() 开始新一局
 *   - onLayout(w: Int, h: Int)：屏幕尺寸确定后调用，用于计算棋盘/网格等布局参数
 *
 * 被调用位置：
 *   - GameActivity.createGame()：通过反射创建实例，注入 sound/haptic/voice/callbacks，添加到 FrameLayout
 *   - GameActivity.togglePause()：调用 pause()/resume()
 *   - GameActivity.showGameOver() 的"再来一局"按钮：调用 reset()
 *   - GameActivity.onPause()：自动暂停
 *
 * 扩展提示：
 *   新增游戏：继承此类，实现四个抽象方法，在 GameRegistry 注册即可。
 *   dp()/sp() 是常用的尺寸转换工具，textPaint()/roundRect() 是常用的绘制辅助。
 *   gameOver() 会冻结画面并通知 Activity 弹出结算框——不要在 update() 中直接调用，
 *   而是在检测到游戏结束条件时调用。
 */
abstract class GameView(context: Context) : View(context) {

    /**
     * 全库统一色板（深色主题）。
     * 所有游戏共享同一套颜色，保证视觉一致性。子类可以直接引用这些常量。
     * 颜色格式：0xAARRGGBB（AA=透明度，RR=红，GG=绿，BB=蓝，均为 16 进制）。
     */
    companion object {
        const val C_BG = 0xFF141A2E.toInt()       // 游戏背景色（深蓝黑）
        const val C_PANEL = 0xFF2A3557.toInt()    // 面板/按钮底色（深蓝灰）
        const val C_TEXT = 0xFFE8EAF0.toInt()     // 主文字色（近白）
        const val C_DIM = 0xFF8A93A6.toInt()      // 次要文字色（灰蓝）
        const val C_ACCENT = 0xFF4A6CF7.toInt()   // 强调色（亮蓝）
        const val C_GREEN = 0xFF3ED98A.toInt()    // 绿色（成功/得分）
        const val C_RED = 0xFFFF5252.toInt()      // 红色（失败/警告）
        const val C_GOLD = 0xFFFFD54F.toInt()     // 金色（最高分/奖励）

        /**
         * 顶部安全区高度（dp）。
         * GameActivity 在屏幕顶部叠加了悬浮控制层（退出/暂停/重开/帮助按钮），
         * 游戏内顶部标题/状态文字应从该高度之下开始绘制，避免被遮挡。
         * 子类在 onLayout() 中使用：val topPad = dp(SAFE_TOP_DP).toFloat()
         */
        const val SAFE_TOP_DP = 100
    }

    /**
     * 游戏回调接口——由 GameActivity 实现并注入。
     * 子类通过 setScore() 和 gameOver() 间接调用这两个回调。
     * 相当于 C++ 的回调函数指针或 Java 的 Observer 模式。
     */
    interface Callbacks {
        /** 得分变化时调用，GameActivity 更新右下角得分显示 */
        fun onScoreChanged(score: Int)
        /** 游戏结束时调用，GameActivity 弹出结算对话框 */
        fun onGameOver(result: GameResult)
    }

    // ---- 由 GameActivity.createGame() 注入的外部依赖 ----
    var callbacks: Callbacks? = null   // 得分/结束回调（GameActivity 实现）
    var sound: SoundManager? = null    // 音效播放器
    var haptic: HapticManager? = null  // 震动反馈器
    var voice: VoiceManager? = null    // 语音播报器

    /**
     * 游戏是否处于暂停状态。
     * true 时帧循环不推进（update 不被调用），画面显示半透明暂停遮罩。
     * 由 GameActivity 的暂停按钮、onPause() 生命周期回调控制。
     */
    var paused = true
        private set

    /**
     * 帧循环是否仍在推进（即对局进行中）。
     * gameOver() 之后为 false，供 GameActivity 判断"暂停键是否还有意义"。
     */
    val isRunning: Boolean get() = running
    /**
     * 游戏画面区域（像素坐标）。
     * 在 onSizeChanged() 中设置为整个 View 的尺寸 (0, 0, w, h)。
     * 子类可用于绘制全屏背景：canvas.drawRect(gameRect, bgPaint)
     */
    protected val gameRect = RectF()

    // ---- 帧循环内部状态 ----
    private var running = false     // 帧循环是否激活（start() 后为 true，gameOver() 后为 false）
    private var lastFrame = 0L      // 上一帧的时间戳（纳秒），用于计算 dt
    private var showPauseOverlay = false  // 是否显示暂停遮罩层

    // 帧回调是否已挂起（防止重复注册）。
    // 子类 reset() 中的 start() 可能在同一局内被多次触发（按「↻」重开、onLayout 二次布局、
    // 过关时重启等），若无此标志，每触发一次就会永久多出一份 Choreographer 回调，
    // 表现为每帧执行 N 次 update()/invalidate()，CPU 占用与耗电随操作次数线性上涨。
    private var frameScheduled = false

    private val pausePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f * resources.displayMetrics.scaledDensity  // scaledDensity 联动系统字体大小设置
        color = 0xFFFFFFFF.toInt()
    }

    /**
     * 帧回调——Choreographer 每帧调用一次。
     * Choreographer 是 Android 的帧调度器，与屏幕 VSync 信号同步（通常 60Hz 或 120Hz）。
     * 相当于游戏引擎的主循环：每帧计算时间差 → 更新逻辑 → 触发重绘 → 注册下一帧回调。
     */
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || paused) {
                frameScheduled = false  // 循环已停止：释放标志，让下次 start()/resume() 能重新注册
                return
            }
            // 计算上一帧到本帧的时间差（秒）
            // lastFrame=0 表示刚恢复，给一个默认 16ms（约 60fps 一帧的时间）
            val dt = if (lastFrame > 0) (frameTimeNanos - lastFrame) / 1e9f else 0.016f
            lastFrame = frameTimeNanos
            // coerceIn 限制 dt 范围：防止切后台回来时 dt 过大导致游戏状态跳跃
            update(dt.coerceIn(0f, 0.05f))  // 最大 50ms，约 20fps 的一帧
            invalidate()  // 标记 View 需要重绘 → 触发 onDraw()
            Choreographer.getInstance().postFrameCallback(this)  // 注册下一帧回调（递归）
        }
    }

    /**
     * 挂起帧回调（幂等）。
     * 已在挂起状态时不再重复 postFrameCallback —— 这是"每按一次重开就多一份帧回调"的修复点。
     */
    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    /**
     * 取消已挂起的帧回调（幂等）。
     */
    private fun unscheduleFrame() {
        if (!frameScheduled) return
        frameScheduled = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    /**
     * View 尺寸变化时调用（首次布局或屏幕旋转时）。
     * 在此更新 gameRect 并通知子类重新计算布局。
     * 子类不应重写此方法，而是重写 onLayout()。
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gameRect.set(0f, 0f, w.toFloat(), h.toFloat())
        onLayout(w, h)
    }

    /**
     * 屏幕尺寸确定后调用，子类在此计算棋盘/网格/按钮等布局参数。
     * @param w View 宽度（像素）
     * @param h View 高度（像素）
     */
    protected open fun onLayout(w: Int, h: Int) {}

    /**
     * 游戏逻辑步进（每帧调用一次）。
     * 子类在此实现：移动、碰撞检测、状态更新等所有游戏逻辑。
     * @param dt 上一帧到本帧的时间差（秒），通常 0.016（60fps），上限 0.05
     */
    protected abstract fun update(dt: Float)

    /**
     * 绘制游戏画面（每帧调用一次，在 update 之后）。
     * 子类在此用 Canvas API 绘制：背景、棋盘、棋子、文字等。
     * @param canvas 画布对象，提供 drawRect/drawCircle/drawText 等绘制方法
     */
    protected abstract fun drawGame(canvas: Canvas)

    /**
     * 重置游戏状态并自动开始新一局。
     * 子类实现：清空棋盘/蛇身/分数等所有状态变量，然后调用 start()。
     * 被 GameActivity 的重开按钮和"再来一局"按钮调用。
     */
    abstract fun reset()

    /** 游戏开始时的钩子（可选重写），如播放开局音效。 */
    protected open fun onGameStart() {}

    /** 游戏暂停时的钩子（可选重写），如保存中间状态。 */
    protected open fun onGamePause() {}

    /**
     * dp → 像素转换。
     * Android 使用 dp（密度无关像素）作为布局单位，不同屏幕密度下自动缩放：
     *   - mdpi（1x）：1dp = 1px
     *   - xhdpi（2x）：1dp = 2px
     *   - xxhdpi（3x）：1dp = 3px
     * density 来自 resources.displayMetrics.density（如 2.0 表示 xhdpi 屏幕）。
     * 相当于 CSS 的 rem 单位概念。
     */
    protected fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /**
     * dp → 像素转换（Float 版，保留小数精度）。
     *
     * 为什么需要这个重载：Canvas 的绘制 API（drawCircle/drawRoundRect/字体大小等）都接受 Float，
     * 用 dp(Int) 的结果会在需要小数尺寸时被截断（例如算出的棋子半径 21.7px 变成 21px），
     * 高频绘制下会出现可见的圆角/圆不齐。有了这个重载，调用处直接写 dp(2.5f) 即可，
     * 不需要在子类里另外定义 dpf() 之类的帮助函数。
     */
    protected fun dp(v: Float): Float = v * resources.displayMetrics.density

    /**
     * sp → 像素转换（用于字号）。
     * sp 与 dp 类似，但还会联动用户在系统设置中调整的字体大小。
     * 用户把系统字体调大时，sp 对应的像素值也会变大，保证文字可读性。
     * scaledDensity 来自 resources.displayMetrics.scaledDensity。
     */
    protected fun sp(v: Float): Float = v * resources.displayMetrics.scaledDensity

    /**
     * 创建带深色阴影的文字 Paint（工厂方法）。
     * 深色背景上的白字容易"融入"背景，阴影让文字有立体感和可读性。
     * @param sizeSp 字号（sp 单位，会联动系统字体设置）
     * @param color 文字颜色（ARGB）
     * @param bold 是否加粗
     * @return 配置好的 Paint 对象，可直接传给 canvas.drawText()
     */
    protected fun textPaint(sizeSp: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sp(sizeSp)
            this.color = color
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xB0000000.toInt())
        }

    /**
     * 绘制带投影的圆角矩形，并返回填充 Paint 供子类复用。
     * @param canvas 画布
     * @param rect 矩形区域（像素坐标）
     * @param fill 填充颜色（ARGB）
     * @param radiusDp 圆角半径（dp 单位）
     * @return 填充用 Paint 对象（已设置颜色，可直接用于其他绘制）
     */
    protected fun roundRect(canvas: Canvas, rect: RectF, fill: Int, radiusDp: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
            setShadowLayer(dp(3).toFloat(), 0f, dp(2).toFloat(), 0x66000000.toInt())
            canvas.drawRoundRect(rect, dp(radiusDp).toFloat(), dp(radiusDp).toFloat(), this)
        }

    /**
     * 计算文字垂直居中的 baseline y 坐标。
     * Canvas.drawText() 的 y 参数是 baseline 而非中心点，
     * 这个方法把 centerY 转换为正确的 baseline 值。
     * 公式原理：baseline = centerY - (descent + ascent) / 2
     *   ascent 是 baseline 到文字顶部的距离（负值），descent 是 baseline 到底部的距离（正值）
     */
    protected fun centerBaseline(centerY: Float, paint: Paint): Float =
        centerY - (paint.descent() + paint.ascent()) / 2f

    /**
     * 以 (cx, cy) 为中心绘制棋子文字，支持旋转 180°（面向屏幕上方玩家）。
     * 用于双人棋类游戏（如象棋、斗兽棋）的"对向视角"：
     *   - 红方（屏幕下方）棋子文字正常绘制（facingUp=false）
     *   - 黑方（屏幕上方）棋子文字旋转 180°（facingUp=true），使文字正对上方玩家
     *
     * @param canvas 画布
     * @param paint 文字 Paint
     * @param text 棋子文字（如 "帥"、"車"）
     * @param cx 棋子中心 x（像素）
     * @param cy 棋子中心 y（像素）
     * @param facingUp true 时文字旋转 180°（面向屏幕上方玩家）
     */
    protected fun drawPieceText(canvas: Canvas, paint: Paint, text: String, cx: Float, cy: Float, facingUp: Boolean) {
        val baseline = (paint.descent() + paint.ascent()) / 2f
        if (!facingUp) {
            canvas.drawText(text, cx, cy - baseline, paint)
        } else {
            // 旋转 180°：先平移到目标中心点 → 旋转 → 在原点绘制 → 恢复变换
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(180f)
            canvas.drawText(text, 0f, -baseline, paint)
            canvas.restore()
        }
    }

    /**
     * 供 [BoardArt] 等辅助类调用的棋子文字绘制入口。
     *
     * `drawPieceText` 是 protected，同包的辅助类无法直接调用；这里包一层 public 版本，
     * 让美术工具箱能复用同一份「上方玩家文字旋转 180°」的逻辑，避免两处实现漂移。
     * 子类仍应直接使用 protected 的 [drawPieceText]。
     */
    fun drawPieceTextPublic(canvas: Canvas, paint: Paint, text: String, cx: Float, cy: Float, facingUp: Boolean) {
        drawPieceText(canvas, paint, text, cx, cy, facingUp)
    }

    /**
     * View 绘制入口（Android 框架每帧自动调用，不要直接调用）。
     * final 不可重写——子类应重写 drawGame() 而非 onDraw()。
     * 流程：先调用子类的 drawGame() 绘制游戏画面，再叠加暂停遮罩（如果需要）。
     */
    final override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGame(canvas)
        // 暂停时显示半透明黑色遮罩 + "已暂停" 文字
        if (paused && showPauseOverlay) {
            canvas.drawColor(0x66000000.toInt())  // 40% 透明黑色
            canvas.drawText("⏸ 已暂停", width / 2f, height / 2f, pausePaint)
        }
    }

    /**
     * 启动帧循环，开始游戏。
     * 在 reset() 中调用——子类的 reset() 先清空状态再调用 start()。
     */
    fun start() {
        paused = false
        showPauseOverlay = false
        running = true
        lastFrame = 0
        onGameStart()  // 钩子：子类可重写做开局处理
        scheduleFrame()  // 幂等：已在运行时不会重复注册帧回调
    }

    /**
     * 暂停游戏。
     * 被 GameActivity 的暂停按钮、onPause() 生命周期回调调用。
     * 暂停后帧循环停止（doFrame 中 running||paused 检查），画面显示暂停遮罩。
     */
    fun pause() {
        if (paused) return
        paused = true
        showPauseOverlay = true
        onGamePause()  // 钩子
        unscheduleFrame()  // 彻底停掉帧回调，避免暂停期间空转
        invalidate()   // 触发重绘以显示暂停遮罩
    }

    /**
     * 恢复游戏。
     * 被 GameActivity 的继续按钮调用。
     * lastFrame 重置为 0，下一帧 doFrame 会使用默认 dt（16ms），避免恢复时 dt 过大。
     */
    fun resume() {
        if (!paused) return
        paused = false
        showPauseOverlay = false
        lastFrame = 0
        scheduleFrame()
    }

    /**
     * 触摸事件入口（Android 框架调用，不要直接调用）。
     * 返回 true 表示始终消费事件（防止父 View 拦截）。
     * 子类重写 onTouch() 处理具体逻辑。
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        onTouch(event)
        // 满足 ClickableViewAccessibility 契约：无障碍服务（TalkBack）依赖 performClick 激活控件
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 触摸事件处理（子类重写）。@param event 触摸事件（包含坐标、动作类型等） */
    protected open fun onTouch(event: MotionEvent) {}

    /**
     * 通知 Activity 更新得分显示。
     * 子类在得分变化时调用（如 SnakeGameView 吃到食物后 setScore(score)）。
     */
    protected fun setScore(score: Int) {
        callbacks?.onScoreChanged(score)
    }

    /**
     * 播放音效的便捷方法（委托给 SoundManager）。
     * @param rawId R.raw.xxx 资源 ID（如 SoundManager.EAT）
     */
    protected fun playSound(rawId: Int) {
        sound?.play(rawId)
    }

    /** 触发轻震动（委托给 HapticManager）。 */
    protected fun buzz() {
        haptic?.buzz()
    }

    /**
     * 播放语音（委托给 VoiceManager）。
     * @param tag 语音标识（如 VoiceManager.JIANGJUN）
     */
    protected fun speakVoice(tag: String) {
        voice?.speak(tag)
    }

    /**
     * 游戏结束：冻结帧循环并通知 Activity 弹出结算框。
     * 子类在检测到结束条件时调用（如蛇撞墙、五子连珠）。
     * @param result 包含得分、胜负、摘要信息的 GameResult
     */
    protected fun gameOver(result: GameResult) {
        // 重入防护：子类可能在递归/多分支中重复调用（典型是扫雷洪水填充的每一层递归末尾
        // 都判一次胜利），没有这道门闩就会叠出多个结算对话框并重复写入历史记录。
        if (!running) return
        paused = true
        running = false
        unscheduleFrame()
        // 立即重绘最后一帧：帧循环已停，若这次 gameOver 由触摸触发（如扫雷踩雷、台球落袋），
        // 不主动 invalidate 的话画面会定格在触摸之前，看不到翻开的雷区。
        invalidate()
        callbacks?.onGameOver(result)
    }
}

/**
 * 游戏结算数据。
 *
 * @param score 本局得分（正整数，0 表示无得分机制的游戏如五子棋）
 * @param won 是否获胜（true 显示 "🎉 胜利"，false 显示 "游戏结束"）
 * @param summary 结果摘要（如 "蛇身长度 15"、"黑方五连获胜！"），显示在结算弹窗正文
 */
data class GameResult(val score: Int, val won: Boolean, val summary: String)
