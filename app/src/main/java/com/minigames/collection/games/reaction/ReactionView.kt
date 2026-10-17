package com.minigames.collection.games.reaction

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 反应力对决：屏幕变绿后先点击自己半屏者得分，抢拍（未变绿就点）判对方得分，先到 10 分获胜。 */
class ReactionView(context: Context) : GameView(context) {

    companion object {
        private const val WIN_SCORE = 10
        private const val WAIT = 0
        private const val READY = 1
        private const val SETTLED = 2
    }

    private var state = WAIT
    private var waitTimer = 0f
    private var settleTimer = 0f
    private var s1 = 0
    private var s2 = 0
    private var earlyFlash1 = false
    private var earlyFlash2 = false
    private var lastWinner = -1
    private var over = false

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val waitPaint = Paint().apply { color = 0xFF2A3557.toInt() }
    private val readyPaint = Paint().apply { color = 0xFF2E9E5B.toInt() }
    private val flashPaint = Paint().apply { color = 0xFFB3392B.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 62f
        color = 0xFFE8EAF0.toInt()
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFF8A93A6.toInt()
    }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt()
        strokeWidth = 2f
    }
    /** 半屏底色画笔（复用，避免每帧新建 Paint）。 */
    private val halfPaint = Paint()

    override fun onLayout(w: Int, h: Int) {
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (paused || over) return
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN
        ) return
        val idx = event.actionIndex
        // 玩家 1 在下半屏、玩家 2 在上半屏（对齐全库"玩家 1 朝下"的座位约定）
        val tap = if (event.getY(idx) < height / 2f) 1 else 0
        if (state == READY) {
            state = SETTLED
            settleTimer = 1.0f
            lastWinner = tap
            if (tap == 0) s1++ else s2++
            playSound(SoundManager.EAT)
            checkWin()
        } else if (state == WAIT) {
            // 抢拍：对方得分
            state = SETTLED
            settleTimer = 1.0f
            lastWinner = 1 - tap
            if (tap == 0) earlyFlash1 = true else earlyFlash2 = true
            if (lastWinner == 0) s1++ else s2++
            playSound(SoundManager.LOSE)
            // 抢拍同样可能把比分推到 10 分，必须走同一段胜负判定。
            // 旧实现只在"抢答得分"分支判定，A 已 9 分时 B 抢拍会让比分继续涨到 11、12…，
            // 结算文案却仍写"率先拿到 10 分"，与实际比分矛盾。
            checkWin()
        }
    }

    /** 统一胜负判定：达到 WIN_SCORE 即结算，未被此判定拦下则开始新一轮。 */
    private fun checkWin() {
        if (s1 >= WIN_SCORE || s2 >= WIN_SCORE) {
            over = true
            val p1Win = s1 > s2
            playSound(if (p1Win) SoundManager.WIN else SoundManager.LOSE)
            gameOver(GameResult(0, p1Win, "玩家 " + (if (p1Win) 1 else 2) + " 率先拿到 " + WIN_SCORE + " 分获胜！"))
        }
    }

    override fun update(dt: Float) {
        if (state == WAIT) {
            waitTimer -= dt
            if (waitTimer <= 0f) {
                state = READY
                playSound(SoundManager.MOVE)
            }
        } else if (state == SETTLED) {
            settleTimer -= dt
            if (settleTimer <= 0f) newRound()
        }
    }

    private fun newRound() {
        state = WAIT
        waitTimer = 0.8f + Math.random().toFloat() * 1.8f
        earlyFlash1 = false
        earlyFlash2 = false
        lastWinner = -1
    }

    private fun drawHalf(canvas: Canvas, top: Boolean, color: Int, label: String) {
        val r = if (top) RectF(0f, 0f, width.toFloat(), height / 2f)
        else RectF(0f, height / 2f, width.toFloat(), height.toFloat())
        halfPaint.color = color
        canvas.drawRect(r, halfPaint)
        // 上半屏属于玩家 2：文字必须旋转 180°，否则上方玩家看到的是倒字。
        // 旋转中心取该半屏中心，文字位置自然镜像到靠近上边缘处（正对上方玩家）。
        if (top) {
            canvas.save()
            canvas.translate(width / 2f, height / 4f)
            canvas.rotate(180f)
            canvas.translate(-width / 2f, -height / 4f)
            canvas.drawText(label, width / 2f, height * 0.42f, textPaint)
            canvas.restore()
        } else {
            canvas.drawText(label, width / 2f, height * 0.92f, textPaint)
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        val label1: String
        val label2: String
        val c1: Int
        val c2: Int
        when (state) {
            READY -> {
                label1 = "点击！"
                label2 = "点击！"
                c1 = readyPaint.color
                c2 = readyPaint.color
            }
            SETTLED -> {
                label1 = if (lastWinner == 0) "+1" else ""
                label2 = if (lastWinner == 1) "+1" else ""
                c1 = if (earlyFlash1) flashPaint.color else if (lastWinner == 0) readyPaint.color else waitPaint.color
                c2 = if (earlyFlash2) flashPaint.color else if (lastWinner == 1) readyPaint.color else waitPaint.color
            }
            else -> {
                label1 = "等待…"
                label2 = "等待…"
                c1 = waitPaint.color
                c2 = waitPaint.color
            }
        }
        drawHalf(canvas, true, c2, label2)    // 上半屏 = 玩家 2
        drawHalf(canvas, false, c1, label1)   // 下半屏 = 玩家 1
        canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, midPaint)
        val scoreText = "玩家1  " + s1 + " : " + s2 + "  玩家2 · 先到 " + WIN_SCORE + " 分获胜"
        canvas.drawText(scoreText, width / 2f, height - dp(20).toFloat(), scorePaint)
        drawPieceText(canvas, scorePaint, scoreText, width / 2f, dp(40).toFloat(), true)
    }

    override fun reset() {
        state = WAIT
        waitTimer = 1.2f
        settleTimer = 0f
        s1 = 0
        s2 = 0
        earlyFlash1 = false
        earlyFlash2 = false
        lastWinner = -1
        over = false
        start()
    }
}
