package com.minigames.collection.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val arm = UpdatePolicy.Asset("arm64-v8a", "https://github.com/NFraw/android-offline-minigames/releases/download/v4.0/game.apk", "a".repeat(64), 5000)
    private val release = UpdatePolicy.Release(40, "4.0", "notes", listOf(arm))
    @Test fun newerCompatibleReleaseOnly() {
        assertEquals(arm, UpdatePolicy.select(release,39,listOf("arm64-v8a")))
        assertNull(UpdatePolicy.select(release,40,listOf("arm64-v8a")))
        assertNull(UpdatePolicy.select(release,41,listOf("arm64-v8a")))
        assertNull(UpdatePolicy.select(release,39,listOf("x86_64")))
    }
    @Test fun rejectsUntrustedOrIncompleteDownloads() {
        assertFalse(UpdatePolicy.validateAsset(arm.copy(url=arm.url.replace("github.com","github.com.evil.test"))))
        assertFalse(UpdatePolicy.validateAsset(arm.copy(url=arm.url.replace("https:","http:"))))
        assertFalse(UpdatePolicy.validateAsset(arm.copy(url=arm.url.replace("NFraw","other"))))
        assertFalse(UpdatePolicy.validateAsset(arm.copy(url=arm.url+"?file=other")))
        assertFalse(UpdatePolicy.validateAsset(arm.copy(sha256="")))
        assertFalse(UpdatePolicy.validateAsset(arm.copy(size=0)))
        assertFalse(UpdatePolicy.validateAsset(arm.copy(size=UpdatePolicy.MAX_APK_BYTES+1)))
    }
    @Test fun prefersDeviceAbiOrder() {
        val x86 = arm.copy(abi="x86_64")
        assertEquals(x86, UpdatePolicy.select(release.copy(assets=listOf(arm,x86)),39,listOf("x86_64","arm64-v8a")))
    }
}
