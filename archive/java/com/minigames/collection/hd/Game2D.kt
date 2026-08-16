package com.minigames.collection.hd

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.minigames.collection.common.HapticManager
import com.minigames.collection.common.SoundManager

/**
 * libGDX 游戏基类：统一背景、字体、渲染器管理，以及由宿主注入的音效/震动/
 * 最高分与用户暂停状态。
 * 子类实现 onCreate()/onResize() 并渲染到 batch/shapes。
 */
abstract class Game2D(val gameId: String) : Game() {

    lateinit var batch: SpriteBatch
        private set
    lateinit var shapes: ShapeRenderer
        private set
    lateinit var font: BitmapFont
        private set
    lateinit var fontBig: BitmapFont
        private set
    var screenW = 0f
        private set
    var screenH = 0f
        private set

    /** 主题色 */
    open val accent: Color get() = Color.valueOf("4A6CF7")

    /** 宿主注入：音效 / 震动（受设置页开关控制）。 */
    var sounds: SoundManager? = null
    var haptic: HapticManager? = null

    /** 宿主注入：本游戏历史最高分；达到新纪录时回调 [onNewBest]。 */
    var bestScore: Int = 0
    var onNewBest: ((Int) -> Unit)? = null

    /** 宿主注入：本局结束（含胜利）时回调（score, won）。 */
    var onEnd: ((score: Int, won: Boolean) -> Unit)? = null

    /** 用户暂停（悬浮层 ⏸）：暂停时子类应停止推进逻辑并绘制暂停遮罩。 */
    private var userPaused = false

    fun setUserPaused(paused: Boolean) {
        userPaused = paused
    }

    val isUserPaused: Boolean get() = userPaused

    fun toggleUserPaused(): Boolean {
        userPaused = !userPaused
        return userPaused
    }

    /** 悬浮层 ↻：重开一局（子类按需覆盖）。 */
    open fun restart() {}

    override fun create() {
        screenW = Gdx.graphics.width.toFloat()
        screenH = Gdx.graphics.height.toFloat()
        batch = SpriteBatch()
        shapes = ShapeRenderer()
        buildFonts()
        onCreate()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        screenW = width.toFloat()
        screenH = height.toFloat()
        onResize()
    }

    /** 尺寸变化（旋转/窗口调整）时重算布局。 */
    protected open fun onResize() {}

    private fun buildFonts() {
        // 内置中文子集字体（assets/fonts/ui.ttf，由全部源码文案字符集生成）
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/ui.ttf"))
        val param = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = 40
            color = Color.WHITE
            borderWidth = 1.5f
            borderColor = Color.valueOf("1C2333")
            minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
            magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
        }
        font = generator.generateFont(param)
        val paramBig = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = 72
            color = Color.WHITE
            borderWidth = 2f
            borderColor = Color.valueOf("1C2333")
        }
        fontBig = generator.generateFont(paramBig)
        generator.dispose()
    }

    protected abstract fun onCreate()

    /** 半透明遮罩 + “已暂停”。子类在 render() 末尾、用户暂停时调用。 */
    protected fun drawPauseOverlay() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.setColor(0f, 0f, 0f, 0.55f)
        shapes.rect(0f, 0f, screenW, screenH)
        shapes.end()
        val layout = GlyphLayout(fontBig, "已暂停")
        batch.begin()
        fontBig.color.set(1f, 1f, 1f, 1f)
        fontBig.draw(batch, layout, (screenW - layout.width) / 2f, screenH / 2f + 40f)
        val sub = GlyphLayout(font, "点击右上角按钮继续")
        font.color.set(0.8f, 0.85f, 0.95f, 1f)
        font.draw(batch, sub, (screenW - sub.width) / 2f, screenH / 2f - 30f)
        batch.end()
    }

    override fun render() {
        super.render()
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        font.dispose()
        fontBig.dispose()
        super.dispose()
    }
}