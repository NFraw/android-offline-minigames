package com.minigames.collection

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.minigames.collection.common.GameRegistry
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameSpec
import com.minigames.collection.common.GameView
import com.minigames.collection.common.HapticManager
import com.minigames.collection.common.ScoreStore
import com.minigames.collection.common.SettingsStore
import com.minigames.collection.common.SoundManager

/** 统一游戏容器：顶栏（返回/标题/得分/重开/暂停）+ 游戏视图 + 结算弹窗。 */
class GameActivity : Activity() {

    private lateinit var spec: GameSpec
    private lateinit var game: GameView
    private lateinit var sound: SoundManager
    private lateinit var settings: SettingsStore
    private lateinit var store: ScoreStore
    private lateinit var scoreView: TextView
    private lateinit var pauseBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val gameId = intent.getStringExtra("game")
        spec = GameRegistry.byId(gameId ?: "") ?: run { finish(); return }
        settings = SettingsStore(this)
        sound = SoundManager(this, settings)
        store = ScoreStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F6FA.toInt())
        }

        // ---- 顶栏 ----
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        bar.addView(toolButton("‹", 28f) { finish() }, LinearLayout.LayoutParams(dp(46), dp(44)))
        bar.addView(TextView(this).apply {
            text = spec.name
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1C2333.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        scoreView = TextView(this).apply {
            text = "得分 0"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF4A6CF7.toInt())
            gravity = Gravity.CENTER
        }
        bar.addView(scoreView, LinearLayout.LayoutParams(dp(88), dp(44)))
        bar.addView(toolButton("?", 20f) { showHelp() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        bar.addView(toolButton("↻", 20f) { game.reset() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        pauseBtn = toolButton("⏸", 17f) { togglePause() }
        bar.addView(pauseBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)))

        // ---- 游戏视图（反射创建，由注册表驱动） ----
        val container = FrameLayout(this)
        val cls = Class.forName(spec.className).getConstructor(Context::class.java).newInstance(this)
        game = cls as GameView
        game.sound = sound
        game.haptic = HapticManager(this, settings)
        game.callbacks = object : GameView.Callbacks {
            override fun onScoreChanged(score: Int) {
                scoreView.text = "得分 " + score
            }

            override fun onGameOver(result: GameResult) {
                showGameOver(result)
            }
        }
        container.addView(game, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(container, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
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
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle(spec.emoji + " " + spec.name)
            .setMessage(spec.desc + "\n\n" + spec.hint)
            .setPositiveButton("知道了", null)
            .show()
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

    private fun toolButton(label: String, size: Float, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(0xFF4A6CF7.toInt())
            background = rounded(0xFFEDF0FF.toInt(), dp(12).toFloat())
            setOnClickListener { onClick() }
        }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
