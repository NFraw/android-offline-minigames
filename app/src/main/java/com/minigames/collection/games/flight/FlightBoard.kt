package com.minigames.collection.games.flight

/**
 * 飞行棋棋盘几何（纯逻辑，可单测）。
 *
 * ## 为什么需要独立一个类
 * 旧实现里机库、起飞格、冲刺道、终点各用一套互不相关的网格坐标，导致"四个色块完全对不上"。
 * 这里改成**单一数据源**：所有几何量都从 [size]（棋盘边长，单位：格）一次推导，
 * 四个阵营用同一套公式旋转 90° 得到，天然保证严格对称、严格对应。
 *
 * ## 棋盘模型
 * 棋盘是 size×size 的方格网，取 size = 17（奇数，中心落在一格的中心而非格线）。
 *   - 外圈跑道：环形 52 格，每边 13 格，**顺时针**（右上→右下→左下→左上）
 *   - 四个机库：占满棋盘四角的 5×5 区块，每个机库 4 个停机位
 *   - 四条冲刺道：从各自边的中点向中心延伸 6 格，终点是正中心那一格
 *   - 中心终点：正中心格，四条冲刺道汇合于此
 *
 * 所有"阵营 → 坐标"的函数都写成 **阵营 0 的坐标 + 90° 旋转 k 次**，
 * 因此不可能出现某一色块偏移半格、另一色块偏一格这类不一致。
 */
class FlightBoard(val playerCount: Int) {

    companion object {
        /** 棋盘格数（边长），必须是奇数，让中心落在格中心。 */
        const val SIZE = 17
        /** 中心格坐标（也是终点）。 */
        const val CENTER = SIZE / 2          // = 8

        /** 外圈跑道每边格数。52 = 4 × 13。 */
        const val PER_SIDE = 13
        /** 外圈跑道总格数。 */
        const val RING = PER_SIDE * 4        // = 52

        /** 冲刺道格数（从跑道到终点前一格）。 */
        const val HOME_LEN = 6

        /** 机库占角区块边长（格）。 */
        const val HANGAR = 5
    }

    /** 一个棋盘格中心点（格坐标，可带 0.5 的半格偏移）。 */
    data class Pt(val cx: Float, val cy: Float)

    /** 4 格构成的机库停机位（相对机库区块左上角的偏移）。 */
    private val HANGAR_SLOTS = arrayOf(
        floatArrayOf(0.25f, 0.25f), floatArrayOf(1.25f, 0.25f),
        floatArrayOf(0.25f, 1.25f), floatArrayOf(1.25f, 1.25f)
    )

    /**
     * 把点绕棋盘中心顺时针旋转 k 个 90°。
     * 这是整套几何的对称基元：所有阵营差异都靠它实现。
     */
    private fun rotate(p: Pt, k: Int): Pt {
        var x = p.cx - CENTER
        var y = p.cy - CENTER
        repeat(((k % 4) + 4) % 4) {
            val nx = -y
            val ny = x
            x = nx; y = ny
        }
        return Pt(x + CENTER, y + CENTER)
    }

    /**
     * 阵营 0（红，正上方）的第 n 个跑道格（n = 0 表示起飞格），n ∈ 0..51。
     *
     * 走法：从正上方边的起飞格开始，**顺时针**绕外圈一圈。
     * 正上方边的跑道格位于 y = 1（y=0 一行留作机库顶边之外的边框）。
     * 这里直接用参数化方式铺满整圈，保证 52 格均匀分布、四边对称。
     */
    private fun ringPoint0(n: Int): Pt {
        val s = SIZE
        val side = PER_SIDE                // 13
        val seg = n / side                 // 0..3：上→右→下→左
        val off = n % side                 // 该边上的第几格
        val p = when (seg) {
            0 -> Pt(2f + off, 1f)          // 上边：从左向右，y=1
            1 -> Pt((s - 2).toFloat(), 2f + off)   // 右边：从上向下，x=s-2
            2 -> Pt((s - 3 - off).toFloat(), (s - 2).toFloat())  // 下边：从右向左，y=s-2
            else -> Pt(1f, (s - 3 - off).toFloat())              // 左边：从下向上，x=1
        }
        return p
    }

    /** 第 n 个跑道格中心（格坐标），n ∈ 0..51，按顺时针。 */
    fun ringPoint(n: Int): Pt = ringPoint0(((n % RING) + RING) % RING)

    /** 该玩家的起飞格（跑道索引）。四家起点均匀分布在 52 格上，彼此相差 13。 */
    fun startIndex(player: Int): Int = ((player * RING) / playerCount) % RING

    /** 该玩家从跑道第 [from] 步开始的绝对跑道索引（已完成 laps 已折算进 from）。 */
    fun absoluteRing(player: Int, progress: Int): Int = (startIndex(player) + progress) % RING

    /**
     * 阵营 0 的冲刺道第 k 格（k = 1..HOME_LEN）。
     *
     * 正上方冲刺道：从正上方边向**中心**延伸。
     * k = HOME_LEN 时正好落在中心格的正上方邻格，与终点（中心格）严格相邻。
     */
    private fun homePoint0(k: Int): Pt = Pt(CENTER.toFloat(), (CENTER - HOME_LEN + k - 1).toFloat())

    /** 该玩家冲刺道第 k 格中心（k = 1..6）。 */
    fun homePoint(player: Int, k: Int): Pt = rotate(homePoint0(k), player)

    /** 终点格中心（四条冲刺道的汇合点）。 */
    fun homePoint(): Pt = Pt(CENTER.toFloat(), CENTER.toFloat())

    /**
     * 阵营 0 的机库（左上角区块）第 i 个停机位中心（i = 0..3）。
     * 机库块位于左上角，顶点留 1 格边框。
     */
    private fun hangarPoint0(i: Int): Pt {
        val slot = HANGAR_SLOTS[i]
        return Pt(1f + slot[0], 1f + slot[1])
    }

    /** 该玩家机库第 i 个停机位中心（i = 0..3）。 */
    fun hangarPoint(player: Int, i: Int): Pt = rotate(hangarPoint0(i), player)

    /**
     * 阵营 0 的机库外框（格坐标矩形）。
     * 左上角 (1,1) 到 (1+HANGAR, 1+HANGAR)。
     */
    private fun hangarRect0(): FloatArray = floatArrayOf(1f, 1f, 1f + HANGAR, 1f + HANGAR)

    /** 该玩家机库外框（left, top, right, bottom，格坐标）。 */
    fun hangarRect(player: Int): FloatArray {
        val r = hangarRect0()
        // 四个角点分别旋转
        val tl = rotate(Pt(r[0], r[1]), player)
        val br = rotate(Pt(r[2], r[3]), player)
        return floatArrayOf(
            minOf(tl.cx, br.cx), minOf(tl.cy, br.cy),
            maxOf(tl.cx, br.cx), maxOf(tl.cy, br.cy)
        )
    }

    /**
     * 外圈跑道某一格是否是「安全格」（不可被踢）。
     * 起飞格与飞行格为安全格 —— 与 FlightRules.isSafeFor 保持一致的语义。
     */
    fun isSafeSquare(relProgress: Int): Boolean =
        relProgress == 0 || relProgress == FlightRules.FLY_SQUARE

    /**
     * 该玩家的飞行格（相对起点进度 = FLY_SQUARE）在跑道上的绝对索引。
     */
    fun flySquare(player: Int): Int = absoluteRing(player, FlightRules.FLY_SQUARE)

    /**
     * 该玩家的跳格（相对起点进度 0/13/26/39）在跑道上的绝对索引。
     */
    fun jumpSquares(player: Int): IntArray =
        FlightRules.JUMP_SQUARES.map { absoluteRing(player, it) }.toIntArray()
}
