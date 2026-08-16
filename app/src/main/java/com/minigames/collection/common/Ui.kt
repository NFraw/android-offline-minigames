package com.minigames.collection.common

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable

/** Activity 侧共享 UI 工具：圆角 drawable、统一弹窗样式。 */
object Ui {

    /** 生成圆角矩形背景，可带描边。 */
    fun rounded(color: Int, radiusDp: Float, density: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
            if (stroke > 0) {
                setStroke(stroke, strokeColor)
            }
        }

    /** 给 AlertDialog 套上统一的圆角白底样式，去掉系统方角默认背景。 */
    fun styleDialog(dialog: AlertDialog, density: Float) {
        dialog.window?.setBackgroundDrawable(rounded(0xFFFAFAFC.toInt(), 20f, density))
    }
}
