package com.minigames.collection.update

import java.net.URI

/** Offline, deterministic validation of the public release manifest. */
object UpdatePolicy {
    const val REPOSITORY = "NFraw/android-offline-minigames"
    const val MANIFEST_URL = "https://github.com/$REPOSITORY/releases/latest/download/update.json"
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    data class Asset(val abi: String, val url: String, val sha256: String, val size: Long)
    data class Release(val versionCode: Long, val versionName: String, val notes: String, val assets: List<Asset>)
    fun validateAsset(asset: Asset): Boolean = runCatching {
        val uri = URI(asset.url)
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
            uri.rawUserInfo == null && uri.query == null && uri.fragment == null &&
            uri.rawPath.startsWith("/$REPOSITORY/releases/download/") &&
            uri.rawPath.endsWith(".apk") && !uri.rawPath.contains("..") &&
            asset.size in 1..MAX_APK_BYTES && asset.sha256.matches(Regex("[a-fA-F0-9]{64}"))
    }.getOrDefault(false)
    fun select(release: Release, installedCode: Long, supportedAbis: List<String>): Asset? {
        if (release.versionCode <= installedCode || release.versionName.isBlank()) return null
        return supportedAbis.firstNotNullOfOrNull { abi ->
            release.assets.firstOrNull { it.abi == abi && validateAsset(it) }
        }
    }
}
