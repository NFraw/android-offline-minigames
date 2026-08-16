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
import com.minigames.collection.common.Ui
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

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF141A2E.toInt()) }

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

        // 右下角：得分胶囊（细窄，不遮挡游戏顶部状态与玩法区）
        scoreView = TextView(this).apply {
            text = "得分 0"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = rounded(0xB31C2333.toInt(), dp(16).toFloat())
            setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        overlay.addView(scoreView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.RIGHT).apply { rightMargin = dp(12); bottomMargin = dp(12) })

        root.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)

        // 全部游戏先展示规则介绍，点击「开始游戏」后再开局（带电脑模式的再选模式）
        showRulesDialog(root)
    }

    /** 规则介绍：进入游戏前展示玩法说明，点击开始后创建游戏视图。 */
    private fun showRulesDialog(root: FrameLayout) {
        val d = AlertDialog.Builder(this)
            .setTitle(spec.emoji + " " + spec.name)
            .setMessage(spec.desc + "\n\n" + spec.hint)
            .setCancelable(false)
            .setPositiveButton("开始游戏") { _, _ ->
                if (spec.aiClass != null) showModeDialog(root) else createGame(spec.className, root)
            }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
        Ui.styleDialog(d, resources.displayMetrics.density)
    }

    /** 反射创建指定模式的游戏视图并挂载到容器。 */
    private fun createGame(className: String, root: FrameLayout) {
        val cls = Class.forName(className).getConstructor(Context::class.java).newInstance(this)
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
        root.addView(game, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** 双人游戏带电脑对战模式时，进入后弹出模式选择（自定义按钮，兼容所有系统版本）。 */
    private fun showModeDialog(root: FrameLayout) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val d = AlertDialog.Builder(this)
            .setTitle(spec.emoji + " " + spec.name)
            .setMessage("请选择对战模式")
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .create()
        container.addView(dialogActionButton("👥 双人对战") {
            // 先关闭模式对话框再创建游戏，否则对话框会一直盖在游戏上层（且不可点击外部关闭）
            d.dismiss()
            createGame(spec.className, root)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
            bottomMargin = dp(12)
        })
        container.addView(dialogActionButton("🤖 电脑对战") {
            d.dismiss()
            createGame(spec.aiClass!!, root)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        d.show()
        Ui.styleDialog(d, resources.displayMetrics.density)
    }

    /** 模式选择对话框中的大按钮。 */
    private fun dialogActionButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = Ui.rounded(0xFF4A6CF7.toInt(), 14f, resources.displayMetrics.density)
            setOnClickListener { onClick() }
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
        container.addView(dialogSwitchRow("🗣 语音播报", settings.voiceEnabled) { on ->
            settings.voiceEnabled = on
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
            textSize = 27f
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

        val d = AlertDialog.Builder(this)
            .setTitle(if (r.won) "🎉 胜利" else "游戏结束")
            .setMessage(text)
            .setCancelable(false)
            .setPositiveButton("再来一局") { _, _ -> game.reset() }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
        Ui.styleDialog(d, resources.displayMetrics.density)
    }

    private fun overlayBtn(label: String, size: Float, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            // 深色圆角胶囊 + 细描边，浅色游戏画面上也清晰可辨
            background = Ui.rounded(0xC91C2333.toInt(), 14f, resources.displayMetrics.density,
                stroke = 1, strokeColor = 0x33FFFFFF.toInt())
            setOnClickListener { onClick() }
        }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}