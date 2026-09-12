package com.example.changewallpaper

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppUpdateClientTest {
    private val validInfo = AppUpdateInfo(
        app = "changewallpaper",
        platform = "android",
        channel = "stable",
        version = "2.1",
        versionCode = 3,
        url = "https://update.wonyoung.top/changewallpaper/android/stable/changewallpaper-2.1.apk",
        sha256 = "a".repeat(64),
        size = 1024,
        notes = "测试更新"
    )

    @Test
    fun latestUrlUsesRequestedStableAndroidFolder() {
        assertEquals(
            "https://update.wonyoung.top/changewallpaper/android/stable/latest.json",
            AppUpdateClient.LATEST_URL
        )
    }

    @Test
    fun validManifestPassesValidation() {
        AppUpdateClient.validateManifest(validInfo)
    }

    @Test
    fun mismatchedAppIsRejected() {
        assertThrows(IOException::class.java) {
            AppUpdateClient.validateManifest(validInfo.copy(app = "wallpaper"))
        }
    }

    @Test
    fun insecureDownloadUrlIsRejected() {
        assertThrows(IOException::class.java) {
            AppUpdateClient.validateManifest(validInfo.copy(url = "http://example.com/update.apk"))
        }
    }

    @Test
    fun malformedSha256IsRejected() {
        assertThrows(IOException::class.java) {
            AppUpdateClient.validateManifest(validInfo.copy(sha256 = "not-a-sha256"))
        }
    }
}
