package com.minigames.collection.hd

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/**
 * 2048 HD（精品重做版）：经典浅色主题、标准配色、
 * 径向渐变方块、滑动/回弹/缩放动画、得分飘字、自绘结算面板。
 */
class Lgdx2048 : Game2D("2048_hd") {

    companion object {
        private const val SIZE = 4
        private const val MOVE_TIME = 0.1f
        private const val POP_TIME = 0.14f
        private const val SPAWN_TIME = 0.14f
        private const val FLOAT_TIME = 0.7f
        private const val CELL_R = 18
    }

    // ---- 动画 ----
    private class MoveAnim(val fromIdx: Int, val toIdx: Int, var t: Float)
    private class PopAnim(val idx: Int, var t: Float)
    private class SpawnAnim(val idx: Int, var t: Float)
    private class FloatText(val x: Float, val y: Float, val text: String, var t: Float, val color: Color)

    private val board = IntArray(SIZE * SIZE)
    private val moves = ArrayList<MoveAnim>()
    private val pops = ArrayList<PopAnim>()
    private val spawns = ArrayList<SpawnAnim>()
    private val floats = ArrayList<FloatText>()
    private val particles = ArrayList<Particle2>()

    private class Particle2(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Color)

    // ---- 经典配色 ----
    private val bgTop = Color.valueOf("FBF8F1")
    private val bgBottom = Color.valueOf("EEE9DE")
    private val boardColor = Color.valueOf("BBADA0")
    private val cellColor = Color.valueOf("CDC1B4")
    private val textDark = Color.valueOf("776E65")
    private val textLight = Color.valueOf("F9F6F2")
    private val titleColor = Color.valueOf("776E65")
    private val scoreCard = Color.valueOf("BBADA0")
    private val newBtn = Color.valueOf("8F7A66")

    private val tileColors = mapOf(
        2 to Color.valueOf("EEE4DA"), 4 to Color.valueOf("EDE0C8"),
        8 to Color.valueOf("F2B179"), 16 to Color.valueOf("F59563"),
        32 to Color.valueOf("F67C5F"), 64 to Color.valueOf("F65E3B"),
        128 to Color.valueOf("EDCF72"), 256 to Color.valueOf("EDCC61"),
        512 to Color.valueOf("EDC850"), 1024 to Color.valueOf("EDC53F"),
        2048 to Color.valueOf("EDC22E")
    )
    private val tileTexts = mapOf(
        2 to textDark, 4 to textDark,
        8 to textLight, 16 to textLight, 32 to textLight, 64 to textLight,
        128 to textLight, 256 to textLight, 512 to textLight, 1024 to textLight, 2048 to textLight
    )
    private val tiles = HashMap<Int, Texture>()
    private lateinit var cellTex: Texture
    private lateinit var boardTex: Texture
    private lateinit var panelTex: Texture
    private lateinit var btnTex: Texture

    private var cellPx = 0
    private var gapPx = 0
    private var boardSize = 0f
    private var boardX = 0f
    private var boardY = 0f
    private var score = 0
    private var displayScore = 0
    private var best = 0
    private var over = false
    private var won = false
    private var endNotified = false
    private var time = 0f
    private val layout = GlyphLayout()
    private val scoreRect = com.badlogic.gdx.math.Rectangle()
    private val bestRect = com.badlogic.gdx.math.Rectangle()
    private val btnRect = com.badlogic.gdx.math.Rectangle()

    override val accent: Color get() = Color.valueOf("F59563")

    override fun onCreate() {
        Gdx.app.log("2048hd", "create " + screenW + "x" + screenH)
        // 预生成方块纹理
        for ((v, c) in tileColors) {
            tiles[v] = Gfx.tileTexture(128, CELL_R, c, 0.20f, 0.24f)
        }
        cellTex = Gfx.tileTexture(128, CELL_R, cellColor, 0.04f, 0.10f)
        boardTex = Gfx.roundedRect(128, 22, 0.06f, 0.18f)
        panelTex = Gfx.roundedRect(128, 20, 0.05f, 0.15f)
        btnTex = Gfx.roundedRect(128, 24, 0.12f, 0.22f)
        best = max(bestScore, 0)
        layoutBoard()
        setupInput()
        reset()
    }

    private fun layoutBoard() {
        // 顶部 UI 行（标题 + 得分卡 ×2 + 新游戏按钮）固定在屏幕顶部安全区下方，
        // 棋盘占据剩余高度——竖屏、横屏都完整可见，不随方向溢出。
        val topY = screenH - dp(56).toFloat() - dp(44).toFloat()
        val availH = topY - dp(14).toFloat() - dp(20).toFloat()
        val target = min(screenW - dp(16).toFloat(), availH)
        cellPx = (target / 4.55f).toInt().coerceAtLeast(40)
        gapPx = (cellPx * 0.11f).toInt().coerceAtLeast(6)
        boardSize = (cellPx * 4 + gapPx * 5).toFloat()
        boardX = (screenW - boardSize) / 2f
        val boardTop = topY - dp(14).toFloat()
        boardY = boardTop - boardSize
        // 顶部 UI：右下 [新游戏][得分][最佳]，标题左对齐棋盘
        val cardW = dp(92).toFloat()
        val cardH = dp(44).toFloat()
        val btnW = dp(76).toFloat()
        val gap = dp(8).toFloat()
        btnRect.set(boardX + boardSize - btnW, topY, btnW, cardH)
        scoreRect.set(btnRect.x - gap - cardW, topY, cardW, cardH)
        bestRect.set(scoreRect.x - gap - cardW, topY, cardW, cardH)
    }

    override fun onResize() {
        layoutBoard()
    }

    private fun setupInput() {
        Gdx.input.inputProcessor = object : InputAdapter() {
            private var downX = 0f
            private var downY = 0f
            private var downT = 0L
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                downX = screenX.toFloat()
                downY = screenY.toFloat()
                downT = System.nanoTime()
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val tapX = screenX.toFloat()
                val tapY = screenH - screenY
                val dx = screenX - downX
                val dy = screenY - downY
                val tap = Math.abs(dx) < 30 && Math.abs(dy) < 30
                // 结果面板"再来一局"
                if (over && moves.isEmpty() && resultBtn.contains(tapX, tapY)) {
                    reset()
                    playClick()
                    return true
                }
                // 顶部"新游戏"按钮
                if (btnRect.contains(tapX, tapY) && tap) {
                    reset()
                    playClick()
                    return true
                }
                if (over && moves.isEmpty()) return true
                if (Math.abs(dx) > 35 || Math.abs(dy) > 35) {
                    if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > 0) move(1, 0) else move(-1, 0)
                    } else {
                        // 屏幕 Y 向下：下滑 dy>0 → 方块向下
                        if (dy > 0) move(0, -1) else move(0, 1)
                    }
                }
                return true
            }
        }
    }

    private fun playClick() {
        sounds?.play(SoundManager.CLICK)
    }

    private fun reset() {
        for (i in board.indices) board[i] = 0
        moves.clear()
        pops.clear()
        spawns.clear()
        floats.clear()
        particles.clear()
        score = 0
        displayScore = 0
        over = false
        won = false
        endNotified = false
        best = max(bestScore, 0)
        spawn()
        spawn()
    }

    private fun spawn() {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return
        val idx = empty.random()
        board[idx] = if (Math.random() < 0.9) 2 else 4
        spawns.add(SpawnAnim(idx, 0f))
    }

    private fun move(dx: Int, dy: Int) {
        if (over || moves.isNotEmpty()) return
        var moved = false
        var gained = 0
        val newMoves = ArrayList<MoveAnim>()
        val mergePops = ArrayList<PopAnim>()
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
            // 动画：源 → 目标
            val used = BooleanArray(SIZE)
            for (j in 0 until SIZE) {
                val v = out[j]
                if (v == 0) continue
                var found = -1
                for (s in 0 until line.size) {
                    if (!used[s] && line[s] == v) {
                        found = s
                        break
                    }
                }
                if (found >= 0) {
                    used[found] = true
                    newMoves.add(MoveAnim(seq[found], seq[j], 0f))
                } else {
                    mergePops.add(PopAnim(seq[j], 0f))
                    spawnMergeParticles(idxPos(seq[j]), v)
                    floats.add(FloatText(idxPos(seq[j]).x, idxPos(seq[j]).y + cellPx * 0.45f, "+" + (v / 2), 0f, Color(textLight)))
                }
            }
        }
        if (!moved) return
        sounds?.play(SoundManager.MOVE)
        if (gained > 0) sounds?.play(SoundManager.EAT)
        score += gained
        if (score > best) best = score
        val prevWon = won
        if (!won && board.any { it == 2048 }) won = true
        if (won && !prevWon) {
            sounds?.play(SoundManager.WIN)
            haptic?.buzz(50)
        }
        moves.addAll(newMoves)
        pops.addAll(mergePops)
        spawn()
        if (noMoves()) {
            over = true
            notifyEnd(false)
        } else if (won) {
            notifyEnd(true)
        }
    }

    private fun idxPos(idx: Int): Vector2 = Vector2(
        boardX + (idx % SIZE) * (cellPx + gapPx) + gapPx + cellPx / 2f,
        boardY + (idx / SIZE) * (cellPx + gapPx) + gapPx + cellPx / 2f
    )

    private fun spawnMergeParticles(pos: Vector2, value: Int) {
        val color = tileColors[value] ?: Color.WHITE
        repeat(12) {
            val ang = Math.random() * Math.PI * 2
            val speed = 90f + Math.random().toFloat() * 200f
            particles.add(Particle2(
                pos.x, pos.y,
                Math.cos(ang).toFloat() * speed,
                Math.sin(ang).toFloat() * speed,
                0.4f + Math.random().toFloat() * 0.25f,
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
        val dt = min(Gdx.graphics.deltaTime, 0.05f)
        time += dt
        // 动画推进
        val mit = moves.iterator()
        while (mit.hasNext()) {
            val m = mit.next()
            m.t += dt / MOVE_TIME
            if (m.t >= 1f) mit.remove()
        }
        val pit = pops.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.t += dt / POP_TIME
            if (p.t >= 1f) pit.remove()
        }
        val sit = spawns.iterator()
        while (sit.hasNext()) {
            val s = sit.next()
            s.t += dt / SPAWN_TIME
            if (s.t >= 1f) sit.remove()
        }
        val fit = floats.iterator()
        while (fit.hasNext()) {
            val f = fit.next()
            f.t += dt / FLOAT_TIME
            if (f.t >= 1f) fit.remove()
        }
        val pit2 = particles.iterator()
        while (pit2.hasNext()) {
            val p = pit2.next()
            p.life -= dt
            if (p.life <= 0f) {
                pit2.remove()
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.93f
            p.vy *= 0.93f
        }

        Gdx.gl.glClearColor(0.98f, 0.96f, 0.93f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // 背景垂直渐变
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.rect(0f, 0f, screenW, screenH, bgTop, bgTop, bgBottom, bgBottom)
        shapes.end()

        // 得分数字滚动动画
        if (displayScore != score) {
            displayScore += ((score - displayScore) * min(1f, dt * 8f)).toInt()
            if (Math.abs(score - displayScore) < 2) displayScore = score
        }

        // ---- 顶部 UI：标题 + 新游戏按钮 + 得分卡片 ----
        batch.begin()
        fontBig.color.set(titleColor)
        fontBig.draw(batch, "2048", boardX, scoreRect.y + scoreRect.height / 2f + fontBig.capHeight / 2f)
        drawRounded(batch, btnRect, newBtn, dp(12).toFloat())
        font.color.set(Color.WHITE)
        layout.setText(font, "新游戏")
        font.draw(batch, layout, btnRect.x + (btnRect.width - layout.width) / 2f,
            btnRect.y + (btnRect.height + layout.height) / 2f)
        batch.end()

        drawScoreCard(batch, scoreRect, "得分", displayScore)
        drawScoreCard(batch, bestRect, "最佳", best)

        // ---- 棋盘 ----
        batch.begin()
        batch.setColor(Color.WHITE)
        batch.draw(TextureRegion(boardTex), boardX - gapPx, boardY - gapPx, boardSize + gapPx * 2, boardSize + gapPx * 2)
        // 空格子
        for (i in board.indices) {
            val v = board[i]
            if (v != 0 && moves.none { it.toIdx == i }) continue
            val p = idxPos(i)
            batch.draw(cellTex, p.x - cellPx / 2f, p.y - cellPx / 2f, cellPx.toFloat(), cellPx.toFloat())
        }
        // 移动中的方块
        for (m in moves) {
            val from = idxPos(m.fromIdx)
            val to = idxPos(m.toIdx)
            val t = easeInOutCubic(m.t.coerceIn(0f, 1f))
            val x = from.x + (to.x - from.x) * t
            val y = from.y + (to.y - from.y) * t
            drawTile(batch, x - cellPx / 2f, y - cellPx / 2f, board[m.toIdx], 1f)
        }
        // 静止方块 + 回弹/出生动画
        for (i in board.indices) {
            val v = board[i]
            if (v == 0) continue
            if (moves.any { it.toIdx == i }) continue
            var scale = 1f
            val pop = pops.firstOrNull { it.idx == i }
            if (pop != null) {
                val t = pop.t.coerceIn(0f, 1f)
                scale = if (t < 0.5f) 1f + 0.35f * (t / 0.5f) else 1f + 0.35f * (1f - (t - 0.5f) / 0.5f)
            }
            val sp = spawns.firstOrNull { it.idx == i }
            if (sp != null) {
                scale *= easeOutBack(sp.t.coerceIn(0f, 1f))
            }
            val p = idxPos(i)
            val s = cellPx * scale
            drawTile(batch, p.x - s / 2f, p.y - s / 2f, v, scale)
        }
        // 粒子
        for (p in particles) {
            val a = (p.life / 0.4f).coerceIn(0f, 1f)
            batch.setColor(p.color.r, p.color.g, p.color.b, a)
            val r = cellPx * 0.09f * (1.6f - p.life / 0.4f)
            batch.draw(cellTex, p.x - r, p.y - r, r * 2, r * 2)
        }
        batch.setColor(Color.WHITE)
        batch.end()

        // 得分飘字（在 batch 上层）
        if (floats.isNotEmpty()) {
            batch.begin()
            for (f in floats) {
                val t = f.t.coerceIn(0f, 1f)
                val a = 1f - t
                font.color.set(f.color.r, f.color.g, f.color.b, a)
                layout.setText(font, f.text)
                font.draw(batch, layout, f.x - layout.width / 2f, f.y + t * dp(34).toFloat())
            }
            font.color.set(1f, 1f, 1f, 1f)
            batch.end()
        }

        // 2048 方块脉冲（胜利后）
        if (won && !over) {
            val idx = board.indexOf(2048)
            if (idx >= 0) {
                val pulse = (Math.sin(time * 5.0) * 0.5 + 0.5).toFloat()
                val p = idxPos(idx)
                shapes.begin(ShapeRenderer.ShapeType.Line)
                shapes.setColor(1f, 0.85f, 0.3f, 0.4f + 0.5f * pulse)
                shapes.circle(p.x, p.y, cellPx * (0.72f + 0.08f * pulse), 40)
                shapes.end()
            }
        }

        // 游戏结束遮罩 + 面板
        if (over && moves.isEmpty()) {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            shapes.setColor(0f, 0f, 0f, 0.45f)
            shapes.rect(0f, 0f, screenW, screenH)
            shapes.end()
            drawResultPanel()
        }
        if (isUserPaused) drawPauseOverlay()
    }

    private fun drawScoreCard(batch: SpriteBatch, rect: com.badlogic.gdx.math.Rectangle, label: String, value: Int) {
        batch.begin()
        batch.setColor(scoreCard)
        batch.draw(panelTex, rect.x, rect.y, rect.width, rect.height)
        font.color.set(Color.valueOf("EEE4DA"))
        layout.setText(font, label)
        font.draw(batch, layout, rect.x + (rect.width - layout.width) / 2f, rect.y + rect.height - dp(6).toFloat())
        font.color.set(Color.WHITE)
        layout.setText(font, value.toString())
        font.draw(batch, layout, rect.x + (rect.width - layout.width) / 2f, rect.y + dp(12).toFloat())
        batch.end()
    }

    private fun drawRounded(batch: SpriteBatch, rect: com.badlogic.gdx.math.Rectangle, color: Color, radius: Float) {
        batch.setColor(color)
        batch.draw(btnTex, rect.x, rect.y, rect.width, rect.height)
        batch.setColor(Color.WHITE)
    }

    private fun drawTile(batch: SpriteBatch, x: Float, y: Float, value: Int, scale: Float) {
        val tex = tiles[value] ?: tiles[2]!!
        val s = cellPx * scale
        batch.setColor(Color.WHITE)
        batch.draw(tex, x, y, s, s)
        // 数字
        val textColor = tileTexts[value] ?: textLight
        font.color.set(textColor)
        layout.setText(font, value.toString())
        font.draw(batch, layout, x + (s - layout.width) / 2f, y + (s + layout.height) / 2f - 2f)
        font.color.set(1f, 1f, 1f, 1f)
    }

    private fun drawResultPanel() {
        val pw = min(screenW * 0.72f, dp(300).toFloat())
        val ph = dp(170).toFloat()
        val px = (screenW - pw) / 2f
        val py = (screenH - ph) / 2f + dp(30).toFloat()
        batch.begin()
        batch.setColor(Color.valueOf("FAF8EF"))
        batch.draw(panelTex, px, py, pw, ph)
        // 标题（emoji 🎉 不在子集字体里，用文字）
        fontBig.color.set(textDark)
        layout.setText(fontBig, if (won) "你赢了！" else "游戏结束")
        fontBig.draw(batch, layout, px + (pw - layout.width) / 2f, py + ph - dp(26).toFloat())
        // 得分
        font.color.set(textDark)
        layout.setText(font, "本局得分 " + score + " · 最佳 " + best)
        font.draw(batch, layout, px + (pw - layout.width) / 2f, py + ph - dp(64).toFloat())
        // 按钮
        val bw = dp(120).toFloat()
        val bh = dp(44).toFloat()
        val bx = px + (pw - bw) / 2f
        val by = py + dp(28).toFloat()
        batch.setColor(newBtn)
        batch.draw(btnTex, bx, by, bw, bh)
        font.color.set(Color.WHITE)
        layout.setText(font, "再来一局")
        font.draw(batch, layout, bx + (bw - layout.width) / 2f, by + (bh + layout.height) / 2f)
        batch.end()
        // 记录按钮位置用于点击
        resultBtn.set(bx, by, bw, bh)
    }

    private val resultBtn = com.badlogic.gdx.math.Rectangle()

    private fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - Math.pow((-2f * t + 2f).toDouble(), 3.0).toFloat() / 2f

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    private fun max(a: Int, b: Int): Int = if (a > b) a else b

    private fun dp(v: Int): Float = v * Gdx.graphics.getDensity()

    override fun restart() {
        reset()
    }

    private fun notifyEnd(wonFlag: Boolean) {
        if (endNotified) return
        endNotified = true
        if (wonFlag) {
            sounds?.play(SoundManager.WIN)
            haptic?.buzz(60)
        } else {
            sounds?.play(SoundManager.LOSE)
            haptic?.buzz(90)
        }
        onEnd?.invoke(score, wonFlag)
        if (score > bestScore) {
            bestScore = score
            onNewBest?.invoke(score)
        }
    }

    override fun dispose() {
        for (t in tiles.values) t.dispose()
        cellTex.dispose()
        boardTex.dispose()
        panelTex.dispose()
        btnTex.dispose()
        super.dispose()
    }
}