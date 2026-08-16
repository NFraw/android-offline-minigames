package com.minigames.collection.games.xiangqi

import android.app.AlertDialog
import android.content.Context
import android.view.ViewGroup
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

    /** 悔棋需对方同意：弹出确认框，对方同意后才撤销上一步；每人每局限 3 次。 */
    override fun requestUndo(side: Int) {
        if (gameOverFlag) return
        if (undoLeft[side] <= 0) {
            Toast.makeText(context, XiangqiRules.sideName(side) + "本局悔棋次数已用完", Toast.LENGTH_SHORT).show()
            return
        }
        if (history.isEmpty()) {
            Toast.makeText(context, "还没有可撤销的步骤", Toast.LENGTH_SHORT).show()
            return
        }
        val opponent = 1 - side
        val dialog = AlertDialog.Builder(context)
            .setTitle("悔棋申请")
            .setMessage(
                XiangqiRules.sideName(side) + "请求悔棋，撤销上一步（剩余 " + undoLeft[side] + " 次）。\n" +
                    XiangqiRules.sideName(opponent) + "是否同意？"
            )
            .setPositiveButton("同意") { _, _ ->
                // 仅在真正撤销成功时才扣减次数，避免动画进行中撤销被跳过仍浪费次数
                if (undo()) undoLeft[side]--
            }
            .setNegativeButton("拒绝", null)
            .create()
        dialog.show()
        // 弹窗应朝向"被请求方"（即对方玩家）：红方悔棋时弹窗转 180° 朝向上方黑方玩家，
        // 黑方悔棋时弹窗保持正立朝向下方红方玩家。只有红方（下方）悔棋才需要旋转。
        // 只能旋转内容子视图（decorView 的直接子节点）：根视图的旋转不会参与触摸命中测试，
        // 旋转子视图后 ViewGroup 会按子视图矩阵反变换触摸点，按钮点击才能正常命中。
        if (side == XiangqiRules.RED) {
            val decor = dialog.window?.decorView as? ViewGroup
            if (decor != null && decor.childCount > 0) {
                decor.getChildAt(0).rotation = 180f
            }
        }
    }
}
