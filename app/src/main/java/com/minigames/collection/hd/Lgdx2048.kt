package com.minigames.collection.hd

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2

/**
 * 2048 HD：libGDX 渲染 —— 渐变方块、滑动补间动画、合并粒子、圆角 UI。
 */
class Lgdx2048 : Game2D("2048_hd") {

    companion object {
        private const val SIZE = 4
        private const val ANIM_TIME = 0.12f
        private const val SLIDE = 200f
    }

    private data class Moving(val fromIdx: Int, val toIdx: Int, val value: Int, var t: Float, val merged: Boolean)
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Color)

    private val board = IntArray(SIZE * SIZE)
    private val moving = ArrayList<Moving>()
    private val particles = ArrayList<Particle>()

    private lateinit var cellTex: Texture       // 白底圆角方块（可染色）
    private lateinit var boardTex: Texture
    private var cellPx = 0
    private var gapPx = 0
    private var boardX = 0f
    private var boardY = 0f
    private var score = 0
    private var over = false
    private var won = false

    private val tileColors = mapOf(
        2 to Color.valueOf("ECEFF5"), 4 to Color.valueOf("D7DEEB"),
        8 to Color.valueOf("F5B971"), 16 to Color.valueOf("F59A52"),
        32 to Color.valueOf("F57C4A"), 64 to Color.valueOf("F0603E"),
        128 to Color.valueOf("E8C94A"), 256 to Color.valueOf("E8B84A"),
        512 to Color.valueOf("E8A23A"), 1024 to Color.valueOf("E88C2E"),
        2048 to Color.valueOf("F0562E")
    )

    override val accent: Color get() = Color.valueOf("F59A52")

    override fun onCreate() {
        com.badlogic.gdx.Gdx.app.log("2048hd", "screen " + screenW + "x" + screenH + " fontOK=" + (font.capHeight > 0))
        cellTex = Gfx.roundedRect(64, 14, 0.5f, 0.28f)
        boardTex = Gfx.roundedRect(64, 16, 0.15f, 0.3f)

        cellPx = (screenW / 4.2f).toInt()
        gapPx = (screenW * 0.02f).toInt()
        boardX = (screenW - cellPx * 4 - gapPx * 5) / 2f
        boardY = (screenH - cellPx * 4 - gapPx * 5) / 2f + screenH * 0.05f

        Gdx.input.inputProcessor = object : InputAdapter() {
            private var downX = 0f
            private var downY = 0f
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                downX = screenX.toFloat()
                downY = screenY.toFloat()
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val dx = screenX - downX
                val dy = screenY - downY
                if (Math.abs(dx) > 40 || Math.abs(dy) > 40) {
                    if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > 0) move(1, 0) else move(-1, 0)
                    } else {
                        // 屏幕 Y 轴向下：向下滑 dy>0 → 方块向下
                        if (dy > 0) move(0, -1) else move(0, 1)
                    }
                }
                return true
            }
        }

        reset()
    }

    private fun reset() {
        for (i in board.indices) board[i] = 0
        moving.clear()
        particles.clear()
        score = 0
        over = false
        won = false
        spawn()
        spawn()
    }

    private fun spawn() {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return
        val idx = empty.random()
        board[idx] = if (Math.random() < 0.9) 2 else 4
    }

    private fun move(dx: Int, dy: Int) {
        if (over || moving.isNotEmpty()) return
        var moved = false
        var gained = 0
        val newMoving = ArrayList<Moving>()
        for (i in 0 until SIZE) {
            val seq = if (dx != 0) {
                (0 until SIZE).map { j -> if (dx > 0) i * SIZE + (SIZE - 1 - j) else i * SIZE + j }
            } else {
                (0 until SIZE).map { j -> if (dy > 0) (SIZE - 1 - j) * SIZE + i else j * SIZE + i }
            }
            val line = seq.map { board[it] }.filter { it != 0 }
            val out = ArrayList<Int>()
            var k = 0
            while (k < line.size) {
                if (k + 1 < line.size && line[k] == line[k + 1]) {
                    out.add(line[k] * 2)
                    gained += line[k] * 2
                    k += 2
                } else {
                    out.add(line[k])
                    k++
                }
            }
            while (out.size < SIZE) out.add(0)
            for (j in 0 until SIZE) {
                val target = seq[j]
                if (board[target] != out[j]) moved = true
                board[target] = out[j]
            }
            // 记录动画：source 位置 → target 位置
            var srcPos = 0
            val used = BooleanArray(SIZE)
            for (j in 0 until SIZE) {
                val v = out[j]
                if (v == 0) continue
                // 找到对应源（匹配值，未使用）
                var found = -1
                for (s in srcPos until line.size) {
                    if (!used[s] && line[s] == v) {
                        found = s
                        break
                    }
                }
                if (found >= 0) {
                    used[found] = true
                    newMoving.add(Moving(seq[found], seq[j], v, 0f, false))
                } else {
                    // 合并产生的方块：从目标位置原地出现（合并动画由粒子表现）
                    newMoving.add(Moving(seq[j], seq[j], v, 0f, true))
                    spawnMergeParticles(idxPos(seq[j]), v)
                }
            }
        }
        if (!moved) return
        score += gained
        if (!won && board.any { it == 2048 }) won = true
        moving.addAll(newMoving)
        spawn()
        if (noMoves()) over = true
    }

    private fun idxPos(idx: Int): Vector2 = Vector2(
        boardX + (idx % SIZE) * (cellPx + gapPx) + gapPx + cellPx / 2f,
        boardY + (idx / SIZE) * (cellPx + gapPx) + gapPx + cellPx / 2f
    )

    private fun spawnMergeParticles(pos: Vector2, value: Int) {
        val color = tileColors[value] ?: Color.WHITE
        repeat(14) {
            val ang = Math.random() * Math.PI * 2
            val speed = 60f + Math.random().toFloat() * 160f
            particles.add(Particle(
                pos.x, pos.y,
                Math.cos(ang).toFloat() * speed,
                Math.sin(ang).toFloat() * speed,
                0.35f + Math.random().toFloat() * 0.2f,
                Color(color)
            ))
        }
    }

    private fun noMoves(): Boolean {
        if (board.any { it == 0 }) return false
        for (i in 0 until SIZE) for (j in 0 until SIZE) {
            val v = board[i * SIZE + j]
            if (j + 1 < SIZE && board[i * SIZE + j + 1] == v) return false
            if (i + 1 < SIZE && board[(i + 1) * SIZE + j] == v) return false
        }
        return true
    }

    override fun render() {
        val dt = Math.min(Gdx.graphics.deltaTime, 0.05f)
        // 推进动画
        val it = moving.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.t += dt / ANIM_TIME
            if (m.t >= 1f) it.remove()
        }
        // 粒子
        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.life -= dt
            if (p.life <= 0f) {
                pit.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.94f
            p.vy *= 0.94f
        }

        Gdx.gl.glClearColor(0.05f, 0.07f, 0.12f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // 背景：顶点色渐变（GL 插值，四个角）
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.rect(0f, 0f, screenW, screenH,
            Color.valueOf("182038"), Color.valueOf("101523"),
            Color.valueOf("2A3A66"), Color.valueOf("1C2540"))
        shapes.end()

        batch.begin()
        // 标题与得分
        fontBig.color.set(1f, 1f, 1f, 1f)
        fontBig.draw(batch, "2048", 20f, screenH - 30f)
        font.color.set(0.7f, 0.75f, 0.9f, 1f)
        font.draw(batch, "得分 " + score, 20f, screenH - 90f)
        if (won && !over) {
            font.color.set(1f, 0.85f, 0.4f, 1f)
            font.draw(batch, "已合成 2048，继续！", screenW / 2f, screenH - 120f)
        }
        // 棋盘底板（白色纹理染色）
        batch.setColor(Color.valueOf("202941"))
        batch.draw(TextureRegion(boardTex), boardX - gapPx, boardY - gapPx, cellPx * 4f + gapPx * 6, cellPx * 4f + gapPx * 6)
        batch.end()

        // 方块：先画动画中的，再画静止的
        batch.begin()
        // 动画方块（插值位置）
        for (m in moving) {
            val from = idxPos(m.fromIdx)
            val to = idxPos(m.toIdx)
            val e = easeOut(m.t.coerceIn(0f, 1f))
            val x = from.x + (to.x - from.x) * e
            val y = from.y + (to.y - from.y) * e
            drawTile(batch, x - cellPx / 2f, y - cellPx / 2f, m.value, if (m.merged) 1.15f else 1f)
        }
        // 静止方块（跳过动画目标位，避免与动画方块重叠）
        for (i in board.indices) {
            val v = board[i]
            if (v == 0) continue
            if (moving.any { it.toIdx == i }) continue
            val p = idxPos(i)
            drawTile(batch, p.x - cellPx / 2f, p.y - cellPx / 2f, v, 1f)
        }
        // 粒子
        for (p in particles) {
            val a = (p.life / 0.45f).coerceIn(0f, 1f)
            batch.setColor(p.color.r, p.color.g, p.color.b, a * 0.9f)
            val r = cellPx * 0.10f * (1.4f - p.life / 0.45f)
            batch.draw(cellTex, p.x - r, p.y - r, r * 2, r * 2)
        }
        batch.setColor(Color.WHITE)
        batch.end()

        if (over && moving.isEmpty()) {
            fontBig.color.set(1f, 1f, 1f, 1f)
            batch.begin()
            fontBig.draw(batch, "游戏结束", screenW / 2f, screenH / 2f + 20f)
            font.draw(batch, "得分 " + score + " · 点击任意处重新开始", screenW / 2f, screenH / 2f - 30f)
            batch.end()
            if (Gdx.input.justTouched()) reset()
        }
    }

    private fun drawTile(batch: SpriteBatch, x: Float, y: Float, value: Int, scale: Float) {
        val color = tileColors[value] ?: Color.WHITE
        val s = cellPx * scale
        val cx = x + cellPx / 2f
        val cy = y + cellPx / 2f
        // 深色描边底
        batch.setColor(0.1f, 0.12f, 0.2f, 0.9f)
        batch.draw(cellTex, cx - s / 2f, cy - s / 2f, s, s)
        // 主色方块
        batch.setColor(color)
        batch.draw(cellTex, cx - s / 2f + 2f, cy - s / 2f + 2f, s - 4f, s - 4f)
        // 数字（居中）
        font.color.set(if (value <= 4) Color.valueOf("1C2333") else Color.WHITE)
        val text = value.toString()
        tileLayout.setText(font, text)
        font.draw(batch, tileLayout, cx - tileLayout.width / 2f, cy + tileLayout.height / 2f - 4f)
    }

    private val tileLayout = com.badlogic.gdx.graphics.g2d.GlyphLayout()

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

    override fun dispose() {
        cellTex.dispose()
        boardTex.dispose()
        super.dispose()
    }
}