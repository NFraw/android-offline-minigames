package com.minigames.collection.games.huarong

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager

/** 三国华容道：滑动方块，让曹操从底部出口逃出。 */
class HuarongView(context: Context) : GameView(context) {

    companion object {
        private const val COLS = 4
        private const val ROWS = 5
    }

    // 方块：(r, c, w, h, 名字)
    private data class Block(var r: Int, var c: Int, val w: Int, val h: Int, val name: String)

    private val blocks = ArrayList<Block>()
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var moves = 0
    private var over = false
    private var dragIdx = -1
    private var dragOffX = 0f
    private var dragOffY = 0f

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
        cell = kotlin.math.min(w, h) / COLS.toFloat()
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
            MotionEvent.ACTION_UP -> {
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
        canvas.drawText("把曹操移到下方出口 · 步数 " + moves, width / 2f, boardTop + cell * ROWS + dp(34).toFloat(), hintPaint)
    }

    override fun reset() {
        blocks.clear()
        blocks.add(Block(0, 1, 2, 2, "曹操"))
        blocks.add(Block(0, 0, 1, 2, "关羽"))
        blocks.add(Block(0, 3, 1, 2, "张飞"))
        blocks.add(Block(2, 0, 2, 1, "赵云"))
        blocks.add(Block(2, 2, 2, 1, "马超"))
        blocks.add(Block(3, 0, 1, 1, "卒"))
        blocks.add(Block(3, 1, 1, 1, "卒"))
        blocks.add(Block(3, 2, 1, 1, "卒"))
        blocks.add(Block(3, 3, 1, 1, "卒"))
        blocks.add(Block(4, 1, 2, 1, "黄忠"))
        moves = 0
        over = false
        start()
    }
}
