# 迷你游戏合集 · Godot 新版

37 款小游戏、大厅和设置均使用 Godot 4.7.2 / GDScript。Android Kotlin 只承载引擎、生命周期和本机平台服务。Android 13+，完全离线，应用 ID `com.minigames.collection.next`，可与原版共存。

原仓库 `D:\03Project\安卓离线小游戏` 保留；开发全部在本新版目录进行。原 Kotlin 游戏源码保留用于规则比较和旧测试，正式 37 款入口均使用 Godot 场景。

本版面向小学1—6年级，增加每款主题、三页图解、大字与简化准备流程；具体调整见 [适龄版改造](docs/development/适龄版改造.md)。

## 安装与运行

- 手机签名包：`releases/迷你游戏合集-v3.5.1-bosses-arm64-v8a.apk`。
- 电脑模拟器：`powershell -ExecutionPolicy Bypass -File scripts/Preview-Android.ps1`。
- Godot 编辑器：打开 `godot/project.godot`，F5 进入完整大厅；桌面单款预览可加 `-- --game=snake`。
- 开发与构建：[开发指南](docs/development/README.md)。

大厅支持全部、单人、双人、多人筛选，以及玩法、最高成绩和最近记录。设置包含音效、震动、中文语音和清空记录。游戏内支持暂停、重新选局、结算与返回大厅；切到后台会自动暂停，恢复后由玩家继续。进程被系统终止后的对局恢复尚未实现。

## 游戏

| 分类 | 游戏 |
|---|---|
| 单人 13 款 | 贪吃蛇、2048、俄罗斯方块、打地鼠、记忆翻牌、扫雷、别踩白块、数字华容道、切水果、弹一弹、打砖块、飞机大战、西蒙记忆 |
| 双人 13 款 | 五子棋、贪吃蛇对战、乒乓对决、井字棋、黑白棋、反应力对决、围棋、象棋、斗兽棋、暗棋、记忆对决、抢答算术、台球 |
| 多人 3 款 | 飞行棋、冰壶对决、四人反应 |

五子棋和黑白棋支持电脑对战。双人/多人同屏保留独立准备与多点操作；上方玩家文字朝向上方。横屏游戏按注册表切换方向，尺寸变化保留对局。

## 构建和验证

```powershell
powershell -ExecutionPolicy Bypass -File scripts/Test-Godot.ps1
powershell -ExecutionPolicy Bypass -File scripts/Build-Android.ps1 -Release -Test
powershell -ExecutionPolicy Bypass -File scripts/Build-Android.ps1 -Install -Device emulator-5554
```

37 款共用一个引擎与 PCK，手机默认只包含 ARM64，模拟器使用 x86_64。矢量绘制为主，避免大型贴图和模型；原版约 3MB 的体积不适用于 Godot 新版。构建仍保留 GBK JVM 设置。

26 个收集音效已复制到 [音效备份](assets-library/audio/kenney-sfx-backup/README.md)，包含源工具、授权记录和 SHA256 清单。v3.4.0已接入，并新增真实掷骰声与原创短琴音；保留原音效语义与中文语音开关。

迁移规则、测试和限制见 [全量迁移记录](docs/development/全量Godot迁移.md)；权威游戏清单见 [游戏状态管理](游戏状态管理.md)。Godot 和第三方组件许可保存在 `godot/assets/licenses/Godot-and-third-party.txt`，可在大厅设置中离线查看。

本轮[视觉与音效优化记录](docs/development/视觉与音效优化.md)：缓存纹理、细化角色/棋子/台面、反馈和声音，包约30.50MiB。

飞机大战现为六关战役：技能包可叠加三发、快射、导弹与追踪弹；敌方导弹可击落，逐关挑战Boss。数值与流程见 [战役设计](docs/development/飞机大战战役设计.md)。

## 应用更新
新版支持 GitHub Release 应用内检查、下载和系统确认安装；首次需手动安装支持更新的版本。游戏仍可离线玩。源码分支使用 godot-engine，旧默认分支保留。发布前请按 docs/development/GitHub应用更新.md 准备正式签名 APK 和 update.json。
