package com.minigames.collection.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 双人游戏开局同意遮罩。
 *
 * 功能：双人游戏（如五子棋、象棋）进入时显示全屏分屏页面，两个半区分别面向
 * 屏幕两端的玩家，双方各自点击自己的半屏「点击开始」后才正式开局。
 * 玩家 2 的半区整体旋转 180°（正对屏幕上方玩家），保证两人都能正常阅读规则。
 *
 * 使用场景：
 *   竖屏游戏：上下分屏（玩家 1 在下，玩家 2 在上）
 *   横屏游戏：左右分屏（玩家 1 在左，玩家 2 在右）
 *
 * 被调用位置：
 *   - GameActivity.showDualConsent()：创建实例并添加到 FrameLayout 最上层
 *   - onBothReady 回调：移除同意页 → createGame() 创建游戏视图
 *   - onExit 回调：调用 Activity.finish() 返回主菜单
 *
 * @param context Android 上下文
 * @param spec 游戏元数据（用于显示游戏名称、emoji、规则 hint）
 * @param onBothReady 双方都点击后的回调（在主线程执行，可安全操作 UI）
 * @param onExit 返回按钮回调
 */
class DualConsentView(
    context: Context,
    private val spec: GameSpec,
    private val onBothReady: () -> Unit,
    private val onExit: () -> Unit
) : View(context) {

    private val started = BooleanArray(2)

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private val bgPaint = Paint().apply { color = 0xEC141A2E.toInt() }
    private val tint0 = Paint().apply { color = 0x1F4A6CF7.toInt() }
    private val tint1 = Paint().apply { color = 0x1FFF7A45.toInt() }
    private val divider = Paint().apply { color = 0x55FFFFFF.toInt() }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = 0xFFE8EAF0.toInt()
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFB8C0D4.toInt()
    }
    private val btnFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val btnFillReady = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E9E5B.toInt() }
    private val btnStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66FFFFFF.toInt()
    }
    private val btnText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.DEFAULT_BOLD
    }
    private val backFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xC91C2333.toInt() }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
        textSize = 30f * scaledDensity
    }
    private val backBtn = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backBtn.set(dp(12), dp(28), dp(12) + dp(44), dp(28) + dp(44))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val portrait = height >= width
        val r0: RectF
        val r1: RectF
        if (portrait) {
            r0 = RectF(0f, height / 2f, width.toFloat(), height.toFloat())
            r1 = RectF(0f, 0f, width.toFloat(), height / 2f)
        } else {
            r0 = RectF(0f, 0f, width / 2f, height.toFloat())
            r1 = RectF(width / 2f, 0f, width.toFloat(), height.toFloat())
        }
        drawHalf(canvas, 0, r0)
        drawHalf(canvas, 1, r1)
        if (portrait) {
            canvas.drawRect(0f, height / 2f - 1f, width.toFloat(), height / 2f + 1f, divider)
        } else {
            canvas.drawRect(width / 2f - 1f, 0f, width / 2f + 1f, height.toFloat(), divider)
        }
        // 返回菜单按钮（不随玩家旋转）
        canvas.drawCircle(backBtn.centerX(), backBtn.centerY(), dp(22), backFill)
        canvas.drawCircle(backBtn.centerX(), backBtn.centerY(), dp(22), btnStroke)
        canvas.drawText("‹", backBtn.centerX(),
            backBtn.centerY() - (backPaint.ascent() + backPaint.descent()) / 2f, backPaint)
    }

    private fun drawHalf(canvas: Canvas, p: Int, region: RectF) {
        canvas.save()
        if (p == 1) {
            canvas.translate(region.centerX(), region.centerY())
            canvas.rotate(180f)
            canvas.translate(-region.centerX(), -region.centerY())
        }
        canvas.drawRect(region, if (p == 0) tint0 else tint1)
        titlePaint.textSize = sp(20f)
        canvas.drawText(spec.emoji + " " + spec.name + " · 玩家 " + (p + 1),
            region.centerX(), region.top + dp(74), titlePaint)
        // 规则正文：按可用高度自动缩小字号换行
        var size = 13f
        while (true) {
            bodyPaint.textSize = sp(size)
            val lines = wrapText(spec.hint, bodyPaint, region.width() - dp(56))
            val needH = lines.size * bodyPaint.fontSpacing
            val availH = region.bottom - dp(104) - region.top - dp(120)
            if (needH <= availH || size <= 8f) {
                var y = region.top + dp(120)
                for (line in lines) {
                    canvas.drawText(line, region.centerX(), y, bodyPaint)
                    y += bodyPaint.fontSpacing
                }
                break
            }
            size -= 0.5f
        }
        // 开始按钮（整半区点击均可）
        val bw = min(region.width() - dp(80), dp(220))
        val bh = dp(56)
        val btn = RectF(region.centerX() - bw / 2f, region.bottom - dp(104),
            region.centerX() + bw / 2f, region.bottom - dp(104) + bh)
        val ready = started[p]
        canvas.drawRoundRect(btn, dp(16), dp(16), if (ready) btnFillReady else btnFill)
        canvas.drawRoundRect(btn, dp(16), dp(16), btnStroke)
        btnText.textSize = sp(17f)
        canvas.drawText(if (ready) "✓ 已就绪 · 等待对方" else "点击开始",
            region.centerX(), btn.centerY() - (btnText.ascent() + btnText.descent()) / 2f, btnText)
        canvas.restore()
    }

    /** 按字符换行（适配中英文混排）。 */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = ArrayList<String>()
        val cur = StringBuilder()
        for (ch in text) {
            if (ch == '\n') {
                lines.add(cur.toString())
                cur.setLength(0)
                continue
            }
            val test = cur.toString() + ch
            if (cur.isNotEmpty() && paint.measureText(test) > maxWidth) {
                lines.add(cur.toString())
                cur.setLength(0)
                cur.append(ch)
            } else {
                cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        val x = event.x
        val y = event.y
        if (backBtn.contains(x, y)) {
            readyRunnable?.let { removeCallbacks(it) }
            readyRunnable = null
            onExit()
            return true
        }
        val portrait = height >= width
        val p = if (portrait) {
            if (y >= height / 2f) 0 else 1
        } else {
            if (x < width / 2f) 0 else 1
        }
        if (!started[p]) {
            started[p] = true
            invalidate()
            if (started[0] && started[1]) {
                // 存字段而非匿名对象：离开页面时必须能撤销，否则 300ms 内退出（点 ‹ 或系统回收）
                // 仍会回调 onBothReady，在已结束的 Activity 上创建游戏 View 并启动一个
                // 永远不会被暂停的帧循环（Choreographer 持有已销毁 Activity 引用 → 泄漏）。
                readyRunnable = Runnable {
                    readyRunnable = null
                    if (isAttachedToWindow) onBothReady()
                }
                postDelayed(readyRunnable, 300)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        readyRunnable?.let { removeCallbacks(it) }
        readyRunnable = null
    }

    private var readyRunnable: Runnable? = null

    private fun dp(v: Int): Float = v * density
    private fun dp(v: Float): Float = v * density
    private fun sp(v: Float): Float = v * scaledDensity
}
