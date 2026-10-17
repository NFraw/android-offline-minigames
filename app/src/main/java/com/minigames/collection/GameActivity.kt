package com.minigames.collection

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.minigames.collection.common.DualConsentView
import com.minigames.collection.common.GameCategory
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
 * 统一游戏容器 Activity。
 *
 * 功能：所有游戏共用的全屏容器，负责：
 *   1. 全屏沉浸模式（隐藏状态栏/导航栏）
 *   2. 悬浮控制层（退出/设置/暂停/重开/帮助按钮 + 得分显示）
 *   3. 入场流程调度（模式选择 → 双人同意/规则弹窗 → 反射创建游戏 View）
 *   4. 游戏生命周期管理（暂停/恢复/销毁）
 *   5. 游戏结束结算（最高分判断、再来一局/返回菜单）
 *
 * Android 核心概念说明：
 *   - Activity：Android 的"页面"，相当于 C++/Java 中的窗口/Frame。每个 Activity 有自己的
 *     生命周期（onCreate→onStart→onResume→onPause→onStop→onDestroy），由系统管理。
 *   - Intent：页面间传参的机制。MainActivity 通过 Intent("game", "snake") 告诉 GameActivity
 *     要启动哪款游戏，GameActivity 通过 intent.getStringExtra("game") 读取。
 *   - setContentView()：设置页面的根布局 View，相当于 HTML 的 document.body。
 *
 * 被调用位置：
 *   - MainActivity：点击游戏卡片 → startActivity(Intent(this, GameActivity::class.java).putExtra("game", id))
 *
 * 代码纯 Kotlin + Android 原生 View（不用 XML 布局、不用 Jetpack Compose），
 * 所有 UI 元素（按钮、文字、容器）都在 onCreate() 中用代码动态创建。
 */
class GameActivity : Activity() {

    private lateinit var spec: GameSpec
    private val canvasClassName: String get() =
        (spec.runtime as? com.minigames.collection.common.GameRuntime.Canvas)?.viewClassName ?: spec.className
    private lateinit var game: GameView
    private lateinit var sound: SoundManager
    private lateinit var voice: VoiceManager
    private lateinit var settings: SettingsStore
    private lateinit var store: ScoreStore
    private lateinit var scoreView: TextView
    private lateinit var pauseBtn: TextView

    /**
     * Activity 创建入口（生命周期第一个回调）。
     * savedInstanceState 是系统恢复状态用的 Bundle（横竖屏切换/后台杀死时保存的数据），
     * 本项目不使用状态恢复，因此忽略。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)  // 游戏时保持屏幕常亮
        enterImmersive()  // 进入全屏沉浸模式

        val gameId = intent.getStringExtra("game")
        spec = GameRegistry.byId(gameId ?: "") ?: run { finish(); return }
        // 方向锁定：横屏游戏（如切水果、记忆对决）转横屏，其余全部锁定竖屏。
        // 竖屏锁定不是审美取舍而是必要修复——32 款游戏的 onLayout() 末尾都会调用 reset()，
        // 一旦发生旋转/尺寸变化就会把进行中的对局静默清空重开；部分游戏（俄罗斯方块、
        // 贪吃蛇）的布局在横屏下还会溢出错位。
        requestedOrientation = if (spec.landscape)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        settings = SettingsStore(this)
        sound = SoundManager(this, settings)
        voice = VoiceManager(this, settings)
        store = ScoreStore(this)

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF141A2E.toInt()) }

        // ---- 悬浮控制层 ----
        val overlay = FrameLayout(this)
        // sideControls 的游戏（反应力、抢答算术、乒乓等）把按钮挪到屏幕左右两侧的竖直居中位置。
        // 这些游戏需要整屏盲拍，四角的按钮正好落在玩家最常点击的区域，会吃掉触摸导致误退出/误重开。
        val leftGravity = if (spec.sideControls) Gravity.CENTER_VERTICAL or Gravity.LEFT
                          else Gravity.TOP or Gravity.LEFT
        val rightGravity = if (spec.sideControls) Gravity.CENTER_VERTICAL or Gravity.RIGHT
                           else Gravity.TOP or Gravity.RIGHT
        val leftTop = if (spec.sideControls) 0 else dp(28)
        val rightTop = if (spec.sideControls) 0 else dp(28)

        // 左上：退出 + 设置
        val leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftCol.addView(overlayBtn("‹", 26f) { finish() }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
        leftCol.addView(overlayBtn("⚙", 19f) { showSettingsDialog() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        overlay.addView(leftCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            leftGravity).apply { setMargins(dp(12), leftTop, 0, 0) })

        // 右上：暂停 / 重开 / 帮助
        val rightCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pauseBtn = overlayBtn("⏸", 15f) { togglePause() }
        rightCol.addView(pauseBtn, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
        rightCol.addView(overlayBtn("↻", 17f) { restartGame() }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(8) })
        rightCol.addView(overlayBtn("?", 17f) { showHelp() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        overlay.addView(rightCol, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            rightGravity).apply { setMargins(0, rightTop, dp(12), 0) })

        // 右下角：得分胶囊（细窄，不遮挡游戏顶部状态与玩法区）
        // 初始隐藏，等游戏真的产生分数（setScore > 0）再显示。
        // 14 款双人/多人棋类游戏从不计分，常驻的"得分 0"没有信息量，
        // 还会压住游戏底部的提示文字（反应力、乒乓、四人反应等都是居中长文案）。
        scoreView = TextView(this).apply {
            text = "得分 0"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = rounded(0xB31C2333.toInt(), dp(16).toFloat())
            setPadding(dp(16), dp(6), dp(16), dp(6))
            visibility = View.INVISIBLE
        }
        overlay.addView(scoreView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.RIGHT).apply { rightMargin = dp(12); bottomMargin = dp(12) })

        root.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)

        // 入场流程：可模式选择游戏先选模式（#23）；双人游戏走双视角规则+双方同意（#17）；
        // 其余（单人/多人/电脑模式）展示常规规则弹窗。
        when {
            spec.aiClass != null -> showModeDialog(root)
            spec.category == GameCategory.DUEL -> showDualConsent(root)
            else -> showRulesDialog(root)
        }
    }

    /** 规则介绍：进入游戏前展示玩法说明，点击开始后创建指定模式的游戏视图。 */
    private fun showRulesDialog(root: FrameLayout, className: String = canvasClassName) {
        val d = AlertDialog.Builder(this)
            .setTitle(spec.emoji + " " + spec.name)
            .setMessage(spec.desc + "\n\n" + spec.hint)
            .setCancelable(false)
            .setPositiveButton("开始游戏") { _, _ -> createGame(className, root) }
            .setNegativeButton("返回菜单") { _, _ -> finish() }
            .show()
        Ui.styleDialog(d, resources.displayMetrics.density)
    }

    /**
     * 通过反射创建游戏 View 实例并挂载到 FrameLayout。
     * 反射机制：className 是字符串（如 "com.minigames.collection.games.snake.SnakeGameView"），
     * Class.forName() 根据字符串找到对应的 Class 对象，再通过 getConstructor().newInstance() 创建实例。
     * 等价于 Java 的 new SnakeGameView(this)，但类名是动态的——这样 GameActivity 不需要直接依赖
     * 每个游戏的类，GameRegistry 只需声明字符串即可。
     *
     * @param className 游戏 View 类的全限定名（来自 GameSpec.className 或 aiClass）
     * @param root 根容器 FrameLayout
     */
    private fun createGame(className: String, root: FrameLayout) {
        // className 是字符串而非类引用（GameRegistry 的设计），拼写错误只会在运行时暴露。
        // 这里必须兜底：否则一旦类名写错，点「开始游戏」会直接闪退，玩家看不到任何提示。
        val instance = try {
            Class.forName(className).getConstructor(Context::class.java).newInstance(this)
        } catch (t: Throwable) {
            AlertDialog.Builder(this)
                .setTitle("无法启动游戏")
                .setMessage("游戏类加载失败：\n" + className + "\n\n" + t.javaClass.simpleName)
                .setCancelable(false)
                .setPositiveButton("返回菜单") { _, _ -> finish() }
                .show()
                .also { Ui.styleDialog(it, resources.displayMetrics.density) }
            return
        }
        if (instance !is GameView) {
            finish()
            return
        }
        game = instance
        game.sound = sound
        game.haptic = HapticManager(this, settings)
        game.voice = voice
        game.callbacks = object : GameView.Callbacks {
            override fun onScoreChanged(score: Int) {
                scoreView.text = "得分 " + score
                if (score > 0) scoreView.visibility = View.VISIBLE
            }

            override fun onGameOver(result: GameResult) {
                showGameOver(result)
            }
        }
        // 游戏视图插入到最底层（index 0），悬浮控制层（退出/暂停/得分等）必须盖在游戏之上，
        // 否则不透明的游戏背景会把悬浮按钮全部遮挡，导致"沉浸式部件消失、无法暂停/继续"。
        root.addView(game, 0, FrameLayout.LayoutParams(
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
            // 先关闭模式对话框再进入双视角规则同意页，否则对话框会一直盖在页面上层
            d.dismiss()
            showDualConsent(root)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
            bottomMargin = dp(12)
        })
        container.addView(dialogActionButton("🤖 电脑对战") {
            d.dismiss()
            showRulesDialog(root, spec.aiClass!!)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        d.show()
        Ui.styleDialog(d, resources.displayMetrics.density)
    }

    /** 双人游戏开局：全屏双视角规则同意页，双方各自点击自己的半屏「点击开始」后才开局。 */
    private fun showDualConsent(root: FrameLayout) {
        // lateinit 供 onBothReady 闭包引用（该回调在视图创建后才触发）
        lateinit var consent: DualConsentView
        consent = DualConsentView(
            this, spec,
            onBothReady = {
                // 移除同意页后创建游戏视图（悬浮控制层重新露出）
                root.removeView(consent)
                createGame(canvasClassName, root)
            },
            onExit = { finish() })
        // 追加到最上层：盖住悬浮控制层，避免控制按钮与同意页自身返回按钮冲突
        root.addView(consent, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
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
        // 已结算（帧循环已停）时暂停键无意义：resume() 不会重新开局，
        // 反而会把暂停遮罩去掉、让按钮图标与实际状态脱节。
        if (!game.isRunning) return
        if (game.paused) {
            game.resume()
            pauseBtn.text = "⏸"
        } else {
            game.pause()
            pauseBtn.text = "▶"
        }
    }

    /**
     * 「↻」重开。
     * reset() 内部会调用 start() 把 paused 置回 false，因此必须同步刷新暂停按钮图标，
     * 否则会出现"图标显示 ▶（已暂停）但游戏其实在跑，再点一下反倒把游戏暂停了"。
     */
    private fun restartGame() {
        game.reset()
        pauseBtn.text = "⏸"
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
        // 每次对局都写入历史（ScoreStore 内部会跳过非正分并只在破纪录时更新最高分）
        store.save(spec.id, r.score)

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
            .setPositiveButton("再来一局") { _, _ -> restartGame() }
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

