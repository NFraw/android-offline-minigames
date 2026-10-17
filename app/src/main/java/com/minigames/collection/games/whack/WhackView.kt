package com.minigames.collection.games.whack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.max
import kotlin.math.min

/** 打地鼠：60 秒限时，点击冒出的地鼠得分。 */
class WhackView(context: Context) : GameView(context) {

    private val SIZE = 3
    private val GAME_SECONDS = 30
    private var started = false
    private var timeLeft = GAME_SECONDS.toFloat()
    private var score = 0
    private var hits = 0
    private var moleHole = -1
    private var moleTimer = 0f
    private var spawnTimer = 0.5f
    private var flashHole = -1
    private var flashTimer = 0f

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val holes = Array(SIZE * SIZE) { RectF() }
    private val startBtn = RectF()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E1424.toInt() }
    private val grassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF24354A.toInt() }
    private val molePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB8865A.toInt() }
    private val moleDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A5A38.toInt() }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141A2E.toInt() }
    private val timerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 46f
        color = 0xFFE8EAF0.toInt()
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 59f
    }

    override fun onLayout(w: Int, h: Int) {
        val side = min(w * 0.92f, h * 0.72f)
        cell = side / SIZE
        boardLeft = (w - side) / 2f
        boardTop = (h - side) / 2f + cell * 0.25f
        for (i in 0 until SIZE) for (j in 0 until SIZE) {
            val inset = cell * 0.14f
            holes[i * SIZE + j].set(
                boardLeft + j * cell + inset, boardTop + i * cell + inset,
                boardLeft + (j + 1) * cell - inset, boardTop + (i + 1) * cell - inset)
        }
        val bw = w * 0.55f
        val bh = dp(68).toFloat()
        startBtn.set(w / 2f - bw / 2f, h * 0.55f - bh / 2f, w / 2f + bw / 2f, h * 0.55f + bh / 2f)
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        // 同时接受 POINTER_DOWN：打地鼠是"快速连点"玩法，若先按住一根手指再换另一根敲，
        // 第二根手指产生的是 ACTION_POINTER_DOWN，旧实现只认 ACTION_DOWN 会漏记。
        if (event.actionMasked != MotionEvent.ACTION_DOWN &&
            event.actionMasked != MotionEvent.ACTION_POINTER_DOWN) return
        if (paused) return
        val idx = event.actionIndex
        val x = event.getX(idx)
        val y = event.getY(idx)
        if (!started) {
            if (startBtn.contains(x, y)) {
                started = true
                timeLeft = GAME_SECONDS.toFloat()
                playSound(SoundManager.MOVE)
            }
            return
        }
        for (i in 0 until SIZE * SIZE) {
            if (holes[i].contains(x, y)) {
                if (i == moleHole && flashTimer <= 0f) {
                    hits++
                    score += 10
                    setScore(score)
                    playSound(SoundManager.EAT)
                    buzz()
                    moleHole = -1
                    spawnTimer = 0.3f
                    flashHole = i
                    flashTimer = 0.25f
                } else {
                    playSound(SoundManager.CLICK)
                }
                return
            }
        }
    }

    override fun update(dt: Float) {
        if (!started) return
        timeLeft -= dt
        if (timeLeft <= 0f) {
            timeLeft = 0f
            playSound(SoundManager.WIN)
            gameOver(GameResult(score, true, "时间到！共打中 " + hits + " 只地鼠"))
            return
        }
        if (flashTimer > 0f) flashTimer -= dt
        if (moleHole >= 0) {
            moleTimer -= dt
            if (moleTimer <= 0f) {
                moleHole = -1
                spawnTimer = 0.35f
            }
        } else {
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                moleHole = (0 until SIZE * SIZE).random()
                moleTimer = max(0.5f, 1.15f - score * 0.004f)
                spawnTimer = -1f
            }
        }
    }

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        if (!started) {
            // ---- 开始界面 ----
            timerPaint.textSize = 64f
            timerPaint.color = 0xFFE8EAF0.toInt()
            canvas.drawText("🔨 打地鼠", width / 2f, height * 0.36f, timerPaint)
            timerPaint.textSize = 44f
            timerPaint.color = 0xFF8A93A6.toInt()
            canvas.drawText("地鼠冒出时点击它得分 · 限时 " + GAME_SECONDS + " 秒", width / 2f, height * 0.44f, timerPaint)
            val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() }
            canvas.drawRoundRect(startBtn, dp(18).toFloat(), dp(18).toFloat(), btnPaint)
            timerPaint.textSize = 46f
            timerPaint.color = 0xFF141A2E.toInt()
            canvas.drawText("开始游戏", width / 2f, startBtn.centerY() - (timerPaint.descent() + timerPaint.ascent()) / 2f, timerPaint)
            return
        }
        // 计时
        val secs = Math.ceil(timeLeft.toDouble()).toInt()
        timerPaint.color = if (secs <= 10) 0xFFFF5252.toInt() else 0xFFE8EAF0.toInt()
        canvas.drawText("⏱ " + secs + " 秒", width / 2f, boardTop - cell * 0.5f, timerPaint)
        // 洞
        for (i in 0 until SIZE * SIZE) {
            val r = holes[i]
            canvas.drawOval(r, holePaint)
            canvas.drawOval(RectF(r.left - cell * 0.03f, r.top - cell * 0.05f, r.right + cell * 0.03f, r.top + cell * 0.12f), grassPaint)
            // 地鼠
            if (i == moleHole) {
                val cx = r.centerX()
                val cy = r.centerY()
                val mr = cell * 0.26f
                canvas.drawCircle(cx - mr * 0.8f, cy - mr * 0.9f, mr * 0.42f, molePaint) // 左耳
                canvas.drawCircle(cx + mr * 0.8f, cy - mr * 0.9f, mr * 0.42f, molePaint) // 右耳
                canvas.drawCircle(cx - mr * 0.8f, cy - mr * 0.9f, mr * 0.18f, moleDarkPaint)
                canvas.drawCircle(cx + mr * 0.8f, cy - mr * 0.9f, mr * 0.18f, moleDarkPaint)
                canvas.drawCircle(cx, cy - mr * 0.15f, mr, molePaint) // 头
                canvas.drawCircle(cx - mr * 0.35f, cy - mr * 0.25f, mr * 0.12f, eyePaint)
                canvas.drawCircle(cx + mr * 0.35f, cy - mr * 0.25f, mr * 0.12f, eyePaint)
                canvas.drawCircle(cx, cy + mr * 0.25f, mr * 0.3f, moleDarkPaint) // 鼻子
            }
            if (i == flashHole && flashTimer > 0f) {
                canvas.drawText("💥", r.centerX(), r.centerY() + cell * 0.1f, flashPaint)
            }
        }
    }

    override fun reset() {
        started = false
        timeLeft = GAME_SECONDS.toFloat()
        score = 0
        hits = 0
        moleHole = -1
        moleTimer = 0f
        spawnTimer = 0.5f
        flashHole = -1
        flashTimer = 0f
        setScore(0)
        start()
    }
}
