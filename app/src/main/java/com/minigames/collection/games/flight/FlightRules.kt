package com.minigames.collection.games.flight

/**
 * 飞行棋规则引擎（纯逻辑，无 Android 依赖，可单测）。
 *
 * ## 规则来源
 * 以下规则综合自两份公开玩法说明，取其一致部分，冲突处以「传统实物棋盘」为准：
 *   - 边锋飞行棋《游戏介绍·游戏规则》(gameabc.com)
 *   - 心动小镇 飞行棋玩法规则 (TapTap)
 *
 * ## 核心规则
 * 1. **起飞**：必须掷出 6，才能从停机坪把一架飞机放到自己的起飞格；掷到 6 可以再掷一次。
 *    连续三次掷出 6 则整轮作废（传统规则），换下一位。
 * 2. **行进**：按点数沿跑道顺时针前进，走完一圈后由自己的入口拐进「冲刺道」。
 * 3. **冲刺道**：自己的最后一段，点数必须**恰好**走到终点才算到达；超出则原地不动（本机取"恰好到达"规则，
 *    因为它对手感最直观，且与两份来源的"超出则不能走"一致）。
 * 4. **跳格**：落在与自己同色的格子上，可直接**跳到下一个同色格**（等效 +4 格）。
 * 5. **飞行**：落在自己的「飞行格」（带虚线的同色格）上，可飞到正对面的同色格，再跳到下一个同色格。
 * 6. **踢子**：走到有**对方**飞机的格子，把对方那架踢回其停机坪。己方飞机可叠在同一格（叠子）。
 *    落在己方叠子上不产生任何效果（本机简化：叠子不阻挡、不反弹）。
 * 7. **安全格**：起点格与飞行格为本方安全格，对方飞机**不能**踢走安全格上的飞机。
 * 8. **胜利**：自己 4 架飞机全部到达终点即获胜。
 *
 * ## 进度表示
 * `progress` 用单一整数描述一架飞机的绝对位置：
 *   -1               停机坪（未起飞）
 *   0..51            外圈跑道（相对自己起点的偏移，0 = 自己的起飞格）
 *   52..57           冲刺道（52 = 入口，57 = 终点前最后一格）
 *   58               终点（已到达）
 * 这样"行进 / 跳 / 飞 / 冲刺 / 越界"全部可以在一个数轴上算，避免坐标系统互不对应的老问题。
 */
class FlightRules(val playerCount: Int) {

    init {
        require(playerCount in 2..4) { "飞行棋支持 2~4 人，实际 $playerCount" }
    }

    companion object {
        /** 外圈跑道格数。 */
        const val TRACK = 52
        /** 冲刺道格数（不含终点）。 */
        const val HOME_STRETCH = 6
        /** 绝对进度：完成状态。 */
        const val FINISHED = TRACK + HOME_STRETCH   // 58
        /** 在停机坪。 */
        const val IN_BASE = -1

        /** 每位玩家 4 架飞机。 */
        const val PLANES_PER_PLAYER = 4

        /**
         * 跳格：每个玩家在自己的一圈上有 4 个同色格（含起飞格），
         * 落到任一同色格 → 跳到下一个同色格，等效 +4。
         * 以"相对自己起点的进度"表示，任何玩家都适用。
         */
        val JUMP_SQUARES = intArrayOf(0, 13, 26, 39)

        /**
         * 飞行格：相对起点进度 20 的那一格（对应经典棋盘上的"带虚线的同色格"）。
         * 落到它 → 飞到对面同色格（进度 +20），再按跳格规则跳到下一个同色格（+4）。
         * 飞行只在外圈有效（进度 < TRACK），进入冲刺道后不再触发。
         */
        const val FLY_SQUARE = 20
        const val FLY_DISTANCE = 20

        /** 一个同色格到下一个同色格的距离（4 个同色格均匀分布在 52 格上，52/13 = 4）。 */
        const val JUMP_DISTANCE = 4

        /** 连续三次掷出 6 整轮作废。 */
        const val THREE_SIXES_PENALTY = 3
    }

    /**
     * 一架飞机的绝对进度。
     *
     * @param progress 见类注释的取值约定
     */
    data class Plane(val player: Int, val index: Int, var progress: Int = IN_BASE) {
        /** 是否还在停机坪。 */
        val inBase: Boolean get() = progress < 0
        /** 是否已到达终点。 */
        val atHome: Boolean get() = progress >= FINISHED
    }

    /** 全部飞机。 */
    val planes = ArrayList<Plane>(playerCount * PLANES_PER_PLAYER).apply {
        for (p in 0 until playerCount) for (i in 0 until PLANES_PER_PLAYER) add(Plane(p, i))
    }

    /** 当前回合玩家。 */
    var turn = 0
        private set

    /** 本回合是否已经掷过骰子（决定是否还能走子）。 */
    var rolled = false
        private set

    /** 本回合掷出的点数。 */
    var lastRoll = 0
        private set

    /** 本回合连续掷 6 的次数（用于三次 6 判罚）。 */
    var sixStreak = 0
        private set

    /** 已完成全部飞机的玩家（按完成先后排序）。 */
    val finishedPlayers = ArrayList<Int>()

    /** 该玩家是否还有可动的飞机。 */
    fun planesOf(player: Int): List<Plane> = planes.filter { it.player == player }

    /** 该玩家到达终点的飞机数。 */
    fun homeCount(player: Int): Int = planesOf(player).count { it.atHome }

    /** 该玩家是否已经获胜。 */
    fun isWinner(player: Int): Boolean = homeCount(player) == PLANES_PER_PLAYER

    /**
     * 掷骰子。返回本次点数。
     * 连续第三次掷出 6 时整轮作废（点数归 0 并换手）。
     */
    fun roll(dice: Int): Int {
        require(dice in 1..6) { "骰子点数必须 1~6，实际 $dice" }
        if (dice == 6) {
            sixStreak++
            if (sixStreak >= THREE_SIXES_PENALTY) {
                // 三次 6：本轮作废，换下一位
                sixStreak = 0
                rolled = false
                lastRoll = 0
                nextTurn()
                return 0
            }
        } else {
            sixStreak = 0
        }
        lastRoll = dice
        rolled = true
        return dice
    }

    /** 是否还能继续掷（掷到 6 可以再掷）。 */
    fun canRollAgain(): Boolean = lastRoll == 6 && rolled

    /**
     * 该玩家当前点数下可选的走法（飞机下标 → 走法）。
     * 空表示无子可走，应当换手。
     */
    fun legalMoves(dice: Int): Map<Int, Move> {
        val out = LinkedHashMap<Int, Move>()
        for (pl in planesOf(turn)) {
            computeMove(pl, dice)?.let { out[pl.index] = it }
        }
        return out
    }

    /**
     * 走法结果。
     *
     * @param from 起始进度
     * @param to   落点进度（已含跳/飞的加成，且已修正越界）
     * @param jumped 是否触发了跳格
     * @param flew   是否触发了飞行
     * @param kicked 被踢回停机坪的对方飞机（可能是空）
     * @param bounced 是否因为超出终点而回退
     */
    data class Move(
        val from: Int,
        val to: Int,
        val jumped: Boolean = false,
        val flew: Boolean = false,
        val bounced: Boolean = false,
        val kicked: List<Plane> = emptyList()
    )

    /**
     * 计算单架飞机的走法（不改变状态）。
     * 返回 null 表示这架飞机本点数下走不了（例如在停机坪但点数不是 6、或冲刺道点数超出）。
     */
    fun computeMove(plane: Plane, dice: Int): Move? {
        // 停机坪：只有掷到 6 才能起飞到自己的起飞格（progress = 0）
        if (plane.inBase) {
            return if (dice == 6) Move(IN_BASE, 0) else null
        }
        // 已经到终点：不用再动
        if (plane.atHome) return null

        val from = plane.progress
        val raw = from + dice

        // 冲刺道：点数必须恰好走到终点；超出则本架走不了
        if (from >= TRACK) {
            return if (raw == FINISHED) Move(from, raw) else null
        }

        // 外圈行进（可能跨越起点进入冲刺道）
        var to = raw
        var jumped = false
        var flew = false

        // 跳格 / 飞行只在纯外圈段触发（落点也必须在外圈上）
        if (to < TRACK) {
            // 飞行：落在自己的飞行格
            if (to == FLY_SQUARE) {
                flew = true
                to += FLY_DISTANCE
                // 飞到对面后，再按跳格规则跳到下一个同色格
                if (to < TRACK && to in JUMP_SQUARES) {
                    to += JUMP_DISTANCE
                    jumped = true
                }
            } else if (to in JUMP_SQUARES) {
                // 跳格：落到同色格 → 跳到下一个同色格
                to += JUMP_DISTANCE
                jumped = true
            }
        }

        // 越过终点：外圈段允许跨入冲刺道；冲刺道内绝不允许越界
        if (to > FINISHED) to = FINISHED

        val kicked = kickTargets(plane, to)
        return Move(from, to, jumped, flew, bounced = false, kicked = kicked)
    }

    /**
     * 落点上属于**对方**且**不在安全格**的飞机（会被踢回停机坪）。
     * 己方飞机可叠，不踢。
     */
    private fun kickTargets(plane: Plane, to: Int): List<Plane> {
        if (to < 0 || to >= FINISHED) return emptyList()
        // 起点格与飞行格是本方安全格，对方不能在此被踢
        if (isSafeFor(plane.player, to)) return emptyList()
        return planes.filter {
            it.player != plane.player && it.progress == to
        }
    }

    /**
     * 某个绝对进度是否是 [player] 的安全格（起点 / 飞行格）。
     * 用"相对该玩家起点的进度"判断，因此与具体玩家无关的位置会自动落到同一套格上。
     */
    fun isSafeFor(player: Int, absolute: Int): Boolean {
        val rel = relProgress(player, absolute)
        return rel == 0 || rel == FLY_SQUARE
    }

    /**
     * 绝对进度 → 相对该玩家起点的进度。
     * 由于各玩家起点在跑道上均匀分布（52/玩家数），本机采用**按玩家数等分起点**的模型，
     * 使得每个玩家的跳格/飞行格在视觉上严格对应同一套色块。
     */
    fun relProgress(player: Int, absolute: Int): Int {
        if (absolute < 0) return absolute
        val stride = TRACK / playerCount
        // 换算成"相对该玩家起点"的进度：绝对进度以 0 = 玩家 0 起点为基准
        val rel = (absolute - player * stride) % TRACK
        return if (rel < 0) rel + TRACK else rel
    }

    /** 该玩家的起飞格在跑道上的绝对索引。 */
    fun startSquare(player: Int): Int = player * (TRACK / playerCount)

    /** 执行一次走子（含踢子）。返回走法；null 表示非法。 */
    fun applyMove(plane: Plane, dice: Int): Move? {
        val mv = computeMove(plane, dice) ?: return null
        plane.progress = mv.to
        for (k in mv.kicked) k.progress = IN_BASE
        checkWinner()
        if (dice != 6) nextTurn()
        return mv
    }

    /** 无子可走时直接换手。 */
    fun passTurn() {
        rolled = false
        lastRoll = 0
        nextTurn()
    }

    private fun nextTurn() {
        turn = (turn + 1) % playerCount
        rolled = false
        lastRoll = 0
    }

    private fun checkWinner() {
        for (p in 0 until playerCount) {
            if (isWinner(p) && p !in finishedPlayers) finishedPlayers.add(p)
        }
    }
}
