package com.minigames.collection.games.flight

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.min

/**
 * 3D 骰子绘制器（纯 Canvas，无 GL，无第三方库）。
 *
 * 用 [Camera] 把一个立方体的 8 个顶点做真实 3D 旋转，再投影到 2D 画布上，
 * 按 z 排序后从远到近画三个可见面（顶/前/右），每个面铺点数。
 * 配合 [animateTo] 的缓动旋转，形成"骰子在转、停下来是几点"的完整动作，
 * 比平面换图更有"掷骰子"的实感。
 *
 * 之所以不用 Rotation 动画或帧动画：Canvas 没有原生 3D，
 * 但 [Camera] 提供的就是真正的透视矩阵，可以让立方体的三条棱在旋转时正确收敛。
 */
class Dice3D(private val density: Float) {

    /** 立方体半边长（像素）。 */
    private var half = 60f

    /** 当前欧拉角（度）。 */
    var rotX = -32f
    var rotY = 38f
    var rotZ = 0f

    /** 目标角（供动画收敛）。 */
    private var targetX = -32f
    private var targetY = 38f
    private var targetZ = 0f

    /** 动画是否已完成。 */
    val isSettled: Boolean
        get() = kotlin.math.abs(rotX - targetX) < 0.4f &&
            kotlin.math.abs(rotY - targetY) < 0.4f &&
            kotlin.math.abs(rotZ - targetZ) < 0.4f

    // 顶点 / 面缓存，避免每帧分配
    private val faceDepth = FloatArray(6)
    private val faceOrder = IntArray(6)

    private val facePath = Path()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x55000000
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    /** 6 个面，每个面 4 个顶点索引（按逆时针）。 */
    private val faces = arrayOf(
        intArrayOf(4, 5, 7, 6),   // 顶
        intArrayOf(0, 1, 3, 2),   // 底
        intArrayOf(2, 3, 7, 6),   // 前
        intArrayOf(4, 5, 1, 0),   // 后
        intArrayOf(1, 5, 7, 3),   // 右
        intArrayOf(0, 4, 6, 2)    // 左
    )

    /** 每个面对应的点数（标准骰子：1-6 相对两面和为 7）。 */
    private val faceValue = intArrayOf(1, 6, 2, 5, 3, 4)

    /** 点数在该面局部坐标（[-1,1]²）里的位置。 */
    private val pipLayout = arrayOf(
        floatArrayOf(),
        floatArrayOf(0f, 0f),
        floatArrayOf(-0.5f, -0.5f, 0.5f, 0.5f),
        floatArrayOf(-0.5f, -0.5f, 0f, 0f, 0.5f, 0.5f),
        floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f),
        floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, 0f, 0f, -0.5f, 0.5f, 0.5f, 0.5f),
        floatArrayOf(-0.5f, -0.6f, 0.5f, -0.6f, -0.5f, 0f, 0.5f, 0f, -0.5f, 0.6f, 0.5f, 0.6f)
    )

    /**
     * 让骰子转到"目标点数"对应的朝向。
     *
     * 标准骰子点数分布决定了每个点数该转到哪个角度：
     * 用一组固定的欧拉角把对应的面转到正对屏幕。
     */
    fun animateTo(value: Int, spinTurns: Int = 2) {
        val (bx, by) = orientationFor(value)
        // 多转几圈，制造"正在掷"的观感
        targetX = bx + spinTurns * 360f
        targetY = by + spinTurns * 360f
        targetZ = spinTurns * 360f
        // 先把当前角对齐到同一圈数，避免动画开始时反向甩
        rotX = targetX - spinTurns * 360f + (rotX % 360f)
        rotY = targetY - spinTurns * 360f + (rotY % 360f)
        rotZ = 0f
    }

    /** 直接设定角度（无动画），用于瞬时状态。 */
    fun snap(value: Int) {
        val (bx, by) = orientationFor(value)
        rotX = bx; rotY = by; rotZ = 0f
        targetX = bx; targetY = by; targetZ = 0f
    }

    /** 该点数对应的欧拉角（把该面转到正对屏幕，并保持明显倾斜以显示 3D 立体感）。 */
    private fun orientationFor(value: Int): Pair<Float, Float> = when (value) {
        1 -> Pair(-32f, 38f)     // 顶面
        2 -> Pair(-24f, -80f)    // 前面
        3 -> Pair(-26f, 122f)    // 右面
        4 -> Pair(-24f, -122f)   // 左面
        5 -> Pair(24f, -38f)     // 后面
        6 -> Pair(32f, 38f)      // 底面
        else -> Pair(-32f, 38f)
    }

    /**
     * 推进动画（每帧调用）。
     * @param dt 秒
     * @param speed 收敛速度系数（越大越快）
     */
    fun step(dt: Float, speed: Float = 6f) {
        if (isSettled) {
            rotX = targetX; rotY = targetY; rotZ = targetZ
            return
        }
        val k = 1f - Math.exp(-speed * dt.toDouble()).toFloat()
        rotX += (targetX - rotX) * k
        rotY += (targetY - rotY) * k
        rotZ += (targetZ - rotZ) * k
    }

    /**
     * 在 (cx, cy) 画一枚 3D 骰子。
     * @param size 骰子外接正方形边长（像素）
     * @param alpha 0..1 透明度（用于飞行中的淡入淡出）
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, size: Float, alpha: Int = 255) {
        half = size / 2f

        // ---- 1. 8 个顶点做 3D 旋转 + 透视投影 ----
        // 不用 Camera，自己算投影，保证深度排序与绘制坐标用的是同一套变换（避免双重变换）。
        val radX = Math.toRadians(rotX.toDouble())
        val radY = Math.toRadians(rotY.toDouble())
        val radZ = Math.toRadians(rotZ.toDouble())
        val cosX = Math.cos(radX); val sinX = Math.sin(radX)
        val cosY = Math.cos(radY); val sinY = Math.sin(radY)
        val cosZ = Math.cos(radZ); val sinZ = Math.sin(radZ)
        val camDist = half * 4.2f   // 相机距离，越小透视越强

        val verts = Array(8) { floatArrayOf(0f, 0f, 0f) }
        var i = 0
        for (z in intArrayOf(-1, 1)) for (y in intArrayOf(-1, 1)) for (x in intArrayOf(-1, 1)) {
            var vx = x.toDouble() * half
            var vy = y.toDouble() * half
            var vz = z.toDouble() * half
            // X 轴
            var ty = vy * cosX - vz * sinX
            var tz = vy * sinX + vz * cosX
            vy = ty; vz = tz
            // Y 轴
            var tx = vx * cosY + vz * sinY
            tz = -vx * sinY + vz * cosY
            vx = tx; vz = tz
            // Z 轴
            tx = vx * cosZ - vy * sinZ
            ty = vx * sinZ + vy * cosZ
            vx = tx; vy = ty
            // 透视投影
            val persp = camDist / (camDist - vz.toFloat())
            verts[i][0] = cx + vx.toFloat() * persp
            verts[i][1] = cy + vy.toFloat() * persp
            verts[i][2] = vz.toFloat()
            i++
        }

        // ---- 3. 计算每个面的深度（越小越远，先画）----
        for (f in 0 until 6) {
            val fv = faces[f]
            var depth = 0f
            for (vi in fv) depth += verts[vi][2]
            faceDepth[f] = depth / 4f
            faceOrder[f] = f
        }
        // 简单冒泡按深度升序（远的先画）
        for (a in 0 until 5) for (b in 0 until 5 - a) {
            if (faceDepth[faceOrder[b]] > faceDepth[faceOrder[b + 1]]) {
                val t = faceOrder[b]; faceOrder[b] = faceOrder[b + 1]; faceOrder[b + 1] = t
            }
        }

        // ---- 4. 从远到近画每个面 ----
        for (fi in 0 until 6) {
            val f = faceOrder[fi]
            val fv = faces[f]
            facePath.reset()
            facePath.moveTo(verts[fv[0]][0], verts[fv[0]][1])
            for (k in 1 until 4) facePath.lineTo(verts[fv[k]][0], verts[fv[k]][1])
            facePath.close()

            // 用深度给面着色：越近越亮，模拟光照（柔和卡通：奶油白 → 米灰）
            val shade = (0.62f + 0.38f * ((faceDepth[f] + 1.5f * half) / (3f * half))).coerceIn(0f, 1f)
            val col = Color.rgb(
                (255 * shade).toInt(),
                (252 * shade + 3).toInt(),
                (246 * shade + 9).toInt())
            val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = col or (alpha shl 24)
                style = Paint.Style.FILL
            }
            canvas.drawPath(facePath, facePaint)
            edgePaint.alpha = (alpha * 0.22f).toInt()
            edgePaint.strokeWidth = half * 0.045f
            canvas.drawPath(facePath, edgePaint)

            // ---- 5. 铺点数（只有面向观察者的面才画）----
            drawPips(canvas, fv, faceValue[f], alpha, verts)
        }

    }

    /** 在某个面上按局部坐标画点数。 */
    private fun drawPips(canvas: Canvas, fv: IntArray, value: Int, alpha: Int, verts: Array<FloatArray>) {
        val pips = pipLayout[value]
        if (pips.isEmpty()) return
        // 面的两个基向量（用于把局部坐标映射到面）
        val ax = floatArrayOf(verts[fv[1]][0] - verts[fv[0]][0], verts[fv[1]][1] - verts[fv[0]][1])
        val ay = floatArrayOf(verts[fv[3]][0] - verts[fv[0]][0], verts[fv[3]][1] - verts[fv[0]][1])
        val lenA = kotlin.math.hypot(ax[0].toDouble(), ax[1].toDouble()).toFloat()
        val lenB = kotlin.math.hypot(ay[0].toDouble(), ay[1].toDouble()).toFloat()
        val pipR = min(lenA, lenB) * 0.11f
        if (pipR <= 0.5f) return

        for (k in 0 until pips.size step 2) {
            val u = pips[k]
            val v = pips[k + 1]
            // 局部坐标归一化到面中心
            val x = verts[fv[0]][0] + ax[0] * (0.5f + u * 0.5f) + ay[0] * (0.5f + v * 0.5f)
            val y = verts[fv[0]][1] + ax[1] * (0.5f + u * 0.5f) + ay[1] * (0.5f + v * 0.5f)
            // 深色实心圆点
            dotPaint.color = 0xFF2B2B33.toInt() or (alpha shl 24)
            canvas.drawCircle(x, y, pipR, dotPaint)
            // 极小高光，只在左上角，不遮挡点数
            dotPaint.color = 0x44FFFFFF or (alpha shl 24)
            canvas.drawCircle(x - pipR * 0.28f, y - pipR * 0.32f, pipR * 0.3f, dotPaint)
        }
    }

    /** 辅助：在给定值上直接画（无动画）。 */
    fun drawFixed(canvas: Canvas, cx: Float, cy: Float, size: Float, value: Int) {
        snap(value)
        draw(canvas, cx, cy, size)
    }
}
