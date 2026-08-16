# 🎮 迷你游戏合集 MiniGames v1.1.0

**纯离线**的安卓小游戏合集：单个 APK 安装后**无需联网**即可游玩，
内置 **15 款游戏**（7 款单人 + 8 款双人同屏对战），支持本地最高分、内置音效/震动（可在设置中开关）、横竖屏自适应、双人**多点触控**同时操作。

## 📲 快速开始

| 版本 | 路径 | 说明 |
|---|---|---|
| **正式版** | 从 **GitHub Releases** 下载 `迷你游戏合集-v1.1.0.apk` | 已签名，可直接安装到 Android 7.0+ 手机 |
| 调试版 | `app/build/outputs/apk/debug/app-debug.apk` | 用于开发调试 |

安装后从桌面图标「迷你游戏合集」进入主菜单，按「单人 / 双人」分类选择游戏。

## 🎯 游戏列表

### 👤 单人（7 款）
| 游戏 | 玩法 |
|---|---|
| 🐍 贪吃蛇 | 滑动控制方向，吃到食物变长加速，撞墙/咬到自己结束 |
| 🔢 2048 | 滑动合并数字，合成 2048 获胜 |
| 🧱 俄罗斯方块 | 屏幕下方 ◀ ▶ ↻ ▼ 四键控制，消行得分 |
| 🤖 象棋（人机） | 执红对电脑 AI，将死对方获胜 |
| 🔨 打地鼠 | 点击「开始游戏」后 30 秒限时，点击冒出的地鼠得分 |
| 🃏 记忆翻牌 | 4×4 翻牌配对，全部配对完成获胜 |
| 💣 扫雷 | 点击翻格，长按插旗，翻开所有非雷格获胜 |

### 👥 双人同屏（8 款）
| 游戏 | 玩法 |
|---|---|
| ⚫ 五子棋 | 双方轮流落子，先连成五子者胜 |
| 🥊 贪吃蛇对战 | 上/下半屏各控一条蛇抢食物，撞对方/自己即输 |
| 🏓 乒乓对决 | 上/下半屏各拖一拍，先到 5 分获胜 |
| ❌ 井字棋 | 三子连线获胜，每局交换先手 |
| ⚪ 黑白棋 | 夹住对方棋子即可翻转，无子可下时跳过，子多者胜 |
| ⚡ 反应力对决 | 半屏变绿后抢先点击得分，抢拍判负，先到 10 分获胜 |
| 🎱 围棋 | 围地吃子，可虚手，终局按子数与围空定胜负 |
| ♜ 象棋（双人） | 完整中国象棋规则，将死对方获胜 |

## 🛠 技术栈

- 纯原生 **Kotlin + View/Canvas 自绘**，零第三方游戏引擎、零网络依赖
- **多点触控**：乒乓/贪吃蛇对战/反应力对决支持两名玩家同时操作各自半屏
- **横竖屏自适应**：竖屏手机、横屏平板均可游玩（平板体验更佳）
- **设置页**：音效/震动开关（SharedPreferences 持久化）、清空最高分
- `minSdk 24`（Android 7.0）~ `targetSdk 36`，单 APK 约 1MB
- 音效为程序合成的 WAV 全部内置（`res/raw`），无外部素材
- 最高分存 SharedPreferences（`scores.xml`），仅 VIBRATE 普通权限，无需网络
- 构建：AGP 8.13.2 + Kotlin 2.1.21 + Gradle 8.14.3

## 🔧 构建方法

仓库根目录（Windows，注意路径含中文，`gradle.properties` 已配置
`android.overridePathCheck=true`）：

```
gradlew.bat :app:assembleRelease
```

或使用本机已下载的 Gradle：

```
.tools\gradle-8.14.3\bin\gradle.bat :app:assembleRelease
```

**签名信息**：`release.keystore`（alias: `minigames`，密码: `minigames123`）
——个人项目使用，如需上架请自行更换密钥。

## 📁 项目结构

```
app/src/main/java/com/minigames/collection/
├── MainActivity.kt          # 主菜单（分类筛选 + 游戏宫格）
├── GameActivity.kt          # 游戏统一容器（顶栏/暂停/重开/结算弹窗）
├── common/
│   ├── GameView.kt          # 游戏基类（帧循环/暂停/触摸/得分回调）
│   ├── GameSpec.kt          # 游戏注册表（新增游戏只需加一条）
│   ├── ScoreStore.kt        # 本地最高分
│   ├── SoundManager.kt      # 内置音效
│   └── SwipeHelper.kt       # 滑动识别
└── games/                   # 每款游戏一个包（全部继承 GameView）
    ├── snake/  game2048/  tetris/  whack/  memory/  mines/
    └── gomoku/ snakebattle/ pong/ tictactoe/ reversi/ reaction/
```

## ✅ 已验证

- 全部 12 款游戏在模拟器（Android 16 / API 36）启动、操作、结算无崩溃
- 分类筛选、游戏进入/返回、暂停/恢复、重开、最高分持久化均正常
- 截图见 `.tools/shots/`（菜单 + 12 款游戏）