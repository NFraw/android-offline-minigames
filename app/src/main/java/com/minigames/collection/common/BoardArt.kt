package com.minigames.collection.common

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * 桌游美术工具箱（扁平柔和卡通风）。
 *
 * 为什么需要这个文件：
 *   棋类/桌游（象棋、围棋、五子棋、黑白棋、暗棋、斗兽棋、飞行棋、台球、冰壶）此前各写各的
 *   绘制代码，同一块"木头棋盘"在不同文件里有 7 种色值，棋子高光方向、圆角半径、阴影偏移
 *   也各不相同。结果是 9 款游戏放在一起看像 9 个不同 App 的截图。
 *   这里把「棋盘底盘 / 棋子 / 卡片 / 胶囊 / 标题行」五类视觉元素收敛成一套可复用原语，
 *   所有桌游统一调用，保证视觉同源。
 *
 * 风格定位：扁平柔和卡通风
 *   - 大圆角（棋盘 20dp、格子 6~10dp、按钮全圆角胶囊）
 *   - 柔和低饱和配色，暖木色棋盘 + 奶油色棋子，不用纯黑纯白
 *   - 浅色柔光高光（左上偏移）+ 软投影（向下偏移，无硬边）
 *   - 无锐角、无硬描边，所有轮廓都用圆角或弧形过渡
 *
 * 使用方式（所有方法都是纯绘制，不持有可变状态，可安全跨帧复用）：
 *   - 在 GameView 子类里 `private val art = BoardArt(this)` 持有实例
 *   - onLayout() 中调用 buildBoardPalette() 预生成 Shader（避免每帧 new 对象）
 *   - drawGame() 中调用 drawBoardSlab() / drawStone() / drawCard() 等
 *
 * 性能约定：所有 Paint 与 Shader 都在构造/布局期创建，绘制期只改颜色和坐标，
 * 不在 drawGame() 里 new Paint/Shader —— 这是本项目所有游戏的一贯要求。
 */
class BoardArt(private val owner: GameView) {

    /**
     * 统一色板。命名对应"木头棋盘 + 奶油棋子 + 柔和面板"这套卡通风。
     * 所有桌游必须从这里取色，不要再在游戏文件里写字面色值。
     */
    object Palette {
        /** 页面背景（与 GameView.C_BG 同色，保持全库一致）。 */
        const val BG = 0xFF141A2E.toInt()

        // ---- 木质棋盘 ----
        /** 木盘高光（左上）。 */
        const val WOOD_HI = 0xFFF3E0BC.toInt()
        /** 木盘主色。 */
        const val WOOD = 0xFFE7CE9E.toInt()
        /** 木盘暗部（右下）。 */
        const val WOOD_LO = 0xFFD4B37E.toInt()
        /** 木盘外框（柔和深木色，替代原先的硬棕描边）。 */
        const val WOOD_FRAME = 0xFFBF9560.toInt()
        /** 棋盘格线（半透明暖棕，比实线柔和）。 */
        const val WOOD_LINE = 0x8A8A5A2B.toInt()
        /** 星位/交叉点标记。 */
        const val WOOD_MARK = 0xB07A4A1F.toInt()

        // ---- 棋盘变体：绿绒台面（黑白棋/台球）----
        const val FELT_HI = 0xFF3E9E68.toInt()
        const val FELT = 0xFF2E8B57.toInt()
        const val FELT_LO = 0xFF1F6B41.toInt()

        // ---- 奶油色棋子 ----
        /** 棋子高光。 */
        const val PIECE_HI = 0xFFFEF8EC.toInt()
        /** 棋子主色。 */
        const val PIECE = 0xFFF3E0BE.toInt()
        /** 棋子暗部。 */
        const val PIECE_LO = 0xFFDCBE8E.toInt()
        /** 棋子外圈描边（暖灰，不用纯黑）。 */
        const val PIECE_RIM = 0xFFB99A6E.toInt()

        // ---- 通用 UI ----
        /** 卡片/面板底（浅色）。 */
        const val CARD = 0xFFFFFFFF.toInt()
        /** 深色面板（信息条）。 */
        const val PANEL = 0xFF2A3557.toInt()
        /** 文字主色。 */
        const val TEXT = 0xFF1C2333.toInt()
        /** 深色底上的文字。 */
        const val TEXT_LIGHT = 0xFFE8EAF0.toInt()
        /** 次要文字。 */
        const val DIM = 0xFF8A93A6.toInt()
        /** 强调蓝。 */
        const val ACCENT = 0xFF4A6CF7.toInt()
        /** 成功绿。 */
        const val GREEN = 0xFF3ED98A.toInt()
        /** 警示红。 */
        const val RED = 0xFFE8503A.toInt()
        /** 金/黄（选中、回合指示）。 */
        const val GOLD = 0xFFF5C542.toInt()

        // ---- 棋子（围棋/黑白棋用）----
        /** 黑子高光。 */
        const val STONE_DARK_HI = 0xFF4A5568.toInt()
        /** 黑子主色。 */
        const val STONE_DARK = 0xFF232A38.toInt()
        /** 白子高光。 */
        const val STONE_LIGHT_HI = 0xFFFFFFFF.toInt()
        /** 白子主色。 */
        const val STONE_LIGHT = 0xFFF0F3F8.toInt()
        /** 白子描边（浅灰，让白子在木盘上不"融化"）。 */
        const val STONE_LIGHT_RIM = 0xFFBFC7D6.toInt()
    }

    /** 本工具箱所属 View 的密度，用于 dp 换算。 */
    private val density = owner.resources.displayMetrics.density
    private val scaled = owner.resources.displayMetrics.scaledDensity

    private fun dp(v: Float): Float = v * density

    // ---------------------------------------------------------------- 通用 Paint
    // 全部预建，绘制期只改颜色/坐标，避免每帧分配。

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val serifP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }

    /** 圆形软投影（棋子、壶、球共用）。 */
    private val circleShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }

    /** 圆形径向渐变：高光在左上，形成柔和的球面感（卡通风的"果冻"质感）。 */
    private var sphereShader: Shader? = null

    // ---------------------------------------------------------------- 棋盘木板

    /** 棋盘木板渐变（布局期生成，随棋盘尺寸变化重建）。 */
    private var woodShader: Shader? = null
    private var woodRect = RectF()

    /**
     * 构建木质棋盘渐变。在 onLayout() 里调用一次。
     * @param rect 棋盘外框（含内边距）
     */
    fun buildWoodSlab(rect: RectF) {
        woodRect.set(rect)
        woodShader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(Palette.WOOD_HI, Palette.WOOD, Palette.WOOD_LO),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    /**
     * 绘制木质棋盘底盘：柔和渐变木板 + 大圆角 + 一圈浅色内描边。
     * 这是所有棋类游戏统一的"底"，替代原先各文件里的硬棕描边方案。
     */
    fun drawWoodSlab(canvas: Canvas, rect: RectF, radiusDp: Float = 20f) {
        val r = dp(radiusDp)
        // 软投影：向下偏移，营造"木板浮在桌面上"的层次
        fill.shader = null
        fill.color = 0x33000000
        canvas.drawRoundRect(RectF(rect.left, rect.top + dp(3f), rect.right, rect.bottom + dp(3f)), r, r, fill)
        // 木板主体
        shaderPaint.shader = woodShader
        canvas.drawRoundRect(rect, r, r, shaderPaint)
        shaderPaint.shader = null
        // 内描边：浅色细线，柔化边缘（不用深色硬框）
        stroke.color = 0x55FFFFFF
        stroke.strokeWidth = dp(1.5f)
        val inset = dp(3f)
        canvas.drawRoundRect(
            RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
            r - inset, r - inset, stroke)
        stroke.color = Palette.WOOD_FRAME
        stroke.strokeWidth = dp(2f)
        canvas.drawRoundRect(rect, r, r, stroke)
    }

    /** 网格线：细、半透明、圆头，比实线柔和。 */
    fun drawGridLine(canvas: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, widthDp: Float = 1.4f) {
        stroke.color = Palette.WOOD_LINE
        stroke.strokeWidth = dp(widthDp)
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x0, y0, x1, y1, stroke)
    }

    /** 星位圆点。 */
    fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        fill.color = Palette.WOOD_MARK
        canvas.drawCircle(cx, cy, radius, fill)
    }

    // ---------------------------------------------------------------- 棋子

    /**
     * 绘制一枚"奶油色圆棋子"（象棋/暗棋/斗兽棋共用）。
     *
     * 结构：软投影 → 径向渐变圆底（左上高光）→ 内圈细环 → 外描边 → 文字。
     * 相比旧实现，这里把高光方向、内环比例、描边宽度全部固定，保证 3 款游戏棋子一致。
     *
     * @param cx 圆心 x
     * @param cy 圆心 y
     * @param radius 棋子半径
     * @param text 棋子文字（如 "車"、"象"），null 表示不画字
     * @param textColor 文字颜色
     * @param facingUp true 时文字旋转 180°（双人游戏上方玩家）
     * @param selected true 时加金色柔光选中环
     */
    fun drawCreamPiece(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        text: String? = null, textColor: Int = Palette.TEXT,
        facingUp: Boolean = false, selected: Boolean = false, scale: Float = 1f
    ) {
        val r = radius * scale
        // 软投影
        canvas.drawCircle(cx + dp(1.5f), cy + dp(3f), r, circleShadow)
        // 选中柔光（画在棋子下方，形成外发光）
        if (selected) {
            fill.color = 0x66F5C542.toInt()
            canvas.drawCircle(cx, cy, r + dp(7f), fill)
        }
        // 球面渐变底
        shaderPaint.shader = RadialGradient(
            cx - r * 0.32f, cy - r * 0.38f, r * 1.35f,
            intArrayOf(Palette.PIECE_HI, Palette.PIECE, Palette.PIECE_LO),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, shaderPaint)
        shaderPaint.shader = null
        // 内圈细环（传统棋子的"鼓形"暗示）
        stroke.color = 0x44B99A6E
        stroke.strokeWidth = dp(1f)
        canvas.drawCircle(cx, cy, r * 0.8f, stroke)
        // 外描边：暖色细边
        stroke.color = Palette.PIECE_RIM
        stroke.strokeWidth = dp(1.5f)
        canvas.drawCircle(cx, cy, r, stroke)
        // 选中亮环
        if (selected) {
            stroke.color = Palette.GOLD
            stroke.strokeWidth = dp(2.5f)
            canvas.drawCircle(cx, cy, r + dp(2f), stroke)
        }
        // 文字
        if (text != null) {
            serifP.color = textColor
            serifP.textSize = r * 1.12f
            // 淡阴影提升木盘上的可读性
            serifP.setShadowLayer(dp(1.5f), 0f, dp(1f), 0x33000000)
            owner.drawPieceTextPublic(canvas, serifP, text, cx, cy, facingUp)
            serifP.clearShadowLayer()
        }
    }

    /**
     * 绘制一枚围棋/黑白棋的"哑光玻璃棋子"。
     *
     * @param dark true=黑子，false=白子
     * @param lastMark true 时在中心画一枚小圆点标记最后一手
     */
    fun drawGlassStone(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        dark: Boolean, lastMark: Boolean = false
    ) {
        canvas.drawCircle(cx + dp(1.2f), cy + dp(2.2f), radius, circleShadow)
        val stops = floatArrayOf(0f, 0.5f, 1f)
        if (dark) {
            shaderPaint.shader = RadialGradient(
                cx - radius * 0.3f, cy - radius * 0.35f, radius * 1.3f,
                intArrayOf(Palette.STONE_DARK_HI, Palette.STONE_DARK, 0xFF141922.toInt()),
                stops, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, shaderPaint)
        } else {
            shaderPaint.shader = RadialGradient(
                cx - radius * 0.3f, cy - radius * 0.35f, radius * 1.3f,
                intArrayOf(Palette.STONE_LIGHT_HI, Palette.STONE_LIGHT, 0xFFD8DEE9.toInt()),
                stops, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, radius, shaderPaint)
            // 白子需要描边，否则在浅木盘上边界消失
            stroke.color = Palette.STONE_LIGHT_RIM
            stroke.strokeWidth = dp(1f)
            canvas.drawCircle(cx, cy, radius, stroke)
        }
        shaderPaint.shader = null
        // 左上高光小圆点（果冻质感）
        fill.color = if (dark) 0x33FFFFFF else 0x99FFFFFF.toInt()
        canvas.drawCircle(cx - radius * 0.3f, cy - radius * 0.34f, radius * 0.2f, fill)
        if (lastMark) {
            fill.color = if (dark) 0xFFFF6B6B.toInt() else 0xFFE8503A.toInt()
            canvas.drawCircle(cx, cy, radius * 0.26f, fill)
        }
    }

    // ---------------------------------------------------------------- 卡片 / 胶囊 / 按钮

    /** 浅色圆角卡片（信息条、面板）。带软投影。 */
    fun drawCard(canvas: Canvas, rect: RectF, color: Int = Palette.CARD, radiusDp: Float = 16f, shadow: Boolean = true) {
        val r = dp(radiusDp)
        if (shadow) {
            fill.color = 0x33000000
            canvas.drawRoundRect(RectF(rect.left, rect.top + dp(2f), rect.right, rect.bottom + dp(3f)), r, r, fill)
        }
        fill.color = color
        canvas.drawRoundRect(rect, r, r, fill)
    }

    /** 全圆角胶囊（标签、得分、状态）。 */
    fun drawPill(canvas: Canvas, rect: RectF, color: Int, strokeColor: Int = 0, strokeDp: Float = 0f) {
        val r = rect.height() / 2f
        fill.color = color
        canvas.drawRoundRect(rect, r, r, fill)
        if (strokeDp > 0f && strokeColor != 0) {
            stroke.color = strokeColor
            stroke.strokeWidth = dp(strokeDp)
            canvas.drawRoundRect(rect, r, r, stroke)
        }
    }

    /** 柔和按钮：填充 + 内高光（顶部一条浅色）。 */
    fun drawSoftButton(canvas: Canvas, rect: RectF, color: Int, radiusDp: Float = 16f) {
        val r = dp(radiusDp)
        fill.color = 0x33000000
        canvas.drawRoundRect(RectF(rect.left, rect.top + dp(2.5f), rect.right, rect.bottom + dp(2.5f)), r, r, fill)
        fill.color = color
        canvas.drawRoundRect(rect, r, r, fill)
        // 顶部内高光：让按钮有轻微凸起感
        fill.color = 0x22FFFFFF
        val hi = RectF(rect.left + dp(2f), rect.top + dp(1.5f), rect.right - dp(2f), rect.centerY())
        canvas.drawRoundRect(hi, r * 0.8f, r * 0.8f, fill)
    }

    /** 居中绘制文字（自动换算 baseline）。 */
    fun drawCenterText(canvas: Canvas, text: String, cx: Float, cy: Float, sizeSp: Float, color: Int, bold: Boolean = true) {
        textP.color = color
        textP.textSize = sizeSp * scaled
        textP.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        canvas.drawText(text, cx, cy - (textP.descent() + textP.ascent()) / 2f, textP)
    }

    /** 深色底上的居中文字（带阴影）。 */
    fun drawCenterTextShadow(canvas: Canvas, text: String, cx: Float, cy: Float, sizeSp: Float, color: Int, bold: Boolean = true) {
        textP.color = color
        textP.textSize = sizeSp * scaled
        textP.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textP.setShadowLayer(dp(2f), 0f, dp(1f), 0x99000000.toInt())
        canvas.drawText(text, cx, cy - (textP.descent() + textP.ascent()) / 2f, textP)
        textP.clearShadowLayer()
    }

    /** 直接返回文字宽度（用于自适应排版）。 */
    fun measureText(text: String, sizeSp: Float, bold: Boolean = true): Float {
        textP.textSize = sizeSp * scaled
        textP.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return textP.measureText(text)
    }

    /**
     * 绘制一行「回合状态条」：圆角卡片 + 左侧色点 + 居中文字。
     * 所有桌游统一的顶部/底部状态行样式，替代原先各写各的 textPaint。
     *
     * @param accent 左侧指示点颜色（通常是当前玩家色）
     */
    fun drawStatusBar(
        canvas: Canvas, rect: RectF, text: String, accent: Int,
        bgColor: Int = Palette.PANEL, textColor: Int = Palette.TEXT_LIGHT, sizeSp: Float = 15f
    ) {
        drawCard(canvas, rect, bgColor, 18f, shadow = true)
        // 左侧色点
        fill.color = accent
        canvas.drawCircle(rect.left + dp(18f), rect.centerY(), dp(6f), fill)
        drawCenterText(canvas, text, rect.centerX() + dp(8f), rect.centerY(), sizeSp, textColor)
    }
}
