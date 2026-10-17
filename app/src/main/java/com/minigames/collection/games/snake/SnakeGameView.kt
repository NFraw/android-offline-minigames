package com.minigames.collection.games.snake

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import com.minigames.collection.common.GameResult
import com.minigames.collection.common.GameView
import com.minigames.collection.common.SoundManager
import kotlin.math.min

/**
 * 贪吃蛇游戏实现。
 *
 * 游戏规则：滑动屏幕或点击下方方向键控制蛇的移动方向，吃到食物蛇身变长并逐渐加速，
 * 撞墙或咬到自己游戏结束，蛇身越长分数越高。
 *
 * 实现架构（所有游戏都遵循同一模式）：
 *   1. 数据模型：用 data class Pt(x,y) 表示坐标，ArrayDeque<Pt> 表示蛇身
 *   2. 游戏逻辑：在 update(dt) 中按速度累积时间，到时执行 step() 移动一步
 *   3. 绘制画面：在 drawGame(canvas) 中用 Canvas API 绘制背景、网格、蛇身、食物、方向键
 *   4. 触摸处理：在 onTouch() 中识别方向键点击和滑动手势
 *   5. 重置：在 reset() 中清空所有状态并调用 start() 启动帧循环
 *
 * 学习要点：
 *   - GestureDetector 用于识别滑动手势（Fling）
 *   - Canvas 绘制：drawRoundRect（圆角矩形）、drawCircle（圆）、drawText（文字）
 *   - 帧累积模式：acc += dt * speed; while(acc >= 1) { step(); acc -= 1; }
 *     实现"每 N 秒移动一步"的效果，速度通过调整 speed 值实现
 *
 * 被调用位置：
 *   - GameActivity 通过反射创建：Class.forName("com.minigames.collection.games.snake.SnakeGameView")
 *   - GameSpec 注册在 GameRegistry.games 列表中
 */
class SnakeGameView(context: Context) : GameView(context) {

    /** 坐标点，data class 自动生成 equals/hashCode/toString/copy */
    private data class Pt(val x: Int, val y: Int)

    /** 方向键按钮：rect=触摸区域, dx/dy=方向, label=显示文字 */
    private data class Btn(val rect: RectF, val dx: Int, val dy: Int, val label: String)

    private val body = ArrayDeque<Pt>()
    private var dirX = 1
    private var dirY = 0
    private var nextDirX = 1
    private var nextDirY = 0
    private var food = Pt(0, 0)
    private var score = 0
    private var speed = 5f
    private var acc = 0f
    private var alive = true
    private var started = false
    private var cols = 16
    private var rows = 24
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardW = 0f
    private var boardH = 0f

    // 方向键（下排控制区）
    private val padBtns = ArrayList<Btn>()
    private var padAreaTop = 0f

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (Math.abs(vx) > Math.abs(vy)) {
                setDir(if (vx > 0) 1 else -1, 0)
            } else {
                setDir(0, if (vy > 0) 1 else -1)
            }
            return true
        }
    })

    private val bgPaint = Paint().apply { color = 0xFF141A2E.toInt() }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x0DFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val wallFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A4668.toInt() }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8EAF0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6C8BFF.toInt() }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A6CF7.toInt() }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5252.toInt() }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A3557.toInt()
        style = Paint.Style.FILL
    }
    private val padStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A6CF7.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val padTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 53f
        color = 0xFFFFFFFF.toInt()
    }

    /**
     * 屏幕尺寸确定后计算棋盘和方向键布局。
     * @param w View 宽度（像素）
     * @param h View 高度（像素）
     * 计算逻辑：固定 16×24 格子，格子大小取宽/高适配的较小值，保证棋盘不超出可用区域。
     */
    override fun onLayout(w: Int, h: Int) {
        val padH = dp(150)
        val topPad = dp(110).toFloat() // 顶部安全区：悬浮按钮层占用，棋盘从该高度之下开始
        // 可用高度保底：分屏/小窗等极端情况下 h 可能小于 padH + topPad，
        // 不保底的话 cell 会变成负数，drawRoundRect 反向矩形什么都画不出来（棋盘整体消失）。
        val availH = (h - padH - topPad).coerceAtLeast(dp(120).toFloat())
        // 固定 16×24 棋盘，格子取宽度/高度各自的适配值，保证棋盘绝不超出可用区域（不再有 10px 钳制溢出）
        cell = min(w / 16f, availH / 24f).coerceAtLeast(1f)
        cols = 16
        rows = 24
        boardW = cols * cell
        boardH = rows * cell
        boardLeft = (w - boardW) / 2f
        boardTop = topPad + (availH - boardH) / 2f
        padAreaTop = (h - padH).toFloat()

        // 方向键布局（十字）
        padBtns.clear()
        val bs = dp(62)
        val gap = dp(12)
        val cx = w / 2f
        val cy = padAreaTop + dp(62)
        padBtns.add(Btn(RectF(cx - bs / 2f, cy - bs - gap, cx + bs / 2f, cy - gap), 0, -1, "▲"))
        padBtns.add(Btn(RectF(cx - bs - gap - bs / 2f, cy - bs / 2f, cx - gap - bs / 2f, cy + bs / 2f), -1, 0, "◀"))
        padBtns.add(Btn(RectF(cx + gap + bs / 2f, cy - bs / 2f, cx + bs + gap + bs / 2f, cy + bs / 2f), 1, 0, "▶"))
        padBtns.add(Btn(RectF(cx - bs / 2f, cy + gap, cx + bs / 2f, cy + bs + gap), 0, 1, "▼"))
        reset()
    }

    override fun onTouch(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) {
            for (b in padBtns) {
                if (b.rect.contains(event.x, event.y)) {
                    setDir(b.dx, b.dy)
                    playSound(SoundManager.MOVE)
                    return
                }
            }
        }
        gesture.onTouchEvent(event)
    }

    private fun setDir(dx: Int, dy: Int) {
        // 与当前方向或待执行方向相反都不接受，防止快速连按时丢指令/误判 180° 掉头
        if ((dx == -dirX && dy == -dirY) || (dx == -nextDirX && dy == -nextDirY)) return
        nextDirX = dx
        nextDirY = dy
        started = true
    }

    /**
     * 在随机空闲位置生成食物。
     * 逻辑：收集所有不在蛇身上的空位 → 随机选一个 → 设为食物位置。
     * 如果没有空位（蛇填满整个棋盘），判定玩家获胜。
     */
    private fun spawnFood() {
        val occupied = body.toSet()
        val free = (0 until cols).flatMap { x -> (0 until rows).map { y -> Pt(x, y) } }
            .filter { it !in occupied }
        if (free.isEmpty()) {
            gameOver(GameResult(score, true, "你赢了！整个棋盘都被蛇填满了"))
            return
        }
        food = free.random()
    }

    /**
     * 帧更新（每帧由 GameView 基类调用）。
     * 使用"帧累积"模式控制蛇的移动速度：每帧增加 dt*speed 的累积量，
     * 累积满 1 就执行一步移动。speed 越大，每帧累积越快，蛇移动越频繁。
     */
    override fun update(dt: Float) {
        if (!alive || !started) return
        acc += dt * speed          // 累积时间（如 dt=0.016s, speed=5 → 每帧累积 0.08）
        while (acc >= 1f) {        // 累积满 1 执行一步（可一次执行多步，如切后台回来时）
            acc -= 1f
            step()                 // 移动一步
            if (!alive) return     // 撞墙/咬自己后停止
        }
    }

    /**
     * 蛇移动一步。
     * 逻辑：应用待定方向 → 计算新头部位置 → 边界检测 → 自咬检测 → 更新蛇身。
     */
    private fun step() {
        dirX = nextDirX  // 将待定方向生效（setDir 只设置 nextDir，step 才真正应用）
        dirY = nextDirY
        val head = body.first()
        val nh = Pt(head.x + dirX, head.y + dirY)
        // 边界检测：新头位置超出棋盘范围
        if (nh.x < 0 || nh.x >= cols || nh.y < 0 || nh.y >= rows) {
            die("撞到墙了")
            return
        }
        // 自咬检测：新头位置与蛇身任意一节重合
        if (body.any { it == nh }) {
            die("咬到自己了")
            return
        }
        body.addFirst(nh)  // 新头插入队首
        if (nh == food) {
            // 吃到食物：加分、加速、生成新食物（不移除尾部 → 蛇变长）
            score += 10
            setScore(score)           // 通知 Activity 更新得分显示
            playSound(SoundManager.EAT)
            if (score % 50 == 0 && speed < 13f) speed += 0.6f  // 每 50 分加速一次
            spawnFood()
        } else {
            body.removeLast()  // 未吃到食物：移除尾部 → 蛇长度不变但位置前移
        }
    }

    /**
     * 蛇死亡处理。
     * @param reason 死亡原因（显示在结算弹窗）
     */
    private fun die(reason: String) {
        alive = false
        playSound(SoundManager.LOSE)
        // GameResult(score, won=false, summary) → Activity 弹出"游戏结束"弹窗
        gameOver(GameResult(score, false, reason + "，蛇身长度 " + body.size))
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 46f
        color = 0xFF8A93A6.toInt()
    }
    private val padHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 32f
        color = 0xFF8A93A6.toInt()
    }

    /**
     * 绘制游戏画面（每帧调用，在 update 之后）。
     * 绘制顺序：背景 → 边界墙 → 网格 → 食物 → 蛇身 → 蛇眼 → 方向键
     * Canvas 坐标系：左上角为原点 (0,0)，x 向右增大，y 向下增大。
     */
    override fun drawGame(canvas: Canvas) {
        canvas.drawRect(gameRect, bgPaint)
        if (!started) {
            canvas.drawText("👆 滑动屏幕或点击下方方向键开始", width / 2f, boardTop + boardH / 2f, hintPaint)
        }
        // 边界墙（清晰可见：外圈填充 + 亮色描边）
        val wallMargin = dp(5)
        canvas.drawRoundRect(
            RectF(boardLeft - wallMargin, boardTop - wallMargin, boardLeft + boardW + wallMargin, boardTop + boardH + wallMargin),
            dp(10).toFloat(), dp(10).toFloat(), wallFillPaint)
        canvas.drawRoundRect(
            RectF(boardLeft - wallMargin, boardTop - wallMargin, boardLeft + boardW + wallMargin, boardTop + boardH + wallMargin),
            dp(10).toFloat(), dp(10).toFloat(), wallPaint)
        // 网格
        for (x in 0..cols) {
            canvas.drawLine(boardLeft + x * cell, boardTop, boardLeft + x * cell, boardTop + boardH, gridPaint)
        }
        for (y in 0..rows) {
            canvas.drawLine(boardLeft, boardTop + y * cell, boardLeft + boardW, boardTop + y * cell, gridPaint)
        }
        // 食物
        canvas.drawCircle(
            boardLeft + food.x * cell + cell / 2f,
            boardTop + food.y * cell + cell / 2f,
            cell * 0.32f, foodPaint)
        // 蛇身
        body.forEachIndexed { i, p ->
            val r = RectF(
                boardLeft + p.x * cell + 1.5f, boardTop + p.y * cell + 1.5f,
                boardLeft + (p.x + 1) * cell - 1.5f, boardTop + (p.y + 1) * cell - 1.5f)
            canvas.drawRoundRect(r, cell * 0.22f, cell * 0.22f, if (i == 0) headPaint else bodyPaint)
        }
        // 蛇眼
        if (alive) {
            val hx = boardLeft + body.first().x * cell + cell / 2f
            val hy = boardTop + body.first().y * cell + cell / 2f
            val ex = dirX * cell * 0.22f
            val ey = dirY * cell * 0.22f
            val px = if (dirX != 0) 0f else cell * 0.15f
            val py = if (dirY != 0) 0f else cell * 0.15f
            canvas.drawCircle(hx + ex - px, hy + ey - py, cell * 0.09f, eyePaint)
            canvas.drawCircle(hx + ex + px, hy + ey + py, cell * 0.09f, eyePaint)
        }
        // 方向键
        canvas.drawText("方向键（也可滑动屏幕）", width / 2f, padAreaTop + dp(14).toFloat(), padHintPaint)
        for (b in padBtns) {
            canvas.drawRoundRect(b.rect, dp(14).toFloat(), dp(14).toFloat(), padPaint)
            canvas.drawRoundRect(b.rect, dp(14).toFloat(), dp(14).toFloat(), padStroke)
            canvas.drawText(b.label, b.rect.centerX(), b.rect.centerY() - (padTextPaint.ascent() + padTextPaint.descent()) / 2f, padTextPaint)
        }
    }

    /**
     * 重置所有游戏状态并开始新一局。
     * 被 GameActivity 的重开按钮（↻）和"再来一局"按钮调用。
     * 注意最后调用 start()——启动帧循环。
     */
    override fun reset() {
        body.clear()
        val cy = rows / 2
        val cx = cols / 2
        body.addFirst(Pt(cx, cy))
        body.addLast(Pt(cx - 1, cy))
        body.addLast(Pt(cx - 2, cy))
        dirX = 1; dirY = 0
        nextDirX = 1; nextDirY = 0
        score = 0
        speed = 5f
        acc = 0f
        alive = true
        started = false
        setScore(0)
        spawnFood()
        start()
    }
}
