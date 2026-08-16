package com.minigames.collection

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.minigames.collection.common.GameRegistry
import com.minigames.collection.common.GameSpec
import com.minigames.collection.common.HapticManager
import com.minigames.collection.common.ScoreStore
import com.minigames.collection.common.SettingsStore
import com.minigames.collection.common.SoundManager
import com.minigames.collection.common.Ui
import com.minigames.collection.hd.Game2D
import com.minigames.collection.hd.HdGames

/**
 * libGDX 游戏宿主：全屏沉浸式，通过 game 参数选择 HD 游戏。
 * 在 GLSurfaceView 之上叠加悬浮控制层（退出/设置/暂停/重开/帮助），
 * 与经典版 GameActivity 交互一致；音效/震动/最高分注入游戏实例。
 */
class LgdxGameActivity : AndroidApplication() {

    private lateinit var game: Game2D
    private lateinit var spec: GameSpec
    private lateinit var sound: SoundManager
    private lateinit var settings: SettingsStore
    private lateinit var store: ScoreStore
    private lateinit var pauseBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onCreate(savedInstanceState)

        val gameId = intent.getStringExtra("game") ?: ""
        spec = GameRegistry.byId(gameId) ?: run {
            finish()
            return
        }
        settings = SettingsStore(this)
        sound = SoundManager(this, settings)
        store = ScoreStore(this)

        game = HdGames.create(gameId)
        game.sounds = sound
        game.haptic = HapticManager(this, settings)
        game.bestScore = store.best(game.gameId)
        game.onNewBest = { best -> store.save(game.gameId, best) }
        game.onEnd = { score, _ -> store.save(game.gameId, score) }

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useWakelock = true
        }
        initialize(game, config)

        enterImmersive()
        buildOverlay()
    }

    private fun buildOverlay() {
        val content = window.decorView.findViewById<FrameLayout>(android.R.id.content) ?: return
        val overlay = FrameLayout(this)

        // 左上：退出 + 设置
        val leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftCol.addView(overlayBtn("‹", 26f) { finish() },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
        leftCol.addView(overlayBtn("⚙", 19f) { showSettingsDialog() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        overlay.addView(leftCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.LEFT).apply { setMargins(dp(12), dp(28), 0, 0) })

        // 右上：暂停 / 重开 / 帮助
        val rightCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pauseBtn = overlayBtn("⏸", 15f) { togglePause() }
        rightCol.addView(pauseBtn,
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
        rightCol.addView(overlayBtn("↻", 17f) { game.restart() },
            LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
        rightCol.addView(overlayBtn("?", 17f) { showHelp() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        overlay.addView(rightCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.RIGHT).apply { setMargins(0, dp(28), dp(12), 0) })

        content.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        // 切后台自动暂停；恢复后由用户手动继续（与经典版一致）
        if (::game.isInitialized && !game.isUserPaused) {
            game.setUserPaused(true)
            if (::pauseBtn.isInitialized) pauseBtn.text = "▶"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sound.isInitialized) sound.release()
    }

    private fun enterImmersive() {
        window.setDecorFitsSystemWindows(false)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        window.decorView.windowInsetsController?.let { c ->
            c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun togglePause() {
        val paused = game.toggleUserPaused()
        pauseBtn.text = if (paused) "▶" else "⏸"
    }

    private fun showHelp() {
        val d = AlertDialog.Builder(this)
            .setTitle(spec.emoji + " " + spec.name)
            .setMessage(spec.desc + "\n\n" + spec.hint)
            .setPositiveButton("知道了", null)
            .show()
        Ui.styleDialog(d, resources.displayMetrics.density)
    }

    private fun showSettingsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        container.addView(dialogSwitchRow("🔊 音效", settings.soundEnabled) { on ->
            settings.soundEnabled = on
        })
        container.addView(dialogSwitchRow("📳 震动反馈", settings.hapticEnabled) { on ->
            settings.hapticEnabled = on
        })
        val d = AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(container)
            .setPositiveButton("完成", null)
            .show()
        Ui.styleDialog(d, resources.displayMetrics.density)
    }

    private fun dialogSwitchRow(title: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        row.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(0xFF1C2333.toInt())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply { isChecked = initial }
        sw.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        row.addView(sw)
        return row
    }

    private fun overlayBtn(label: String, size: Float, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = Ui.rounded(0xC91C2333.toInt(), 14f, resources.displayMetrics.density,
                stroke = 1, strokeColor = 0x33FFFFFF.toInt())
            setOnClickListener { onClick() }
        }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
