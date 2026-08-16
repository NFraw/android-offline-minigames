package com.minigames.collection.common

import android.content.Context

/** 用户配置持久化（音效/震动开关等）。 */
class SettingsStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) {
            prefs.edit().putBoolean("sound", v).apply()
        }

    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic", true)
        set(v) {
            prefs.edit().putBoolean("haptic", v).apply()
        }

    fun clearScores() {
        context.getSharedPreferences("scores", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
