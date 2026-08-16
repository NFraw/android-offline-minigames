package com.minigames.collection

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.minigames.collection.common.GameCategory
import com.minigames.collection.common.GameRegistry
import com.minigames.collection.common.GameSpec

/** 主菜单：标题 + 分类筛选（全部/单人/双人）+ 游戏卡片宫格。 */
class MainActivity : Activity() {

    private var currentCategory: GameCategory? = null
    private lateinit var grid: GridView
    private val chips = mutableListOf<TextView>()
    /** 平板（最小宽度 ≥600dp）走响应式布局：宫格列数更多、间距更大。 */
    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F6FA.toInt())
        }

        // ---- 标题区 ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(16), dp(10))
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "🎮 迷你游戏合集"
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1C2333.toInt())
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(TextView(this).apply {
            text = "⚙"
            textSize = 37f
            gravity = Gravity.CENTER
            setTextColor(0xFF4A6CF7.toInt())
            background = rounded(0xFFEDF0FF.toInt(), dp(14).toFloat())
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(titleRow)
        header.addView(TextView(this).apply {
            text = "已收录 " + GameRegistry.games.size + " 款游戏 · 纯离线运行 · 点击卡片查看玩法"
            textSize = 26f
            setTextColor(0xFF8A93A6.toInt())
            setPadding(0, dp(6), 0, 0)
        })
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 分类 chips ----
        val chipRow = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(16), 0, dp(16), 0)
        }
        val chipContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(12))
        }
        listOf<Pair<GameCategory?, String>>(
            null to "全部", GameCategory.SOLO to "单人", GameCategory.DUEL to "双人", GameCategory.MULTI to "多人"
        ).forEach { (cat, label) ->
            chipContainer.addView(makeChip(label, cat), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(10) })
        }
        chipRow.addView(chipContainer)
        root.addView(chipRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- 游戏宫格（平板响应式：列数/间距随屏宽变化）----
        grid = GridView(this).apply {
            numColumns = when {
                isTablet && resources.configuration.screenWidthDp >= 900 -> 4
                isTablet -> 3
                else -> 2
            }
            val gap = dp(if (isTablet) 16 else 12)
            horizontalSpacing = gap
            verticalSpacing = gap
            val side = dp(if (isTablet) 20 else 16)
            setPadding(side, dp(6), side, dp(20))
            clipToPadding = false
        }
        grid.adapter = GameAdapter()
        grid.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            startActivity(Intent(this, GameActivity::class.java).putExtra("game", games()[position].id))
        }
        root.addView(grid, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        refreshChips()
    }

    private fun games(): List<GameSpec> = GameRegistry.filtered(currentCategory)

    private fun makeChip(label: String, cat: GameCategory?): TextView =
        TextView(this).apply {
            text = label
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            setOnClickListener {
                currentCategory = cat
                refreshChips()
                grid.adapter = GameAdapter()
            }
        }.also { chips.add(it) }

    private fun refreshChips() {
        chips.forEach { chip ->
            val active = when (chip.text.toString()) {
                "全部" -> currentCategory == null
                "单人" -> currentCategory == GameCategory.SOLO
                "双人" -> currentCategory == GameCategory.DUEL
                "多人" -> currentCategory == GameCategory.MULTI
                else -> false
            }
            chip.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xFF1C2333.toInt())
            chip.background = if (active) {
                rounded(0xFF4A6CF7.toInt(), dp(18).toFloat())
            } else {
                rounded(0xFFFFFFFF.toInt(), dp(18).toFloat())
            }
        }
    }

    inner class GameAdapter : BaseAdapter() {
        override fun getCount(): Int = games().size
        override fun getItem(pos: Int): Any = games()[pos]
        override fun getItemId(pos: Int): Long = pos.toLong()

        override fun getView(pos: Int, convertView: View?, parent: ViewGroup?): View {
            val spec = games()[pos]
            val card = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(16), dp(6), dp(14))
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            // 图标（emoji + 主题色浅底圆角）
            val iconBox = LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER
                background = rounded((spec.color and 0x00FFFFFF.toInt()) or 0x14000000.toInt(), dp(18).toFloat())
            }
            val icon = TextView(this@MainActivity).apply {
                text = spec.emoji
                textSize = if (isTablet) 64f else 50f
                gravity = Gravity.CENTER
            }
            val iconSize = dp(if (isTablet) 84 else 64)
            iconBox.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
            val name = TextView(this@MainActivity).apply {
                text = spec.name
                textSize = 27f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF1C2333.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            }
            val tag = TextView(this@MainActivity).apply {
                text = spec.category.label + " · " + spec.desc
                textSize = 22f
                setTextColor(spec.color)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(3), dp(8), 0)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            card.removeAllViews()
            card.addView(iconBox)
            card.addView(name)
            card.addView(tag)
            return card
        }
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}