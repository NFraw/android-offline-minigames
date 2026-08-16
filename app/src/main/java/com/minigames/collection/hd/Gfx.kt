package com.minigames.collection.hd

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion

/** libGDX 纹理工具：运行时生成圆角渐变纹理（零图片资源）。 */
object Gfx {

    private fun lerp(a: Color, b: Color, t: Float): Color =
        Color(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, 1f)

    /**
     * 生成圆角矩形渐变纹理（白底可染色）。
     * @param radius 圆角半径（像素）
     * @param topHigh 顶部高光强度 0..1（白色叠加）
     * @param bottomDark 底部压暗强度 0..1
     */
    fun roundedRect(size: Int, radius: Int, topHigh: Float = 0.35f, bottomDark: Float = 0.25f): Texture {
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.setBlending(Pixmap.Blending.None)
        val r2 = radius * radius
        for (y in 0 until size) {
            val t = y / (size - 1f)
            var c = Color(1f, 1f, 1f, 1f)
            // 顶部高光 + 底部压暗
            val shade = (1f - bottomDark) + (topHigh + bottomDark) * (1f - t)
            c.r *= shade
            c.g *= shade
            c.b *= shade
            pm.setColor(c)
            for (x in 0 until size) {
                // 圆角：四角区域外的像素透明
                val inRadius = when {
                    x < radius && y < radius -> (x - radius) * (x - radius) + (y - radius) * (y - radius) <= r2
                    x >= size - radius && y < radius -> (x - (size - radius)) * (x - (size - radius)) + (y - radius) * (y - radius) <= r2
                    x < radius && y >= size - radius -> (x - radius) * (x - radius) + (y - (size - radius)) * (y - (size - radius)) <= r2
                    x >= size - radius && y >= size - radius -> (x - (size - radius)) * (x - (size - radius)) + (y - (size - radius)) * (y - (size - radius)) <= r2
                    else -> true
                }
                if (inRadius) pm.drawPixel(x, y) else pm.drawPixel(x, y, 0)
            }
        }
        val tex = Texture(pm)
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pm.dispose()
        return tex
    }

    /** 垂直渐变背景纹理。 */
    fun verticalGradient(w: Int, h: Int, top: Color, bottom: Color): Texture {
        val pm = Pixmap(w, h, Pixmap.Format.RGBA8888)
        pm.setBlending(Pixmap.Blending.None)
        for (y in 0 until h) {
            val t = y / (h - 1f)
            val c = lerp(top, bottom, t)
            pm.setColor(c)
            for (x in 0 until w) pm.drawPixel(x, y)
        }
        val tex = Texture(pm)
        pm.dispose()
        return tex
    }
}
