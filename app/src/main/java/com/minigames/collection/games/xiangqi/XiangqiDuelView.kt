package com.minigames.collection.games.xiangqi

import android.content.Context
import com.minigames.collection.common.SoundManager

/** 象棋（双人）：红方先手，轮流走子；支持悔棋。 */
class XiangqiDuelView(context: Context) : XiangqiBaseView(context) {

    override fun onIntersectionTap(idx: Int) {
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
        val next = 1 - turn
        if (checkGameOver(next)) return
        turn = next
        updateStatus()
        announceCheckIfAny()
    }

    override fun getStatusLine(): String =
        if (gameOverFlag) "" else XiangqiRules.sideName(turn) + "回合" + (if (statusText.isNotEmpty()) " · " + statusText else "")
}