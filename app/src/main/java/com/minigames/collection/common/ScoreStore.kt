package com.minigames.collection.common

import android.content.Context

/** 本地最高分持久化（SharedPreferences，纯离线）。 */
class ScoreStore(context: Context) {
    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)

    fun best(gameId: String): Int = prefs.getInt(gameId, 0)

    fun save(gameId: String, score: Int) {
        if (score > best(gameId)) {
            prefs.edit().putInt(gameId, score).apply()
        }
    }
}
