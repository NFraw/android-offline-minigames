package com.minigames.collection.games.huarong

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.widget.Toast
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.max
import kotlin.math.min

/** 三国华容道：滑动方块，让曹操（红色大方块）从底部出口逃出。共 8 个经典布局关卡。 */
class HuarongView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 4
        private const val ROWS = 5
    }

    private data class Block(var r: Int, var c: Int, val w: Int, val h: Int, val name: String)

    private data class Level(val name: String, val blocks: List<Block>)

    private val LEVELS = listOf(
        Level("横刀立马", listOf(
            Block(0, 0, 1, 2, "张飞"), Block(0, 1, 2, 2, "曹操"), Block(3, 0, 1, 2, "马超"),
            Block(0, 2, 1, 2, "赵云"), Block(1, 2, 2, 1, "关羽"), Block(3, 2, 1, 2, "黄忠"),
            Block(1, 3, 1, 1, "卒"), Block(2, 3, 1, 1, "卒"), Block(0, 4, 1, 1, "卒"), Block(3, 4, 1, 1, "卒"))),
        Level("兵分三路", listOf(
            Block(0, 0, 1, 2, "张飞"), Block(0, 1, 2, 2, "曹操"), Block(3, 0, 1, 2, "马超"),
            Block(0, 2, 1, 2, "赵云"), Block(3, 2, 1, 2, "黄忠"), Block(1, 3, 2, 1, "关羽"),
            Block(1, 2, 1, 1, "卒"), Block(2, 2, 1, 1, "卒"), Block(0, 4, 1, 1, "卒"), Block(3, 4, 1, 1, "卒"))),
        Level("近在咫尺", listOf(
            Block(0, 0, 1, 2, "赵云"), Block(0, 1, 2, 2, "曹操"), Block(3, 0, 1, 2, "马超"),
            Block(0, 2, 1, 2, "张飞"), Block(1, 2, 2, 1, "关羽"), Block(3, 2, 1, 2, "黄忠"),
            Block(1, 3, 1, 1, "卒"), Block(2, 3, 1, 1, "卒"), Block(1, 4, 1, 1, "卒"), Block(2, 4, 1, 1, "卒"))),
        Level("四路进兵", listOf(
            Block(0, 0, 1, 2, "张飞"), Block(0, 1, 2, 2, "曹操"), Block(3, 0, 1, 2, "黄忠"),
            Block(0, 2, 1, 2, "赵云"), Block(3, 2, 1, 2, "马超"), Block(1, 3, 2, 1, "关羽"),
            Block(1, 2, 1, 1, "卒"), Block(2, 2, 1, 1, "卒"), Block(1, 4, 1, 1, "卒"), Block(2, 4, 1, 1, "卒"))),
        Level("层层设防", listOf(
            Block(0, 0, 1, 2, "马超"), Block(0, 1, 2, 2, "曹操"), Block(3, 0, 1, 2, "张飞"),
            Block(0, 2, 1, 2, "黄忠"), Block(1, 2, 2, 1, "关羽"), Block(3, 2, 1, 2, "赵云"),
            Block(1, 3, 1, 1, "卒"), Block(2, 3, 1, 1, "卒"), Block(1, 4, 1, 1, "卒"), Block(2, 4, 1, 1, "卒"))),
        Level("巧过五关", listOf(
            Block(0, 0, 1, 2, "赵云"), Block(0, 1, 2, 2, "曹操"), Block(3, 0, 1, 2, "黄忠"),
            Block(0, 2, 1, 2, "马超"), Block(3, 2, 1, 2, "张飞"), Block(1, 3, 2, 1, "关羽"),
            Block(1, 2, 1, 1, "卒"), Block(2, 2, 1, 1, "卒"), Block(0, 4, 1, 1, "卒"), Block(3, 4, 1, 1, "卒"))),
        Level("峰回路转", listOf(
            Block(0, 0, 1, 2, "马超"), Block(0, 1, 2, 2, "曹操"), Block(3, 0, 1, 2, "赵云"),
            Block(0, 2, 1, 2, "张飞"), Block(1, 2, 2, 1, "关羽"), Block(3, 2, 1, 2, "黄忠"),
            Block(1, 3, 1, 1, "卒"), Block(2, 3, 1, 1, "卒"), Block(0, 4, 1, 1, "卒"), Block(3, 4, 1, 1, "卒"))),
        Level("走投无路", listOf(
            Block(0, 0, 1, 2, "黄忠"), Block(0, 1, 2, 2, "曹操"), Block(3, 0, 1, 2, "马超"),
            Block(0, 2, 1, 2, "赵云"), Block(3, 2, 1, 2, "张飞"), Block(1, 3, 2, 1, "关羽"),
            Block(1, 2, 1, 1, "卒"), Block(2, 2, 1, 1, "卒"), Block(1, 4, 1, 1, "卒"), Block(2, 4, 1, 1, "卒")))
    )

    private val blocks = ArrayList<Block>()
    private var levelIndex = 0
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var moves = 0
    private var over = false
    private var dragIdx = -1
    private var dragOffX = 0f
    private var dragOffY = 0f
    private var dragStartR = 0
    private var dragStartC = 0

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val caoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8503A.toInt() }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5A54A.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFFFFFFFF.toInt()
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 36f
        color = 0xFF8A93A6.toInt()
    }

    override fun onLayout(w: Int, h: Int) {
        cell = min(w, h) / COLS.toFloat()
        boardLeft = (w - cell * COLS) / 2f
        boardTop = (h - cell * ROWS) / 2f
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (over || paused) return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val gx = (event.x - boardLeft) / cell
                val gy = (event.y - boardTop) / cell
                for (i in blocks.indices) {
                    val b = blocks[i]
                    if (gx >= b.c && gx < b.c + b.w && gy >= b.r && gy < b.r + b.h) {
                        dragIdx = i
                        dragOffX = event.x - (boardLeft + b.c * cell)
                        dragOffY = event.y - (boardTop + b.r * cell)
                        dragStartR = b.r
                        dragStartC = b.c
                        break
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragIdx >= 0) {
                    val b = blocks[dragIdx]
                    val targetC = Math.round(((event.x - dragOffX) - boardLeft) / cell).toInt()
                    val targetR = Math.round(((event.y - dragOffY) - boardTop) / cell).toInt()
                    if (canMoveTo(b, targetC, targetR)) {
                        b.c = targetC
                        b.r = targetR
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragIdx >= 0) {
                    val b = blocks[dragIdx]
                    if (b.r != dragStartR || b.c != dragStartC) {
                        moves++
                        playSound(SoundManager.MOVE)
                        checkWin()
                    }
                }
                dragIdx = -1
            }
        }
    }

    private fun canMoveTo(b: Block, tc: Int, tr: Int): Boolean {
        if (tc < 0 || tr < 0 || tc + b.w > COLS || tr + b.h > ROWS) return false
        for (other in blocks) {
            if (other === b) continue
            if (tc < other.c + other.w && tc + b.w > other.c && tr < other.r + other.h && tr + b.h > other.r) return false
        }
        return true
    }

    /** 曹操 2×2 抵达底部出口（r=3, c=1）即过关。 */
    private fun checkWin() {
        if (over) return
        val cao = blocks.firstOrNull { it.name == "曹操" } ?: return
        if (cao.r == ROWS - 2 && cao.c == 1) {
            playSound(SoundManager.WIN)
            if (levelIndex == LEVELS.lastIndex) {
                over = true
                gameOver(GameResult(max(0, 600 - moves), true, "全部通关！总步数 " + moves))
            } else {
                levelIndex++
                reset()
                Toast.makeText(context, "过关！第 " + (levelIndex + 1) + " 关：" + LEVELS[levelIndex].name, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            canvas.drawRoundRect(RectF(boardLeft + c * cell, boardTop + r * cell, boardLeft + (c + 1) * cell, boardTop + (r + 1) * cell), dp(3).toFloat(), dp(3).toFloat(), floorPaint)
        }
        for (b in blocks) {
            val x = boardLeft + b.c * cell
            val y = boardTop + b.r * cell
            val w = b.w * cell
            val h = b.h * cell
            val isCao = b.name == "曹操"
            val rect = RectF(x + 2f, y + 2f, x + w - 2f, y + h - 2f)
            canvas.drawRoundRect(rect, dp(8).toFloat(), dp(8).toFloat(), if (isCao) caoPaint else blockPaint)
            textPaint.textSize = cell * (if (isCao) 0.6f else 0.42f)
            canvas.drawText(b.name, x + w / 2f, y + h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }
        // 出口
        canvas.drawRect(RectF(boardLeft + cell, boardTop + ROWS * cell - dp(4).toFloat(), boardLeft + 3 * cell, boardTop + ROWS * cell + dp(4).toFloat()), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3ED98A.toInt() })
        hintPaint.color = 0xFF8A93A6.toInt()
        canvas.drawText(
            "第 " + (levelIndex + 1) + "/" + LEVELS.size + " 关：" + LEVELS[levelIndex].name + " · 步数 " + moves + " · 把曹操移到下方出口",
            width / 2f, boardTop + cell * ROWS + dp(34).toFloat(), hintPaint)
    }

    override fun reset() {
        blocks.clear()
        for (b in LEVELS[levelIndex].blocks) {
            blocks.add(Block(b.r, b.c, b.w, b.h, b.name))
        }
        moves = 0
        over = false
        dragIdx = -1
        start()
    }
}
