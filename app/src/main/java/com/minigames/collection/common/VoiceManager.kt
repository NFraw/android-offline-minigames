package com.minigames.collection.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * 语音播报管理器。
 *
 * 功能：播放中文语音片段（如"将军"、"吃"、"将死"），为棋类游戏提供听觉反馈。
 * 与 SoundManager 的区别：SoundManager 播放短促音效（click/eat），VoiceManager 播放语义化语音。
 * 两者底层都用 SoundPool，但 VoiceManager 使用 CONTENT_TYPE_SPEECH 而非 SONIFICATION。
 *
 * 语音素材索引接口（无需改代码即可扩展）：
 *   将 wav 文件放入 `app/src/main/res/raw/`，命名为 `voice_<tag>.wav`，并在 SOURCES 表中登记。
 *   建议格式：16bit PCM 单声道 WAV，采样率 22050Hz 或 44100Hz。
 *
 * 被调用位置：
 *   - GameActivity.onCreate()：创建实例并注入到 GameView.voice 属性
 *   - 各棋类游戏 View 类通过 speakVoice(VoiceManager.JIANGJUN) 等调用
 *   - GameActivity.onDestroy()：调用 release() 释放原生音频资源
 *
 * @param context Android 上下文，用于加载 raw 资源
 * @param settings 设置存储，读取 voiceEnabled 开关
 */
class VoiceManager(context: Context, private val settings: SettingsStore) {

    private val pool: SoundPool
    // tag 名称 → SoundPool 音频 ID 的映射
    private val voiceIds = HashMap<String, Int>()
    // 已完成解码的 tag（SoundPool.load 为异步，解码前 play() 不会发声）
    private val ready = HashSet<String>()
    // SoundPool 内部 id → tag 的反查表
    private val idToTag = HashMap<Int, String>()

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)  // 语音类型（区别于音效的 SONIFICATION）
            .build()
        pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            val tag = idToTag[sampleId] ?: return@setOnLoadCompleteListener
            if (status == 0) ready.add(tag)
        }
        // 显式登记资源 ID。
        // 旧实现用 resources.getIdentifier("voice_"+tag, ...) 按名字反射查找，一旦将来开启
        // shrinkResources/R8 资源压缩，这些"代码里没有静态引用"的 wav 会被判为无用资源删掉，
        // 语音会整体静默失效且不报任何错。显式 R.raw 引用同时解决这个问题和查找开销。
        for ((tag, resId) in SOURCES) {
            val id = pool.load(context, resId, 1)
            voiceIds[tag] = id
            idToTag[id] = tag
        }
        // 兜底：1.5 秒后仍未收到加载完成事件就放开播放
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (ready.isEmpty()) ready.addAll(voiceIds.keys)
        }, 1500)
    }

    /**
     * 播报指定 tag 的语音。
     * @param tag 语音标识，使用 companion object 中的常量（如 VoiceManager.JIANGJUN）
     */
    fun speak(tag: String) {
        if (!settings.voiceEnabled) return  // 开关关闭时静默
        val id = voiceIds[tag] ?: return     // 无对应素材时静默
        if (tag !in ready) return            // 尚未解码完成，跳过（避免无效调用）
        pool.play(id, 1f, 1f, 1, 0, 1f)     // 左右声道满音量，优先级 1，不循环
    }

    fun release() {
        pool.release()
    }

    companion object {
        // 语音 tag 常量——对应 res/raw/voice_xxx.wav 文件名中的 xxx 部分
        const val JIANGJUN = "jiangjun"   // 将军（象棋）
        const val CHI = "chi"             // 吃子（象棋/斗兽棋）
        const val JIANGSI = "jiangsi"     // 将死（象棋）
        const val RENSHU = "renshu"       // 认输（象棋）
        const val HUIQI = "huiqi"         // 悔棋（象棋）
        const val HEQI = "heqi"           // 和棋（象棋）
        const val WIN = "win"             // 胜利（通用）
        const val LOSE = "lose"           // 失败（通用）
        const val START = "start"         // 开局（通用）

        /** tag → 素材资源 ID 的显式映射表（新增语音在此登记一条即可）。 */
        private val SOURCES: Map<String, Int> = mapOf(
            JIANGJUN to com.minigames.collection.R.raw.voice_jiangjun,
            CHI to com.minigames.collection.R.raw.voice_chi,
            JIANGSI to com.minigames.collection.R.raw.voice_jiangsi,
            RENSHU to com.minigames.collection.R.raw.voice_renshu,
            HUIQI to com.minigames.collection.R.raw.voice_huiqi,
            HEQI to com.minigames.collection.R.raw.voice_heqi,
            WIN to com.minigames.collection.R.raw.voice_win,
            LOSE to com.minigames.collection.R.raw.voice_lose,
            START to com.minigames.collection.R.raw.voice_start
        )

        // 所有已知 tag 列表——保留给外部遍历/校验使用
        val SUPPORTED_TAGS = listOf(JIANGJUN, CHI, JIANGSI, RENSHU, HUIQI, HEQI, WIN, LOSE, START)
    }
}
