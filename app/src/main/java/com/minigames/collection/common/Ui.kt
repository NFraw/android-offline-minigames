package com.minigames.collection.common

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable

/**
 * Activity 侧共享 UI 工具集。
 *
 * 功能：提供圆角矩形背景生成和 AlertDialog 统一样式，保证整个 App 的弹窗和按钮外观一致。
 * 为什么用 object：Kotlin 的 object 是单例——全局只有一份实例，不需要创建对象即可调用方法，
 * 相当于 Java 的 static 工具类。
 *
 * 被调用位置：
 *   - GameActivity：overlayBtn() 创建悬浮按钮、showSettingsDialog() 样式弹窗
 *   - MainActivity：创建芯片/卡片背景、历史记录弹窗样式
 *   - SettingsActivity：设置页按钮背景
 *   - GameActivity.showGameOver()：游戏结束弹窗样式
 *
 * 扩展提示：
 *   如需新增全局 UI 工具方法（如统一按钮样式、颜色主题），在此 object 中添加即可。
 */
object Ui {

    /**
     * 生成圆角矩形 GradientDrawable，可带描边。
     * GradientDrawable 是 Android 的矢量图形类，可作为 View 的 background。
     * 相当于 XML 中的 <shape><corners/><solid/></shape>，但用代码动态创建。
     *
     * @param color 填充颜色（ARGB 格式，如 0xFF4A6CF7.toInt()）
     * @param radiusDp 圆角半径（dp 单位，会乘以 density 转为像素）
     * @param density 屏幕密度（resources.displayMetrics.density），用于 dp→px 转换
     * @param stroke 描边宽度（像素），0 表示无描边
     * @param strokeColor 描边颜色
     * @return 配置好的 GradientDrawable，可直接设为 View.background
     */
    fun rounded(color: Int, radiusDp: Float, density: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density  // dp × density = 像素值
            if (stroke > 0) {
                setStroke(stroke, strokeColor)
            }
        }

    /**
     * 统一 AlertDialog 的背景样式。
     * 系统默认的 AlertDialog 背景是方角的，这里替换为圆角白底，与 App 整体风格一致。
     *
     * @param dialog 需要美化的 AlertDialog 实例
     * @param density 屏幕密度，用于圆角半径计算
     */
    fun styleDialog(dialog: AlertDialog, density: Float) {
        dialog.window?.setBackgroundDrawable(rounded(0xFFFAFAFC.toInt(), 20f, density))
    }
}
