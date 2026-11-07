package com.example.changewallpaper

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkWallpaperClientTest {
    @Test
    fun `legacy dynamic history never requests another image`() {
        listOf(
            "https://example.com/current",
            "https://example.com/api/next",
            "https://example.com/image/api/current",
            "http://192.168.1.10:8080/random?folder=IU",
            "file:///data/user/0/app/cache/wallpaper-previous",
            "file:///data/user/0/app/cache/wallpaper-latest"
        ).forEach { uri ->
            val entry = HistoryEntry(123L, "同步壁纸", uri, WallpaperTarget.BOTH, true)
            val migrated = NetworkWallpaperClient.normalizeHistoryEntry(entry)

            assertEquals("", migrated.imageUri)
            assertEquals(entry.timestamp, migrated.timestamp)
            assertEquals(entry.imageName, migrated.imageName)
            assertTrue(migrated.success)
            assertTrue(migrated.message.contains("无法还原预览"))
            assertEquals(migrated, NetworkWallpaperClient.normalizeHistoryEntry(migrated))
        }
    }

    @Test
    fun `fixed gallery and local history references remain intact`() {
        listOf(
            "https://example.com/image/IU/a.jpg",
            "https://example.com/image/current",
            "file:/data/user/0/app/no_backup/network-wallpaper-history/abc.image",
            "content://com.android.providers.media.documents/document/image%3A1"
        ).forEach { uri ->
            val entry = HistoryEntry(123L, "壁纸", uri, WallpaperTarget.HOME, true)
            assertEquals(entry, NetworkWallpaperClient.normalizeHistoryEntry(entry))
        }
    }

    @Test
    fun `gallery paths encode each segment without encoding slash`() {
        val url = NetworkWallpaperClient.thumbnailUrl("分类/hello world.jpg")

        assertEquals(listOf("thumb", "分类", "hello world.jpg"), url.toHttpUrl().pathSegments)
        assertFalse(url.contains("%2F", ignoreCase = true))
    }

    @Test
    fun `custom base URL is used for API and gallery paths`() {
        val baseUrl = "https://example.com/wallpaper"

        assertEquals(
            "https://example.com/wallpaper/next",
            NetworkWallpaperClient.endpointUrl(NetworkMode.NEXT, baseUrl)
        )
        assertEquals(
            "https://example.com/wallpaper/image/folder/a.jpg",
            NetworkWallpaperClient.imageUrl("folder/a.jpg", baseUrl)
        )
    }

    @Test
    fun `random URL selects and encodes a network album`() {
        val url = NetworkWallpaperClient.endpointUrl(NetworkMode.RANDOM, folder = "IDOL / IU")

        assertEquals("IDOL / IU", url.toHttpUrl().queryParameter("folder"))
        assertEquals("random", url.toHttpUrl().pathSegments.last())
        assertFalse(url.contains("folder=IDOL / IU"))
        assertThrows(IllegalArgumentException::class.java) {
            NetworkWallpaperClient.endpointUrl(NetworkMode.CURRENT, folder = "IU")
        }
    }

    @Test
    fun `previous network image stays on the active server and selected album`() {
        val wifi = "http://192.168.1.10:8080"
        val mobile = "https://wallpaper.example.com"
        val previous = NetworkWallpaperClient.endpointUrl(NetworkMode.RANDOM, wifi, "IU")

        assertTrue(NetworkWallpaperClient.isPreviousUrlAllowed(previous, wifi, "IU"))
        assertFalse(NetworkWallpaperClient.isPreviousUrlAllowed(previous, mobile, "IU"))
        assertFalse(NetworkWallpaperClient.isPreviousUrlAllowed(previous, wifi, "Karina"))
        assertFalse(NetworkWallpaperClient.isPreviousUrlAllowed(null, wifi, "IU"))
    }

    @Test
    fun `base URL normalization accepts HTTP and HTTPS and removes trailing slash`() {
        assertEquals("https://example.com/api", NetworkWallpaperClient.normalizeBaseUrl(" https://example.com/api/ "))
        assertEquals("http://192.168.1.10:8080/api", NetworkWallpaperClient.normalizeBaseUrl(" http://192.168.1.10:8080/api/ "))
        assertEquals(null, NetworkWallpaperClient.normalizeBaseUrl("not a url"))
    }
}
