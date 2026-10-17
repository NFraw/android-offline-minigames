# 新版安卓开发

新版路径：`D:\03Project\安卓离线小游戏-新版`。原项目和原 Git 仓库保持独立。

本工程采用Godot 4.7.2 标准版/GDScript 大厅、设置及29款游戏 + Kotlin 平台宿主。新游戏共用同一引擎，以场景组织。默认使用 Compatibility 渲染器，面向 Android 13+，targetSdk 36，运行时纯离线。

## 日常命令

在新版根目录打开 PowerShell：

```powershell
# 检查编辑器、模板、Java、安卓 SDK、ADB
powershell -ExecutionPolicy Bypass -File scripts/Check-Environment.ps1

# 打开 Godot 编辑器；按 F5 运行完整工程（内容场景通过公共壳启动）
powershell -ExecutionPolicy Bypass -File scripts/Open-Godot.ps1

# 编译新版 APK，同时跑 Godot 规则测试和 Kotlin 单元测试
powershell -ExecutionPolicy Bypass -File scripts/Build-Android.ps1 -Test

# 启动已有模拟器，然后构建并安装新版
powershell -ExecutionPolicy Bypass -File scripts/Start-Emulator.ps1
powershell -ExecutionPolicy Bypass -File scripts/Build-Android.ps1 -Install -Device emulator-5554

# 在模拟器上运行仪器化测试（包括保留的旧源码回归）
powershell -ExecutionPolicy Bypass -File scripts/Build-Android.ps1 -Instrumented -Device emulator-5554

# 构建签名正式包
powershell -ExecutionPolicy Bypass -File scripts/Build-Android.ps1 -Release
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`。正式 APK：`app/build/outputs/apk/release/app-release.apk`。

新版包名 `com.minigames.collection.next`，旧版 `com.minigames.collection`，可共存，存档分开。当前版本标识 `3.2.0-godot`。namespace 不变，避免无意义地移动现有全部源码。

## 从哪里开始

- `godot/project.godot`：Godot 工程入口。
- `godot/scenes/game_shell.tscn`：正式游戏通用壳；`engine_lab.tscn` 保留为开发验证场景。
- `godot/games/<id>/`：后续每款新游戏的场景、脚本和专用资源。
- `godot/assets/`：公共素材；同类素材统一风格，记录来源及许可，不使用绝对路径。
- `app/src/main/java/com/minigames/collection/engine/EngineGameActivity.kt`：正式安卓宿主与平台插件；`GamePlatformProvider.kt` 管理主进程服务。
- `scripts/Build-GodotPack.ps1`：资源导入、PCK 打包。Gradle 构建时自动调用，无须手工复制资源。
- `tools.lock.json`：锁定编辑器/安卓模板版本及官方 SHA512，记录 Gradle 校验值。

打开新版即进入 Godot 大厅；所有游戏在同一 `:godot` 进程中切换场景，返回大厅不重启引擎。主进程提供设置、成绩和音效服务。

## 工具与环境

Godot 为项目内便携安装，位置 `.tools/godot/`；`_sc_` 文件使配置、缓存、安卓模板保存在该目录的 `editor_data/`。不依赖全局 PATH 中有 Godot。

使用本机 JDK 21 和已有 Android SDK/API 36、Build Tools。模拟器脚本显式使用宿主 GPU；当前 AVD 默认的软件 SwiftShader 驱动有着色器 uniform 上限问题，会导致引擎画面空白，已在 NVIDIA 宿主 GPU 上验证正常。JVM 保留 `file.encoding=GBK` 以适配中文路径；PowerShell 脚本使用 UTF-8 BOM 兼容 Windows PowerShell 5.1；Kotlin/GDScript 使用 UTF-8。

已准备的是预编译 Godot AAR 接入链路，无需编译引擎源码，当前不需要安装 NDK/CMake 或 .NET。

新电脑先安装 JDK 17/21、Android SDK/API 36（含 build-tools、platform-tools）并接受 SDK 许可，再运行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/Setup-Tools.ps1 -SdkPath 'C:\Android\Sdk' -JavaPath 'C:\Java\jdk-21'
```

首次下载和 Gradle 依赖解析需要网络；安装后的游戏运行无需网络。编辑器及安卓模板来自 Godot 官方镜像，下载后校验 SHA512。官方整套模板下载较大，脚本只解压安卓部分。

`dev.local.json`、`local.properties`、`.tools/`、Godot 导入缓存和生成的 PCK/APK 均不提交。`release.keystore` 仍按现有项目的本机签名方式保留，不提交。还没有创建任何 Git 提交或远程仓库。

## 后续第一款游戏

飞机大战已作为第一款 Godot 迁移样板。公共承载层与后续接入方式见 [第一阶段重构](第一阶段重构.md)。实现游戏规则前仍需阅读根目录 `游戏逻辑.md`。

当前平台桥接已提供设置、音效/震动、最高分和幂等结算；通过主进程 ContentProvider 访问，不直接跨进程读取 SharedPreferences。29款已全部迁移；系统终止进程后的中途存档恢复尚未实现。

Godot 版本升级时同步修改工具锁定文件、Gradle AAR 版本和导出模板，然后重跑模拟器验证。发布包大小以实测为准，原项目“约 3MB”不再适用于新版。APK 默认仅含 ARM64；调试模拟器使用 x86_64，可用 -Abi all 生成多架构包。原生库压缩以减少离线安装包体积。

## 参考与许可

- [Godot Android 嵌入官方文档](https://docs.godotengine.org/en/stable/tutorials/platform/android/android_library.html)
- [Godot 安卓导出](https://docs.godotengine.org/en/stable/tutorials/export/exporting_for_android.html)
- [Godot 许可与第三方声明](https://godotengine.org/license/)

Godot 按 MIT 许可使用；对外发行时需要保留 Godot 和相关第三方组件声明。正式游戏增加外部美术/字体/音效时，统一登记来源及授权。




## 飞行棋体验版

见 [玩法、验收与体积记录](飞行棋体验版.md)。Build-Android 默认手机 Release 为 arm64-v8a，Debug 为 x86_64；手机调试请指定 -Abi arm64-v8a。多架构使用 -Abi all。

当前正式全量集成测试：`EngineIntegrationTest` 覆盖29款启动、暂停、返回、单实例复用及后台恢复；旧 Canvas 测试只验证保留基线。
