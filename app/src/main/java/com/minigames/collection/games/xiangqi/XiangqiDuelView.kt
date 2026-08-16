package com.minigames.collection.games.xiangqi

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import com.minigames.collection.common.SoundManager

/** 象棋（双人）：红方先手，轮流走子；悔棋需对方同意。 */
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

    /** 悔棋需对方同意：弹出确认框，对方同意后才撤销上一步。 */
    override fun requestUndo(side: Int) {
        if (gameOverFlag) return
        if (history.isEmpty()) {
            Toast.makeText(context, "还没有可撤销的步骤", Toast.LENGTH_SHORT).show()
            return
        }
        val opponent = 1 - side
        AlertDialog.Builder(context)
            .setTitle("悔棋申请")
            .setMessage(XiangqiRules.sideName(side) + "请求悔棋，撤销上一步。" + XiangqiRules.sideName(opponent) + "是否同意？")
            .setPositiveButton("同意") { _, _ -> undo() }
            .setNegativeButton("拒绝", null)
            .show()
    }
}
