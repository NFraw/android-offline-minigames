package com.minigames.collection

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minigames.collection.TestGames.boolArr
import com.minigames.collection.TestGames.boolVal
import com.minigames.collection.TestGames.intArr
import com.minigames.collection.TestGames.intVal
import com.minigames.collection.TestGames.withGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 规则与初始布局专项测试：直接构造游戏 View 并反射校验内部状态，
 * 防止"象棋一排兵""黑白棋开局子数不对"这类初始化错误回归。
 *
 * 说明：游戏 View 由 TestGames.withGame(id) 直接构造（完成 measure/layout，
 * 等价于玩家点完「开始游戏」之后的状态），不再依赖 GameActivity 的对话框流程。
 */
@RunWith(AndroidJUnit4::class)
class GameLogicTest {

    /** 暗棋：标准 32 子配子（红 1..7 / 黑 11..17，均为正数编码）。 */
    @Test
    fun darkChessHasStandard32Pieces() {
        withGame("darkchess") { view ->
            val board = intArr(view, "board")
            assertEquals(32, board.size)
            assertEquals(32, board.count { it != 0 })
            // 红 16 + 黑 16
            assertEquals(16, board.count { it in 1..7 })
            assertEquals(16, board.count { it in 11..17 })
            // 红方配子：帅1 仕2 相2 车2 马2 炮2 兵5
            assertEquals(1, board.count { it == 1 })
            assertEquals(2, board.count { it == 2 })
            assertEquals(2, board.count { it == 3 })
            assertEquals(2, board.count { it == 4 })
            assertEquals(2, board.count { it == 5 })
            assertEquals(2, board.count { it == 6 })
            assertEquals(5, board.count { it == 7 })
            // 黑方配子：帅1 仕2 相2 车2 马2 炮2 兵5
            assertEquals(1, board.count { it == 11 })
            assertEquals(2, board.count { it == 12 })
            assertEquals(2, board.count { it == 13 })
            assertEquals(2, board.count { it == 14 })
            assertEquals(2, board.count { it == 15 })
            assertEquals(2, board.count { it == 16 })
            assertEquals(5, board.count { it == 17 })
            // 全部暗置
            val revealed = boolArr(view, "revealed")
            assertTrue(revealed.none { it })
        }
    }

    /** 斗兽棋：16 子（每样 1 红 1 黑），河区无子，兽穴空。 */
    @Test
    fun animalChessHas16Pieces() {
        withGame("animal") { view ->
            val board = intArr(view, "board")
            assertEquals(63, board.size)
            assertEquals(16, board.count { it != 0 })
            assertEquals(8, board.count { it > 0 })
            assertEquals(8, board.count { it < 0 })
            for (a in 1..8) {
                assertEquals(1, board.count { it == a })
                assertEquals(1, board.count { it == -a })
            }
            // 河区（x 3..5, y 1..2 & 4..5）初始无棋子
            for (x in 3..5) for (y in intArrayOf(1, 2, 4, 5)) {
                assertEquals(0, board[y * 9 + x])
            }
            // 兽穴在 (4,0) 与 (4,6)
            assertEquals(0, board[0 * 9 + 4])
            assertEquals(0, board[6 * 9 + 4])
        }
    }

    /** 围棋：初始空盘。 */
    @Test
    fun weiqiStartsEmpty() {
        withGame("weiqi") { view ->
            val board = intArr(view, "board")
            assertEquals(225, board.size)
            assertTrue(board.none { it != 0 })
        }
    }

    /** 黑白棋：初始 4 子标准布局。 */
    @Test
    fun reversiStartsWith4CenterPieces() {
        withGame("reversi") { view ->
            val board = intArr(view, "board")
            assertEquals(4, board.count { it != 0 })
            assertEquals(2, board.count { it == 1 })
            assertEquals(2, board.count { it == 2 })
            // 标准布局：(3,3)白 (3,4)黑 (4,3)黑 (4,4)白
            assertEquals(2, board[3 * 8 + 3])
            assertEquals(1, board[3 * 8 + 4])
            assertEquals(1, board[4 * 8 + 3])
            assertEquals(2, board[4 * 8 + 4])
        }
    }

    /** 五子棋：空棋盘。 */
    @Test
    fun gomokuStartsEmpty() {
        withGame("gomoku") { view ->
            val board = intArr(view, "board")
            assertEquals(225, board.size)
            assertTrue(board.none { it != 0 })
        }
    }

    /** 记忆翻牌：16 张 8 对。 */
    @Test
    fun memoryHas8Pairs() {
        withGame("memory") { view ->
            val cards = intArr(view, "cards")
            assertEquals(16, cards.size)
            val counts = IntArray(8)
            for (c in cards) counts[c]++
            for (c in counts) assertEquals(2, c)
        }
    }

    /** 贪吃蛇对战：初始各 3 节。 */
    @Test
    fun snakeBattleStartsProperly() {
        withGame("snakebattle") { view ->
            val body1: ArrayDeque<*> = TestGames.anyVal(view, "body1")
            val body2: ArrayDeque<*> = TestGames.anyVal(view, "body2")
            assertEquals(3, body1.size)
            assertEquals(3, body2.size)
        }
    }

    /**
     * 贪吃蛇对战：两条蛇开局不能在同一条横线相向而行。
     * 旧实现把两条蛇都放在 rows/2 且方向相对，双方各按一次「前进」4 拍后就头撞头双双暴毙。
     */
    @Test
    fun snakeBattleSnakesStartOnDifferentRows() {
        withGame("snakebattle") { view ->
            val body1: ArrayDeque<*> = TestGames.anyVal(view, "body1")
            val body2: ArrayDeque<*> = TestGames.anyVal(view, "body2")
            val head1 = body1.first()!!
            val head2 = body2.first()!!
            val y1 = TestGames.fieldOf(head1, "y").getInt(head1)
            val y2 = TestGames.fieldOf(head2, "y").getInt(head2)
            assertTrue("两条蛇开局不应在同一行（$y1 vs $y2）", y1 != y2)
        }
    }

    /** 象棋（双人）：32 子标准布局（含兵 5+5）。 */
    @Test
    fun xiangqiBoardIsStandard() {
        withGame("xiangqi_duel") { view ->
            val board = intArr(view, "board")
            assertEquals(90, board.size)
            assertEquals(32, board.count { it != 0 })
            assertEquals(5, board.count { it == 7 })
            assertEquals(5, board.count { it == 17 })
            // 兵在 0,2,4,6,8 列
            for (c in intArrayOf(0, 2, 4, 6, 8)) {
                assertEquals(7, board[6 * 9 + c])
                assertEquals(17, board[3 * 9 + c])
            }
        }
    }

    /** 扫雷：初始全暗、无旗、未布雷。 */
    @Test
    fun minesStartsHiddenWithoutMines() {
        withGame("mines") { view ->
            val revealed = boolArr(view, "revealed")
            val flagged = boolArr(view, "flagged")
            assertEquals(108, revealed.size)
            assertTrue(revealed.none { it })
            assertTrue(flagged.none { it })
            assertFalse("首击前不应布雷", boolVal(view, "minesPlaced"))
            assertEquals(0, intVal(view, "revealedCount"))
        }
    }

    /** 数字华容道：初始排列必须可解（空格在末格时逆序数为偶）。 */
    @Test
    fun slidingPuzzleIsSolvable() {
        withGame("sliding") { view ->
            val board = intArr(view, "board")
            assertEquals(16, board.size)
            assertEquals(0, board[15])
            var inversions = 0
            for (i in 0 until 15) for (j in i + 1 until 15) {
                if (board[i] != 0 && board[j] != 0 && board[i] > board[j]) inversions++
            }
            assertEquals("初始排列必须为偶逆序数（可解）", 0, inversions % 2)
        }
    }

    /** 俄罗斯方块：形状集合覆盖 7 种，且各形状都是 4 格，开局已有当前方块。 */
    @Test
    fun tetrisShapesAreWellFormed() {
        withGame("tetris") { view ->
            val shapes: List<*> = TestGames.anyVal(view, "SHAPES")
            assertEquals(7, shapes.size)
            for (s in shapes) {
                assertEquals(4, (s as List<*>).size)
            }
            assertTrue(TestGames.anyVal<List<*>>(view, "curCells").isNotEmpty())
        }
    }
}
