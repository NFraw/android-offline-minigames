package com.minigames.collection

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
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
import com.minigames.collection.common.GameRegistry
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameSpec
import com.minigames.collection.common.GameView
import com.minigames.collection.common.HapticManager
import com.minigames.collection.common.ScoreStore
import com.minigames.collection.common.SettingsStore
import com.minigames.collection.common.SoundManager
import com.minigames.collection.common.VoiceManager

/**
 * 统一游戏容器：全屏沉浸式 + 悬浮控制层（退出/设置/暂停/重开/帮助/得分）。
 * 系统栏隐藏，游戏画面最大化；控制按钮半透明悬浮，不遮挡玩法。
 */
class GameActivity : Activity() {

    private lateinit var spec: GameSpec
    private lateinit var game: GameView
    private lateinit var sound: SoundManager
    private lateinit var voice: VoiceManager
    private lateinit var settings: SettingsStore
    private lateinit var store: ScoreStore
    private lateinit var scoreView: TextView
    private lateinit var pauseBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersive()

        val gameId = intent.getStringExtra("game")
        spec = GameRegistry.byId(gameId ?: "") ?: run { finish(); return }
        settings = SettingsStore(this)
        sound = SoundManager(this, settings)
        voice = VoiceManager(this, settings)
        store = ScoreStore(this)

        // ---- 创建游戏视图（反射） ----
        val cls = Class.forName(spec.className).getConstructor(Context::class.java).newInstance(this)
        game = cls as GameView
        game.sound = sound
        game.haptic = HapticManager(this, settings)
        game.voice = voice
        game.callbacks = object : GameView.Callbacks {
            override fun onScoreChanged(score: Int) {
                scoreView.text = "得分 " + score
            }

            override fun onGameOver(result: GameResult) {
                showGameOver(result)
            }
        }

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF141A2E.toInt()) }
        root.addView(game, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // ---- 悬浮控制层 ----
        val overlay = FrameLayout(this)

        // 左上：退出 + 设置
        val leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftCol.addView(overlayBtn("‹", 26f) { finish() }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
        leftCol.addView(overlayBtn("⚙", 19f) { showSettingsDialog() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        overlay.addView(leftCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.LEFT).apply { setMargins(dp(12), dp(28), 0, 0) })

        // 右上：暂停 / 重开 / 帮助
        val rightCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pauseBtn = overlayBtn("⏸", 15f) { togglePause() }
        rightCol.addView(pauseBtn, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
        rightCol.addView(overlayBtn("↻", 17f) { game.reset() }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
        rightCol.addView(overlayBtn("?", 17f) { showHelp() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        overlay.addView(rightCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.RIGHT).apply { setMargins(0, dp(28), dp(12), 0) })

        // 顶部中间：得分
        scoreView = TextView(this).apply {
            text = "得分 0"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = rounded(0x88000000.toInt(), dp(12).toFloat())
            setPadding(dp(16), dp(7), dp(16), dp(7))
        }
        overlay.addView(scoreView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(34) })

        root.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        if (::game.isInitialized && !game.paused) {
            game.pause()
            pauseBtn.text = "▶"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sound.isInitialized) sound.release()
        if (::voice.isInitialized) voice.release()
    }

    private fun enterImmersive() {
        // Android 13+（minSdk 33）：统一使用 WindowInsetsController
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
        if (game.paused) {
            game.resume()
            pauseBtn.text = "⏸"
        } else {
            game.pause()
            pauseBtn.text = "▶"
        }
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle(spec.emoji + " " + spec.name)
            .setMessage(spec.desc + "\n\n" + spec.hint)
            .setPositiveButton("知道了", null)
            .show()
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
        container.addView(dialogSwitchRow("🗣 语音播报", settings.voiceEnabled) { on ->
            settings.voiceEnabled = on
        })
        AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(container)
            .setPositiveButton("完成", null)
            .show()
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

    private fun showGameOver(r: GameResult) {
        val best = store.best(spec.id)
        val newBest = r.score > best
        if (newBest) store.save(spec.id, r.score)

        val msg = StringBuilder().append(r.summary)
        if (r.score > 0) {
            msg.append("\n\n本局得分：").append(r.score)
                .append("\n最高分：").append(if (newBest) r.score else best)
                .append(if (newBest) "\n🎉 新纪录！" else "")
        }
        val text = msg.toString()

        AlertDialog.Builder(this)
            .setTitle(if (r.won) "🎉 胜利" else "游戏结束")
            .setMessage(text)
            .setCancelable(false)
            .setPositiveButton("再来一局") { _, _ -> game.reset() }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
    }

    private fun overlayBtn(label: String, size: Float, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = rounded(0x99000000.toInt(), dp(12).toFloat())
            setOnClickListener { onClick() }
        }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}