package com.minigames.collection.games.xiangqi

import kotlin.math.max
import kotlin.math.min

/** 象棋 AI：MiniMax + Alpha-Beta 剪枝，深度 3，同分随机选择增加变化。 */
object XiangqiAi {

    private val PIECE_VALUE = mapOf(
        XiangqiRules.R_KING to 10000, XiangqiRules.R_ADVISOR to 120, XiangqiRules.R_ELEPHANT to 120,
        XiangqiRules.R_HORSE to 270, XiangqiRules.R_ROOK to 600, XiangqiRules.R_CANNON to 285, XiangqiRules.R_PAWN to 80,
        XiangqiRules.B_KING to 10000, XiangqiRules.B_ADVISOR to 120, XiangqiRules.B_ELEPHANT to 120,
        XiangqiRules.B_HORSE to 270, XiangqiRules.B_ROOK to 600, XiangqiRules.B_CANNON to 285, XiangqiRules.B_PAWN to 80
    )

    fun evaluate(board: IntArray): Int {
        var score = 0
        for (i in board.indices) {
            val p = board[i]
            if (p == XiangqiRules.EMPTY) continue
            val v = PIECE_VALUE[p] ?: 0
            score += if (XiangqiRules.sideOf(p) == XiangqiRules.RED) v else -v
        }
        return score
    }

    fun bestMove(board: IntArray, side: Int, depth: Int = 3): XiangqiRules.Move? {
        val moves = XiangqiRules.generateMoves(board, side)
        if (moves.isEmpty()) return null
        var bestScore = if (side == XiangqiRules.RED) Int.MIN_VALUE else Int.MAX_VALUE
        val best = ArrayList<XiangqiRules.Move>()
        for (m in moves) {
            val copy = board.copyOf()
            copy[m.to] = copy[m.from]
            copy[m.from] = XiangqiRules.EMPTY
            val s = minimax(copy, depth - 1, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2, 1 - side)
            if (side == XiangqiRules.RED) {
                if (s > bestScore) {
                    bestScore = s
                    best.clear()
                    best.add(m)
                } else if (s == bestScore) {
                    best.add(m)
                }
            } else {
                if (s < bestScore) {
                    bestScore = s
                    best.clear()
                    best.add(m)
                } else if (s == bestScore) {
                    best.add(m)
                }
            }
        }
        return best.randomOrNull()
    }

    private fun minimax(board: IntArray, depth: Int, alpha: Int, beta: Int, side: Int): Int {
        val moves = XiangqiRules.generateMoves(board, side)
        if (moves.isEmpty()) {
            // 无子可走：判负（深度越大越早发现越好）
            return if (side == XiangqiRules.RED) -100000 + depth else 100000 - depth
        }
        if (depth == 0) return evaluate(board)
        if (side == XiangqiRules.RED) {
            var best = Int.MIN_VALUE / 2
            var a = alpha
            for (m in moves) {
                val copy = board.copyOf()
                copy[m.to] = copy[m.from]
                copy[m.from] = XiangqiRules.EMPTY
                val s = minimax(copy, depth - 1, a, beta, XiangqiRules.BLACK)
                best = max(best, s)
                a = max(a, best)
                if (beta <= a) break
            }
            return best
        } else {
            var best = Int.MAX_VALUE / 2
            var b = beta
            for (m in moves) {
                val copy = board.copyOf()
                copy[m.to] = copy[m.from]
                copy[m.from] = XiangqiRules.EMPTY
                val s = minimax(copy, depth - 1, alpha, b, XiangqiRules.RED)
                best = min(best, s)
                b = min(b, best)
                if (b <= alpha) break
            }
            return best
        }
    }
}
