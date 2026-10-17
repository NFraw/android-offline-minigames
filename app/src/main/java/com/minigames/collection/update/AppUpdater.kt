package com.minigames.collection.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Optional, public GitHub updates. Never blocks an offline game or installs silently. */
class AppUpdater(private val host: Activity, private val inHall: () -> Boolean) {
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs = host.getSharedPreferences("app_updates",0)
    private val cancelled = AtomicBoolean(false)
    private var checking = false
    private var downloading = false
    private var manualResult = false
    private var dialog: AlertDialog? = null
    private var pendingInstall: File? = null
    private val installed get() = host.packageManager.getPackageInfo(host.packageName,0).longVersionCode
    private fun ui(block: () -> Unit) = host.runOnUiThread {
        if (!host.isFinishing && !host.isDestroyed) block()
    }
    private fun show(builder: AlertDialog.Builder): AlertDialog {
        return builder.create().also { d ->
            dialog?.dismiss(); dialog = d; d.show()
            d.window?.setBackgroundDrawable(GradientDrawable().apply {
                setColor(Color.rgb(255,250,240)); cornerRadius = 24*host.resources.displayMetrics.density
            })
            for (id in listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE))
                d.getButton(id)?.setTextColor(Color.rgb(54,125,175))
        }
    }
    fun check(manual: Boolean) {
        if (downloading || !inHall()) return
        if (checking) {
            if (manual) {
                manualResult = true
                show(AlertDialog.Builder(host).setTitle("检查更新").setMessage("正在连接更新服务…").setNegativeButton("关闭",null))
            }
            return
        }
        val now = System.currentTimeMillis()
        if (!manual && now-prefs.getLong("last_check",0) < 24*60*60*1000L) return
        checking = true
        manualResult = manual
        android.util.Log.i("MiniGamesUpdate","UPDATE_CHECK manual=$manual")
        prefs.edit().putLong("last_check",now).apply()
        if (manual) show(AlertDialog.Builder(host).setTitle("检查更新").setMessage("正在连接更新服务…").setNegativeButton("关闭",null))
        worker.execute {
            runCatching { fetchRelease() }.fold(onSuccess = { release ->
                prefs.edit().putLong("last_check",now).apply()
                ui {
                    checking = false
                    if (!inHall()) { dialog?.dismiss(); return@ui }
                    val asset = UpdatePolicy.select(release,installed,Build.SUPPORTED_ABIS.toList())
                    if (asset != null) offer(release,asset)
                    else if (manualResult) message("检查更新",if (release.versionCode <= installed) "当前已是最新版本。" else "这个设备暂时没有可用的新版安装包。")
                }
            },onFailure = {
                android.util.Log.i("MiniGamesUpdate","UPDATE_CHECK_UNAVAILABLE")
                ui { checking = false; if (manualResult && inHall()) message("暂时无法检查更新","请稍后重试，或检查 GitHub 是否可以访问。所有游戏仍可离线游玩。") }
            })
        }
    }
    private fun fetchRelease(): UpdatePolicy.Release {
        val connection = connect(UpdatePolicy.MANIFEST_URL)
        try {
            val bytes = connection.inputStream.use { it.readNBytes(65537) }
            require(bytes.size <= 65536)
            val json = JSONObject(String(bytes,Charsets.UTF_8))
            require(json.getInt("schema") == 1 && json.getString("applicationId") == host.packageName)
            val entries = json.getJSONArray("assets")
            require(entries.length() in 1..8)
            val assets = (0 until entries.length()).map { index ->
                val a = entries.getJSONObject(index)
                UpdatePolicy.Asset(a.getString("abi"),a.getString("url"),a.getString("sha256"),a.getLong("size"))
            }
            return UpdatePolicy.Release(json.getLong("versionCode"),json.getString("versionName").take(80),json.optString("notes").take(3000),assets)
        } finally { connection.disconnect() }
    }
    private fun connect(address: String): HttpURLConnection {
        var url = URL(address)
        repeat(6) {
            require(url.protocol == "https")
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 12000; c.readTimeout = 20000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent","OfflineMiniGames-Android")
            val code = c.responseCode
            if (code in listOf(301,302,303,307,308)) {
                val location = c.getHeaderField("Location")
                c.disconnect(); require(!location.isNullOrBlank())
                url = URL(url,location)
            } else {
                if (code != 200) { c.disconnect(); error("HTTP $code") }
                return c
            }
        }
        error("Too many redirects")
    }
    private fun offer(release: UpdatePolicy.Release, asset: UpdatePolicy.Asset) {
        show(AlertDialog.Builder(host).setTitle("发现新版本 ${release.versionName}")
            .setMessage("安装包约 ${asset.size/1024/1024} MB\n\n${release.notes}\n\n更新会保留本机成绩和设置。")
            .setNegativeButton("稍后再说",null).setPositiveButton("下载更新") { _,_ -> download(release,asset) })
    }
    private fun download(release: UpdatePolicy.Release, asset: UpdatePolicy.Asset) {
        downloading = true; cancelled.set(false)
        val layout = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL; setPadding(32,16,32,16) }
        val status = TextView(host).apply { text = "正在下载 0%"; textSize = 18f; setTextColor(Color.rgb(37,56,75)) }
        val progress = ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        layout.addView(status); layout.addView(progress)
        val progressDialog = show(AlertDialog.Builder(host).setTitle("下载更新").setView(layout)
            .setNegativeButton("取消") { _,_ -> cancelled.set(true) }.setCancelable(false))
        worker.execute {
            val file = File(host.cacheDir,"updates/new-version.apk")
            runCatching {
                require(UpdatePolicy.validateAsset(asset)); file.parentFile!!.mkdirs()
                val connection = connect(asset.url)
                try {
                    val sha = MessageDigest.getInstance("SHA-256")
                    var total = 0L; var lastPercent = -1
                    connection.inputStream.use { input -> file.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            check(!cancelled.get() && !Thread.currentThread().isInterrupted)
                            val count = input.read(buffer); if (count < 0) break
                            total += count; require(total <= asset.size && total <= UpdatePolicy.MAX_APK_BYTES)
                            output.write(buffer,0,count); sha.update(buffer,0,count)
                            val percent = (total*100/asset.size).toInt()
                            if (percent != lastPercent) { lastPercent = percent; ui { progress.progress = percent; status.text = "正在下载 $percent%" } }
                        }
                    } }
                    require(total == asset.size)
                    require(sha.digest().joinToString("") { "%02x".format(it) }.equals(asset.sha256,true))
                    validateApk(file,release.versionCode)
                } finally { connection.disconnect() }
                ui { downloading = false; progressDialog.dismiss(); pendingInstall = file; requestInstall() }
            }.onFailure {
                file.delete()
                ui { downloading = false; progressDialog.dismiss(); if (!cancelled.get()) message("下载未完成","网络中断或安装包校验未通过，请重新检查更新。") }
            }
        }
    }
    private fun validateApk(file: File, expectedCode: Long) {
        val pm = host.packageManager
        val apk = requireNotNull(pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNING_CERTIFICATES))
        val own = pm.getPackageInfo(host.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        require(apk.packageName == host.packageName && apk.longVersionCode == expectedCode && expectedCode > own.longVersionCode)
        val expected = requireNotNull(own.signingInfo).apkContentsSigners.map { it.toCharsString() }.toSet()
        val actual = requireNotNull(apk.signingInfo).apkContentsSigners.map { it.toCharsString() }.toSet()
        require(expected.isNotEmpty() && expected == actual)
    }
    private fun requestInstall() {
        val file = pendingInstall ?: return
        if (!host.packageManager.canRequestPackageInstalls()) {
            show(AlertDialog.Builder(host).setTitle("允许安装更新")
                .setMessage("请在系统设置中允许本应用安装更新，返回后继续安装。")
                .setNegativeButton("稍后安装") { _,_ -> pendingInstall = null }
                .setPositiveButton("前往设置") { _,_ ->
                    runCatching { host.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${host.packageName}"))) }
                        .onFailure { pendingInstall = null; message("无法打开设置","请在系统设置中允许本应用安装未知应用后重试。") }
                })
            return
        }
        pendingInstall = null
        runCatching {
            val uri = FileProvider.getUriForFile(host,host.packageName+".update-files",file)
            host.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { message("无法打开安装程序","请稍后重试，或从 GitHub Release 下载新版安装。") }
    }
    fun onResume() {
        if (pendingInstall != null && host.packageManager.canRequestPackageInstalls()) requestInstall()
    }
    private fun message(title: String, text: String) {
        show(AlertDialog.Builder(host).setTitle(title).setMessage(text).setPositiveButton("知道了",null))
    }
    fun destroy() { cancelled.set(true); worker.shutdownNow(); dialog?.dismiss() }
}
