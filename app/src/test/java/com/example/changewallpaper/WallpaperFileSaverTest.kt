package com.example.changewallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperFileSaverTest {
    @Test
    fun `download file name uses leaf name and replaces invalid characters`() {
        assertEquals(
            "hello_world.jpg",
            WallpaperFileSaver.safeFileName("folder/hello:world.jpg")
        )
    }

    @Test
    fun `download file name gets a jpeg extension when missing`() {
        assertEquals("wallpaper.jpg", WallpaperFileSaver.safeFileName("folder/wallpaper"))
    }
}
