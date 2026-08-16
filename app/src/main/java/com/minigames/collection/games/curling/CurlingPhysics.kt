package com.minigames.collection.games.curling

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * 冰壶物理引擎（纯 Kotlin，可 JVM 单测）：
 * 等质量弹性碰撞 + 子步进防穿透 + 逐帧摩擦阻尼 + 边界约束。
 * 与 View/Canvas 解耦，坐标沿用 Android 屏幕坐标系（y 向下）。
 */
class CurlingPhysics(val stoneR: Float, val restitution: Float = 0.4f) {

    class Stone(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val player: Int
    ) {
        val moving: Boolean get() = vx != 0f || vy != 0f
    }

    val stones = ArrayList<Stone>()

    /** 边界矩形（left, top, right, bottom），坐标 y 向下。 */
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f

    /** 本 step 内最强的碰撞冲击速度（相对法向速度），供音效/震动判断。 */
    var lastImpactSpeed = 0f
        private set

    fun addStone(x: Float, y: Float, vx: Float, vy: Float, player: Int): Stone {
        val s = Stone(x, y, vx, vy, player)
        stones.add(s)
        return s
    }

    fun clear() {
        stones.clear()
        lastImpactSpeed = 0f
    }

    /**
     * 推进 dt 秒：位置按子步进移动（每步位移 ≤ 石径一半，防高速穿透），
     * 每子步检测并解算碰撞；摩擦阻尼按帧（dt 基准）应用一次。
     *
     * @param stopSpeed 速度低于该值视为停止
     * @return 是否还有石头在运动
     */
    fun step(dt: Float, stopSpeed: Float): Boolean {
        lastImpactSpeed = 0f
        var maxSpeed = 0f
        for (s in stones) {
            val sp = hypot(s.vx, s.vy)
            if (sp > maxSpeed) maxSpeed = sp
        }
        if (maxSpeed <= stopSpeed) {
            // 全部低于阈值：清零速度（进入静止）并分离残余重叠
            for (s in stones) {
                s.vx = 0f
                s.vy = 0f
            }
            resolveOverlaps()
            return false
        }
        val maxMove = maxSpeed * dt
        val n = ceil(maxMove / (stoneR * 0.5f)).toInt().coerceIn(1, 16)
        val h = dt / n
        for (k in 0 until n) {
            for (s in stones) {
                if (!s.moving) continue
                s.x += s.vx * h
                s.y += s.vy * h
            }
            collide()          // 先施加冲量：此刻位移刚发生，重叠尚未被分离，能命中碰撞
            resolveOverlaps()  // 再分离几何重叠
            boundsClamp()
        }
        // 摩擦阻尼：以 60fps 为基准（每帧 ×0.987），随 dt 归一化
        val f = Math.pow(0.987, (60.0 * dt).toDouble()).toFloat()
        var any = false
        for (s in stones) {
            s.vx *= f
            s.vy *= f
            if (hypot(s.vx, s.vy) < stopSpeed) {
                s.vx = 0f
                s.vy = 0f
            } else {
                any = true
            }
        }
        return any
    }

    private fun boundsClamp() {
        for (s in stones) {
            if (s.x < left + stoneR) { s.x = left + stoneR; s.vx = -s.vx * 0.25f }
            if (s.x > right - stoneR) { s.x = right - stoneR; s.vx = -s.vx * 0.25f }
            if (s.y < top + stoneR) { s.y = top + stoneR; s.vy = -s.vy * 0.25f }
            if (s.y > bottom - stoneR) { s.y = bottom - stoneR; s.vy = -s.vy * 0.25f }
        }
    }

    /** 分离全部重叠（含两静止石），均摊位移。 */
    private fun resolveOverlaps() {
        for (i in stones.indices) {
            for (j in i + 1 until stones.size) {
                val a = stones[i]
                val b = stones[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = hypot(dx, dy)
                val minDist = stoneR * 2
                if (dist >= minDist) continue
                val nx: Float
                val ny: Float
                if (dist < 0.001f) { nx = 1f; ny = 0f } else { nx = dx / dist; ny = dy / dist }
                val overlap = minDist - dist
                a.x -= nx * overlap / 2f
                a.y -= ny * overlap / 2f
                b.x += nx * overlap / 2f
                b.y += ny * overlap / 2f
            }
        }
    }

    /** 等质量弹性碰撞冲量：j = -(1+e)·rel/2。 */
    private fun collide() {
        for (i in stones.indices) {
            for (j in i + 1 until stones.size) {
                val a = stones[i]
                val b = stones[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = hypot(dx, dy)
                val minDist = stoneR * 2
                if (dist > minDist || dist < 0.001f) continue
                val nx = dx / dist
                val ny = dy / dist
                val rel = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if (rel >= 0f) continue   // 正在分离，不施加冲量
                val jImp = -(1f + restitution) * rel / 2f
                a.vx -= jImp * nx
                a.vy -= jImp * ny
                b.vx += jImp * nx
                b.vy += jImp * ny
                if (-rel > lastImpactSpeed) lastImpactSpeed = -rel
            }
        }
    }
}
