# GitHub 应用内更新

发布仓库：`NFraw/android-offline-minigames`。新版源码只使用 `godot-engine` 分支，旧版默认分支不覆盖。Release 与源码分支分离：APK 及 update.json 发布在同一个正式、最新 Release 中。

应用读取固定地址 `https://github.com/NFraw/android-offline-minigames/releases/latest/download/update.json`，无需用户登录 GitHub，也不在应用内携带 GitHub 令牌。仓库必须公开。应用进入大厅每天最多自动检查一次，设置可手动检查；对局中不弹升级窗口。无网络/服务不可达不影响游玩。

版本比较使用 Android versionCode，选择设备支持的 ABI。下载需用户同意，显示大小、更新说明与进度，可取消。下载后校验 SHA-256、文件大小、应用 ID、versionCode 与当前应用签名；失败不安装。首次需用户允许“安装未知应用”，随后启动系统安装确认页。不能静默安装。安装后保留本地成绩和设置。下载未完成不保留断点，需重新下载；仅 APK 更新走网络，游戏无需网络。

已有 v3.8.1 及更早版本没有更新功能，须手动安装一次 v3.9.0 或以后版本。后续一直用同一 release.keystore 签名并递增 versionCode，不要卸载旧版来更新。debug 模拟器签名与正式版不同，不能互相覆盖；正式更新包不能安装到 debug 版上。

## 每次发布

1. 在 godot-engine 修改源码，递增 app/build.gradle.kts 中 versionCode 和 versionName，保存更新说明文件。
2. 运行 `powershell -ExecutionPolicy Bypass -File scripts/Prepare-UpdateRelease.ps1 -Build -NotesFile <更新说明文件>`。
3. 检查 releases/github-版本/ 中的 APK、update.json、release-notes.md。先完成真机基本验收。
4. GitHub 新建 Release，标签使用脚本输出值，目标 godot-engine；将 APK 和 update.json 两个文件均上传；说明使用 release-notes.md。先保存草稿，确认附件齐全后发布为正式版本，并设为 Latest。
5. 未登录访问固定 update.json 地址，检查可下载，再用较旧正式签名版本测试完整下载与系统安装。GitHub 在部分网络访问不稳定时，应用会提示稍后重试；没有绕过证书或改用不受信任镜像。

Prepare-UpdateRelease.ps1 只准备素材。验收并将源码推送到 godot-engine 后，可使用 Publish-UpdateRelease.ps1，通过已登录的 GitHub CLI 创建含完整附件的草稿，再发布为正式 Latest。脚本拒绝其他分支、拒绝远程提交不匹配、拒绝覆盖现有版本。不要提交 release.keystore、本机配置、缓存、日志和截图；Godot 分支继承旧版 main 历史，发布时不改写 main。

规则基础：[GitHub Release 资产](https://docs.github.com/en/rest/releases/assets)、[Android 安装来源授权](https://android-developers.googleblog.com/2017/08/making-it-safer-to-get-apps-on-android-o.html)、[Android 安全文件分享](https://developer.android.com/training/secure-file-sharing/setup-sharing)。
