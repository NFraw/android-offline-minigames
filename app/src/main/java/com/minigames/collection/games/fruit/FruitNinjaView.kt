package com.minigames.collection.games.fruit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.widget.Toast
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.ScoreStore
import com.minigames.collection.common.SoundManager
import kotlin.math.hypot
import kotlin.math.max

/**
 * 切水果（水果忍者）：滑动切割抛起的水果，不同水果不同分值；
 * 切中炸弹或漏掉水果扣命；每 30 秒进入下一阶段，水果更快更多、炸弹更频繁。
 */
class FruitNinjaView(context: Context) : GameView(context) {

    companion object {
        private data class FruitSpec(val emoji: String, val value: Int, val sizeMul: Float)

        private val FRUITS = listOf(
            FruitSpec("🍉", 20, 1.0f),
            FruitSpec("🍊", 15, 0.85f),
            FruitSpec("🍎", 15, 0.85f),
            FruitSpec("🍌", 10, 0.9f),
            FruitSpec("🍇", 10, 0.8f),
            FruitSpec("🥝", 10, 0.75f),
            FruitSpec("🍓", 5, 0.7f),
            FruitSpec("🍒", 5, 0.65f))
        private const val STAGE_LEN = 30f
        private const val MAX_LIVES = 3
    }

    private data class Fruit(
        val emoji: String, val value: Int, var x: Float, var y: Float,
        var vx: Float, var vy: Float, var r: Float,
        var cut: Boolean = false, val bomb: Boolean = false)

    private val fruits = ArrayList<Fruit>()
    private var grav = 0f // 重力加速度，随屏幕尺寸自适应
    private var spawnTimer = 0.6f
    private var score = 0
    private var lives = MAX_LIVES
    private var stage = 1
    private var stageTimer = 0f
    private var best = 0
    private var over = false
    private var lastX = 0f
    private var lastY = 0f
    private var hasLast = false
    private val slash = Path()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val slashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        strokeWidth = dp(6).toFloat()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFE8EAF0.toInt()
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val bombPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C2333.toInt() }
    private val bombStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A93A6.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onLayout(w: Int, h: Int) {
        // 重力随屏幕高度自适应：不同手机/平板、横竖屏下弧线比例一致
        grav = h * 2.2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                hasLast = true
                slash.rewind()
                slash.moveTo(lastX, lastY)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!hasLast) return
                slash.lineTo(event.x, event.y)
                val hit = ArrayList<Fruit>()
                for (f in fruits) {
                    if (f.cut) continue
                    if (lineHitsCircle(lastX, lastY, event.x, event.y, f.x, f.y, f.r * 1.1f)) hit.add(f)
                }
                for (f in hit) {
                    f.cut = true
                    if (f.bomb) {
                        lives--
                        playSound(SoundManager.LOSE)
                        Toast.makeText(context, "切到炸弹，扣一条命！", Toast.LENGTH_SHORT).show()
                        if (lives <= 0) {
                            endGame("切中炸弹，生命耗尽")
                            return
                        }
                    } else {
                        score += f.value
                        setScore(score)
                        playSound(SoundManager.EAT)
                    }
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                hasLast = false
                slash.rewind()
            }
        }
    }

    private fun lineHitsCircle(x1: Float, y1: Float, x2: Float, y2: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy)
        if (len < 1f) return hypot(cx - x1, cy - y1) < r
        val t = ((cx - x1) * dx + (cy - y1) * dy) / (len * len)
        val tx = x1 + t.coerceIn(0f, 1f) * dx
        val ty = y1 + t.coerceIn(0f, 1f) * dy
        return hypot(cx - tx, cy - ty) < r
    }

    private fun endGame(reason: String) {
        over = true
        playSound(SoundManager.LOSE)
        gameOver(GameResult(score, false, reason + "！得分 " + score + " · 最高 " + max(best, score) + " · 到达第 " + stage + " 阶段"))
    }

    override fun update(dt: Float) {
        if (over) return
        stageTimer += dt
        if (stageTimer >= STAGE_LEN) {
            stageTimer -= STAGE_LEN
            stage++
            playSound(SoundManager.MOVE)
            Toast.makeText(context, "进入第 " + stage + " 阶段：更快、更多、炸弹更密！", Toast.LENGTH_SHORT).show()
        }
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = spawnInterval()
            spawnBatch()
        }
        val it = fruits.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.vy += dt * grav
            f.x += f.vx * dt
            // 左右边界：不能飞出设备两侧，碰壁反弹
            if (f.x < f.r) { f.x = f.r; f.vx = -f.vx * 0.8f }
            if (f.x > width - f.r) { f.x = width - f.r; f.vx = -f.vx * 0.8f }
            f.y += f.vy * dt
            // 顶部边界：不能高于设备，触顶回落
            if (f.y < f.r) { f.y = f.r; f.vy = -f.vy * 0.5f }
            if (f.y > height * 1.15f) {
                it.remove()
                if (!f.cut && !f.bomb) {
                    lives--
                    playSound(SoundManager.CLICK)
                    if (lives <= 0) {
                        endGame("漏掉 3 个水果")
                        return
                    }
                }
            }
        }
        invalidate()
    }

    private fun spawnInterval(): Float =
        max(0.4f, 1.1f - stage * 0.1f) + Math.random().toFloat() * 0.35f

    private fun spawnBatch() {
        val count = if (stage >= 4 && Math.random() < 0.35f) 2 else 1
        repeat(count) { spawnOne() }
    }

    private fun spawnOne() {
        val bombChance = when {
            stage <= 1 -> 0f
            stage == 2 -> 0.12f
            stage == 3 -> 0.2f
            else -> 0.28f
        }
        val speedMul = 1f + (stage - 1) * 0.09f
        val x = width * (0.12f + Math.random().toFloat() * 0.76f)
        // 水平速度按屏宽自适应，保证不同尺寸屏幕下横向漂移比例一致
        val vx = (Math.random().toFloat() - 0.5f) * width * 0.5f * speedMul
        // 竖直初速度按屏高自适应：弧顶高度约为屏高 0.35~0.85 处，含阶段加速后触顶由边界回落
        val rise = height * (0.35f + Math.random().toFloat() * 0.5f) * speedMul
        val vy0 = -Math.sqrt((2f * grav * rise).toDouble()).toFloat()
        if (Math.random() < bombChance) {
            fruits.add(Fruit("💣", 0, x, height * 1.05f, vx, vy0, dp(38).toFloat(), bomb = true))
        } else {
            val spec = FRUITS.random()
            fruits.add(Fruit(spec.emoji, spec.value, x, height * 1.05f, vx, vy0, dp(28).toFloat() * spec.sizeMul))
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (f in fruits) {
            if (f.cut) continue
            if (f.bomb) {
                canvas.drawCircle(f.x, f.y, f.r, bombPaint)
                canvas.drawCircle(f.x, f.y, f.r, bombStroke)
                canvas.drawCircle(f.x - f.r * 0.3f, f.y - f.r * 0.3f, f.r * 0.18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF.toInt() })
            } else {
                emojiPaint.textSize = f.r * 2.2f
                canvas.drawText(f.emoji, f.x, f.y - (emojiPaint.ascent() + emojiPaint.descent()) / 2f, emojiPaint)
            }
        }
        if (!slash.isEmpty) {
            canvas.drawPath(slash, slashPaint)
        }
        // 顶部信息
        textPaint.textSize = sp(16f)
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("得分 " + score, width / 2f, dp(42).toFloat(), textPaint)
        textPaint.color = 0xFFFFD54F.toInt()
        canvas.drawText("最高 " + max(best, score) + (if (score > best) " 🎉" else ""), width / 2f, dp(82).toFloat(), textPaint)
        textPaint.color = 0xFFFF6B6B.toInt()
        canvas.drawText("❤ ".repeat(lives.coerceAtLeast(0)), width / 2f, dp(120).toFloat(), textPaint)
        textPaint.color = 0xFF3ED98A.toInt()
        canvas.drawText("第 " + stage + " 阶段", width / 2f, dp(158).toFloat(), textPaint)
        textPaint.color = 0xFF8A93A6.toInt()
        textPaint.textSize = sp(14f)
        canvas.drawText("滑动切割水果得分 · 避开 💣 · 漏掉水果或切到炸弹扣命", width / 2f, height - dp(26).toFloat(), textPaint)
    }

    override fun reset() {
        fruits.clear()
        spawnTimer = 0.6f
        score = 0
        lives = MAX_LIVES
        stage = 1
        stageTimer = 0f
        best = ScoreStore(context).best("fruit")
        over = false
        hasLast = false
        slash.rewind()
        setScore(0)
        start()
    }
}
