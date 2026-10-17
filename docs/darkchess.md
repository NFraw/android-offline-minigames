# 暗棋（DarkChessView）

## 1. 游戏类型与视角

- **类型**：双人棋类（暗棋 / 半盘翻棋）
- **视角**：俯视，4×8 棋盘。玩家 1 在底部，玩家 2 在顶部。玩家 2 的信息条旋转 180° 正对上方玩家。棋子复用象棋视觉，玩家 2 阵营的棋子字面朝上（旋转 180°）便于上方阅读。
- **屏幕方向**：竖屏

## 2. 数据模型

```kotlin
companion object {
    const val COLS = 4
    const val ROWS = 8
    // 复用象棋棋子编码：红 1..7，黑 11..17
    val RED_PIECES = intArrayOf(
        R_KING, R_ADVISOR, R_ADVISOR, R_ELEPHANT, R_ELEPHANT,
        R_ROOK, R_ROOK, R_HORSE, R_HORSE,
        R_CANNON, R_CANNON, R_PAWN, R_PAWN, R_PAWN, R_PAWN, R_PAWN
    )
}

val board = IntArray(COLS * ROWS)       // 棋盘，行优先，0=空
val revealed = BooleanArray(COLS * ROWS) // 是否已翻开
var selected: Int                        // 当前选中的己方明子索引，-1=未选中
var targets: ArrayList<Int>              // 选中棋子的合法落点列表
var turn: Int                            // 当前回合：0=玩家1（下），1=玩家2（上）
var color1: Int                          // 玩家1 的阵营色：1=红，-1=黑，0=未定（首次翻子决定）
var over: Boolean                        // 游戏是否已结束

// 布局相关
var cell: Float
var boardLeft: Float
var boardTop: Float
```

## 3. 初始化逻辑（reset）

1. 将 `RED_PIECES` 中每种棋子各取一份红（原值）一份黑（原值+10），共 32 子。
2. 随机打乱（`shuffle`）。
3. 依次填入 `board[0..31]`，`revealed` 全部设为 false（全部暗置）。
4. `selected = -1`，`targets.clear()`，`turn = 0`，`color1 = 0`（阵营未定），`over = false`。
5. 调用 `start()`。

## 4. 输入处理

- **触发条件**：`ACTION_DOWN`，且 `!over && !paused`
- **坐标转换**：触摸坐标 → 列 `c`、行 `r`，越界检查
- **点击暗子**（`!revealed[idx]`）：调用 `onFlip(idx)`
  - 翻开棋子（`revealed[idx] = true`）
  - 首次翻子决定阵营：`color1 = pieceSideCode(board[idx])`
  - 换手（`turn = 1 - turn`），检查僵局
- **点击明子**：
  - 未选中时：点击己方明子则选中（`select(idx)`），计算合法落点
  - 已选中时：
    - 点击同一棋子：取消选中
    - 点击己方另一棋子：改选
    - 点击合法落点：执行 `tryMove(to)`（移动/吃子）
    - 点击非法位置：取消选中

## 5. 核心更新循环（update(dt)）

**空实现** —— 纯回合制棋类，所有状态变更在 `onTouch` 中即时完成。

## 6. 胜负判定

### 吃光获胜（checkEnd）
移动/吃子后检查：若红方或黑方棋子数为 0，另一方获胜。

### 僵局获胜（checkStalemate）
每次换手后检查当前回合方是否有合法行动：
- 有暗子可翻 → 有行动
- 有明子可走（`legalTargets` 非空）→ 有行动
- 两者都没有 → 当前回合方判负，对方获胜

### 和棋兜底（quietPlies）
连续 120 手（60 回合）没有发生任何吃子时判和棋。
没有这条兜底时，双方都不主动进攻的僵持局可以无限走闲、永远不结束，玩家只能靠悬浮层的重开/退出逃离。

## 7. 边界与特殊规则

- **阵营决定**：`color1` 初始为 0，首次翻子时根据棋子颜色确定。`playerColor(0) = color1`，`playerColor(1) = -color1`。
- **棋子阵营判定**：`pieceSideCode(p)` 通过 `XiangqiRules.sideOf(p)` 判断，红方返回 1，黑方返回 -1。
- **吃子等级**（`rank` 函数）：
  - 帅将=7, 仕士=6, 相象=5, 车=4, 马=3, 炮=2, 兵卒=1
- **普通吃子**（`canCapture`，不含炮）：
  - 兵卒（1）可吃帅将（7）：`ar==1 && tr==7` 返回 true
  - 帅将（7）不能吃兵卒（1）：`ar==7 && tr==1` 返回 false
  - 同阶互吃：`ar >= tr`
- **炮的特殊规则**：
  - 炮不能直接相邻吃子（`legalTargets` 中跳过炮的普通吃子判定）
  - 炮只能沿直线恰好隔一枚棋子（炮架，任意颜色）跳吃
  - 炮的跳吃不受等级限制，可吃任何子（含帅将）
  - 实现：检查四方向，中间格（1 格远）必须有子，目标格（2 格远）必须有敌方明子
- **移动范围**：只能走相邻一格（上下左右，曼哈顿距离 1），不能斜走。
- **明子/暗子**：只有 `revealed[idx] == true` 的棋子才可见可操作。暗子只能被翻开，不能被吃。

## 8. 渲染要求

- 棋盘背景为木纹渐变（`LinearGradient`，`0xFFF6E4BD` → `0xFFDBB477`），带圆角边框。
- 暗子：棕色圆角矩形（`faceDownPaint`，`0xFFB08046`），中心有描边圆环。
- 明子：复用象棋渐变棋子视觉（`RadialGradient`），红方红色描边（`0xFFB3402F`），黑方深色描边（`0xFF2A2F3A`）。
- 选中高亮：金色发光圆（`selGlowPaint`，`0x33F5D547`）+ 金色描边圆环。
- 合法落点提示：空位显示半透明白色圆点，可吃子位置显示橙色描边圆环（`capPaint`，`0xFFFF7A45`）。
- 上下玩家信息条：当前回合方高亮绿色背景（`0xFF2E9E5B`），非回合方暗色。玩家 2 的信息条旋转 180°。
- 底部显示规则提示文字。
