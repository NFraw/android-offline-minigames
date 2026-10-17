package com.minigames.collection.common

import android.content.Context

/**
 * 本地最高分与历史记录持久化存储。
 *
 * 功能：记录每款游戏的最高分和最近 20 条得分历史，纯离线 SharedPreferences 实现。
 * 存储格式：
 *   - 最高分：key = 游戏 id（如 "snake"），value = Int
 *   - 历史记录：key = "hist_" + 游戏 id，value = 逗号分隔的得分字符串（如 "100,80,60"）
 *
 * 被调用位置：
 *   - GameActivity.showGameOver()：每局结束时写入得分（save），读取最高分判断是否破纪录
 *   - MainActivity.GameAdapter：主菜单卡片显示最高分标签（best）
 *   - MainActivity.showHistoryDialog()：点击卡片弹出历史记录弹窗（history + best）
 *   - SettingsStore.clearScores()：清空所有最高分（直接操作同一 SharedPreferences 文件）
 *
 * 扩展提示：
 *   新增统计维度（如总局数、平均分）只需仿照 best/history 增加新 key 和方法。
 *   history 的存储格式是简单的逗号分隔字符串——适合少量数据，大量数据建议用 JSON 或数据库。
 *
 * @param context Android 上下文，用于获取 SharedPreferences 实例
 */
class ScoreStore(context: Context) {
    // 与 SettingsStore 使用不同的文件名 "scores"，互不干扰
    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)

    /**
     * 读取指定游戏的最高分。
     * @param gameId 游戏唯一标识，对应 GameSpec.id（如 "snake"、"gomoku"）
     * @return 最高分，从未玩过返回 0
     */
    fun best(gameId: String): Int = prefs.getInt(gameId, 0)

    /**
     * 保存一局得分。
     * 逻辑：
     *   1. 每局都写入历史记录（含 0 分的失败局、棋类的对局记录）——
     *      主菜单靠 history 非空来判断"是否展示历史记录弹窗"，若跳过 0 分，
     *      五子棋、象棋、黑白棋等不计分游戏的历史功能会整体失效
     *   2. 仅当得分 > 最高分时才更新最高分 key（0 分不会污染最高分）
     *   3. 将得分插入历史记录列表头部（新→旧），截断到 MAX_HISTORY 条
     *   4. 一次性 apply() 写盘（批量编辑减少 IO）
     *
     * @param gameId 游戏唯一标识
     * @param score 本局得分
     */
    fun save(gameId: String, score: Int) {
        val e = prefs.edit()
        if (score > best(gameId)) e.putInt(gameId, score)
        val hist = ArrayList(history(gameId))
        hist.add(0, score)  // 新得分插入头部，实现新→旧排序
        e.putString("hist_" + gameId, hist.take(MAX_HISTORY).joinToString(","))
        e.apply()
    }

    /**
     * 读取指定游戏的最近得分历史（新→旧排序）。
     * @param gameId 游戏唯一标识
     * @return 得分列表，最多 20 条；无记录返回空列表
     */
    fun history(gameId: String): List<Int> =
        prefs.getString("hist_" + gameId, "")
            ?.split(",")?.filter { it.isNotEmpty() }?.mapNotNull { it.toIntOrNull() } ?: emptyList()

    /** 主进程原子保存成绩和回执；跨进程调用必须通过 GamePlatformProvider。 */
    fun saveOnce(gameId: String, score: Int, roundId: String): Boolean = synchronized(prefs) {
        require(score >= 0 && roundId.isNotBlank() && roundId.length <= 120 && ',' !in roundId)
        val receiptKey = "seen_" + gameId
        val receipts = prefs.getString(receiptKey, "").orEmpty().split(',').filter { it.isNotEmpty() }
        if (roundId in receipts) return@synchronized false
        val editor = prefs.edit()
            .putString("hist_" + gameId, (listOf(score) + history(gameId)).take(MAX_HISTORY).joinToString(","))
            .putString(receiptKey, (listOf(roundId) + receipts).take(128).joinToString(","))
        if (score > best(gameId)) editor.putInt(gameId, score)
        check(editor.commit()) { "成绩写入失败" }
        true
    }
    private companion object {
        const val MAX_HISTORY = 20  // 每款游戏最多保留的历史记录条数
    }
}

