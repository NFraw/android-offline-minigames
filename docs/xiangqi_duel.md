# 象棋双人对战（XiangqiDuel）游戏逻辑文档

本文档基于 **XiangqiBaseView**（公共基类）和 **XiangqiRules**（规则引擎），描述 **XiangqiDuelView**（双人对战版）的完整逻辑。

## 1. 游戏类型与视角

- **类型**：双人回合制棋类
- **视角约定**：红方在屏幕下方（y=5..9），黑方在屏幕上方（y=0..4）。黑方棋子文字朝上（旋转 180°），红方棋子文字朝下（正常）。双方各自有独立的悔棋按钮。
- **屏幕方向**：竖屏

## 2. 数据模型

### 2.1 棋盘与棋子（XiangqiRules）

```kotlin
const val COLS = 9              // 列数
const val ROWS = 10             // 行数

// 棋子编码：红方 1..7，黑方 11..17
const val EMPTY = 0
const val R_KING = 1      // 帥
const val R_ADVISOR = 2   // 仕
const val R_ELEPHANT = 3  // 相
const val R_HORSE = 4     // 傌
const val R_ROOK = 5      // 俥
const val R_CANNON = 6    // 炮
const val R_PAWN = 7      // 兵
const val B_KING = 11     // 將
const val B_ADVISOR = 12  // 士
const val B_ELEPHANT = 13 // 象
const val B_HORSE = 14    // 馬
const val B_ROOK = 15     // 車
const val B_CANNON = 16   // 砲
const val B_PAWN = 17     // 卒

const val RED = 0          // 红方
const val BLACK = 1        // 黑方

data class Move(val from: Int, val to: Int)
```

### 2.2 视图状态（XiangqiBaseView）

```kotlin
protected var board: IntArray           // 棋盘状态（90 格）
protected var turn = RED                // 当前回合方
protected var selected = -1             // 当前选中的棋子位置（-1 未选中）
protected var legalTargets: ArrayList<Int>  // 选中棋子的合法目标位置
protected var lastFrom = -1             // 上一步起始位置
protected var lastTo = -1               // 上一步目标位置
protected var gameOverFlag = false
protected var statusText = ""           // 状态文字（如"将军！"）
protected var aiThinking = false        // AI 思考中（双人版不使用）

protected val history: ArrayList<HistoryEntry>    // 走子历史
protected val capturedRed: ArrayList<Int>          // 红方吃掉的黑子
protected val capturedBlack: ArrayList<Int>        // 黑方吃掉的红子
protected val undoLeft = IntArray(2) { 3 }         // 每方剩余悔棋次数（上限 3）

// 走子动画
private var animFrom = -1
private var animTo = -1
private var animProgress = 0f
private var animating = false

// 布局参数
private var cellX = 0f           // 水平格距
private var cellY = 0f           // 垂直格距
private var boardLeft = 0f       // 棋盘左边界
private var boardTop = 0f        // 棋盘上边界
private var stoneR = 0f          // 棋子半径
private var topStripY = 0f       // 黑方信息条 y
private var bottomStripY = 0f    // 红方信息条 y

// 按钮区域
protected val undoBtnBlack = RectF()   // 黑方悔棋按钮
protected val undoBtnRed = RectF()     // 红方悔棋按钮
protected val resignBtn = RectF()      // 认输按钮（双人版未启用）

// 历史记录条目
protected class HistoryEntry(val from: Int, val to: Int, val captured: Int)
```

## 3. 初始化逻辑（reset）

1. `board = XiangqiRules.initialBoard()` —— 标准开局
2. `turn = RED`（红方先手）
3. `selected = -1`，`legalTargets` 清空
4. `lastFrom = lastTo = -1`
5. `gameOverFlag = false`，`statusText = ""`
6. `history`、`capturedRed`、`capturedBlack` 清空
7. `undoLeft[RED] = undoLeft[BLACK] = 3`
8. 重置动画状态
9. 播放 START 语音
10. 调用 `start()`

### 标准开局布局（initialBoard）

```
Row 0 (黑方底线): 車 馬 象 士 將 士 象 馬 車
Row 1:            ·  ·  ·  ·  ·  ·  ·  ·  ·
Row 2:            · 砲 ·  ·  ·  ·  · 砲 ·
Row 3:            卒 ·  卒 ·  卒 ·  卒 ·  卒
Row 4:            ·  ·  ·  ·  ·  ·  ·  ·  ·
Row 5:            ·  ·  ·  ·  ·  ·  ·  ·  ·
Row 6:            兵 ·  兵 ·  兵 ·  兵 ·  兵
Row 7:            · 炮 ·  ·  ·  ·  · 炮 ·
Row 8:            ·  ·  ·  ·  ·  ·  ·  ·  ·
Row 9 (红方底线): 俥 傌 相 仕 帥 仕 相 傌 俥
```

## 4. 输入处理（XiangqiBaseView.onTouch）

- 仅处理 `ACTION_DOWN`，且 `gameOverFlag` 或 `paused` 时忽略

### 4.1 按钮区域
- 检测触摸是否命中 `undoBtnBlack` 或 `undoBtnRed`
- 命中则调用 `requestUndo(side)`

### 4.2 棋盘点击
- 将触摸坐标转换为最近交叉点 `(r, c)`
- 距离检查：`Math.hypot(dx, dy) > min(cellX, cellY) * 0.5` 则忽略
- 调用 `onIntersectionTap(idx)`（由子类实现）

### 4.3 XiangqiDuelView.onIntersectionTap 逻辑

**无选中状态**（`selected < 0`）：
- 点击己方棋子 → 选中，计算合法目标位置
- 其他情况 → 忽略

**已选中状态**：
- 点击同一棋子 → 取消选中
- 点击己方其他棋子 → 切换选中
- 点击合法目标位置 → 执行走子 `doMove(Move(selected, idx))`
- 其他情况 → 取消选中

## 5. 核心更新循环（update(dt)）

仅处理走子动画：

```kotlin
if (animating) {
    animProgress += dt / 0.14f   // 动画时长 0.14 秒
    if (animProgress >= 1f) {
        animProgress = 1f
        animating = false
    }
    invalidate()
}
```

动画使用平滑曲线：`t = progress^2 * (3 - 2*progress)`

## 6. 走子与回合流程

### 6.1 doMove(move) —— 执行走子

1. 记录被吃棋子 `captured = board[move.to]`
2. 添加历史记录 `HistoryEntry(from, to, captured)`
3. 如果吃子：
   - 添加到对应方的吃子列表
   - 播放 EAT 音效 + 震动 + 语音"吃"
4. 否则播放 MOVE 音效
5. 更新棋盘：`board[to] = board[from]`，`board[from] = EMPTY`
6. 更新 `lastFrom/lastTo`
7. 清除选中状态
8. 启动走子动画
9. 调用 `onMoveCompleted(move)`

### 6.2 XiangqiDuelView.onMoveCompleted(move)

1. 计算下一方 `next = 1 - turn`
2. 调用 `checkGameOver(next)` 检查是否将死/困毙
3. 未结束则 `turn = next`
4. 调用 `updateStatus()` 更新状态文字
5. 调用 `announceCheckIfAny()` 检查是否将军并播报

## 7. 规则引擎（XiangqiRules）

### 7.1 棋子走法

| 棋子 | 走法 | 限制 |
|------|------|------|
| 車/俥 | 直线任意距离 | 遇子停（可吃） |
| 馬/傌 | 日字（先直后斜） | 蹩马腿：直线第一步有子则不能走 |
| 象/相 | 田字（斜走两格） | 塞象眼：中心点有子则不能走；不过河 |
| 士/仕 | 斜走一格 | 仅限九宫（3-5列，红方7-9行/黑方0-2行） |
| 將/帥 | 直走一格 | 仅限九宫 |
| 砲/炮 | 直线移动（不吃子时同車） | 吃子必须隔一子（炮架） |
| 卒/兵 | 过河前只能前进；过河后可左右 | 红方向上（r-1），黑方向下（r+1）；过河线：红方 r<5，黑方 r>4 |

### 7.2 isLegalMove(board, from, to) —— 完整合法性

1. 起点必须有棋子
2. 目标不能是己方棋子
3. 目标必须在 `targets()` 返回的可达位置中
4. **模拟走子**：复制棋盘，执行走子，检查己方帅是否被将军
   - 包含飞将检查（双方帅将同列且中间无子）

### 7.3 isInCheck(board, side) —— 将军检查

1. 找到己方帅/将的位置
2. 遍历对方所有棋子，检查帅是否在任何棋子的可达范围内
3. **飞将检查**：双方帅将同列且中间无子 → 被将军

### 7.4 checkGameOver(side) —— 终局检查

1. `hasAnyMove(board, side)` 检查该方是否有合法走子
2. 无合法走子：
   - 被将军 → 将死，对方获胜
   - 未被将军 → 困毙（无子可走），对方获胜
3. 播放 WIN 音效，将死时播放"将死"语音

## 8. 胜负判定

- **将死**：一方被将军且无合法走子可解 → 对方获胜
- **困毙**：一方未被将军但无合法走子 → 对方获胜
- **认输**：`onResignRequested()` → 当前方认输（双人版未启用认输按钮）
- **结算信息**：如"红方获胜！黑方被将死"

## 9. 悔棋机制

### 双人版（XiangqiDuelView.requestUndo）

1. 检查该方悔棋次数是否用完（每方 3 次）
2. 检查是否有可撤销的步骤
3. 弹出 AlertDialog 确认框：
   - 标题："悔棋申请"
   - 内容：说明请求方和剩余次数
   - 按钮："同意" / "拒绝"
4. **黑方悔棋时弹窗旋转 180°**：弹窗内容朝向黑方（上方玩家），使其能正常阅读
   - 旋转 decorView 的直接子视图（非 decorView 本身），确保触摸命中测试正确
5. 对方同意后调用 `undo()`，成功则扣减悔棋次数

### undo() 逻辑

1. 检查历史非空、未结束、未在动画中
2. 弹出最后一条历史记录
3. 恢复棋盘：`board[from] = board[to]`，`board[to] = captured`
4. 恢复吃子列表
5. 更新 `lastFrom/lastTo`
6. 恢复回合方
7. 播放 CLICK 音效 + "悔棋"语音

## 10. 边界与特殊规则

- ⚠️ **飞将**：双方帅将同列且中间无子时，等同于将军。`isInCheck` 和 `isLegalMove` 都会检查。
- ⚠️ **蹩马腿**：马走日字时，直线方向的第一格有子则不能走。
- ⚠️ **塞象眼**：象走田字时，对角线中心点有子则不能走。
- ⚠️ **象不过河**：红方象只能在 row 5-9，黑方象只能在 row 0-4。
- ⚠️ **炮架**：炮移动时不能越子，吃子时必须恰好隔一个棋子（炮架）。
- ⚠️ **九宫限制**：将/帅/士/仕只能在九宫内移动（列 3-5，红方行 7-9，黑方行 0-2）。
- ⚠️ **走子后必须不被将**：`isLegalMove` 会模拟走子并检查己方帅是否被将，包括飞将。
- ⚠️ **走子动画**：动画时长 0.14 秒，使用平滑曲线。动画期间不能悔棋（`undo()` 检查 `animating`）。
- ⚠️ **悔棋弹窗朝向**：黑方（上方玩家）悔棋时弹窗旋转 180°，旋转的是 decorView 的子视图而非 decorView 本身，确保按钮触摸命中正确。
- ⚠️ **悔棋扣减时机**：仅在 `undo()` 返回 true（真正撤销成功）时才扣减次数，避免动画进行中撤销被跳过仍浪费次数。

## 11. 渲染要求

### 棋盘
- **底板**：木纹渐变（暖色木质），圆角矩形，双层描边
- **网格线**：棕色线条，楚河汉界处纵向断开（中间 5 行不画左右边线）
- **九宫斜线**：两个九宫各两条对角线
- **楚河汉界**：中央显示"楚  河"和"汉  界"

### 棋子
- **底色**：奶白色渐变（径向高光）
- **内圈环线**：传统棋子样式
- **外描边**：红方红边（`0xFFB3402F`），黑方黑边（`0xFF2A2F3A`）
- **文字**：衬线粗体，黑方文字朝上（旋转 180°）
- **投影**：柔和阴影

### 交互反馈
- **选中**：柔光光晕 + 亮边描边
- **合法目标**：空位显示小圆点，吃子位置显示红圈
- **最后一步**：起止位置标记
- **将军警示**：帅/将位置脉冲红圈

### 信息条
- **上方**（黑方）：左侧悔棋按钮 + 吃子列表 + "黑方"标签
- **下方**（红方）：左侧悔棋按钮 + 吃子列表 + "红方"标签
- **中央**：回合状态行（如"黑方回合 · 将军！"）

### 棋子名称对照

| 编码 | 红方 | 黑方 |
|------|------|------|
| King | 帥 | 將 |
| Advisor | 仕 | 士 |
| Elephant | 相 | 象 |
| Horse | 傌 | 馬 |
| Rook | 俥 | 車 |
| Cannon | 炮 | 砲 |
| Pawn | 兵 | 卒 |
