package com.minigames.collection.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * 语音播报（受设置页"语音播报"开关控制）。
 *
 * ## 语音素材索引接口（无需改代码即可扩展）
 * 将 wav 文件放入 `app/src/main/res/raw/`，命名为 `voice_<tag>.wav` 即可自动生效：
 * 例如 voice_jiangjun.wav（将军）、voice_chi.wav（吃）、voice_jiangsi.wav（将死）、
 * voice_renshu.wav（认输）、voice_huiqi.wav（悔棋）、voice_heqi.wav（和棋）、
 * voice_win.wav（胜利）、voice_lose.wav（失败）、voice_start.wav（开局）……
 * 建议 16bit PCM 单声道 WAV。找不到对应文件时该 tag 静默跳过。
 */
class VoiceManager(context: Context, private val settings: SettingsStore) {

    private val pool: SoundPool
    private val voiceIds = HashMap<String, Int>()

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        for (tag in SUPPORTED_TAGS) {
            val id = context.resources.getIdentifier("voice_" + tag, "raw", context.packageName)
            if (id != 0) {
                voiceIds[tag] = pool.load(context, id, 1)
            }
        }
    }

    /** 播报指定 tag 的语音；开关关闭或素材缺失时静默。 */
    fun speak(tag: String) {
        if (!settings.voiceEnabled) return
        val id = voiceIds[tag] ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    companion object {
        const val JIANGJUN = "jiangjun"
        const val CHI = "chi"
        const val JIANGSI = "jiangsi"
        const val RENSHU = "renshu"
        const val HUIQI = "huiqi"
        const val HEQI = "heqi"
        const val WIN = "win"
        const val LOSE = "lose"
        const val START = "start"

        val SUPPORTED_TAGS = listOf(JIANGJUN, CHI, JIANGSI, RENSHU, HUIQI, HEQI, WIN, LOSE, START)
    }
}
