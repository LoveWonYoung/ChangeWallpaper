package com.example.changewallpaper

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NetworkWallpaperClientTest {
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
    fun `base URL normalization accepts HTTP and HTTPS and removes trailing slash`() {
        assertEquals("https://example.com/api", NetworkWallpaperClient.normalizeBaseUrl(" https://example.com/api/ "))
        assertEquals("http://192.168.1.10:8080/api", NetworkWallpaperClient.normalizeBaseUrl(" http://192.168.1.10:8080/api/ "))
        assertEquals(null, NetworkWallpaperClient.normalizeBaseUrl("not a url"))
    }
}
