package com.minigames.collection

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.minigames.collection.common.GameRegistry
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 全游戏运行时健壮性测试（无需点开对话框，直接驱动 View 本体）。
 *
 * 与 AllGamesSmokeTest（只验证 Activity 能起来）不同，本测试对每一款游戏：
 *   1. 按手机竖屏尺寸完成 measure/layout —— 触发 onSizeChanged → onLayout → reset → start
 *   2. 断言帧循环确实启动了（isRunning）
 *   3. 在屏幕多个位置派发 ACTION_DOWN / MOVE / UP，覆盖坐标换算与多点触控分支
 *   4. 空跑约 3 秒的帧步进（180 × 16ms），覆盖 update 里的状态机
 * 任何一步抛异常都会把游戏 id 收集起来，最后统一断言，便于定位是哪一款游戏出问题。
 */
@RunWith(AndroidJUnit4::class)
class GameRuntimeTest {

    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 页面尺寸取常见手机竖屏（1080×2340 px）。 */
    private val viewW = 1080
    private val viewH = 2340

    private fun makeView(specClassName: String): GameView {
        val cls = Class.forName(specClassName)
        return cls.getConstructor(Context::class.java).newInstance(ctx) as GameView
    }

    /** update 可能由基类实现（如 XiangqiDuelView 继承 XiangqiBaseView），需沿继承链查找。 */
    private fun updateMethodOf(view: GameView): java.lang.reflect.Method {
        var cls: Class<*>? = view.javaClass
        while (cls != null) {
            try {
                return cls.getDeclaredMethod("update", java.lang.Float.TYPE).apply { isAccessible = true }
            } catch (e: NoSuchMethodException) {
                cls = cls.superclass
            }
        }
        throw NoSuchMethodException("找不到 update(Float)：" + view.javaClass.name)
    }

    @Test
    fun everyGameSurvivesLayoutTouchesAndFrames() {
        val failures = StringBuilder()
        var passed = 0

        // 同时覆盖保留的旧版 PlaneView；未来仅 Godot 的条目不会进入 View 测试。
        for (spec in GameRegistry.games.filter { it.runtime is com.minigames.collection.common.GameRuntime.Canvas || it.className.isNotBlank() }) {
            try {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val view = makeView((spec.runtime as? com.minigames.collection.common.GameRuntime.Canvas)?.viewClassName ?: spec.className)
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(viewW, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(viewH, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, viewW, viewH)
                    assertTrue("${spec.id} 布局后帧循环未启动", view.isRunning)

                    val update = updateMethodOf(view)
                    // 派发若干触摸：四角、中心、以及一条滑动轨迹
                    val points = listOf(
                        40f to 60f, (viewW - 40f) to 60f,
                        40f to (viewH - 60f), (viewW - 40f) to (viewH - 60f),
                        viewW / 2f to viewH / 2f,
                        viewW / 3f to viewH / 3f, viewW * 2f / 3f to viewH * 2f / 3f)
                    for ((x, y) in points) {
                        dispatch(view, MotionEvent.ACTION_DOWN, x, y)
                        dispatch(view, MotionEvent.ACTION_MOVE, x + 30f, y + 30f)
                        dispatch(view, MotionEvent.ACTION_UP, x + 30f, y + 30f)
                    }
                    // 空跑 180 帧（约 3 秒）驱动状态机
                    repeat(180) { update.invoke(view, 0.016f) }
                    // 再补一次触摸，确认推进后依然能安全接收输入
                    dispatch(view, MotionEvent.ACTION_DOWN, viewW / 2f, viewH / 4f)
                    dispatch(view, MotionEvent.ACTION_UP, viewW / 2f, viewH / 4f)
                }
                passed++
            } catch (t: Throwable) {
                failures.append("  ${spec.id} (${spec.name}): ${t.javaClass.simpleName} - ${t.message}\n")
            }
        }

        System.err.println("=== 运行时测试汇总：通过 $passed / ${GameRegistry.games.size} ===")
        assertTrue("以下游戏在布局/触摸/帧步进中失败:\n$failures", failures.isEmpty())
    }

    private fun dispatch(view: View, action: Int, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(now, now, action, x, y, 0)
        try {
            view.onTouchEvent(ev)
        } finally {
            ev.recycle()
        }
    }

    /**
     * gameOver 必须幂等。
     * 子类可能在递归（扫雷洪水填充）或多分支中重复调用它，旧实现会让结算框叠加弹出、
     * 并把同一局分数重复写入历史记录。
     */
    @Test
    fun gameOverIsDeliveredOnlyOnce() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = makeView("com.minigames.collection.games.mines.MinesView")
            view.measure(
                View.MeasureSpec.makeMeasureSpec(viewW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(viewH, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, viewW, viewH)

            var overCount = 0
            view.callbacks = object : GameView.Callbacks {
                override fun onScoreChanged(score: Int) {}
                override fun onGameOver(result: GameResult) {
                    overCount++
                }
            }
            val gameOver = GameView::class.java
                .getDeclaredMethod("gameOver", GameResult::class.java)
                .apply { isAccessible = true }
            gameOver.invoke(view, GameResult(0, false, "第一次"))
            gameOver.invoke(view, GameResult(0, false, "第二次"))
            gameOver.invoke(view, GameResult(0, false, "第三次"))
            assertEquals("gameOver 被重复投递，会导致结算框叠加", 1, overCount)
        }
    }

    /**
     * 帧回调不能重复注册。
     * 旧实现里每次 reset()/start() 都会 postFrameCallback，且从不注销；
     * 按 N 次「↻」重开就会永久多出 N 份回调，每帧执行 N 次 update + invalidate。
     */
    @Test
    fun repeatedStartDoesNotStackFrameCallbacks() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = makeView("com.minigames.collection.games.snake.SnakeGameView")
            view.measure(
                View.MeasureSpec.makeMeasureSpec(viewW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(viewH, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, viewW, viewH)

            val scheduled = GameView::class.java.getDeclaredField("frameScheduled").apply { isAccessible = true }
            // 连续重开 8 次，回调挂起标志应保持"已挂起"而不是越挂越多
            repeat(8) { view.reset() }
            assertTrue("重复 start() 后帧回调仍应处于已挂起状态", scheduled.getBoolean(view))
            assertTrue("重开后帧循环应处于运行中", view.isRunning)
        }
    }
}


