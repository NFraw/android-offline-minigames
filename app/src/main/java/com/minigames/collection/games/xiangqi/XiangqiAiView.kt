package com.minigames.collection.games.xiangqi

import android.content.Context
import com.minigames.collection.common.SoundManager

/**
 * 象棋（人机）：玩家执红先手，AI 执黑应战。
 * AI 在后台线程计算（深度 3 MiniMax），完成后回到主线程走子。
 */
class XiangqiAiView(context: Context) : XiangqiBaseView(context) {

    override fun onIntersectionTap(idx: Int) {
        if (turn != XiangqiRules.RED || aiThinking) return
        if (selected < 0) {
            if (isMyPiece(idx)) {
                selected = idx
                legalTargets.clear()
                for (t in XiangqiRules.targets(board, idx, board[idx])) {
                    if (XiangqiRules.isLegalMove(board, idx, t)) legalTargets.add(t)
                }
                playSound(SoundManager.CLICK)
            }
            return
        }
        if (idx == selected) {
            selected = -1
            legalTargets.clear()
            return
        }
        if (isMyPiece(idx)) {
            selected = idx
            legalTargets.clear()
            for (t in XiangqiRules.targets(board, idx, board[idx])) {
                if (XiangqiRules.isLegalMove(board, idx, t)) legalTargets.add(t)
            }
            playSound(SoundManager.CLICK)
            return
        }
        if (idx in legalTargets) {
            doMove(XiangqiRules.Move(selected, idx))
        } else {
            selected = -1
            legalTargets.clear()
        }
    }

    override fun onMoveCompleted(move: XiangqiRules.Move) {
        if (checkGameOver(XiangqiRules.BLACK)) return
        turn = XiangqiRules.BLACK
        updateStatus()
        aiThinking = true
        val snapshot = board.copyOf()
        Thread {
            val aiMove = XiangqiAi.bestMove(snapshot, XiangqiRules.BLACK, 3)
            post {
                aiThinking = false
                if (gameOverFlag) return@post
                if (aiMove != null) {
                    board = snapshot.copyOf()
                    doMove(aiMove)
                    if (checkGameOver(XiangqiRules.RED)) return@post
                    turn = XiangqiRules.RED
                    updateStatus()
                } else {
                    if (checkGameOver(XiangqiRules.BLACK)) return@post
                    turn = XiangqiRules.RED
                    updateStatus()
                }
            }
        }.start()
    }

    override fun getStatusLine(): String {
        if (gameOverFlag) return ""
        return if (aiThinking) {
            "🤖 电脑思考中…"
        } else if (turn == XiangqiRules.RED) {
            "你（红方）走子" + (if (statusText.isNotEmpty()) " · " + statusText else "")
        } else {
            "电脑（黑方）回合"
        }
    }
}
