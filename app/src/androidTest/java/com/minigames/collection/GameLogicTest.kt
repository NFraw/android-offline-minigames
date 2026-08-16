package com.minigames.collection

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field

/**
 * 规则与布局专项测试：对棋盘类游戏反射验证初始布局与核心规则，
 * 防止"象棋一排兵"类初始化错误回归。
 */
@RunWith(AndroidJUnit4::class)
class GameLogicTest {

    private fun launch(gameId: String): ActivityScenario<GameActivity> =
        ActivityScenario.launch(Intent(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, GameActivity::class.java).putExtra("game", gameId))

    private fun findView(root: View, className: String): View? {
        if (root.javaClass.simpleName == className) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findView(root.getChildAt(i), className)
                if (found != null) return found
            }
        }
        return null
    }

    private fun fieldOf(obj: Any, name: String): Field {
        var f: Field? = null
        var cls: Class<*>? = obj.javaClass
        while (cls != null && f == null) {
            try {
                f = cls.getDeclaredField(name)
            } catch (e: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        f!!.isAccessible = true
        return f
    }

    private fun intArr(obj: Any, name: String): IntArray = fieldOf(obj, name).get(obj) as IntArray
    private fun boolArr(obj: Any, name: String): BooleanArray = fieldOf(obj, name).get(obj) as BooleanArray
    private fun intVal(obj: Any, name: String): Int = fieldOf(obj, name).getInt(obj)

    /** 暗棋：标准 32 子配子。 */
    @Test
    fun darkChessHasStandard32Pieces() {
        launch("darkchess").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "DarkChessView")!!
                val board = intArr(view, "board")
                assertEquals(32, board.size)
                assertEquals(32, board.count { it != 0 })
                // 红方 16 黑方 16
                assertEquals(16, board.count { it > 0 })
                assertEquals(16, board.count { it < 0 })
                // 配子：帅1 仕2 相2 车2 马2 炮2 兵5
                assertEquals(1, board.count { it == 11 })
                assertEquals(2, board.count { it == 12 })
                assertEquals(2, board.count { it == 13 })
                assertEquals(2, board.count { it == 14 })
                assertEquals(2, board.count { it == 15 })
                assertEquals(2, board.count { it == 16 })
                assertEquals(5, board.count { it == 17 })
                assertEquals(5, board.count { it == -17 })
                // 全部暗置
                val revealed = boolArr(view, "revealed")
                assertTrue(revealed.none { it })
            }
        }
    }

    /** 斗兽棋：8+8 子对称布局。 */
    @Test
    fun animalChessHas16Pieces() {
        launch("animal").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "AnimalView")!!
                val board = intArr(view, "board")
                assertEquals(63, board.size)
                assertEquals(16, board.count { it != 0 })
                assertEquals(8, board.count { it > 0 })
                assertEquals(8, board.count { it < 0 })
                // 每种动物 1 红 1 黑
                for (a in 1..8) {
                    assertEquals(1, board.count { it == a })
                    assertEquals(1, board.count { it == -a })
                }
                // 河区（3..5, 1..2 & 4..5）无棋子
                for (x in 3..5) for (y in intArrayOf(1, 2, 4, 5)) {
                    assertEquals(0, board[y * 9 + x])
                }
                // 兽穴在 (4,0) 与 (4,6)
                assertEquals(0, board[0 * 9 + 4])
                assertEquals(0, board[6 * 9 + 4])
            }
        }
    }

    /** 围棋：初始空盘。 */
    @Test
    fun weiqiStartsEmpty() {
        launch("weiqi").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "WeigiView")!!
                val board = intArr(view, "board")
                assertEquals(225, board.size)
                assertTrue(board.none { it != 0 })
            }
        }
    }

    /** 黑白棋：初始 4 子标准布局。 */
    @Test
    fun reversiStartsWith4CenterPieces() {
        launch("reversi").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "ReversiView")!!
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
    }

    /** 五子棋：空棋盘。 */
    @Test
    fun gomokuStartsEmpty() {
        launch("gomoku").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "GomokuView")!!
                val board = intArr(view, "board")
                assertEquals(225, board.size)
                assertTrue(board.none { it != 0 })
            }
        }
    }

    /** 数独：生成谜题行列宫无冲突，提示数 >= 36。 */
    @Test
    fun sudokuPuzzleIsValid() {
        launch("sudoku").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "SudokuView")!!
                val puzzle = intArr(view, "puzzle")
                val solution = intArr(view, "solution")
                val fixed = boolArr(view, "fixed")
                // 提示数
                assertTrue(fixed.count { it } >= 36)
                // 每行每列每宫无重复（solution）
                for (i in 0 until 9) {
                    val row = (0 until 9).map { solution[i * 9 + it] }
                    assertEquals(9, row.toSet().size)
                    val col = (0 until 9).map { solution[it * 9 + i] }
                    assertEquals(9, col.toSet().size)
                }
                for (br in 0 until 3) for (bc in 0 until 3) {
                    val box = (0 until 9).map { solution[(br * 3 + it / 3) * 9 + bc * 3 + it % 3] }
                    assertEquals(9, box.toSet().size)
                }
                // 谜题与解一致
                for (i in puzzle.indices) {
                    if (fixed[i]) assertEquals(solution[i], puzzle[i])
                }
            }
        }
    }

    /** 推箱子：箱子数 = 目标数。 */
    @Test
    fun sokobanBoxCountMatchesGoals() {
        launch("sokoban").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "SokobanView")!!
                val state = intArr(view, "state")
                val level = intArr(view, "level")
                assertEquals(state.count { it == 2 }, level.count { it == 3 })
                assertTrue(state.count { it == 2 } >= 1)
            }
        }
    }

    /** 三消：初始无三连。 */
    @Test
    fun match3StartsWithoutMatches() {
        launch("match3").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "Match3View")!!
                val board = intArr(view, "board")
                assertEquals(64, board.size)
                assertTrue(board.all { it in 1..5 })
                // 无横向三连
                for (r in 0 until 8) for (c in 0 until 6) {
                    val v = board[r * 8 + c]
                    assertFalse(v == board[r * 8 + c + 1] && v == board[r * 8 + c + 2])
                }
                // 无纵向三连
                for (r in 0 until 6) for (c in 0 until 8) {
                    val v = board[r * 8 + c]
                    assertFalse(v == board[(r + 1) * 8 + c] && v == board[(r + 2) * 8 + c])
                }
            }
        }
    }

    /** 记忆翻牌：16 张 8 对。 */
    @Test
    fun memoryHas8Pairs() {
        launch("memory").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "MemoryView")!!
                val cards = intArr(view, "cards")
                assertEquals(16, cards.size)
                val counts = IntArray(8)
                for (c in cards) counts[c]++
                for (c in counts) assertEquals(2, c)
            }
        }
    }

    /** 猜数字：4 位不重复。 */
    @Test
    fun guessSecretIs4UniqueDigits() {
        launch("guess").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "GuessDuelView")!!
                val secret = intArr(view, "secret")
                assertEquals(4, secret.size)
                assertEquals(4, secret.toSet().size)
                assertTrue(secret.all { it in 0..9 })
            }
        }
    }

    /** 21 点：牌堆完整 312 张（6 副）。 */
    @Test
    fun blackjackDeckIsFull() {
        launch("blackjack").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "BlackjackView")!!
                @Suppress("UNCHECKED_CAST")
                val deck = fieldOf(view, "deck").get(view) as ArrayList<*>
                @Suppress("UNCHECKED_CAST")
                val player = fieldOf(view, "player").get(view) as ArrayList<*>
                @Suppress("UNCHECKED_CAST")
                val dealer = fieldOf(view, "dealer").get(view) as ArrayList<*>
                assertEquals(312, deck.size + player.size + dealer.size)
                assertEquals(2, player.size)
                assertEquals(1, dealer.size)
            }
        }
    }

    /** 贪吃蛇对战：初始各 3 节、食物 3 个。 */
    @Test
    fun snakeBattleStartsProperly() {
        launch("snakebattle").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "SnakeBattleView")!!
                @Suppress("UNCHECKED_CAST")
                val body1 = fieldOf(view, "body1").get(view) as ArrayDeque<*>
                @Suppress("UNCHECKED_CAST")
                val body2 = fieldOf(view, "body2").get(view) as ArrayDeque<*>
                assertEquals(3, body1.size)
                assertEquals(3, body2.size)
            }
        }
    }

    /** 飞行棋：4 人全部在起点。 */
    @Test
    fun flightAllAtStart() {
        launch("flight").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "FlightView")!!
                val pos = intArr(view, "pos")
                assertEquals(4, pos.size)
                assertTrue(pos.all { it == 0 })
            }
        }
    }

    /** 象棋（双人）：32 子标准布局（含兵 5+5）。 */
    @Test
    fun xiangqiBoardIsStandard() {
        launch("xiangqi_duel").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "XiangqiDuelView")!!
                @Suppress("UNCHECKED_CAST")
                val board = fieldOf(view, "board").get(view) as IntArray
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
    }
}
