package com.minigames.collection.common

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 震动反馈管理器。
 *
 * 功能：提供轻触震动（默认 25ms），用于游戏中的击中/得分/踩雷等即时反馈。
 * 使用 Android 13+（minSdk 33）的 VibratorManager API，兼容所有目标设备。
 *
 * 被调用位置：
 *   - GameActivity.createGame()：创建实例并注入到 GameView.haptic 属性
 *   - 各游戏 View 类通过 buzz() 方法调用（如 GomokuView 落子时、MinesView 踩雷时）
 *
 * 扩展提示：
 *   如需不同震动模式（如长震、节奏震），可增加 buzzLong() 或 buzzPattern() 方法，
 *   使用 VibrationEffect.createWaveform() 实现自定义节奏。
 *
 * @param context Android 上下文，用于获取系统震动服务
 * @param settings 设置存储，读取 hapticEnabled 开关
 */
class HapticManager(context: Context, private val settings: SettingsStore) {

    // Vibrator 实例，获取失败时为 null（如模拟器或权限被拒）
    private val vibrator: Vibrator? = try {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } catch (e: Exception) {
        null
    }

    /**
     * 触发一次轻震动。
     * @param millis 震动时长（毫秒），默认 25ms 为轻触级别
     */
    fun buzz(millis: Long = 25) {
        if (!settings.hapticEnabled || vibrator == null) return  // 开关关闭或无震动硬件时静默
        try {
            // createOneShot：单次震动，DEFAULT_AMPLITUDE 使用系统默认强度
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // 忽略震动异常（部分设备权限/硬件问题，不影响游戏正常运行）
        }
    }
}
