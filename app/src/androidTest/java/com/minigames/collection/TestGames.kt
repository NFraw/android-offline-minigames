package com.minigames.collection

import android.content.Context
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.minigames.collection.common.GameView
import java.lang.reflect.Field

/**
 * 仪器化测试的公共工具。
 *
 * 关键点：游戏 View 不再通过 GameActivity 获取。
 * GameActivity 在创建游戏前会先弹规则说明/双人对战同意页，直接 launch 后是拿不到游戏 View 的
 * （这正是本目录下老测试全部 NPE 的原因）。这里改为直接在主线程构造游戏 View 并完成
 * measure/layout —— layout 会触发 onSizeChanged → onLayout → reset → start，
 * 等价于"玩家点完开始按钮"之后的状态，且完全绕开对话框。
 */
object TestGames {

    /** 常见手机竖屏尺寸（px）。 */
    const val W = 1080
    const val H = 2400

    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * 在主线程构造并布局指定游戏的 View，执行 [block]，结束后暂停帧循环。
     * block 抛出的断言失败会被原样传播。
     */
    fun <T> withView(className: String, block: (GameView) -> T): T {
        val result = arrayOfNulls<Any>(1)
        var error: Throwable? = null
        var found = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = Class.forName(className)
                .getConstructor(Context::class.java)
                .newInstance(ctx) as GameView
            try {
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, W, H)
                result[0] = block(view)
                found = true
            } catch (t: Throwable) {
                error = t
            } finally {
                view.pause()  // 停掉帧循环，避免测试进程里残留一堆空转的 Choreographer 回调
            }
        }
        error?.let { throw it }
        check(found) { "withView 未正常执行：$className" }
        @Suppress("UNCHECKED_CAST")
        return result[0] as T
    }

    /** 按游戏注册表的 id（如 "mines"）构造 View。 */
    fun withGame(id: String, block: (GameView) -> Unit) {
        val spec = com.minigames.collection.common.GameRegistry.byId(id)
            ?: error("游戏未注册：$id")
        val className = (spec.runtime as? com.minigames.collection.common.GameRuntime.Canvas)?.viewClassName ?: spec.className
        require(className.isNotBlank()) { "该游戏使用 Godot，请运行引擎测试：$id" }
        withView(className, block)
    }

    /** 沿继承链查找字段（子类可能继承基类的 protected 字段）。 */
    fun fieldOf(obj: Any, name: String): Field {
        var cls: Class<*>? = obj.javaClass
        while (cls != null) {
            try {
                return cls.getDeclaredField(name).apply { isAccessible = true }
            } catch (e: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException("找不到字段 $name（${obj.javaClass.name}）")
    }

    fun intArr(obj: Any, name: String): IntArray = fieldOf(obj, name).get(obj) as IntArray
    fun boolArr(obj: Any, name: String): BooleanArray = fieldOf(obj, name).get(obj) as BooleanArray
    fun intVal(obj: Any, name: String): Int = fieldOf(obj, name).getInt(obj)
    fun floatVal(obj: Any, name: String): Float = fieldOf(obj, name).getFloat(obj)
    fun boolVal(obj: Any, name: String): Boolean = fieldOf(obj, name).getBoolean(obj)

    @Suppress("UNCHECKED_CAST")
    fun <T> anyVal(obj: Any, name: String): T = fieldOf(obj, name).get(obj) as T
}

