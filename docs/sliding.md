# 数字华容道（SlidingView）

## 1. 游戏类型与视角

- **类型**：单人益智（滑动拼图 / 15-puzzle）
- **视角**：俯视，正方形 4×4 棋盘
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
companion object {
    const val N = 4          // 棋盘边长
    const val SIZE = N * N   // 格子总数 = 16
}

val board = IntArray(SIZE)   // 棋盘数组，0 表示空格，1~15 表示数字方块
var blank: Int               // 空格当前索引（0..15）
var moves: Int               // 已走步数
var over: Boolean            // 游戏是否已结束

// 布局相关（由 onLayout 计算）
var cell: Float              // 单元格边长（像素）
var boardLeft: Float         // 棋盘左上角 X
var boardTop: Float          // 棋盘左上角 Y
```

## 3. 初始化逻辑（reset）

1. 生成 1~15 的随机排列，填入 `board[0..14]`，`board[15] = 0`。
2. `blank = 15`（空格在右下角）。
3. 检查两个条件，若不满足则重新生成（do-while 循环）：
   - `!isSolved()` —— 不能一开始就已解
   - `isSolvable()` —— 必须数学上可解
4. `moves = 0`，`over = false`，`setScore(0)`，调用 `start()`。

### 可解性判断（isSolvable）

空格在最后一行时，使用**逆序数奇偶性**：统计 1~15 中逆序对个数，若为偶数则可解。

```kotlin
fun isSolvable(): Boolean {
    var inv = 0
    val vals = board.filter { it != 0 }
    for (i in vals.indices)
        for (j in i + 1 until vals.size)
            if (vals[i] > vals[j]) inv++
    return inv % 2 == 0
}
```

## 4. 输入处理

- **触发条件**：`ACTION_DOWN`，且 `!over && !paused`
- **坐标转换**：触摸坐标 → 行列 `(r, c)`
- **有效性检查**：`r` 和 `c` 必须在 `[0, N)` 范围内
- **相邻判断**：点击的格子与 `blank` 的曼哈顿距离 == 1（上下左右相邻）
  - 相邻：执行滑动 —— `board[blank] = board[idx]`，`board[idx] = 0`，`blank = idx`，`moves++`
  - 不相邻：播放点击音效，无操作

## 5. 核心更新循环（update(dt)）

**空实现** —— 纯逻辑游戏，无动画/物理需要每帧更新。所有状态变更在 `onTouch` 中即时完成。

## 6. 胜负判定

滑动后立即检查 `isSolved()`：

- **胜利条件**：`board[0..14] == [1, 2, ..., 15]` 且 `board[15] == 0`
- **得分公式**：`max(0, 500 - moves * 8)`
- **结算消息**：`"拼图完成！用了 {moves} 步"`

没有失败条件 —— 玩家可以无限步数尝试。

## 7. 边界与特殊规则

- **可解性保证**：`reset()` 的 do-while 循环确保每次生成的拼图一定可解，同时排除已解状态。
- **逆序数判断前提**：`isSolvable()` 假设空格在最后一行（`SIZE - 1` 位置），`reset()` 确保了这一点。
- **坐标映射**：`idx = r * N + c`，行优先存储。
- **自定义 abs/max**：源码中定义了 `abs(Int)` 和 `max(Int, Int)` 私有方法，未使用 `kotlin.math`。

## 8. 渲染要求

- 棋盘占屏幕短边的 92%，居中。
- 每个格子有 2px 间距（`+2f` / `-2f`），圆角 `cell * 0.1`。
- 空格用深色 `blankPaint`（`0xFF1E2740`），数字格用 `cellPaint`（`0xFF2A3557`）。
- 数字文字大小 `cell * 0.42f`，居中绘制。
- 棋盘下方显示步数和操作提示。
