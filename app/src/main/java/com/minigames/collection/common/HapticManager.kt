package com.minigames.collection.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** 轻震动反馈（受设置页开关控制）。 */
class HapticManager(context: Context, private val settings: SettingsStore) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun buzz(millis: Long = 25) {
        if (!settings.hapticEnabled || vibrator == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(millis)
        }
    }
}
