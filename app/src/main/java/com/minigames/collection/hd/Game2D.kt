package com.minigames.collection.hd

import com.badlogic.gdx.Game
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

/**
 * libGDX 游戏基类：统一背景、字体、渲染器管理。
 * 子类实现 create() 并渲染到 batch/shapes。
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

    override fun create() {
        screenW = com.badlogic.gdx.Gdx.graphics.width.toFloat()
        screenH = com.badlogic.gdx.Gdx.graphics.height.toFloat()
        batch = SpriteBatch()
        shapes = ShapeRenderer()
        buildFonts()
        onCreate()
    }

    private fun buildFonts() {
        // 使用系统字体（避免打包大字体文件）；ttc 取第一个 face，回退到 DroidSansFallback
        val candidates = listOf(
            "/system/fonts/NotoSansCJK-Regular.ttc",
            "/system/fonts/DroidSansFallback.ttf",
            "/system/fonts/NotoSansCJKsc-Regular.otf"
        )
        var gen: FreeTypeFontGenerator? = null
        for (path in candidates) {
            val fh = com.badlogic.gdx.Gdx.files.absolute(path)
            if (fh.exists()) {
                gen = FreeTypeFontGenerator(fh)
                break
            }
        }
        if (gen == null) {
            font = BitmapFont()
            fontBig = BitmapFont()
            return
        }
        val generator = gen
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