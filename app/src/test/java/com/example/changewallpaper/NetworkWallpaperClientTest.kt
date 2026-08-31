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
}
