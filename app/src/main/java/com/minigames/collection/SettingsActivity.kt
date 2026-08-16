package com.minigames.collection

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.minigames.collection.common.SettingsStore

/** 设置页：音效/震动开关、清空最高分。 */
class SettingsActivity : Activity() {

    private lateinit var settings: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F6FA.toInt())
        }

        // 顶栏
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        bar.addView(TextView(this).apply {
            text = "‹"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(0xFF4A6CF7.toInt())
            background = rounded(0xFFEDF0FF.toInt(), dp(12).toFloat())
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(46), dp(44)))
        bar.addView(TextView(this).apply {
            text = "设置"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1C2333.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(50)))

        // 音效开关
        root.addView(switchRow("🔊 音效", "游戏音效与提示音", settings.soundEnabled) { on ->
            settings.soundEnabled = on
        })
        // 震动开关
        root.addView(switchRow("📳 震动反馈", "击中/得分/踩雷时的震动", settings.hapticEnabled) { on ->
            settings.hapticEnabled = on
        })
        // 语音播报开关
        root.addView(switchRow("🗣 语音播报", "象棋等精品游戏的语音（将军/吃/将死）", settings.voiceEnabled) { on ->
            settings.voiceEnabled = on
        })

        // 清空最高分
        val clearBtn = TextView(this).apply {
            text = "🗑 清空所有最高分"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFE8503A.toInt())
            background = rounded(0xFFFFECEA.toInt(), dp(14).toFloat())
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("确认清空")
                    .setMessage("将删除所有游戏的最高分记录，此操作不可恢复。")
                    .setPositiveButton("清空") { _, _ ->
                        settings.clearScores()
                        Toast.makeText(this@SettingsActivity, "已清空最高分", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        root.addView(clearBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            setMargins(dp(16), dp(24), dp(16), 0)
        })

        // 版本信息
        root.addView(TextView(this).apply {
            text = "迷你游戏合集 v1.1.0 · 纯离线运行 · 无需任何权限"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF8A93A6.toInt())
            setPadding(0, dp(28), 0, 0)
        })

        setContentView(root)
    }

    private fun switchRow(
        title: String,
        subtitle: String,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textCol.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1C2333.toInt())
        })
        textCol.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(0xFF8A93A6.toInt())
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply { isChecked = initial }
        sw.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        row.addView(sw)
        return row
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}