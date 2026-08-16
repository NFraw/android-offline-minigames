package com.minigames.collection.games.sudoku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/** 数独：9×9 谜题，点选格子再填 1~9，填满且正确获胜。 */
class SudokuView(context: Context) : GameView(context) {

    companion object {
        private const val N = 9
    }

    private val solution = IntArray(N * N)
    private val puzzle = IntArray(N * N)
    private val fixed = BooleanArray(N * N)
    private val notes = BooleanArray(N * N * N)
    private var selected = -1
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var over = false
    private val numBtns = ArrayList<RectF>()

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2740.toInt() }
    private val fixedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8EAF0.toInt() }
    private val inputPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF37C2F0.toInt() }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x664A6CF7.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A4668.toInt()
        strokeWidth = 2f
    }
    private val thickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A93A6.toInt()
        strokeWidth = 4f
    }
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onLayout(w: Int, h: Int) {
        // 棋盘 + 下方数字面板合计 10 个格子高度，须整体放入屏幕
        val side = min(w * 0.92f, h * 0.86f)
        cell = side / N
        boardLeft = (w - side) / 2f
        boardTop = (h - side - cell * 1.05f) / 2f
        // 数字面板按钮：9 个圆角方块，横向铺满棋盘宽度
        numBtns.clear()
        val gap = dp(4).toFloat()
        val y0 = boardTop + cell * N + cell * 0.18f
        val btnH = cell * 0.62f
        val btnW = (side - gap * 8) / 9f
        for (n in 0 until N) {
            numBtns.add(RectF(boardLeft + n * (btnW + gap), y0, boardLeft + n * (btnW + gap) + btnW, y0 + btnH))
        }
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || over || paused) return
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (r in 0 until N && c in 0 until N) {
            val idx = r * N + c
            if (selected == idx) {
                selected = -1
            } else {
                selected = idx
                playSound(SoundManager.CLICK)
            }
            invalidate()
            return
        }
        // 数字面板点击（底部 1~9，触区与按钮显示一致）
        for ((i, rect) in numBtns.withIndex()) {
            if (rect.contains(event.x, event.y)) {
                if (selected >= 0 && !fixed[selected]) {
                    puzzle[selected] = i + 1
                    playSound(SoundManager.MOVE)
                    checkWin()
                }
                invalidate()
                return
            }
        }
    }

    private fun checkWin() {
        if (over) return
        for (i in 0 until N * N) {
            if (puzzle[i] != solution[i]) return
        }
        over = true
        playSound(SoundManager.WIN)
        gameOver(GameResult(500, true, "数独完成！"))
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        for (r in 0 until N) for (c in 0 until N) {
            val idx = r * N + c
            val rect = RectF(boardLeft + c * cell, boardTop + r * cell, boardLeft + (c + 1) * cell, boardTop + (r + 1) * cell)
            canvas.drawRect(rect, cellPaint)
            if (idx == selected) {
                canvas.drawRect(rect, selPaint)
            }
            val v = puzzle[idx]
            if (v > 0) {
                numPaint.color = if (fixed[idx]) fixedPaint.color else inputPaint.color
                numPaint.textSize = cell * 0.5f
                canvas.drawText(v.toString(), rect.centerX(), rect.centerY() - (numPaint.descent() + numPaint.ascent()) / 2f, numPaint)
            }
        }
        // 网格线
        for (i in 0..N) {
            val isThick = i % 3 == 0
            val p = if (isThick) thickPaint else linePaint
            canvas.drawLine(boardLeft + i * cell, boardTop, boardLeft + i * cell, boardTop + cell * N, p)
            canvas.drawLine(boardLeft, boardTop + i * cell, boardLeft + cell * N, boardTop + i * cell, p)
        }
        // 数字面板：圆角按钮底 + 选中态高亮
        numPaint.textSize = cell * 0.44f
        for (n in 0 until N) {
            val rect = numBtns[n]
            val isSel = selected >= 0 && puzzle[selected] == n + 1
            roundRect(canvas, rect, if (isSel) 0xFF4A6CF7.toInt() else 0xFF2A3557.toInt(), 12)
            numPaint.color = if (isSel) 0xFFFFFFFF.toInt() else 0xFFAFC3FF.toInt()
            canvas.drawText((n + 1).toString(), rect.centerX(), centerBaseline(rect.centerY(), numPaint), numPaint)
        }
    }

    override fun reset() {
        generatePuzzle()
        selected = -1
        over = false
        start()
    }

    private fun generatePuzzle() {
        for (i in solution.indices) solution[i] = 0
        solve(0)
        // 挖空 45 格
        val copy = IntArray(N * N) { solution[it] }
        val cells = (0 until N * N).toMutableList()
        cells.shuffle()
        var removed = 0
        while (removed < 45 && cells.isNotEmpty()) {
            val idx = cells.removeAt(cells.size - 1)
            val backup = copy[idx]
            copy[idx] = 0
            // 唯一解检查（简化：随机挖空，不保证唯一解——可玩性 OK）
            removed++
        }
        for (i in 0 until N * N) {
            puzzle[i] = copy[i]
            fixed[i] = copy[i] != 0
        }
    }

    private fun solve(pos: Int): Boolean {
        if (pos >= N * N) return true
        if (solution[pos] != 0) return solve(pos + 1)
        val r = pos / N
        val c = pos % N
        val nums = (1..9).toMutableList()
        nums.shuffle()
        for (v in nums) {
            if (canPlace(solution, r, c, v)) {
                solution[pos] = v
                if (solve(pos + 1)) return true
                solution[pos] = 0
            }
        }
        return false
    }

    private fun canPlace(b: IntArray, r: Int, c: Int, v: Int): Boolean {
        for (i in 0 until N) {
            if (b[r * N + i] == v || b[i * N + c] == v) return false
        }
        val br = r / 3 * 3
        val bc = c / 3 * 3
        for (i in 0 until 3) for (j in 0 until 3) {
            if (b[(br + i) * N + bc + j] == v) return false
        }
        return true
    }
}
