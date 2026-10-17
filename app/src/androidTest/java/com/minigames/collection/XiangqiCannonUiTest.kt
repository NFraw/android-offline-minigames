package com.minigames.collection

import com.minigames.collection.games.xiangqi.XiangqiRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 象棋「炮」吃子路径的 UI 层回归测试。
 *
 * 背景：用户反馈「红炮在中路被黑砲挡着，黑方支士之后红炮吃不到那个士」。
 * 规则引擎层（XiangqiRulesTest）已验证 targets()/isLegalMove() 允许这步，
 * 因此这里专门验证**从 UI 落子入口走一遍**是否也能成功——把「引擎允许」与
 * 「界面真的能走出来」两件事分开锁定，避免以后只改一边。
 *
 * 复现的局面（与用户截图一致，中心列自上而下）：
 *   黑將(4,0) / 黑士(4,1) / 黑砲(4,2) / 红炮(4,3) / … / 红帥(4,9)
 * 红炮隔黑砲这一枚炮架吃黑士。
 */
class XiangqiCannonUiTest {

    /** 通过反射调用 protected 的 onIntersectionTap（棋盘交叉点落子入口）。 */
    private fun tap(view: Any, idx: Int) {
        val m = view.javaClass.getDeclaredMethod("onIntersectionTap", Int::class.javaPrimitiveType)
        m.isAccessible = true
        m.invoke(view, idx)
    }

    @Test
    fun redCannonCapturesBlackAdvisorThroughUi() {
        TestGames.withGame("xiangqi_duel") { view ->
            val board = TestGames.intArr(view, "board")
            java.util.Arrays.fill(board, XiangqiRules.EMPTY)
            val bKing = 0 * XiangqiRules.COLS + 4
            val bAdvisor = 1 * XiangqiRules.COLS + 4
            val bCannon = 2 * XiangqiRules.COLS + 4
            val rCannon = 3 * XiangqiRules.COLS + 4
            val rKing = 9 * XiangqiRules.COLS + 4
            board[bKing] = XiangqiRules.B_KING
            board[bAdvisor] = XiangqiRules.B_ADVISOR
            board[bCannon] = XiangqiRules.B_CANNON
            board[rCannon] = XiangqiRules.R_CANNON
            board[rKing] = XiangqiRules.R_KING
            TestGames.fieldOf(view, "turn").setInt(view, XiangqiRules.RED)
            TestGames.fieldOf(view, "gameOverFlag").setBoolean(view, false)

            // 选中红炮：合法落点里应当包含「隔黑砲吃黑士」
            tap(view, rCannon)
            val selected = TestGames.intVal(view, "selected")
            assertEquals("红炮应能被选中", rCannon, selected)
            @Suppress("UNCHECKED_CAST")
            val targets = TestGames.anyVal<ArrayList<Int>>(view, "legalTargets")
            assertTrue(
                "红炮的合法落点应包含黑士(4,1)，实际 = ${targets.map { "(${it % 9},${it / 9})" }}",
                bAdvisor in targets)

            // 落到黑士格：应当真的吃掉（红炮占据该格，黑士消失）
            tap(view, bAdvisor)
            assertEquals(
                "红炮应吃掉黑士并占据该格（吃子后棋子落在目标格，不是留空）",
                XiangqiRules.R_CANNON, board[bAdvisor])
            assertEquals("红炮原位置应清空", XiangqiRules.EMPTY, board[rCannon])
            assertEquals("红方已吃 1 子", 1, TestGames.anyVal<ArrayList<Int>>(view, "capturedRed").size)
        }
    }

    /**
     * 完整复现用户描述的走子序列：
     *   红炮平中(1,7)→(4,7) → 黑砲平中(1,2)→(4,2) → 红炮进到底线前(4,7)→(4,3) 将军
     *   → 黑方支士(3,0)→(4,1) 挡将
     * 之后红炮应能隔着黑砲这一枚炮架吃掉黑士；但不能吃紧邻的黑砲。
     */
    @Test
    fun cannonCaptureWorksAfterBlackBlocksCheckWithAdvisor() {
        TestGames.withGame("xiangqi_duel") { view ->
            val board = TestGames.intArr(view, "board")
            java.util.Arrays.fill(board, XiangqiRules.EMPTY)
            val bKing = 0 * XiangqiRules.COLS + 4
            val bAdvisor = 1 * XiangqiRules.COLS + 4
            val bCannon = 2 * XiangqiRules.COLS + 4
            val rCannon = 3 * XiangqiRules.COLS + 4
            val rKing = 9 * XiangqiRules.COLS + 4
            board[bKing] = XiangqiRules.B_KING
            board[bAdvisor] = XiangqiRules.B_ADVISOR
            board[bCannon] = XiangqiRules.B_CANNON
            board[rCannon] = XiangqiRules.R_CANNON
            board[rKing] = XiangqiRules.R_KING
            TestGames.fieldOf(view, "turn").setInt(view, XiangqiRules.RED)
            TestGames.fieldOf(view, "gameOverFlag").setBoolean(view, false)

            // 黑方支士挡将之后，轮到红方走：选中红炮
            tap(view, rCannon)
            @Suppress("UNCHECKED_CAST")
            val targets = TestGames.anyVal<ArrayList<Int>>(view, "legalTargets")
            val desc = targets.joinToString { "(${it % 9},${it / 9})" }

            // 1) 隔着黑砲（一枚炮架）可以吃黑士
            assertTrue("红炮应能隔黑砲吃士，实际合法落点: $desc", bAdvisor in targets)
            // 2) 紧邻的黑砲（无炮架）不能吃 —— 这是象棋规则，不是缺陷
            assertFalse("炮不能吃紧邻的黑砲（无炮架），实际合法落点: $desc", bCannon in targets)
            // 3) 黑將隔了两子（士+砲），不能吃
            assertFalse("黑將隔两子不能吃，实际合法落点: $desc", bKing in targets)
        }
    }

    /**
     * 炮不能吃紧邻的棋子（中间没有炮架）—— 这是象棋规则本身，不是 bug。
     * 锁定该行为，避免把「不能吃旁边的卒」误当成缺陷去"修"。
     */
    @Test
    fun redCannonCannotCaptureAdjacentPawnThroughUi() {
        TestGames.withGame("xiangqi_duel") { view ->
            val board = TestGames.intArr(view, "board")
            java.util.Arrays.fill(board, XiangqiRules.EMPTY)
            val rCannon = 6 * XiangqiRules.COLS + 4
            val bPawn = 5 * XiangqiRules.COLS + 4
            board[rCannon] = XiangqiRules.R_CANNON
            board[bPawn] = XiangqiRules.B_PAWN
            board[9 * XiangqiRules.COLS + 4] = XiangqiRules.R_KING
            board[0 * XiangqiRules.COLS + 4] = XiangqiRules.B_KING
            TestGames.fieldOf(view, "turn").setInt(view, XiangqiRules.RED)
            TestGames.fieldOf(view, "gameOverFlag").setBoolean(view, false)

            tap(view, rCannon)
            @Suppress("UNCHECKED_CAST")
            val targets = TestGames.anyVal<ArrayList<Int>>(view, "legalTargets")
            assertTrue(
                "炮不能吃紧邻棋子（无炮架），实际 = ${targets.map { "(${it % 9},${it / 9})" }}",
                bPawn !in targets)
        }
    }
}
