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

/**
 * 数独：进入时选难度。迷你 3×3（填 1~3）与 9×9（简单/中等/困难，挖空数递增）。
 * 完整解每次随机生成、挖空后做唯一解校验，保证题目多且质量可靠。
 */
class SudokuView(context: Context) : GameView(context) {

    private enum class Mode(val size: Int, val remove: Int, val label: String) {
        MINI3(3, 4, "迷你 3×3"),
        EASY(9, 30, "简单 9×9"),
        MEDIUM(9, 40, "中等 9×9"),
        HARD(9, 50, "困难 9×9")
    }

    private var solution = IntArray(81)
    private var puzzle = IntArray(81)
    private var fixed = BooleanArray(81)
    private var size = 9
    private var mode: Mode? = null
    private var selected = -1
    private var over = false
    private var lastW = 0
    private var lastH = 0
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val numBtns = ArrayList<RectF>()
    private val modeBtns = ArrayList<Pair<RectF, Mode>>()

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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
        color = 0xFFE8EAF0.toInt()
    }
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3557.toInt() }
    private val maskPaint = Paint().apply { color = 0xCC141A2E.toInt() }

    override fun onLayout(w: Int, h: Int) {
        lastW = w
        lastH = h
        recomputeLayout()
        // 模式选择按钮
        modeBtns.clear()
        val bw = dp(210).toFloat()
        val bh = dp(62).toFloat()
        val gap = dp(18).toFloat()
        val startY = h * 0.42f
        for (i in 0 until 4) {
            modeBtns.add(Pair(RectF(w / 2f - bw / 2f, startY + i * (bh + gap), w / 2f + bw / 2f, startY + i * (bh + gap) + bh), Mode.values()[i]))
        }
        if (mode != null) newPuzzle()
    }

    private fun recomputeLayout() {
        val s = if (mode == null) 9 else mode!!.size
        val side = min(lastW * 0.92f, lastH * 0.86f)
        cell = side / s
        boardLeft = (lastW - side) / 2f
        boardTop = (lastH - side - cell * 1.05f) / 2f
        numBtns.clear()
        val gap = dp(4).toFloat()
        val y0 = boardTop + cell * s + cell * 0.18f
        val btnH = cell * 0.62f
        val btnW = (side - gap * (s - 1)) / s
        for (n in 0 until s) {
            numBtns.add(RectF(boardLeft + n * (btnW + gap), y0, boardLeft + n * (btnW + gap) + btnW, y0 + btnH))
        }
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action != MotionEvent.ACTION_DOWN || paused) return
        if (mode == null) {
            for ((rect, m) in modeBtns) {
                if (rect.contains(event.x, event.y)) {
                    mode = m
                    size = m.size
                    recomputeLayout()
                    newPuzzle()
                    playSound(SoundManager.CLICK)
                    invalidate()
                    return
                }
            }
            return
        }
        if (over) return
        val s = size
        val c = ((event.x - boardLeft) / cell).toInt()
        val r = ((event.y - boardTop) / cell).toInt()
        if (r in 0 until s && c in 0 until s) {
            val idx = r * s + c
            selected = if (selected == idx) -1 else idx
            playSound(SoundManager.CLICK)
            invalidate()
            return
        }
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
        for (i in 0 until size * size) {
            if (puzzle[i] != solution[i]) return
        }
        over = true
        playSound(SoundManager.WIN)
        gameOver(GameResult(500, true, "数独完成！" + mode!!.label))
    }

    override fun update(dt: Float) {}

    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        if (mode != null) {
            drawBoard(canvas)
        }
        if (mode == null) {
            // 选难度界面
            canvas.drawRect(gameRect, maskPaint)
            textPaint.textSize = 60f
            canvas.drawText("🧩 数独", width / 2f, height * 0.24f, textPaint)
            textPaint.textSize = 38f
            canvas.drawText("请选择难度", width / 2f, height * 0.24f + dp(44).toFloat(), textPaint)
            for ((rect, m) in modeBtns) {
                canvas.drawRoundRect(rect, dp(16).toFloat(), dp(16).toFloat(), btnPaint)
                textPaint.textSize = 42f
                canvas.drawText(m.label, rect.centerX(), rect.centerY() + dp(4).toFloat(), textPaint)
            }
        }
    }

    private fun drawBoard(canvas: Canvas) {
        val s = size
        for (r in 0 until s) for (c in 0 until s) {
            val idx = r * s + c
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
        // 网格线：3×3 迷你不分组，9×9 每 3 格加粗
        val step = if (s == 9) 3 else s
        for (i in 0..s) {
            val isThick = i % step == 0
            val p = if (isThick) thickPaint else linePaint
            canvas.drawLine(boardLeft + i * cell, boardTop, boardLeft + i * cell, boardTop + cell * s, p)
            canvas.drawLine(boardLeft, boardTop + i * cell, boardLeft + cell * s, boardTop + i * cell, p)
        }
        numPaint.textSize = cell * 0.44f
        for (n in 0 until s) {
            val rect = numBtns[n]
            val isSel = selected >= 0 && puzzle[selected] == n + 1
            roundRect(canvas, rect, if (isSel) 0xFF4A6CF7.toInt() else 0xFF2A3557.toInt(), 12)
            numPaint.color = if (isSel) 0xFFFFFFFF.toInt() else 0xFFAFC3FF.toInt()
            canvas.drawText((n + 1).toString(), rect.centerX(), centerBaseline(rect.centerY(), numPaint), numPaint)
        }
    }

    override fun reset() {
        if (mode != null) newPuzzle()
        start()
    }

    private fun newPuzzle() {
        val s = mode!!.size
        solution = IntArray(s * s)
        solve(s, 0)
        val copy = solution.copyOf()
        val cells = (0 until s * s).toMutableList()
        cells.shuffle()
        var removed = 0
        val target = mode!!.remove
        while (removed < target && cells.isNotEmpty()) {
            val idx = cells.removeAt(cells.size - 1)
            val backup = copy[idx]
            copy[idx] = 0
            if (countSolutions(copy, s) != 1) {
                copy[idx] = backup
            } else {
                removed++
            }
        }
        puzzle = IntArray(s * s)
        fixed = BooleanArray(s * s)
        for (i in 0 until s * s) {
            puzzle[i] = copy[i]
            fixed[i] = copy[i] != 0
        }
        selected = -1
        over = false
        setScore(0)
    }

    private fun solve(s: Int, pos: Int): Boolean {
        if (pos >= s * s) return true
        if (solution[pos] != 0) return solve(s, pos + 1)
        val r = pos / s
        val c = pos % s
        val nums = (1..s).toMutableList()
        nums.shuffle()
        for (v in nums) {
            if (canPlace(solution, s, r, c, v)) {
                solution[pos] = v
                if (solve(s, pos + 1)) return true
                solution[pos] = 0
            }
        }
        return false
    }

    /** 计算解的数量，超过 1 即提前停止（带节点预算，防止极端情况卡顿）。 */
    private fun countSolutions(b: IntArray, s: Int): Int {
        var count = 0
        var attempts = 0
        fun dfs(pos: Int) {
            if (count > 1) return
            attempts++
            if (attempts > 60000) {
                count = 2
                return
            }
            if (pos >= s * s) {
                count++
                return
            }
            if (b[pos] != 0) {
                dfs(pos + 1)
                return
            }
            for (v in 1..s) {
                if (canPlace(b, s, pos / s, pos % s, v)) {
                    b[pos] = v
                    dfs(pos + 1)
                    b[pos] = 0
                }
            }
        }
        dfs(0)
        return count
    }

    private fun canPlace(b: IntArray, s: Int, r: Int, c: Int, v: Int): Boolean {
        for (i in 0 until s) {
            if (b[r * s + i] == v || b[i * s + c] == v) return false
        }
        if (s == 9) {
            val br = r / 3 * 3
            val bc = c / 3 * 3
            for (i in 0 until 3) for (j in 0 until 3) {
                if (b[(br + i) * s + bc + j] == v) return false
            }
        }
        return true
    }
}
