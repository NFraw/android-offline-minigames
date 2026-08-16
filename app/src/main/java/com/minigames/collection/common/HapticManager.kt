package com.minigames.collection.common

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 轻震动反馈（受设置页开关控制，Android 13+ 统一 VibratorManager 接口）。 */
class HapticManager(context: Context, private val settings: SettingsStore) {

    private val vibrator: Vibrator? = try {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } catch (e: Exception) {
        null
    }

    fun buzz(millis: Long = 25) {
        if (!settings.hapticEnabled || vibrator == null) return
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            // 忽略震动异常（部分设备权限/硬件问题）
        }
    }
}
