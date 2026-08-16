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
}