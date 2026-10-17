package com.minigames.collection.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import com.minigames.collection.R
import java.util.concurrent.ConcurrentHashMap

/** 短音效统一预解码；最多4路。同音限频，事件上报不阻塞Godot渲染线程。 */
class SoundManager(context: Context, private val settings: SettingsStore) {
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = ConcurrentHashMap<Int, Int>()
    private val readySamples = ConcurrentHashMap.newKeySet<Int>()
    private val lastPlayed = mutableMapOf<Int, Long>()
    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) {
            readySamples.add(sampleId)
            if (readySamples.size == ALL.size) android.util.Log.i("MiniGamesAudio", "SFX_READY:" + ALL.size)
        } }
        ALL.forEach { raw -> loaded[raw] = pool.load(context, raw, 1) }
    }
    fun play(rawId: Int, volume: Float = 0.72f, rate: Float = 1f) {
        if (!settings.soundEnabled) return
        val sample = loaded[rawId] ?: return
        if (sample !in readySamples) return
        val now = SystemClock.elapsedRealtime()
        val cooldown = if (rawId == WIN_JINGLE || rawId == LOSE_JINGLE) 700L else 65L
        if (now - (lastPlayed[rawId] ?: -10000L) < cooldown) return
        lastPlayed[rawId] = now
        pool.play(sample, volume, volume, if (rawId in listOf(WIN_JINGLE, LOSE_JINGLE, UI_ERROR)) 2 else 1, 0, rate.coerceIn(0.5f,2f))
    }
    fun playCue(cue: String) {
        val piano = cue.removePrefix("piano").toIntOrNull()
        val simon = cue.removePrefix("simon").toIntOrNull()
        if (piano != null && cue.startsWith("piano")) {
            play(KEY_NOTE,0.64f,NOTES[piano.mod(NOTES.size)]); return
        }
        if (simon != null && cue.startsWith("simon")) {
            play(KEY_NOTE,0.62f,floatArrayOf(1f,1.2599f,1.4983f,2f)[simon.mod(4)]); return
        }
        val raw = CUES[cue] ?: return
        val volume = when (raw) { LASER -> 0.35f; UI_CLICK, UI_TICK -> 0.50f; WIN_JINGLE, LOSE_JINGLE -> 0.65f; else -> 0.72f }
        play(raw,volume)
    }
    fun release() { pool.release(); readySamples.clear(); loaded.clear(); lastPlayed.clear() }
    companion object {
        val CLICK = R.raw.click
        val MOVE = R.raw.move
        val EAT = R.raw.eat
        val WIN = R.raw.win
        val LOSE = R.raw.lose
        val BOARD_CAPTURE = R.raw.board_capture
        val BOARD_PLACE = R.raw.board_place
        val BOARD_SHUFFLE = R.raw.board_shuffle
        val BOARD_SLIDE = R.raw.board_slide
        val COIN = R.raw.coin
        val COMBO = R.raw.combo
        val COUNTDOWN = R.raw.countdown
        val EXPLODE = R.raw.explode
        val HIT_GLASS = R.raw.hit_glass
        val HIT_HEAVY = R.raw.hit_heavy
        val HIT_LIGHT = R.raw.hit_light
        val HIT_METAL = R.raw.hit_metal
        val HIT_SOFT = R.raw.hit_soft
        val LASER = R.raw.laser
        val LEVELUP = R.raw.levelup
        val LOSE_JINGLE = R.raw.lose_jingle
        val POWERUP = R.raw.powerup
        val UI_BACK = R.raw.ui_back
        val UI_CLICK = R.raw.ui_click
        val UI_CONFIRM = R.raw.ui_confirm
        val UI_ERROR = R.raw.ui_error
        val UI_ROLLOVER = R.raw.ui_rollover
        val UI_SWITCH = R.raw.ui_switch
        val UI_TICK = R.raw.ui_tick
        val WIN_JINGLE = R.raw.win_jingle
        val ZAP = R.raw.zap
        val DICE_ROLL = R.raw.dice_roll
        val KEY_NOTE = R.raw.key_note
        val CUES: Map<String,Int> = mapOf(
            "click" to CLICK, "move" to MOVE, "eat" to EAT, "hit" to EAT, "win" to WIN, "lose" to LOSE,
            "board_capture" to BOARD_CAPTURE,
            "board_place" to BOARD_PLACE,
            "board_shuffle" to BOARD_SHUFFLE,
            "board_slide" to BOARD_SLIDE,
            "coin" to COIN,
            "combo" to COMBO,
            "countdown" to COUNTDOWN,
            "explode" to EXPLODE,
            "hit_glass" to HIT_GLASS,
            "hit_heavy" to HIT_HEAVY,
            "hit_light" to HIT_LIGHT,
            "hit_metal" to HIT_METAL,
            "hit_soft" to HIT_SOFT,
            "laser" to LASER,
            "levelup" to LEVELUP,
            "lose_jingle" to LOSE_JINGLE,
            "powerup" to POWERUP,
            "ui_back" to UI_BACK,
            "ui_click" to UI_CLICK,
            "ui_confirm" to UI_CONFIRM,
            "ui_error" to UI_ERROR,
            "ui_rollover" to UI_ROLLOVER,
            "ui_switch" to UI_SWITCH,
            "ui_tick" to UI_TICK,
            "win_jingle" to WIN_JINGLE,
            "zap" to ZAP,
            "dice_roll" to DICE_ROLL,
            "key_note" to KEY_NOTE
        )
        val ALL = CUES.values.distinct()
        private val NOTES = floatArrayOf(1f,1.12246f,1.25992f,1.33484f,1.49831f,1.68179f,1.88775f,2f)
    }
}
