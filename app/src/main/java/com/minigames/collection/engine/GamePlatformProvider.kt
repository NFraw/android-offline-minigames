package com.minigames.collection.engine

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.minigames.collection.common.*
import org.json.JSONArray
import org.json.JSONObject

class GamePlatformProvider : ContentProvider() {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var scores: ScoreStore
    private lateinit var settings: SettingsStore
    private lateinit var sound: SoundManager
    private lateinit var haptic: HapticManager
    private lateinit var voice: VoiceManager
    override fun onCreate(): Boolean {
        val context = requireNotNull(context)
        scores = ScoreStore(context); settings = SettingsStore(context)
        sound = SoundManager(context, settings); haptic = HapticManager(context, settings)
        voice = VoiceManager(context, settings)
        return true
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val gameId = extras?.getString("game").orEmpty()
        require(gameId in setOf("hall", "lab") || GameRegistry.byId(gameId)?.runtime is GameRuntime.Godot)
        return when (method) {
            "configuration" -> Bundle().apply {
                putInt("best", if (gameId in setOf("hall", "lab")) 0 else scores.best(gameId))
                putBoolean("sound", settings.soundEnabled); putBoolean("haptic", settings.hapticEnabled)
                putBoolean("voice", settings.voiceEnabled)
            }
            "catalog" -> Bundle().apply {
                val games = JSONArray()
                GameRegistry.games.forEach { spec ->
                    games.put(JSONObject().apply {
                        put("id", spec.id); put("best", scores.best(spec.id))
                        put("history", JSONArray(scores.history(spec.id)))
                    })
                }
                putString("games", games.toString())
            }
            "complete" -> {
                require(gameId !in setOf("hall", "lab"))
                val payload = requireNotNull(extras)
                val accepted = scores.saveOnce(gameId, payload.getInt("score"), requireNotNull(payload.getString("round")))
                Bundle().apply { putBoolean("accepted", accepted); putInt("best", scores.best(gameId)) }
            }
            "settings" -> {
                val payload = requireNotNull(extras)
                settings.soundEnabled = payload.getBoolean("sound", settings.soundEnabled)
                settings.hapticEnabled = payload.getBoolean("haptic", settings.hapticEnabled)
                settings.voiceEnabled = payload.getBoolean("voice", settings.voiceEnabled)
                Bundle.EMPTY
            }
            "clear_scores" -> { settings.clearScores(); Bundle.EMPTY }
            "feedback" -> {
                main.post {
                    sound.playCue(extras?.getString("sound").orEmpty())
                    if (extras?.getBoolean("haptic") == true) haptic.buzz()
                }
                Bundle.EMPTY
            }
            "voice" -> {
                val tag = extras?.getString("tag").orEmpty()
                if (tag in VoiceManager.SUPPORTED_TAGS) main.post { voice.speak(tag) }
                Bundle.EMPTY
            }
            "pulse" -> {
                val millis = (extras?.getInt("milliseconds", 25) ?: 25).coerceIn(1, 300).toLong()
                main.post { haptic.buzz(millis) }
                Bundle.EMPTY
            }
            else -> throw IllegalArgumentException("未知平台方法：$method")
        }
    }
    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, sort: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
