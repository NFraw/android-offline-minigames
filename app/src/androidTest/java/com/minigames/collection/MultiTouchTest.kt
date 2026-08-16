package com.minigames.collection

import android.content.Intent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 多点触控专项测试：向双人游戏注入双指事件，验证两名玩家可同时独立操作。
 * 直接在游戏 View 上 dispatchTouchEvent，逻辑等价于真实多点输入。
 */
@RunWith(AndroidJUnit4::class)
class MultiTouchTest {

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

    private fun readFloat(obj: Any, field: String): Float {
        val f = obj.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.getFloat(obj)
    }

    private fun readInt(obj: Any, field: String): Int {
        val f = obj.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.getInt(obj)
    }

    private fun launch(gameId: String): ActivityScenario<GameActivity> =
        ActivityScenario.launch(Intent(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, GameActivity::class.java).putExtra("game", gameId))

    private fun pointerDown(downTime: Long, eventTime: Long, x: Float, y: Float, pointerId: Int, count: Int): MotionEvent {
        val props = arrayOfNulls<MotionEvent.PointerProperties>(count)
        val coords = arrayOfNulls<MotionEvent.PointerCoords>(count)
        for (i in 0 until count) {
            props[i] = MotionEvent.PointerProperties().apply {
                id = i
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            coords[i] = MotionEvent.PointerCoords().apply {
                this.x = 0f
                this.y = 0f
                pressure = 1f
                size = 1f
            }
        }
        coords[pointerId]!!.x = x
        coords[pointerId]!!.y = y
        val action = if (pointerId == 0) {
            MotionEvent.ACTION_DOWN
        } else {
            MotionEvent.ACTION_POINTER_DOWN or (pointerId shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        return MotionEvent.obtain(downTime, eventTime, action, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun pointerMove(eventTime: Long, vararg pts: Pair<Float, Float>): MotionEvent {
        val count = pts.size
        val props = arrayOfNulls<MotionEvent.PointerProperties>(count)
        val coords = arrayOfNulls<MotionEvent.PointerCoords>(count)
        for (i in 0 until count) {
            props[i] = MotionEvent.PointerProperties().apply {
                id = i
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            coords[i] = MotionEvent.PointerCoords().apply {
                x = pts[i].first
                y = pts[i].second
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(0L, eventTime, MotionEvent.ACTION_MOVE, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }

    private fun pointerUp(downTime: Long, eventTime: Long, pointerId: Int, count: Int): MotionEvent =
        MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, 0f, 0f, 0)

    /** 乒乓：双指各自拖动上下半屏的球拍。 */
    @Test
    fun pongTwoFingersMoveBothPaddles() {
        launch("pong").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "PongView")!!
                assertTrue("PongView not found", view != null)
                val p1 = readFloat(view, "p1y")
                val p2 = readFloat(view, "p2y")
                val t0 = System.nanoTime() / 1000000
                // 玩家1 手指按下上半屏
                view.dispatchTouchEvent(pointerDown(t0, t0 + 16, 200f, 300f, 0, 1))
                // 玩家2 手指按下下半屏（POINTER_DOWN）
                view.dispatchTouchEvent(pointerDown(t0, t0 + 32, 800f, 2000f, 1, 2))
                // 各自移动
                view.dispatchTouchEvent(pointerMove(t0 + 48, 600f to 300f, 300f to 2000f))
                // 玩家1 继续移
                view.dispatchTouchEvent(pointerMove(t0 + 64, 900f to 300f, 300f to 2000f))
                view.dispatchTouchEvent(pointerUp(t0, t0 + 80, 0, 1))
                view.dispatchTouchEvent(pointerUp(t0, t0 + 96, 1, 1))
                val p1After = readFloat(view, "p1y")
                val p2After = readFloat(view, "p2y")
                assertTrue("玩家1 球拍未跟随移动: $p1 -> $p1After", Math.abs(p1After - p1) > 100f)
                assertTrue("玩家2 球拍未跟随移动: $p2 -> $p2After", Math.abs(p2After - p2) > 100f)
                assertTrue("玩家1 应移到 900 附近: $p1After", Math.abs(p1After - 900f) < 30f)
                assertTrue("玩家2 应移到 300 附近: $p2After", Math.abs(p2After - 300f) < 30f)
            }
        }
    }

    /** 贪吃蛇对战（上下分屏）：双指各自滑动启动两条蛇。 */
    @Test
    fun snakeBattleTwoFingersStartBothSnakes() {
        launch("snakebattle").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "SnakeBattleView")!!
                assertTrue("SnakeBattleView not found", view != null)
                val t0 = System.nanoTime() / 1000000
                // 玩家1 上半屏滑动（右）
                view.dispatchTouchEvent(pointerDown(t0, t0 + 16, 200f, 400f, 0, 1))
                view.dispatchTouchEvent(pointerMove(t0 + 32, 600f to 400f))
                // 玩家2 下半屏滑动（左）
                view.dispatchTouchEvent(pointerDown(t0 + 40, t0 + 48, 900f, 1600f, 1, 2))
                view.dispatchTouchEvent(pointerMove(t0 + 64, 500f to 1600f, 600f to 1600f))
                view.dispatchTouchEvent(pointerUp(t0, t0 + 80, 0, 1))
                view.dispatchTouchEvent(pointerUp(t0, t0 + 96, 1, 1))
                val s1 = readBooleanField(view, "started1")
                val s2 = readBooleanField(view, "started2")
                assertTrue("玩家1 蛇未启动", s1)
                assertTrue("玩家2 蛇未启动", s2)
            }
        }
    }

    private fun readBooleanField(obj: Any, field: String): Boolean {
        val f = obj.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.getBoolean(obj)
    }

    /** 反应力对决：双指同时抢拍，只有先处理的一指计分。 */
    @Test
    fun reactionBothTapsHandled() {
        launch("reaction").use { scenario ->
            scenario.onActivity { activity ->
                val view = findView(activity.window.decorView, "ReactionView")!!
                assertTrue("ReactionView not found", view != null)
                val t0 = System.nanoTime() / 1000000
                // 启动初期处于等待状态：双指同时点击 → 抢拍判对方得分（s1+s2 == 1）
                view.dispatchTouchEvent(pointerDown(t0, t0 + 16, 300f, 300f, 0, 1))
                view.dispatchTouchEvent(pointerDown(t0, t0 + 32, 700f, 2000f, 1, 2))
                view.dispatchTouchEvent(pointerUp(t0, t0 + 48, 0, 1))
                view.dispatchTouchEvent(pointerUp(t0, t0 + 64, 1, 1))
                val s1 = readInt(view, "s1")
                val s2 = readInt(view, "s2")
                assertEquals("抢拍应恰好计一分", 1, s1 + s2)
            }
        }
    }
}