package com.minigames.collection.games.xiangqi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiangqiRulesTest {

    @Test
    fun boardHasCorrectPieceCounts() {
        val b = XiangqiRules.initialBoard()
        // 红方：1帅 2仕 2相 2马 2车 2炮 5兵 = 16
        assertEquals(16, b.count { it != XiangqiRules.EMPTY && XiangqiRules.sideOf(it) == XiangqiRules.RED })
        assertEquals(16, b.count { it != XiangqiRules.EMPTY && XiangqiRules.sideOf(it) == XiangqiRules.BLACK })
        assertEquals(32, b.count { it != XiangqiRules.EMPTY })
        assertEquals(1, b.count { it == XiangqiRules.R_KING })
        assertEquals(2, b.count { it == XiangqiRules.R_ADVISOR })
        assertEquals(2, b.count { it == XiangqiRules.R_ELEPHANT })
        assertEquals(2, b.count { it == XiangqiRules.R_HORSE })
        assertEquals(2, b.count { it == XiangqiRules.R_ROOK })
        assertEquals(2, b.count { it == XiangqiRules.R_CANNON })
        assertEquals(5, b.count { it == XiangqiRules.R_PAWN })
        assertEquals(5, b.count { it == XiangqiRules.B_PAWN })
    }

    @Test
    fun pawnsAreOnCorrectColumns() {
        val b = XiangqiRules.initialBoard()
        // 黑卒第 3 行、红兵第 6 行，仅 0,2,4,6,8 列
        for (c in intArrayOf(0, 2, 4, 6, 8)) {
            assertEquals(XiangqiRules.B_PAWN, b[3 * XiangqiRules.COLS + c])
            assertEquals(XiangqiRules.R_PAWN, b[6 * XiangqiRules.COLS + c])
        }
        for (c in intArrayOf(1, 3, 5, 7)) {
            assertNotEquals(XiangqiRules.B_PAWN, b[3 * XiangqiRules.COLS + c])
            assertNotEquals(XiangqiRules.R_PAWN, b[6 * XiangqiRules.COLS + c])
        }
    }

    @Test
    fun redPawnMovesForwardAndSidewaysAfterRiver() {
        val b = XiangqiRules.initialBoard()
        // 红兵 (c=0, r=6) -> (c=0, r=5) 前进合法
        val from = 6 * XiangqiRules.COLS + 0
        val to = 5 * XiangqiRules.COLS + 0
        assertTrue("红兵应能前进", XiangqiRules.isLegalMove(b, from, to))
        // 未过河不能横走
        val side = 6 * XiangqiRules.COLS + 1
        assertFalse("未过河红兵不能横走", XiangqiRules.isLegalMove(b, from, side))
        // 过河后（r=4 已过河）可横走
        val b2 = b.copyOf()
        b2[4 * XiangqiRules.COLS + 0] = XiangqiRules.R_PAWN
        b2[from] = XiangqiRules.EMPTY
        assertTrue("过河红兵可横走", XiangqiRules.isLegalMove(b2, 4 * XiangqiRules.COLS + 0, 4 * XiangqiRules.COLS + 1))
    }

    @Test
    fun horseIsBlockedByLeg() {
        val b = XiangqiRules.initialBoard()
        // 初始布局：红马 (c=1,r=9)，上方 (1,8) 无子，可跳到 (2,7)/(0,7)
        val horse = 9 * XiangqiRules.COLS + 1
        val targets = XiangqiRules.targets(b, horse, XiangqiRules.R_HORSE)
        assertTrue("马应能跳到 (2,7)", (7 * XiangqiRules.COLS + 2) in targets)
        assertTrue("马应能跳到 (0,7)", (7 * XiangqiRules.COLS + 0) in targets)
        // 蹩马腿：把 (1,8) 放子
        val b2 = b.copyOf()
        b2[8 * XiangqiRules.COLS + 1] = XiangqiRules.R_PAWN
        val targets2 = XiangqiRules.targets(b2, horse, XiangqiRules.R_HORSE)
        assertFalse("蹩马腿后不能跳", (7 * XiangqiRules.COLS + 2) in targets2)
    }

    @Test
    fun cannonNeedsScreenToCapture() {
        val b = XiangqiRules.initialBoard()
        // 红炮 (c=1,r=7)：直线（列）上无子可走；隔子可吃
        val cannon = 7 * XiangqiRules.COLS + 1
        // 竖直向上 (1,6) 空 → 可走
        assertTrue((6 * XiangqiRules.COLS + 1) in XiangqiRules.targets(b, cannon, XiangqiRules.R_CANNON))
        // 横向：左侧 (0,7) 空 → 可走
        assertTrue((7 * XiangqiRules.COLS + 0) in XiangqiRules.targets(b, cannon, XiangqiRules.R_CANNON))
        // 炮架吃子规则：红炮 (1,7) -> 黑炮 (1,2)，中间 (1,6..1,3) 全空 → 无架不能吃
        val b3 = b.copyOf()
        assertFalse("无炮架不能吃子", (2 * XiangqiRules.COLS + 1) in XiangqiRules.targets(b3, cannon, XiangqiRules.R_CANNON))
        // 放一个炮架：红兵 (1,5)
        b3[5 * XiangqiRules.COLS + 1] = XiangqiRules.R_PAWN
        assertTrue("有炮架可吃子", (2 * XiangqiRules.COLS + 1) in XiangqiRules.targets(b3, cannon, XiangqiRules.R_CANNON))
    }

    @Test
    fun kingCannotFaceEnemyKing() {
        val b = IntArray(XiangqiRules.COLS * XiangqiRules.ROWS)
        // 两帅同列无遮挡：红帅 (4,9)，黑将 (4,0)
        b[9 * XiangqiRules.COLS + 4] = XiangqiRules.R_KING
        b[0 * XiangqiRules.COLS + 4] = XiangqiRules.B_KING
        assertTrue("对脸应被将军", XiangqiRules.isInCheck(b, XiangqiRules.RED))
        // 中间放子则不将军
        b[4 * XiangqiRules.COLS + 4] = XiangqiRules.R_ROOK
        assertFalse("有遮挡不对脸", XiangqiRules.isInCheck(b, XiangqiRules.RED))
    }

    @Test
    fun elephantCannotCrossRiver() {
        val b = XiangqiRules.initialBoard()
        val ele = 9 * XiangqiRules.COLS + 2
        // 红相 (2,9) 可到 (4,7)/(0,7)
        val targets = XiangqiRules.targets(b, ele, XiangqiRules.R_ELEPHANT)
        assertTrue((7 * XiangqiRules.COLS + 4) in targets)
        assertFalse("相不能过河", (5 * XiangqiRules.COLS + 2) in targets)
    }

    /**
     * 复现用户反馈的局面（截图）：中心列自上而下为
     * 黑將(4,0) / 黑士(4,1) / 黑砲(4,2) / **红炮(4,3)**（已选中）。
     * 红炮与黑士之间正好隔着黑砲这一枚炮架，应当能吃士。
     */
    @Test
    fun cannonCapturesAdvisorWithEnemyCannonAsScreen() {
        val b = IntArray(XiangqiRules.COLS * XiangqiRules.ROWS)
        b[0 * XiangqiRules.COLS + 4] = XiangqiRules.B_KING
        b[1 * XiangqiRules.COLS + 4] = XiangqiRules.B_ADVISOR
        b[2 * XiangqiRules.COLS + 4] = XiangqiRules.B_CANNON
        b[3 * XiangqiRules.COLS + 4] = XiangqiRules.R_CANNON
        b[9 * XiangqiRules.COLS + 4] = XiangqiRules.R_KING
        val cannon = 3 * XiangqiRules.COLS + 4
        val advisor = 1 * XiangqiRules.COLS + 4
        val targets = XiangqiRules.targets(b, cannon, XiangqiRules.R_CANNON)
        val desc = targets.joinToString { "(${it % XiangqiRules.COLS},${it / XiangqiRules.COLS})" }
        assertTrue("红炮应能隔黑砲吃黑士，实际可走: $desc", advisor in targets)
        assertTrue("吃士应为合法走法，实际可走: $desc", XiangqiRules.isLegalMove(b, cannon, advisor))
    }

    /**
     * 红炮在中心列、过河红兵充当炮架，黑方用士挡在将前 —— 红炮应能隔着红兵吃士。
     * 对应用户描述的「黑方支士/上象挡将之后，红炮吃不到士」。
     */
    @Test
    fun cannonCapturesAdvisorWithOwnPawnAsScreen() {
        val b = IntArray(XiangqiRules.COLS * XiangqiRules.ROWS)
        b[0 * XiangqiRules.COLS + 4] = XiangqiRules.B_KING
        b[1 * XiangqiRules.COLS + 4] = XiangqiRules.B_ADVISOR
        b[4 * XiangqiRules.COLS + 4] = XiangqiRules.R_PAWN
        b[7 * XiangqiRules.COLS + 4] = XiangqiRules.R_CANNON
        b[9 * XiangqiRules.COLS + 4] = XiangqiRules.R_KING
        val cannon = 7 * XiangqiRules.COLS + 4
        val advisor = 1 * XiangqiRules.COLS + 4
        val targets = XiangqiRules.targets(b, cannon, XiangqiRules.R_CANNON)
        val desc = targets.joinToString { "(${it % XiangqiRules.COLS},${it / XiangqiRules.COLS})" }
        assertTrue("红炮应能隔红兵吃黑士，实际可走: $desc", advisor in targets)
        assertTrue("吃士应为合法走法，实际可走: $desc", XiangqiRules.isLegalMove(b, cannon, advisor))
    }

    /**
     * 炮不能吃紧邻的棋子（中间没有炮架）—— 这是规则本身的要求，不是 bug。
     * 用户提到的「吃不到旁边的卒」属于这种情况。
     */
    @Test
    fun cannonCannotCaptureAdjacentPiece() {
        val b = IntArray(XiangqiRules.COLS * XiangqiRules.ROWS)
        b[0 * XiangqiRules.COLS + 4] = XiangqiRules.B_KING
        b[5 * XiangqiRules.COLS + 4] = XiangqiRules.B_PAWN
        b[6 * XiangqiRules.COLS + 4] = XiangqiRules.R_CANNON
        b[9 * XiangqiRules.COLS + 4] = XiangqiRules.R_KING
        val cannon = 6 * XiangqiRules.COLS + 4
        val adjacent = 5 * XiangqiRules.COLS + 4
        assertFalse(
            "炮不能吃紧邻棋子（无炮架）",
            adjacent in XiangqiRules.targets(b, cannon, XiangqiRules.R_CANNON))
    }

    /**
     * 红炮与黑士之间**没有任何棋子**时，黑士不能吃 —— 因为炮吃子必须恰好隔一枚炮架。
     * 这正是「黑方支士挡将之后，红炮吃不到那个士」的真正原因：
     * 士虽然和炮在同一条线上，但中间是空的，炮把它当成了"炮架"而不是"目标"。
     * 本用例锁定这个正确行为（避免以后被误当成缺陷去"修"）。
     */
    @Test
    fun cannonCannotCaptureAdvisorWithNothingBetween() {
        val b = IntArray(XiangqiRules.COLS * XiangqiRules.ROWS)
        b[0 * XiangqiRules.COLS + 4] = XiangqiRules.B_KING
        b[1 * XiangqiRules.COLS + 4] = XiangqiRules.B_ADVISOR
        // (4,2) 故意留空：炮与士之间没有炮架
        b[3 * XiangqiRules.COLS + 4] = XiangqiRules.R_CANNON
        b[9 * XiangqiRules.COLS + 4] = XiangqiRules.R_KING
        val cannon = 3 * XiangqiRules.COLS + 4
        val advisor = 1 * XiangqiRules.COLS + 4
        val targets = XiangqiRules.targets(b, cannon, XiangqiRules.R_CANNON)
        val desc = targets.joinToString { "(${it % XiangqiRules.COLS},${it / XiangqiRules.COLS})" }
        // 士被当作炮架，不是目标；炮只能走到它前面的空格 (4,2)
        assertFalse("无炮架时不能吃士，实际可走: $desc", advisor in targets)
        assertTrue("炮应能走到士前面的空格 (4,2)，实际可走: $desc", 2 * XiangqiRules.COLS + 4 in targets)
    }

    /**
     * 在「无炮架吃不到士」的局面里放一枚任意棋子当炮架，炮立刻就能吃士。
     * 说明问题只取决于炮架，与士本身无关。
     */
    @Test
    fun cannonCapturesAdvisorOnceScreenIsPresent() {
        val b = IntArray(XiangqiRules.COLS * XiangqiRules.ROWS)
        b[0 * XiangqiRules.COLS + 4] = XiangqiRules.B_KING
        b[1 * XiangqiRules.COLS + 4] = XiangqiRules.B_ADVISOR
        b[2 * XiangqiRules.COLS + 4] = XiangqiRules.B_CANNON   // 炮架
        b[3 * XiangqiRules.COLS + 4] = XiangqiRules.R_CANNON
        b[9 * XiangqiRules.COLS + 4] = XiangqiRules.R_KING
        val cannon = 3 * XiangqiRules.COLS + 4
        val advisor = 1 * XiangqiRules.COLS + 4
        assertTrue(
            "有炮架时就能吃士",
            advisor in XiangqiRules.targets(b, cannon, XiangqiRules.R_CANNON))
    }
}