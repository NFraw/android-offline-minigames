package com.minigames.collection.games.curling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** 冰壶物理引擎单测：防穿透、动量传递、重叠分离、边界约束、停止阈值。 */
class CurlingPhysicsTest {

    private val R = 18f          // 石径
    private val MIN = R * 2      // 最小中心距

    private fun physics(vararg bounds: Float): CurlingPhysics = CurlingPhysics(R).apply {
        left = bounds[0]; top = bounds[1]; right = bounds[2]; bottom = bounds[3]
    }

    @Test
    fun fastStoneDoesNotTunnelThroughStaticStone() {
        val p = physics(0f, 0f, 1000f, 1000f)
        p.addStone(140f, 200f, 0f, 0f, 0)      // 静止目标
        val fast = p.addStone(100f, 200f, 3000f, 0f, 1)  // 高速来石
        p.step(0.02f, 7f)
        val target = p.stones[0]
        // 目标被推向右侧，且两颗石头没有互相穿透
        assertTrue("目标应被推向右", target.x > 140f)
        assertTrue("来石不应穿过目标", fast.x < target.x)
        assertTrue("两石中心距不小于最小距离", hypot(fast.x - target.x, fast.y - target.y) >= MIN - 1f)
    }

    @Test
    fun movingStoneTransfersMomentumToStaticTarget() {
        val p = physics(0f, 0f, 1000f, 1000f)
        val target = p.addStone(200f, 200f, 0f, 0f, 0)
        val shooter = p.addStone(180f, 200f, 100f, 0f, 1)
        p.step(0.02f, 7f)
        // 等质量弹性碰撞（e=0.4）：目标获得约 70% 初速
        assertTrue("目标应获得正向速度", target.vx > 0f)
        assertTrue("来石应减速", shooter.vx < 100f)
        assertTrue("碰撞冲击速度应被记录", p.lastImpactSpeed >= 100f)
    }

    @Test
    fun overlappingStaticStonesSeparate() {
        val p = physics(0f, 0f, 1000f, 1000f)
        val a = p.addStone(100f, 100f, 0f, 0f, 0)
        val b = p.addStone(105f, 100f, 0f, 0f, 1)
        val any = p.step(0.02f, 7f)
        assertFalse("静止石头不应返回运动", any)
        assertTrue("重叠静止石应分离", hypot(a.x - b.x, a.y - b.y) >= MIN - 0.5f)
        assertTrue("两石均不应有速度", a.vx == 0f && a.vy == 0f && b.vx == 0f && b.vy == 0f)
    }

    @Test
    fun boundaryClampsAndReflects() {
        val p = physics(0f, 0f, 1000f, 1000f)
        val s = p.addStone(10f, 100f, -50f, 0f, 0)
        p.step(0.02f, 7f)
        assertTrue("石头不应越出左边界", s.x >= R - 0.01f)
        assertTrue("碰壁后应反向", s.vx >= 0f)
    }

    @Test
    fun slowStoneStopsBelowThreshold() {
        val p = physics(0f, 0f, 1000f, 1000f)
        val s = p.addStone(100f, 100f, 5f, 0f, 0)
        val any = p.step(0.02f, 7f)
        assertFalse("低于阈值应停止", any)
        assertEquals("速度应清零", 0f, s.vx)
        assertEquals(0f, s.vy)
    }

    @Test
    fun headOnImpactConservesMomentum() {
        val p = physics(0f, 0f, 1000f, 1000f)
        // 两颗等速相向的石头（等质量弹性碰撞），间距已接触
        val a = p.addStone(100f, 100f, 50f, 0f, 0)
        val b = p.addStone(130f, 100f, -50f, 0f, 1)
        p.step(0.02f, 7f)
        // 碰撞瞬间速度之和守恒（阻尼按帧施加后对称抵消）
        assertTrue("a 应反向", a.vx < 0f)
        assertTrue("b 应反向", b.vx > 0f)
        assertTrue("速度交换应守恒", abs((a.vx + b.vx)) < 25f)
    }

    @Test
    fun clearanceIsMaintainedAfterMultipleFrames() {
        val p = physics(0f, 0f, 1000f, 1000f)
        p.addStone(200f, 200f, 0f, 0f, 0)
        p.addStone(180f, 200f, 200f, 0f, 1)
        repeat(60) { p.step(0.0167f, 7f) }  // 模拟 1 秒
        for (i in p.stones.indices) {
            for (j in i + 1 until p.stones.size) {
                val d = hypot(p.stones[i].x - p.stones[j].x, p.stones[i].y - p.stones[j].y)
                assertTrue("长时间模拟后不应重叠穿透", d >= MIN - 0.5f)
            }
        }
    }
}
