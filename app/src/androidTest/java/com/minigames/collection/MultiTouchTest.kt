package com.minigames.collection

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.minigames.collection.TestGames.boolVal
import com.minigames.collection.TestGames.floatVal
import com.minigames.collection.TestGames.intVal
import com.minigames.collection.TestGames.withGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 多点触控专项测试：向双人游戏注入双指事件，验证两名玩家可同时独立操作。
 * 直接在游戏 View 上 dispatchTouchEvent，逻辑等价于真实多点输入。
 *
 * 座位约定：玩家 1 在下半屏、玩家 2 在上半屏（与全库一致）。
 */
@RunWith(AndroidJUnit4::class)
class MultiTouchTest {

    private var downTime = 1000L

    private fun props(count: Int, vararg pts: Pair<Float, Float>) =
        Array(count) { i ->
            MotionEvent.PointerProperties().apply {
                id = i
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        } to Array(count) { i ->
            MotionEvent.PointerCoords().apply {
                x = pts[i].first
                y = pts[i].second
                pressure = 1f
                size = 1f
            }
        }

    private fun event(action: Int, count: Int, pts: Array<out Pair<Float, Float>>, time: Long): MotionEvent {
        val (p, c) = props(count, *pts)
        return MotionEvent.obtain(downTime, time, action, count, p, c,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    /** 按下第 [id] 根手指（id=0 为 ACTION_DOWN，其余为 ACTION_POINTER_DOWN）。 */
    private fun down(id: Int, count: Int, vararg pts: Pair<Float, Float>): MotionEvent {
        val action = if (id == 0) MotionEvent.ACTION_DOWN
        else MotionEvent.ACTION_POINTER_DOWN or (id shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return event(action, count, pts, downTime + id * 16L)
    }

    /** 抬起第 [id] 根手指。 */
    private fun up(id: Int, count: Int, vararg pts: Pair<Float, Float>): MotionEvent {
        val action = if (id == 0) MotionEvent.ACTION_UP
        else MotionEvent.ACTION_POINTER_UP or (id shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return event(action, count, pts, downTime + 200L + id * 16L)
    }

    private fun move(time: Long, vararg pts: Pair<Float, Float>) =
        event(MotionEvent.ACTION_MOVE, pts.size, pts, time)

    private fun dispatch(view: View, ev: MotionEvent) {
        try {
            view.dispatchTouchEvent(ev)
        } finally {
            ev.recycle()
        }
    }

    /** 乒乓：双指各自拖动上下半屏的球拍（上半屏 = 玩家 2，下半屏 = 玩家 1）。 */
    @Test
    fun pongTwoFingersMoveBothPaddles() {
        withGame("pong") { view ->
            downTime = 1000L
            val p1 = floatVal(view, "p1y")
            val p2 = floatVal(view, "p2y")

            dispatch(view, down(0, 1, 200f to 300f))                              // 上半屏 → 玩家 2
            dispatch(view, down(1, 2, 200f to 300f, 800f to 2000f))                // 下半屏 → 玩家 1
            dispatch(view, move(1100L, 600f to 300f, 300f to 2000f))
            dispatch(view, move(1200L, 900f to 300f, 300f to 2000f))

            val p1After = floatVal(view, "p1y")
            val p2After = floatVal(view, "p2y")
            assertTrue("玩家2（上）球拍未跟随移动: $p2 -> $p2After", Math.abs(p2After - p2) > 100f)
            assertTrue("玩家1（下）球拍未跟随移动: $p1 -> $p1After", Math.abs(p1After - p1) > 100f)
            assertTrue("玩家2 球拍应移到 900 附近: $p2After", Math.abs(p2After - 900f) < 30f)
            assertTrue("玩家1 球拍应移到 300 附近: $p1After", Math.abs(p1After - 300f) < 30f)
        }
    }

    /** 乒乓：同一半屏多按一根手指再抬起，不应把已有球拍"锁死"。 */
    @Test
    fun pongExtraFingerDoesNotLockPaddle() {
        withGame("pong") { view ->
            downTime = 2000L
            dispatch(view, down(0, 1, 200f to 2000f))                       // 玩家 1 按住下半屏
            dispatch(view, down(1, 2, 200f to 2000f, 400f to 2100f))        // 同半屏又落一根手指
            dispatch(view, up(1, 2, 200f to 2000f, 400f to 2100f))          // 抬起第二根
            dispatch(view, move(2300L, 950f to 2000f))                      // 第一根继续拖动
            val p1 = floatVal(view, "p1y")
            assertTrue("抬起多余手指后球拍被锁死: $p1", Math.abs(p1 - 950f) < 30f)
        }
    }

    /** 贪吃蛇对战（上下分屏）：双指各自滑动启动两条蛇。 */
    @Test
    fun snakeBattleTwoFingersStartBothSnakes() {
        withGame("snakebattle") { view ->
            downTime = 3000L
            dispatch(view, down(0, 1, 200f to 400f))                          // 上半屏（玩家 1）
            dispatch(view, move(3050L, 700f to 400f))
            dispatch(view, down(1, 2, 200f to 400f, 900f to 1600f))           // 下半屏（玩家 2）
            dispatch(view, move(3100L, 700f to 400f, 500f to 1600f))

            assertTrue("玩家1 蛇未启动", boolVal(view, "started1"))
            assertTrue("玩家2 蛇未启动", boolVal(view, "started2"))
        }
    }

    /** 反应力对决：双指同时点击，只有一次计入得分（第二指到来时本轮已结算）。 */
    @Test
    fun reactionBothTapsHandled() {
        withGame("reaction") { view ->
            downTime = 4000L
            dispatch(view, down(0, 1, 300f to 300f))                          // 上半屏 → 玩家 2
            dispatch(view, down(1, 2, 300f to 300f, 700f to 2000f))           // 下半屏 → 玩家 1
            val s1 = intVal(view, "s1")
            val s2 = intVal(view, "s2")
            assertEquals("等待阶段双指点击应恰好结算一次抢拍", 1, s1 + s2)
        }
    }

    /** 抢答算术：两根手指必须都能敲到自己的键盘（第二指为 POINTER_DOWN）。 */
    @Test
    fun mathDuelAcceptsSecondFinger() {
        withGame("math_duel") { view ->
            downTime = 5000L
            // phase 0 = 难度选择：点第 1 个模式按钮（左侧按钮中心）
            dispatch(view, down(0, 1, (TestGames.W * 0.3f) to (TestGames.H * 0.34f + 90f)))
            assertEquals("未进入对战阶段", 1, intVal(view, "phase"))

            // 第一根手指停在两块键盘之间的空白带（不触发任何按键），第二根手指去点玩家 2 的「1」键。
            // 第二根手指产生的是 ACTION_POINTER_DOWN —— 旧实现只认 ACTION_DOWN，会把它整段丢弃。
            downTime = 6000L
            val neutralY = TestGames.H / 2f + 10f
            val p2Key1X = 154f
            val p2Key1Y = TestGames.H / 2f + 420f
            dispatch(view, down(0, 1, 540f to neutralY))
            dispatch(view, down(1, 2, 540f to neutralY, p2Key1X to p2Key1Y))

            assertEquals("第二根手指的按键被丢弃", "1", TestGames.anyVal<String>(view, "input2"))
        }
    }
}
