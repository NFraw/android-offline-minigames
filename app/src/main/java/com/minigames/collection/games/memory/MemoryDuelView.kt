package com.minigames.collection.games.memory

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.Toast
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/**
 * 记忆对决：双人轮流翻牌配对，配对成功继续，配错换人。
 * 共 4 关（4×4 → 8×8 → 10×10 → 16×16），分数累计，全部通关后分高者胜。
 * 卡片图案 = emoji × 颜色 组合（16×9 互质，共 144 种不同视觉），保证 16×16 的 128 对也互不相同。
 */
class MemoryDuelView(context: Context) : GameView(context) {

    companion object {
        private val EMOJIS = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
            "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔")
        private val COLORS = intArrayOf(
            0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF3949AB.toInt(),
            0xFF1E88E5.toInt(), 0xFF00897B.toInt(), 0xFF43A047.toInt(), 0xFFF4511E.toInt(),
            0xFFFDD835.toInt())
        private val LEVELS = intArrayOf(4, 8, 10, 16)
    }

    private var cards = IntArray(16)
    private var matched = BooleanArray(16)
    private var size = 4
    private var flipA = -1
    private var flipB = -1
    private var lockTimer = 0f
    private var turn = 0
    private var s1 = 0
    private var s2 = 0
    private var score = 0
    private var level = 0
    private var finished = false

    private var lastW = 0
    private var lastH = 0
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var panelW = 0f

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val faceDownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val faceUpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFE8EAF0.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        lastW = w
        lastH = h
        recomputeLayout()
        reset()
    }

    /** 横屏布局：棋盘居中，玩家 1 信息在左侧、玩家 2 在右侧（面板竖排）。 */
    private fun recomputeLayout() {
        panelW = dp(100).toFloat()
        val availW = lastW - panelW * 2
        val side = min(availW, lastH.toFloat()) * 0.94f
        cell = side / size
        boardLeft = panelW + (availW - side) / 2f
        boardTop = (lastH - side) / 2f
    }

    override fun onTouch(event: MotionEvent) {
        if (paused || finished) return
        // 同时接受 POINTER_DOWN：两人同屏时一方手指还按在屏幕上，另一方的点击就是
        // POINTER_DOWN，旧实现只认 ACTION_DOWN 会把第二人的操作整段丢弃。
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN) return
        if (lockTimer > 0f) return
        val idx0 = event.actionIndex
        val fx = (event.getX(idx0) - boardLeft) / cell
        val fy = (event.getY(idx0) - boardTop) / cell
        // 负值 toInt() 向零截断会把棋盘左侧/上方的留白解析成第 0 行/列，
        // 点空白也会翻牌（横屏下左侧面板外的留白就有约 130dp 宽）。
        if (fx < 0f || fy < 0f) return
        val c = fx.toInt()
        val r = fy.toInt()
        if (r !in 0 until size || c !in 0 until size) return
        val idx = r * size + c
        if (matched[idx] || idx == flipA || idx == flipB) return
        if (flipA < 0) {
            flipA = idx
            playSound(SoundManager.CLICK)
        } else {
            flipB = idx
            if (cards[flipA] == cards[flipB]) {
                matched[flipA] = true
                matched[flipB] = true
                if (turn == 0) s1++ else s2++
                score += 10
                setScore(score)
                playSound(SoundManager.EAT)
                flipA = -1
                flipB = -1
                if (matched.all { it }) levelCleared()
            } else {
                lockTimer = 0.7f
            }
        }
        invalidate()
    }

    private fun levelCleared() {
        if (level == LEVELS.lastIndex) {
            finished = true
            playSound(SoundManager.WIN)
            val p1Win = s1 > s2
            val verdict = if (s1 == s2) "（平局）" else if (p1Win) "，玩家 1 获胜！" else "，玩家 2 获胜！"
            gameOver(GameResult(score, p1Win, "全部通关！玩家 1 " + s1 + " : " + s2 + " 玩家 2" + verdict))
        } else {
            level++
            // 必须按新关卡更新棋盘尺寸。旧实现只做 level++ 就重排重发牌，
            // 于是 4 个关卡永远是 4×4，顶部提示会显示"第 2/4 关：4×4""第 3/4 关：4×4"，
            // 难度曲线形同虚设、四关体验完全重复。
            size = LEVELS[level]
            recomputeLayout()
            deal()
            playSound(SoundManager.WIN)
            Toast.makeText(context, "第 " + (level + 1) + "/" + LEVELS.size + " 关：" + size + "×" + size, Toast.LENGTH_SHORT).show()
            // 这里不能再次 start()：帧循环本来就在运行，重复调用只会多注册一份帧回调。
            // start() 现已幂等，但保持"过关节奏不重启循环"的语义更清晰。
        }
    }

    override fun update(dt: Float) {
        if (lockTimer > 0f) {
            lockTimer -= dt
            if (lockTimer <= 0f) {
                flipA = -1
                flipB = -1
                turn = 1 - turn
            }
            invalidate()
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        // 左右两侧玩家面板：玩家 1 直立（左侧），玩家 2 旋转 180°（右侧）正对玩家 2
        drawPlayerPanel(canvas, 0)
        drawPlayerPanel(canvas, 1)
        // 关卡信息（顶部中央）
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(15f)
        textPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText("第 " + (level + 1) + "/" + LEVELS.size + " 关 · " + size + "×" + size, width / 2f, dp(30).toFloat(), textPaint)
        // 中央棋盘
        for (i in 0 until size * size) {
            val r = i / size
            val c = i % size
            val rect = RectF(
                boardLeft + c * cell + 1.5f, boardTop + r * cell + 1.5f,
                boardLeft + (c + 1) * cell - 1.5f, boardTop + (r + 1) * cell - 1.5f)
            if (matched[i] || i == flipA || i == flipB) {
                canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, faceUpPaint)
                drawCardFace(canvas, i, rect.centerX(), rect.centerY())
            } else {
                canvas.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, faceDownPaint)
                emojiPaint.color = 0xFFFFFFFF.toInt()
                emojiPaint.textSize = cell * 0.42f
                canvas.drawText("?", rect.centerX(), rect.centerY() - emojiPaint.ascent() * 0.4f, emojiPaint)
            }
        }
    }

    /** 玩家信息面板：名字 + 分数 + 底部回合高亮块。玩家 2 的面板绕中心旋转 180° 正对玩家 2。 */
    private fun drawPlayerPanel(canvas: Canvas, p: Int) {
        val halfW = panelW / 2f
        val halfH = height / 2f - dp(8).toFloat()
        canvas.save()
        if (p == 1) {
            canvas.translate(width - panelW / 2f, height / 2f)
            canvas.rotate(180f)
        } else {
            canvas.translate(panelW / 2f, height / 2f)
        }
        // 面板底
        roundRect(canvas, RectF(-halfW, -halfH, halfW, halfH), 0xFF2A3557.toInt(), 16)
        // 玩家名（顶部）
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(15f)
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("玩家 " + (p + 1), 0f, -halfH + dp(38).toFloat(), textPaint)
        // 分数（中部，玩家专属色）
        textPaint.textSize = sp(30f)
        textPaint.color = if (p == 0) 0xFF6C8BFF.toInt() else 0xFFF5A54A.toInt()
        canvas.drawText((if (p == 0) s1 else s2).toString(), 0f, dp(12).toFloat(), textPaint)
        // 底部回合指示块：轮到该玩家时高亮填充
        val block = RectF(-halfW + dp(14).toFloat(), halfH - dp(66).toFloat(), halfW - dp(14).toFloat(), halfH - dp(20).toFloat())
        if (turn == p && !finished) {
            roundRect(canvas, block, if (p == 0) 0xFF4A6CF7.toInt() else 0xFFF5A54A.toInt(), 12)
            textPaint.textSize = sp(13f)
            textPaint.color = 0xFFE8EAF0.toInt()
            canvas.drawText("轮到翻牌", 0f, block.centerY() + sp(4f), textPaint)
        } else {
            roundRect(canvas, block, 0x33FFFFFF.toInt(), 12)
        }
        canvas.restore()
    }

    /** 卡面 = 颜色圆 + emoji，两者组合全局唯一（16 与 9 互质，144 种）。 */
    private fun drawCardFace(canvas: Canvas, i: Int, x: Float, y: Float) {
        val p = cards[i]
        emojiPaint.color = COLORS[p % COLORS.size]
        canvas.drawCircle(x, y, cell * 0.3f, emojiPaint)
        emojiPaint.color = 0xFF1C2333.toInt()
        emojiPaint.textSize = cell * 0.42f
        canvas.drawText(EMOJIS[p % EMOJIS.size], x, y - emojiPaint.ascent() * 0.35f, emojiPaint)
    }

    /** 按当前 size 重新发牌，保留局外状态（level/score/双人比分）。 */
    private fun deal() {
        cards = IntArray(size * size)
        matched = BooleanArray(size * size)
        val deck = ArrayList<Int>()
        for (p in 0 until size * size / 2) {
            deck.add(p)
            deck.add(p)
        }
        deck.shuffle()
        for (i in cards.indices) cards[i] = deck[i]
        flipA = -1
        flipB = -1
        lockTimer = 0f
        turn = 0
        finished = false
    }

    override fun reset() {
        level = 0
        size = LEVELS[0]
        s1 = 0
        s2 = 0
        score = 0
        recomputeLayout()
        deal()
        setScore(0)
        start()
    }
}
