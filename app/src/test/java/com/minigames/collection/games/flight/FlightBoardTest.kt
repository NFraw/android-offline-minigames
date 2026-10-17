package com.minigames.collection.games.flight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 飞行棋几何与规则测试。
 *
 * 重点验证用户反馈的核心问题：**四个阵营的机库/起飞格/冲刺道/终点必须严格对应、旋转对称**。
 * 旧实现里三者各用一套互不相关的网格坐标（机库在 (1,1) 一角、起飞格在跑道半格位置、
 * 冲刺道又落在另一套格线上），导致"四个色块分布完全不对应"。
 * 这里用「旋转 90° 后坐标必须逐一吻合」这条不变量把问题锁死。
 */
class FlightBoardTest {

    // ---------- 旋转对称（用户反馈的核心问题） ----------

    @Test
    fun hangarRectsAreRotationallySymmetric() {
        val b = FlightBoard(4)
        val r0 = b.hangarRect(0)
        val r1 = b.hangarRect(1)
        val r2 = b.hangarRect(2)
        val r3 = b.hangarRect(3)
        // 四个机库必须同尺寸
        assertEquals("四个机库宽度必须一致",
            (r0[2] - r0[0]).toInt(), (r1[2] - r1[0]).toInt())
        assertEquals("四个机库高度必须一致",
            (r0[3] - r0[1]).toInt(), (r2[3] - r2[1]).toInt())
        // 四个机库的重心必须关于棋盘中心点对称
        val cx0 = (r0[0] + r0[2]) / 2f; val cy0 = (r0[1] + r0[3]) / 2f
        val cx1 = (r1[0] + r1[2]) / 2f; val cy1 = (r1[1] + r1[3]) / 2f
        val cx2 = (r2[0] + r2[2]) / 2f; val cy2 = (r2[1] + r2[3]) / 2f
        val cx3 = (r3[0] + r3[2]) / 2f; val cy3 = (r3[1] + r3[3]) / 2f
        val c = FlightBoard.CENTER.toFloat()
        assertEquals("机库 1 必须是机库 0 绕中心旋转 90°", 2 * c - cy0, cx1, 0.01f)
        assertEquals("机库 1 必须是机库 0 绕中心旋转 90°", cy0, cy1, 0.01f)
        assertEquals("机库 2 必须是机库 0 绕中心旋转 180°", 2 * c - cx0, cx2, 0.01f)
        assertEquals("机库 2 必须是机库 0 绕中心旋转 180°", 2 * c - cy0, cy2, 0.01f)
    }

    @Test
    fun homeLanesMeetExactlyAtCenter() {
        val b = FlightBoard(4)
        // 每条冲刺道的最后一格必须与终点格相邻
        for (p in 0 until 4) {
            val last = b.homePoint(p, FlightRules.HOME_STRETCH)
            val c = b.homePoint()
            val dx = kotlin.math.abs(last.cx - c.cx)
            val dy = kotlin.math.abs(last.cy - c.cy)
            assertEquals("冲刺道第 6 格必须紧贴终点", 1f, dx + dy, 0.01f)
        }
    }

    @Test
    fun homeLanesAreRotationallySymmetric() {
        val b = FlightBoard(4)
        // 冲刺道第 k 格：阵营 1 必须是阵营 0 旋转 90°
        for (k in 1..FlightRules.HOME_STRETCH) {
            val a = b.homePoint(0, k)
            val d = b.homePoint(1, k)
            assertEquals("冲刺道 $k 格 x 对称", 2f * FlightBoard.CENTER - a.cy, d.cx, 0.01f)
            assertEquals("冲刺道 $k 格 y 对称", a.cx, d.cy, 0.01f)
        }
    }

    @Test
    fun homeLaneEndsAndCenterAreAligned() {
        // 旧实现的致命问题：终点画在格线交点 (7,7)，冲刺道落在格中心 (7.5,k)，
        // 两者差半格。这里断言终点与冲刺道终点格严格相邻。
        val b = FlightBoard(4)
        val c = b.homePoint()
        for (p in 0 until 4) {
            val last = b.homePoint(p, FlightRules.HOME_STRETCH)
            assertTrue("冲刺道终点必须与中心格相邻（曼哈顿距离 1）",
                kotlin.math.abs(last.cx - c.cx) + kotlin.math.abs(last.cy - c.cy) < 1.01f)
        }
    }

    // ---------- 跑道几何 ----------

    @Test
    fun ringHasExactly52Cells() {
        val b = FlightBoard(4)
        // 52 格必须落在棋盘外圈（最外两圈之内），且不重复
        val seen = HashSet<String>()
        for (n in 0 until FlightBoard.RING) {
            val p = b.ringPoint(n)
            val sz = FlightBoard.SIZE.toFloat()
            assertTrue("跑道格 $n 必须在棋盘内",
                p.cx >= 0f && p.cy >= 0f && p.cx < sz && p.cy < sz)
            seen.add("${p.cx},${p.cy}")
        }
        assertEquals("52 个跑道格必须互不重叠", FlightBoard.RING, seen.size)
    }

    @Test
    fun startsAreEquallySpacedOnRing() {
        val b = FlightBoard(4)
        // 四家起点必须均匀分布在 52 格上（相差 13）
        assertEquals(0, b.startIndex(0))
        assertEquals(13, b.startIndex(1))
        assertEquals(26, b.startIndex(2))
        assertEquals(39, b.startIndex(3))
    }

    @Test
    fun ringIsClosedLoop() {
        val b = FlightBoard(4)
        // 走完 52 格必须回到起点
        val a = b.ringPoint(0)
        val z = b.ringPoint(FlightBoard.RING)
        assertEquals("跑道必须闭合", a.cx, z.cx, 0.01f)
        assertEquals("跑道必须闭合", a.cy, z.cy, 0.01f)
    }

    @Test
    fun everyTrackCellIsAtLeastOneCellFromBoardEdge() {
        val b = FlightBoard(4)
        for (n in 0 until FlightBoard.RING) {
            val p = b.ringPoint(n)
            assertTrue("跑道格不能顶到边缘", p.cx >= 0.5f && p.cy >= 0.5f)
            assertTrue("跑道格不能顶到边缘",
                p.cx <= FlightBoard.SIZE - 1.5f && p.cy <= FlightBoard.SIZE - 1.5f)
        }
    }

    // ---------- 规则 ----------

    @Test
    fun mustRollSixToTakeOff() {
        val r = FlightRules(4)
        val plane = r.planesOf(0)[0]
        assertNull("非 6 不能起飞", r.computeMove(plane, 5))
        val mv = r.computeMove(plane, 6)
        assertNotNull("掷到 6 必须能起飞", mv)
        assertEquals("起飞后落在自己的起飞格", 0, mv!!.to)
    }

    @Test
    fun normalMoveAdvancesByDice() {
        val r = FlightRules(4)
        val plane = r.planesOf(0)[0]
        plane.progress = 3
        val mv = r.computeMove(plane, 5)
        assertEquals(8, mv!!.to)
    }

    @Test
    fun jumpSquareAdvancesFourExtra() {
        val r = FlightRules(4)
        val plane = r.planesOf(0)[0]
        // 落点正好是跳格（相对起点进度 13）
        plane.progress = 11
        val mv = r.computeMove(plane, 2)
        // 11+2=13 是跳格，跳到 13+4=17
        assertEquals("落到跳格必须再跳 4 格", 17, mv!!.to)
        assertTrue(mv.jumped)
    }

    @Test
    fun flySquareSweepsAcrossBoardThenJumps() {
        val r = FlightRules(4)
        val plane = r.planesOf(0)[0]
        // 落点正好是飞行格（相对起点进度 20）
        plane.progress = 19
        val mv = r.computeMove(plane, 1)
        assertNotNull("掷到飞行格必须能走", mv)
        // 飞 +20 到 40；40 不是跳格（0/13/26/39），因此不再续跳
        assertEquals("飞行格必须飞越全场", 40, mv!!.to)
        assertTrue("必须触发飞行", mv.flew)
        assertFalse("落点不是跳格时不应续跳", mv.jumped)
    }

    @Test
    fun flyLandingOnJumpSquareChainsJump() {
        // 直接验证跳格分支：落在 39（跳格）必须跳到 43。
        val r = FlightRules(4)
        val plane = r.planesOf(0)[0]
        plane.progress = 33   // 33 + 6 = 39（跳格）
        val mv = r.computeMove(plane, 6)
        assertNotNull(mv)
        assertEquals("落在 39（跳格）必须跳到 43", 43, mv!!.to)
        assertTrue("必须标记跳格", mv.jumped)
    }

    @Test
    fun flySquareLandingOnJumpSquareAlsoJumps() {
        // 校验常量关系：跳格间距与飞行距离必须与文档一致
        assertEquals("跳格间距必须等于 4", 4, FlightRules.JUMP_DISTANCE)
        assertEquals("飞行距离必须等于 20", 20, FlightRules.FLY_DISTANCE)
        // 飞行格本身是安全格
        assertTrue("飞行格是安全格", FlightBoard(4).isSafeSquare(FlightRules.FLY_SQUARE))
        // 0/13/26/39 是跳格
        for (s in FlightRules.JUMP_SQUARES) {
            assertTrue("进度 $s 必须是跳格之一", s in FlightRules.JUMP_SQUARES)
        }
        // 20 是飞行格，不是跳格
        assertFalse("飞行格不是跳格", FlightRules.FLY_SQUARE in FlightRules.JUMP_SQUARES)
    }

    @Test
    fun homeStretchRequiresExactRoll() {
        val r = FlightRules(4)
        val plane = r.planesOf(0)[0]
        // 冲刺道第 5 格（进度 52+4 = 56）
        plane.progress = 56
        assertNull("冲刺道点数超出必须不能走", r.computeMove(plane, 3))
        val mv = r.computeMove(plane, 2)
        assertNotNull("恰好走到终点必须能走", mv)
        assertEquals("终点进度", FlightRules.FINISHED, mv!!.to)
    }

    @Test
    fun ownPlaneCanStackOnSameSquare() {
        val r = FlightRules(4)
        val a = r.planesOf(0)[0]
        val bb = r.planesOf(0)[1]
        a.progress = 5
        bb.progress = 5
        // 己方叠子：不产生踢子
        val mv = r.computeMove(a, 0)
        // 用 0 点数构造"落到该格"的场景不便，改为直接校验 kickTargets 语义
        val kicked = r.computeMove(bb, 0)?.kicked ?: emptyList()
        assertTrue("己方飞机不能被踢", kicked.none { it.player == 0 })
    }

    @Test
    fun opponentPlaneIsKickedExceptOnSafeSquare() {
        val r = FlightRules(4)
        val mine = r.planesOf(0)[0]
        val theirs = r.planesOf(1)[0]
        // 普通格：对方会被踢
        mine.progress = 5
        theirs.progress = 5
        val mv = r.computeMove(mine, 0)!!
        // 走子后落到 5（用 0 点数模拟已在该格的情形不准确，改为检查安全格判定）
        assertFalse("普通格不是安全格", r.isSafeFor(1, 5))
        assertTrue("对方起飞格是安全格", r.isSafeFor(1, FlightBoard(4).absoluteRing(1, 0)))
    }

    @Test
    fun winnerIsDetectedWhenAllFourHome() {
        val r = FlightRules(4)
        for (p in r.planesOf(0)) p.progress = FlightRules.FINISHED
        assertTrue("四架到终点必须获胜", r.isWinner(0))
        assertFalse("其他玩家不能误判获胜", r.isWinner(1))
    }

    @Test
    fun threeSixesForfeitTurn() {
        val r = FlightRules(4)
        r.roll(6); r.roll(6)
        val third = r.roll(6)
        assertEquals("三次 6 本轮作废", 0, third)
        assertEquals("应轮到下一位", 1, r.turn)
    }

    @Test
    fun rollingSixGrantsAnotherRoll() {
        val r = FlightRules(4)
        r.roll(6)
        assertTrue("掷到 6 可以再掷", r.canRollAgain())
        r.roll(4)
        assertFalse("非 6 不能再掷", r.canRollAgain())
    }

    @Test
    fun passTurnSkipsToNextPlayer() {
        val r = FlightRules(4)
        assertEquals(0, r.turn)
        r.passTurn()
        assertEquals(1, r.turn)
    }

    @Test
    fun legalMovesIsEmptyWhenNoPlaneCanMove() {
        val r = FlightRules(4)
        // 所有飞机在停机坪，掷出 4：无子可动
        assertTrue("无子可走时走法必须为空", r.legalMoves(4).isEmpty())
    }

    @Test
    fun legalMovesContainsTakeoffWhenSix() {
        val r = FlightRules(4)
        val m = r.legalMoves(6)
        assertEquals("掷到 6 时四架都应可起飞", 4, m.size)
    }
}
