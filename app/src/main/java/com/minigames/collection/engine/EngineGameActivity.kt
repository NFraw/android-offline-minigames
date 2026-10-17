package com.minigames.collection.engine

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import com.minigames.collection.common.GameRegistry
import com.minigames.collection.common.GameRuntime
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

open class EngineGameActivity : GodotActivity() {
    internal val gameId: String by lazy { intent.getStringExtra("game") ?: "hall" }
    internal fun sceneFor(id: String): String = when (id) {
        "hall" -> "res://scenes/hall.tscn"
        "lab" -> "res://scenes/engine_lab.tscn"
        else -> (GameRegistry.byId(id)?.runtime as? GameRuntime.Godot)?.scenePath
            ?: error("未注册的引擎游戏：$id")
    }
    internal val scenePath: String by lazy { sceneFor(gameId) }
    internal val launchToken: String = UUID.randomUUID().toString()
    private var bridge: MiniGamesHost? = null
    private var pendingGame: String? = null
    @Volatile internal var showingHall: Boolean = true
    internal val updater by lazy { com.minigames.collection.update.AppUpdater(this) { showingHall } }
    override fun onCreate(savedInstanceState: Bundle?) {
        scenePath
        showingHall = gameId == "hall"
        applyGameOrientation(gameId)
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    // The same Activity hosts every game: update the Android window on every route.
    internal fun applyGameOrientation(id: String) {
        val direction = when {
            id == "hall" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            GameRegistry.byId(id)?.landscape == true -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        runOnUiThread { requestedOrientation = direction }
    }
    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        val requested = newIntent.getStringExtra("game")
        if (requested != null && requested != "hall") {
            pendingGame = requested
            bridge?.requestGame(requested)
        }
    }
    override fun onResume() { super.onResume(); updater.onResume() }
    override fun onDestroy() { updater.destroy(); super.onDestroy() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun getCommandLine(): MutableList<String> = super.getCommandLine().apply {
        addAll(listOf("--main-pack", "res://games.pck", "--rendering-method", "gl_compatibility"))
    }
    override fun getHostPlugins(engine: Godot): Set<GodotPlugin> {
        val plugin = bridge ?: MiniGamesHost(engine, this).also { bridge = it }
        pendingGame?.let { plugin.requestGame(it); pendingGame = null }
        return setOf(plugin)
    }
}

internal class MiniGamesHost(engine: Godot, private val host: EngineGameActivity) : GodotPlugin(engine) {
    private val endpoint = Uri.parse("content://" + host.packageName + ".game-platform")
    private var activeGame = host.gameId
    private var activeSession = host.launchToken
    private var loopStarted = false
    private var pendingGame: String? = null
    override fun getPluginName() = "MiniGamesHost"
    override fun getPluginSignals(): Set<SignalInfo> = setOf(SignalInfo("game_requested", String::class.java))
    override fun onGodotMainLoopStarted() {
        loopStarted = true
        pendingGame?.let { emitSignal("game_requested", it); pendingGame = null }
    }
    fun requestGame(id: String) {
        require(id == "hall" || GameRegistry.byId(id)?.runtime is GameRuntime.Godot)
        if (id == activeGame) return
        if (loopStarted) emitSignal("game_requested", id) else pendingGame = id
    }
    private fun call(method: String, payload: Bundle = Bundle()): Bundle {
        payload.putString("game", activeGame)
        return requireNotNull(host.contentResolver.call(endpoint, method, null, payload))
    }
    @UsedByGodot fun getLaunchConfig(): String {
        val config = call("configuration")
        return JSONObject().apply {
            put("game", activeGame); put("scene", host.sceneFor(activeGame)); put("session", activeSession)
            put("landscape", GameRegistry.byId(activeGame)?.landscape == true)
            put("best", config.getInt("best")); put("sound", config.getBoolean("sound"))
            put("haptic", config.getBoolean("haptic")); put("voice", config.getBoolean("voice"))
            if (activeGame == "hall") put("games", JSONArray(call("catalog").getString("games", "[]")))
        }.toString()
    }
    @UsedByGodot fun selectGame(id: String): String {
        require(id == "hall" || GameRegistry.byId(id)?.runtime is GameRuntime.Godot)
        activeGame = id
        host.showingHall = id == "hall"
        host.applyGameOrientation(id)
        activeSession = UUID.randomUUID().toString()
        return getLaunchConfig()
    }
    @UsedByGodot fun completeRound(round: Int, score: Int): String {
        require(activeGame != "hall" && round > 0 && score >= 0)
        val response = call("complete", Bundle().apply {
            putString("round", "$activeSession:$round"); putInt("score", score)
        })
        return JSONObject().put("accepted", response.getBoolean("accepted")).put("best", response.getInt("best")).toString()
    }
    @UsedByGodot fun setSettings(sound: Boolean, haptic: Boolean) {
        call("settings", Bundle().apply { putBoolean("sound", sound); putBoolean("haptic", haptic) })
    }
    @UsedByGodot fun setPreferences(sound: Boolean, haptic: Boolean, voice: Boolean) {
        call("settings", Bundle().apply { putBoolean("sound", sound); putBoolean("haptic", haptic); putBoolean("voice", voice) })
    }
    @UsedByGodot fun clearScores() { call("clear_scores") }
    @UsedByGodot fun checkForUpdates(manual: Boolean) {
        host.runOnUiThread { host.updater.check(manual) }
    }
    @UsedByGodot fun feedback(sound: String, haptic: Boolean) {
        call("feedback", Bundle().apply { putString("sound", sound); putBoolean("haptic", haptic) })
    }
    @UsedByGodot fun speak(tag: String) { call("voice", Bundle().apply { putString("tag", tag) }) }
    @UsedByGodot fun pulse(milliseconds: Int) { call("pulse", Bundle().apply { putInt("milliseconds", milliseconds) }) }
    @UsedByGodot fun exitToHall() { host.runOnUiThread { host.finish() } }
}
