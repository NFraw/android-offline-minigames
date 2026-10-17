package com.minigames.collection.common

import android.content.Context

/**
 * 用户配置持久化存储。
 *
 * 功能：管理音效、震动、语音播报三个开关的读写，以及清空所有游戏最高分。
 * 存储方案：Android SharedPreferences（键值对 XML 文件，自动持久化到 /data/data/包名/shared_prefs/）。
 *     相当于 C++ 中的 .ini 配置文件读写，或 Java 的 Properties，但 Android 原生提供了这套机制。
 *
 * 被调用位置：
 *   - GameActivity.onCreate()：创建实例读取开关状态，传给 SoundManager/HapticManager/VoiceManager
 *   - SettingsActivity.onCreate()：创建实例，UI 开关 onChange 时写入
 *   - GameActivity.showSettingsDialog()：游戏内设置弹窗读写开关
 *
 * 扩展提示：
 *   新增配置项只需仿照 soundEnabled 加一个 var 属性 + 对应的 SharedPreferences key 即可。
 *   注意 apply() 是异步写盘（不阻塞 UI），commit() 是同步写盘（一般不需要）。
 *
 * @param context Android 上下文，用于获取 SharedPreferences 实例
 */
class SettingsStore(private val context: Context) {

    // SharedPreferences 实例，名称 "settings" 对应文件 /data/data/包名/shared_prefs/settings.xml
    // MODE_PRIVATE 表示仅本应用可访问（其他模式已废弃）
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * 音效开关（默认开启）。
     * 被 SoundManager.play() 读取——关闭时 play() 直接 return 不播放。
     * 被 SettingsActivity / GameActivity 设置弹窗写入。
     * Kotlin 属性语法糖：外部用 `settings.soundEnabled = true` 实际调用 set()，
     * 读取时调用 get()，对调用方透明。
     */
    var soundEnabled: Boolean
        get() = prefs.getBoolean("sound", true)  // key "sound"，默认 true
        set(v) {
            prefs.edit().putBoolean("sound", v).apply()  // apply() 异步写盘，不阻塞调用线程
        }

    /**
     * 震动反馈开关（默认开启）。
     * 被 HapticManager.buzz() 读取——关闭时跳过震动。
     */
    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic", true)
        set(v) {
            prefs.edit().putBoolean("haptic", v).apply()
        }

    /**
     * 语音播报开关（默认开启）。
     * 被 VoiceManager.speak() 读取——关闭时跳过播报。
     * 目前仅象棋/围棋等棋类游戏使用（将军、吃、将死等语音）。
     */
    var voiceEnabled: Boolean
        get() = prefs.getBoolean("voice", true)
        set(v) {
            prefs.edit().putBoolean("voice", v).apply()
        }

    /**
     * 清空所有游戏的最高分记录（保留对局历史）。
     * 直接操作 ScoreStore 使用的 "scores" SharedPreferences 文件。
     * 只删非 hist_ 前缀的键：早期实现用 edit().clear() 会把 hist_xxx 历史记录一并删光，
     * 与按钮文案「清空所有最高分」不符，还让主菜单的历史记录弹窗凭空消失。
     * 被 SettingsActivity 的「清空所有最高分」按钮调用（需二次确认弹窗）。
     */
    fun clearScores() {
        val scorePrefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)
        val e = scorePrefs.edit()
        for (key in scorePrefs.all.keys) {
            if (!key.startsWith("hist_") && !key.startsWith("seen_")) e.remove(key)
        }
        e.apply()
    }
}

