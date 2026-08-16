package com.minigames.collection.games.xiangqi

import kotlin.math.max
import kotlin.math.min

/**
 * 中国象棋规则引擎（纯逻辑，无 Android 依赖）。
 * 棋盘 10 行 × 9 列，红方在下（y=5..9），黑方在上（y=0..4）。
 * 棋子编码：红 1..7，黑 11..17。
 */
object XiangqiRules {

    const val COLS = 9
    const val ROWS = 10

    const val EMPTY = 0
    const val R_KING = 1
    const val R_ADVISOR = 2
    const val R_ELEPHANT = 3
    const val R_HORSE = 4
    const val R_ROOK = 5
    const val R_CANNON = 6
    const val R_PAWN = 7
    const val B_KING = 11
    const val B_ADVISOR = 12
    const val B_ELEPHANT = 13
    const val B_HORSE = 14
    const val B_ROOK = 15
    const val B_CANNON = 16
    const val B_PAWN = 17

    const val RED = 0
    const val BLACK = 1

    fun sideOf(p: Int): Int = if (p in 1..7) RED else BLACK

    /** 棋子文字按传统写法：红方「帥仕相傌俥炮兵」、黑方「將士象馬車砲卒」。 */
    fun pieceName(p: Int): String = when (p) {
        R_KING -> "帥"
        R_ADVISOR -> "仕"
        R_ELEPHANT -> "相"
        R_HORSE -> "傌"
        R_ROOK -> "俥"
        R_CANNON -> "炮"
        R_PAWN -> "兵"
        B_KING -> "將"
        B_ADVISOR -> "士"
        B_ELEPHANT -> "象"
        B_HORSE -> "馬"
        B_ROOK -> "車"
        B_CANNON -> "砲"
        B_PAWN -> "卒"
        else -> ""
    }

    data class Move(val from: Int, val to: Int)

    fun initialBoard(): IntArray {
        val b = IntArray(COLS * ROWS)
        val blackBack = intArrayOf(B_ROOK, B_HORSE, B_ELEPHANT, B_ADVISOR, B_KING, B_ADVISOR, B_ELEPHANT, B_HORSE, B_ROOK)
        val redBack = intArrayOf(R_ROOK, R_HORSE, R_ELEPHANT, R_ADVISOR, R_KING, R_ADVISOR, R_ELEPHANT, R_HORSE, R_ROOK)
        for (c in 0 until COLS) {
            b[c] = blackBack[c]
            b[9 * COLS + c] = redBack[c]
        }
        // 兵/卒每边 5 个：0,2,4,6,8 列
        for (c in 0 until COLS step 2) {
            b[3 * COLS + c] = B_PAWN
            b[6 * COLS + c] = R_PAWN
        }
        b[2 * COLS + 1] = B_CANNON
        b[2 * COLS + 7] = B_CANNON
        b[7 * COLS + 1] = R_CANNON
        b[7 * COLS + 7] = R_CANNON
        return b
    }

    /** 该棋子从 from 出发的所有可到达位置（含吃子，不含走完被将军的过滤）。 */
    fun targets(board: IntArray, from: Int, p: Int): List<Int> {
        val r = from / COLS
        val c = from % COLS
        val out = ArrayList<Int>()
        fun idx(rr: Int, cc: Int) = rr * COLS + cc
        fun inBoard(rr: Int, cc: Int) = rr in 0 until ROWS && cc in 0 until COLS
        when (p) {
            R_ROOK, B_ROOK -> {
                for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    var nr = r + dr
                    var nc = c + dc
                    while (inBoard(nr, nc)) {
                        val v = board[idx(nr, nc)]
                        if (v == EMPTY) {
                            out.add(idx(nr, nc))
                        } else {
                            out.add(idx(nr, nc))
                            break
                        }
                        nr += dr
                        nc += dc
                    }
                }
            }
            R_HORSE, B_HORSE -> {
                // (dr, dc, 蹩腿方向)
                val moves = listOf(
                    Triple(-2, -1, -1 to 0), Triple(-2, 1, -1 to 0),
                    Triple(2, -1, 1 to 0), Triple(2, 1, 1 to 0),
                    Triple(-1, -2, 0 to -1), Triple(1, -2, 0 to -1),
                    Triple(-1, 2, 0 to 1), Triple(1, 2, 0 to 1))
                for ((dr, dc, leg) in moves) {
                    val nr = r + dr
                    val nc = c + dc
                    if (inBoard(nr, nc) && board[idx(r + leg.first, c + leg.second)] == EMPTY) {
                        out.add(idx(nr, nc))
                    }
                }
            }
            R_ELEPHANT, B_ELEPHANT -> {
                for ((dr, dc) in listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2)) {
                    val nr = r + dr
                    val nc = c + dc
                    if (!inBoard(nr, nc)) continue
                    if (p == R_ELEPHANT && nr < 5) continue
                    if (p == B_ELEPHANT && nr > 4) continue
                    if (board[idx(r + dr / 2, c + dc / 2)] == EMPTY) out.add(idx(nr, nc))
                }
            }
            R_ADVISOR, B_ADVISOR -> {
                val minRow = if (p == R_ADVISOR) 7 else 0
                val maxRow = if (p == R_ADVISOR) 9 else 2
                for ((dr, dc) in listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in minRow..maxRow && nc in 3..5) out.add(idx(nr, nc))
                }
            }
            R_KING, B_KING -> {
                val minRow = if (p == R_KING) 7 else 0
                val maxRow = if (p == R_KING) 9 else 2
                for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr in minRow..maxRow && nc in 3..5) out.add(idx(nr, nc))
                }
            }
            R_CANNON, B_CANNON -> {
                for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    var nr = r + dr
                    var nc = c + dc
                    var screen = false
                    while (inBoard(nr, nc)) {
                        val v = board[idx(nr, nc)]
                        if (!screen) {
                            if (v == EMPTY) out.add(idx(nr, nc)) else screen = true
                        } else {
                            if (v != EMPTY) {
                                out.add(idx(nr, nc))
                                break
                            }
                        }
                        nr += dr
                        nc += dc
                    }
                }
            }
            R_PAWN, B_PAWN -> {
                if (p == R_PAWN) {
                    if (r - 1 >= 0) out.add(idx(r - 1, c))
                    if (r < 5) {
                        if (c - 1 >= 0) out.add(idx(r, c - 1))
                        if (c + 1 < COLS) out.add(idx(r, c + 1))
                    }
                } else {
                    if (r + 1 < ROWS) out.add(idx(r + 1, c))
                    if (r > 4) {
                        if (c - 1 >= 0) out.add(idx(r, c - 1))
                        if (c + 1 < COLS) out.add(idx(r, c + 1))
                    }
                }
            }
        }
        return out
    }

    /** 完整合法性：不能吃己方子；走完后己方帅不能被将（含飞将）。 */
    fun isLegalMove(board: IntArray, from: Int, to: Int): Boolean {
        val p = board[from]
        if (p == EMPTY) return false
        val dest = board[to]
        if (dest != EMPTY && sideOf(dest) == sideOf(p)) return false
        if (to !in targets(board, from, p)) return false
        val copy = board.copyOf()
        copy[to] = copy[from]
        copy[from] = EMPTY
        return !isInCheck(copy, sideOf(p))
    }

    fun generateMoves(board: IntArray, side: Int): List<Move> {
        val moves = ArrayList<Move>()
        for (from in board.indices) {
            val p = board[from]
            if (p == EMPTY || sideOf(p) != side) continue
            for (to in targets(board, from, p)) {
                if (isLegalMove(board, from, to)) moves.add(Move(from, to))
            }
        }
        return moves
    }

    /** side 的将是否被将军（含飞将）。 */
    fun isInCheck(board: IntArray, side: Int): Boolean {
        val king = if (side == RED) R_KING else B_KING
        val kingIdx = board.indexOfFirst { it == king }
        if (kingIdx < 0) return false
        val opp = 1 - side
        for (from in board.indices) {
            val p = board[from]
            if (p == EMPTY || sideOf(p) != opp) continue
            if (kingIdx in targets(board, from, p)) return true
        }
        // 飞将：双方帅将同列且中间无子
        val oppKing = if (side == RED) B_KING else R_KING
        val oppIdx = board.indexOfFirst { it == oppKing }
        if (oppIdx >= 0 && oppIdx % COLS == kingIdx % COLS) {
            val col = kingIdx % COLS
            val r1 = kingIdx / COLS
            val r2 = oppIdx / COLS
            var blocked = false
            for (rr in (min(r1, r2) + 1) until max(r1, r2)) {
                if (board[rr * COLS + col] != EMPTY) blocked = true
            }
            if (!blocked) return true
        }
        return false
    }

    fun hasAnyMove(board: IntArray, side: Int): Boolean = generateMoves(board, side).isNotEmpty()

    /** 该棋子中文名（用于 AI 调试/展示）。 */
    fun sideName(side: Int): String = if (side == RED) "红方" else "黑方"
}
