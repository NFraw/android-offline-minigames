# 打地鼠（whack）

## 1. 游戏类型与视角

| 项目 | 值 |
|------|---|
| 类型 | 单人 |
| 视角约定 | 无方向 |
| 屏幕方向 | 竖屏 |

## 2. 数据模型

```kotlin
val SIZE = 3                    // 3×3 洞穴网格
val GAME_SECONDS = 30           // 游戏时长（秒）
var started: Boolean = false    // 是否已点击开始
var timeLeft: Float = 30f       // 剩余时间
var score: Int = 0
var hits: Int = 0               // 命中数
var moleHole: Int = -1          // 当前地鼠所在洞穴索引（-1=无）
var moleTimer: Float = 0f       // 地鼠剩余显示时间
var spawnTimer: Float = 0.5f    // 下次生成等待时间
var flashHole: Int = -1         // 命中闪烁洞穴
var flashTimer: Float = 0f      // 闪烁剩余时间
```

## 3. 初始化逻辑

- 未开始状态，显示开始界面
- `started=false`，`timeLeft=30`，`score=0`，`hits=0`
- 地鼠未出现

## 4. 输入处理

- `ACTION_DOWN` 检测点击位置
- 未开始时：点击"开始游戏"按钮 → `started=true`
- 游戏中：遍历 9 个洞穴矩形，若点击到 `moleHole` 且 `flashTimer<=0` → 命中

## 5. 核心更新循环

```
update(dt):
  若 !started → return
  timeLeft -= dt
  若 timeLeft <= 0 → gameOver("时间到！共打中 N 只地鼠")

  若 flashTimer > 0 → flashTimer -= dt

  若 moleHole >= 0（有地鼠）:
    moleTimer -= dt
    若 moleTimer <= 0 → moleHole = -1（地鼠缩回）; spawnTimer = 0.35
  否则（无地鼠）:
    spawnTimer -= dt
    若 spawnTimer <= 0:
      moleHole = random(0..8)
      moleTimer = max(0.5, 1.15 - score * 0.004)  // 越高分地鼠越快缩回
```

## 6. 胜负判定

- **结束**：时间到 → `gameOver(score, true, "时间到！共打中 N 只地鼠")`
- 无失败条件（限时模式）

## 7. 边界与特殊规则

1. **地鼠显示时间递减**：`moleTimer = max(0.5, 1.15 - score * 0.004)`——分数越高地鼠越快缩回
2. **命中反馈**：命中后 `flashHole=i`，`flashTimer=0.25`，显示"💥"；同时 `moleHole=-1`
3. **无效点击**：点击空洞或已闪 Hole 时只播放 click 音效，不扣分
4. **生成间隔**：地鼠缩回后 `spawnTimer=0.35s`，命中后 `spawnTimer=0.3s`
5. **计时显示**：≤10 秒时文字变红

## 8. 渲染要求

- 3×3 椭圆洞穴，地鼠用圆形+耳朵+鼻子绘制
- 命中时显示"💥"动画（0.25 秒）
- 顶部显示倒计时
- 开始界面有标题、说明和绿色"开始游戏"按钮
