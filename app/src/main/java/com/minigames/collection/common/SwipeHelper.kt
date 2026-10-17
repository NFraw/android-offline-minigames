package com.minigames.collection.common

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent

/**
 * 滑动手势识别工具类。
 *
 * 功能：将原始触摸事件中的快速滑动（Fling）识别为四个方向之一（上下左右），
 * 通过回调返回 ±1 的方向向量。主要用于 2048、华容道等需要四方向滑动的游戏。
 *
 * 实现原理：
 *   内部使用 Android GestureDetector 的 onFling 回调——Fling 是用户手指快速滑动后抬起的
 *   手势，有明确的速度向量 (vx, vy)。再用系统的最小 fling 速度阈值过滤慢速拖动，
 *   比较 |vx| 和 |vy| 确定主方向，然后取正负确定具体方向。
 *
 *   注意阈值必须用 ViewConfiguration.getScaledMinimumFlingVelocity()（单位 px/s，已按屏幕密度
 *   换算），不能写死像素值：写死 80px/s 在 3x 密度屏上只相当于 27dp/s，轻轻一碰就触发滑动，
 *   在 1x 屏上又需要大幅甩动，同一手势在不同机型上手感完全不同。
 *
 * 被调用位置：
 *   - Game2048View、SlidingView 等需要滑动控制的游戏 View：
 *     在 onTouch() 中调用 swipeHelper.onTouch(event) 转发事件
 *
 * 使用示例：
 *   ```
 *   private val swipe = SwipeHelper(context) { dx, dy -> move(dx, dy) }
 *   override fun onTouch(event: MotionEvent) { swipe.onTouch(event) }
 *   ```
 *
 * @param context Android 上下文，传递给 GestureDetector
 * @param onSwipe 方向回调，dx/dy 为 ±1（如右滑返回 (1,0)，上滑返回 (0,-1)）
 */
class SwipeHelper(context: Context, private val onSwipe: (dx: Int, dy: Int) -> Unit) {

    /** 系统最小 fling 速度阈值（像素/秒，已按密度换算）。 */
    private val minVelocity = android.view.ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        /**
         * Fling 回调：手指快速滑动后抬起时触发。
         * @param e1 起点 MotionEvent（可能为 null，需安全访问）
         * @param e2 终点 MotionEvent
         * @param vx 水平速度（像素/秒，正值向右）
         * @param vy 垂直速度（像素/秒，正值向下）
         */
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (Math.abs(vx) < minVelocity && Math.abs(vy) < minVelocity) return false  // 速度太慢，忽略
            if (Math.abs(vx) > Math.abs(vy)) {
                onSwipe(if (vx > 0) 1 else -1, 0)   // 水平方向为主
            } else {
                onSwipe(0, if (vy > 0) 1 else -1)   // 垂直方向为主
            }
            return true
        }
    })

    /**
     * 将触摸事件转发给手势识别器。
     * 在 GameView.onTouch() 中调用此方法。
     * @return true 表示事件已消费
     */
    fun onTouch(event: MotionEvent): Boolean = detector.onTouchEvent(event)
}
