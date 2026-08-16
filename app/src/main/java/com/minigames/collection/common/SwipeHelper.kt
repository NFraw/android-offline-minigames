package com.minigames.collection.common

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent

/** 滑动识别：onSwipe(dx, dy) 中 dx/dy 为 ±1 方向。 */
class SwipeHelper(context: Context, private val onSwipe: (dx: Int, dy: Int) -> Unit) {

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (Math.abs(vx) < 80 && Math.abs(vy) < 80) return false
            if (Math.abs(vx) > Math.abs(vy)) {
                onSwipe(if (vx > 0) 1 else -1, 0)
            } else {
                onSwipe(0, if (vy > 0) 1 else -1)
            }
            return true
        }
    })

    fun onTouch(event: MotionEvent): Boolean = detector.onTouchEvent(event)
}
