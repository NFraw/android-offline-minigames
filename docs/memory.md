# 记忆翻牌（memory）

## 1. 游戏类型与视角

| 项目 | 值 |
|------|---|
| 类型 | 单人 |
| 视角约定 | 无方向 |
| 屏幕方向 | 竖屏 |

## 2. 数据模型

```kotlin
val SIZE = 4                           // 4×4 棋盘
val EMOJIS = listOf("🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼")  // 8 种图案
val cards = IntArray(SIZE * SIZE)      // 每格的图案索引（0~7）
val matched = BooleanArray(SIZE * SIZE) // 已配对
var flipA: Int = -1                    // 第一张翻开的牌
var flipB: Int = -1                    // 第二张翻开的牌
var lockTimer: Float = 0f              // 翻错锁定计时
var moves: Int = 0                     // 步数
var pairs: Int = 0                     // 已配对数
var finished: Boolean = false
```

## 3. 初始化逻辑

- 生成 8 对图案（每种 2 张），共 16 张牌随机打乱
- 全部面朝下，`flipA=flipB=-1`，`moves=0`，`pairs=0`

## 4. 输入处理

- `ACTION_DOWN` 检测点击位置对应格子
- 若 `lockTimer>0` 或 `finished` → 忽略
- 已翻开或已配对的牌 → 忽略
- 第一次点击 → `flipA = idx`
- 第二次点击 → `flipB = idx`，`moves++`
  - 若图案相同 → 标记 matched，`pairs++`，清空 flip
  - 若不同 → `lockTimer = 0.7`

## 5. 核心更新循环

```
update(dt):
  若 lockTimer > 0:
    lockTimer -= dt
    若 lockTimer <= 0:
      flipA = -1; flipB = -1  // 翻回面朝下
```

纯事件驱动，update 只处理翻错延迟。

## 6. 胜负判定

- **胜利**：`pairs == 8`（全部配对）→ 最终得分 = `max(0, 800 - moves * 15)`
- **无失败条件**（步数越多分越低，但不会输）

## 7. 边界与特殊规则

1. **翻错延迟 0.7 秒**：两张不同图案的牌显示 0.7 秒后自动翻回
2. **点击已翻开牌无效**：`idx == flipA || idx == flipB` 时忽略
3. **得分公式**：`max(0, 800 - moves * 15)`——步数越少分越高
4. **配对即时反馈**：配对成功显示绿色背景 + 白色边框
5. **实时显示**：顶部显示"步数 N · 配对 M/8"

## 8. 渲染要求

- 4×4 方格，面朝下为深色 + "?"，面朝上显示 emoji
- 已配对格显示绿色背景 + 半透明白色边框
- 顶部显示步数和配对进度
