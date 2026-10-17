package com.minigames.collection.games.simon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 西蒙记忆：记住并复现颜色序列，每关加长。 */
class SimonView(context: Context) : GameView(context) {

    private val sequence = ArrayList<Int>()
    private var inputIdx = 0
    private var showIdx = 0
    private var showTimer = 0f
    private var showLit = true // true=色块亮起，false=间隔熄灭（让连续同色可区分）
    private var showing = true
    private var level = 1
    private var over = false
    private var quadR = 0f
    private var tapFlash = -1f // 点击反馈剩余时长，-1 表示无
    private var tapQuad = 0

    private val colors = intArrayOf(
        0xFFFF5252.toInt(), 0xFF3ED98A.toInt(), 0xFFF5D547.toInt(), 0xFF37C2F0.toInt()
    )
    private val dimColors = intArrayOf(
        0xFF7A2B2B.toInt(), 0xFF1E5C3A.toInt(), 0xFF7A6A24.toInt(), 0xFF1E4568.toInt()
    )
    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val quadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 41f
        color = 0xFFE8EAF0.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        quadR = kotlin.math.min(w, h) * 0.42f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused || showing) return
        val dx = event.x - width / 2f
        val dy = event.y - height / 2f
        // 用正方形边界判定，与绘制出的色块方阵完全一致（角落也能点到）
        if (Math.abs(dx) > quadR || Math.abs(dy) > quadR) return
        val q = if (dx > 0) if (dy > 0) 3 else 1 else if (dy > 0) 2 else 0
        tapQuad = q
        tapFlash = 0.25f
        invalidate()
        if (q == sequence[inputIdx]) {
            inputIdx++
            playSound(SoundManager.CLICK)
            if (inputIdx >= sequence.size) {
                level++
                setScore(level * 10)   // 实时刷新得分胶囊，否则整局都显示"得分 0"
                sequence.add((0..3).random())
                inputIdx = 0
                showIdx = 0
                showLit = true
                showing = true
                showTimer = 0.5f
                playSound(SoundManager.EAT)
            }
        } else {
            over = true
            playSound(SoundManager.LOSE)
            gameOver(GameResult(level * 10, false, "记错啦！到达第 " + level + " 关"))
        }
        invalidate()
    }

    override fun update(dt: Float) {
        if (tapFlash > 0f) tapFlash -= dt
        if (over || !showing) return
        showTimer -= dt
        if (showTimer <= 0f) {
            if (showLit) {
                // 亮灯阶段结束：先熄灭一下（间隔），再进入下一个色块。
                // 这里要判断的是"下一个色块与本色块是否相同"，即 sequence[showIdx] 与
                // sequence[showIdx+1]；旧实现拿 showIdx-1 与 showIdx 比较，加长间隔的判定
                // 整体错位一格（间隔被放到了相同两色对之后），连续同色的辨识帮助失效。
                showLit = false
                val sameAsNext = showIdx + 1 < sequence.size && sequence[showIdx + 1] == sequence[showIdx]
                showTimer = if (sameAsNext) 0.55f else 0.36f
            } else {
                showIdx++
                if (showIdx >= sequence.size) {
                    // 最后一个色块已闪完，进入等待输入
                    showing = false
                    inputIdx = 0
                } else {
                    showLit = true
                    showTimer = 0.3f
                }
            }
        }
        invalidate()
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        val half = quadR
        for (q in 0..3) {
            val cx = if (q == 0 || q == 2) width / 2f - half else width / 2f
            val cy = if (q == 0 || q == 1) height / 2f - half else height / 2f
            val rect = RectF(cx, cy, cx + half, cy + half)
            // 正在展示时按序列值点亮对应色块；点击反馈单独点亮被按下的色块
            val lit = (tapFlash > 0f && tapQuad == q) ||
                (showing && showLit && showIdx < sequence.size && sequence[showIdx] == q)
            quadPaint.color = if (lit) colors[q] else dimColors[q]
            canvas.drawRoundRect(rect, dp(14).toFloat(), dp(14).toFloat(), quadPaint)
        }
        canvas.drawCircle(width / 2f, height / 2f, half * 0.42f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A2E.toInt() })
        textPaint.color = 0xFFE8EAF0.toInt()
        canvas.drawText("第 " + level + " 关", width / 2f, height / 2f + dp(8).toFloat(), textPaint)
    }

    override fun reset() {
        sequence.clear()
        sequence.add((0..3).random())
        inputIdx = 0
        showIdx = 0
        showLit = true
        showTimer = 0.5f
        showing = true
        level = 1
        over = false
        tapFlash = -1f
        setScore(0)
        start()
    }
}