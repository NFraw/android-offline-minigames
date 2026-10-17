# 五子棋（GomokuView + GomokuAiView）

本文档同时涵盖两个版本：
- **GomokuView**：双人同屏对弈
- **GomokuAiView**：人机对弈（玩家执黑，AI 执白）

---

## 1. 游戏类型与视角

| 属性 | GomokuView（双人） | GomokuAiView（人机） |
|------|-------------------|---------------------|
| 类型 | 双人策略 | 单人策略 |
| 视角 | 俯视，15×15 棋盘 | 俯视，15×15 棋盘 |
| 屏幕方向 | 竖屏 | 竖屏 |
| 玩家 1 | 黑方（先手） | 玩家（黑方，先手） |
| 玩家 2 | 白方 | AI（白方） |

## 2. 数据模型

### 共用常量与状态

```kotlin
companion object {
    const val N = 15            // 棋盘边长
    const val EMPTY = 0
    const val BLACK = 1         // 黑方
    const val WHITE = 2         // 白方
}

val board = IntArray(N * N)    // 棋盘，行优先存储，board[r * N + c]
var curPlayer: Int              // 当前落子方（BLACK 或 WHITE）
var lastX, lastY: Int           // 最后一手的列/行（-1 表示无）
var over: Boolean               // 游戏结束标志
```

### 布局相关

```kotlin
var cell: Float          // 格子间距 = side / (N - 1)
var boardLeft: Float     // 棋盘左上角 X（居中）
var boardTop: Float      // 棋盘左上角 Y（偏下，留出顶部提示空间）
var stoneR: Float        // 棋子半径 = cell * 0.42
```

### GomokuAiView 额外状态

```kotlin
var aiThinking: Boolean        // AI 是否正在思考（阻止玩家输入）
```

## 3. 初始化逻辑（reset）

两个版本几乎一致：

```kotlin
board 全部设为 EMPTY
curPlayer = BLACK              // 黑方先手
lastX = -1, lastY = -1
over = false
// GomokuAiView 额外：aiThinking = false
start()
```

### 布局差异

| 参数 | GomokuView | GomokuAiView |
|------|-----------|-------------|
| 棋盘宽度 | `min(w * 0.96, h * 0.78)` | `min(w * 0.96, h * 0.72)` |
| boardTop 偏移 | `+ cell * 0.7` | `+ cell * 0.7` |

## 4. 输入处理

### GomokuView（双人）

- **触发条件**：`ACTION_DOWN`，且 `!over && !paused`
- **坐标转换**：触摸坐标 → 最近交叉点 `(r, c)`（`Math.round`）
- **距离校验**：触摸点到交叉点中心距离 ≤ `cell * 0.45`
- **空位校验**：`board[r * N + c] == EMPTY`
- **落子**：`board[r * N + c] = curPlayer`，记录 `lastX/lastY`
- **胜负检查**：`checkWin(c, r, curPlayer)`
  - 胜利：结束游戏
  - 平局：棋盘满（`board.none { it == EMPTY }`）→ 结束
  - 继续：切换玩家（`curPlayer = BLACK ↔ WHITE`）

### GomokuAiView（人机）

- **触发条件**：`ACTION_DOWN`，且 `!over && !paused && !aiThinking && curPlayer == BLACK`
  - **额外限制**：仅黑方（玩家）回合可操作，AI 思考期间禁止输入
- **落子**：`board[r * N + c] = BLACK`
- **胜负检查**：
  - 玩家胜利：直接结束
  - 未胜：设置 `aiThinking = true`，启动后台线程计算 AI 最佳落子

### AI 落子流程（后台线程）

```kotlin
Thread {
    val best = bestMove()          // 在副本棋盘上评估
    post {                         // 回到主线程
        aiThinking = false
        if (over) return@post      // 游戏已结束则跳过
        board[best] = WHITE        // AI 落子
        lastX = best % N
        lastY = best / N
        checkWin(lastX, lastY, WHITE)  // 检查 AI 是否获胜
    }
}.start()
```

## 5. 核心更新循环（update(dt)）

**两个版本均为空实现** —— 纯逻辑游戏，无动画/物理。

## 6. 胜负判定

### checkWin(x, y, player)

四个方向检测：水平 `(1,0)`、竖直 `(0,1)`、右下斜 `(1,1)`、左下斜 `(1,-1)`。

对每个方向，从落子点向两端延伸计数，若某一方向连续同色 ≥ 5 则胜利。

```kotlin
fun checkWin(x: Int, y: Int, player: Int): Boolean {
    for ((dx, dy) in DIRS) {
        var count = 1
        for (s in [1, -1]) {        // 正反两个方向
            var i = 1
            while (true) {
                val nx = x + dx * i * s
                val ny = y + dy * i * s
                if (nx in 0..14 && ny in 0..14 && board[ny * N + nx] == player) count++
                else break
                i++
            }
        }
        if (count >= 5) return true
    }
    return false
}
```

### 结果判定

| 情况 | GomokuView | GomokuAiView |
|------|-----------|-------------|
| 黑方五连 | `"黑方五连获胜！"` | `"你赢了！黑方五连"` |
| 白方五连 | `"白方五连获胜！"` | `"电脑获胜！白方五连"` |
| 棋盘满 | `"棋盘已满，平局！"` | 无平局处理（AI 不会触发） |

**注意**：GomokuAiView 的 `finish()` 统一用 `gameOver(GameResult(0, true, msg))`，双方胜利都标记 `true`。

## 7. AI 评估算法（GomokuAiView 专属）

### bestMove()

遍历所有空位，在棋盘**副本**（`board.clone()`）上模拟落子，计算综合得分：

```kotlin
for (每个空位 i) {
    score = 0
    for (player in [WHITE, BLACK]) {    // 先评估 AI，再评估玩家
        在副本上临时落子
        s = evaluateAt(副本, i, player)
        撤回落子
        score += if (player == WHITE) s * 2 else s   // AI 攻击权重 ×2
    }
    取最高 score 的位置
}
```

### evaluateAt(b, x, y, player)

对四个方向，统计从 `(x,y)` 出发的**连续同色棋子数**和**开放端数**（两端是否为空位）：

| 连子数 | 开放端 | 分值 | 含义 |
|--------|--------|------|------|
| ≥ 5 | - | 100000 | 五连（必胜） |
| 4 | 2 | 10000 | 活四（几乎必胜） |
| 4 | 1 | 5000 | 冲四 |
| 3 | 2 | 1000 | 活三 |
| 3 | 1 | 300 | 眠三 |
| 2 | 2 | 100 | 活二 |
| 2 | 1 | 30 | 眠二 |

**攻防权重**：AI（WHITE）的分数 ×2，玩家（BLACK）的分数 ×1。这使 AI 倾向于优先进攻，但也会在关键时刻防守。

### ⚠️ AI 局限性

- **无搜索深度**：仅评估当前一步，不做 Minimax/Alpha-Beta 搜索。
- **无全局视野**：不考虑多步组合（如双三、四三等高级战术）。
- **评估在副本上进行**：`board.clone()` 避免污染 UI 棋盘导致闪烁。
- **后台线程**：AI 计算在 `Thread {}` 中执行，通过 `post {}` 回主线程更新 UI。

## 8. 边界与特殊规则

- **触摸精度**：使用 `Math.round` 取最近交叉点，加上 `cell * 0.45` 距离校验，确保落子在预期位置。
- **星位标记**（仅 GomokuView）：在 `(3,3)`, `(11,3)`, `(3,11)`, `(11,11)`, `(7,7)` 绘制小圆点。
- **最后一手标记**：红色小圆（`0xFFE8503A`），半径 `cell * 0.14`（GomokuView）/ `cell * 0.13`（GomokuAiView）。
- **GomokuAiView 无平局处理**：理论上 15×15 棋盘五子棋极难填满，源码中无 `board.none { it == EMPTY }` 检查。
- **AI 思考阻塞**：`aiThinking` 标志阻止玩家在 AI 落子前继续操作。
- **GomokuAiView 无星位**：`drawGame` 中不绘制星位标记（与 GomokuView 的差异）。

## 9. 渲染要求

### 共用渲染

- 棋盘：米黄色圆角矩形（`0xFFE0B577`），圆角 `cell * 0.4`。
- 网格线：深棕色（`0xFF8A5A2B`），线宽 2px。
- 黑子：双层圆（外层 `0xFF4A5568` 高光，内层 `0xFF1C2333` 实色，内层半径 `0.85r`）。
- 白子：双层圆（外层描边 `0xFFB9C0CE`，内层 `0xFFF5F6FA` 实色，内层半径 `0.95r`）。
- 最后一手：红色小圆标记。

### GomokuView 特有

- 星位：5 个小圆点（半径 `cell * 0.1`）。
- 回合提示在棋盘上方：`"● 黑方落子"` / `"○ 白方落子"`，颜色区分黑白方。

### GomokuAiView 特有

- 状态提示：`"🤖 电脑思考中…"` 或 `"你（黑）落子"`。
- 副标题：`"黑方先手，先连成五子者胜"`。
