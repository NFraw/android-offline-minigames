package com.minigames.collection.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.minigames.collection.R

/** 内置音效播放（SoundPool，全部离线资源），受设置页开关控制。 */
class SoundManager(context: Context, private val settings: SettingsStore) {

    private val pool: SoundPool
    private val loaded = mutableMapOf<Int, Int>()

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        listOf(CLICK, MOVE, EAT, WIN, LOSE).forEach { raw ->
            loaded[raw] = pool.load(context, raw, 1)
        }
    }

    fun play(rawId: Int) {
        if (!settings.soundEnabled) return
        val id = loaded[rawId] ?: return
        pool.play(id, 0.85f, 0.85f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    companion object {
        // 注意：不能用 const——R.raw 引用不会折叠成编译期常量，
        // 用普通 val 让 Companion 生成 getter，调用方按 getter 调用。
        val CLICK = R.raw.click
        val MOVE = R.raw.move
        val EAT = R.raw.eat
        val WIN = R.raw.win
        val LOSE = R.raw.lose
    }
}
