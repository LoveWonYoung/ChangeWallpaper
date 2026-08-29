package com.example.changewallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionLogicTest {
    private val album = WallpaperAlbum("album", "测试", "content://album")
    private val images = listOf(
        WallpaperImage(album.id, "content://1", "1.jpg"),
        WallpaperImage(album.id, "content://2", "2.jpg"),
        WallpaperImage(album.id, "content://3", "3.jpg")
    )

    @Test
    fun sequentialSelectionAdvancesIndex() {
        val settings = AppSettings(albums = listOf(album), indexes = mapOf(album.id to 1))
        val result = SelectionLogic.select(images, settings, album.id, RunCommand.NEXT)
        assertEquals("2.jpg", result?.image?.name)
        assertEquals(2, result?.nextIndex)
    }

    @Test
    fun previousMovesBackFromNextIndex() {
        val settings = AppSettings(albums = listOf(album), indexes = mapOf(album.id to 2))
        val result = SelectionLogic.select(images, settings, album.id, RunCommand.PREVIOUS)
        assertEquals("1.jpg", result?.image?.name)
    }

    @Test
    fun excludedImagesAreNeverSelected() {
        val settings = AppSettings(
            albums = listOf(album),
            excludedUris = setOf("content://1", "content://2")
        )
        val result = SelectionLogic.select(images, settings, album.id, RunCommand.NEXT)
        assertEquals("3.jpg", result?.image?.name)
    }

    @Test
    fun shuffleAvoidsRecentlyUsedImages() {
        val settings = AppSettings(
            albums = listOf(album),
            rotationMode = RotationMode.SHUFFLE,
            recentUris = listOf("content://1", "content://2")
        )
        val result = SelectionLogic.select(images, settings, album.id, RunCommand.NEXT) { 0 }
        assertEquals("3.jpg", result?.image?.name)
        assertTrue(result?.recentUris?.containsAll(listOf("content://1", "content://2", "content://3")) == true)
    }

    @Test
    fun noAvailableImagesReturnsNull() {
        val settings = AppSettings(excludedUris = images.map { it.uri }.toSet())
        assertNull(SelectionLogic.select(images, settings, album.id, RunCommand.NEXT))
    }

    @Test
    fun largeImagesAreSampledWithoutDroppingBelowUsefulResolution() {
        assertEquals(2, WallpaperRenderer.calculateSampleSize(8_000, 6_000, 1_080, 2_400))
        assertEquals(2, WallpaperRenderer.calculateSampleSize(20_000, 2_000, 1_080, 2_400))
        assertEquals(1, WallpaperRenderer.calculateSampleSize(1_080, 2_400, 1_080, 2_400))
    }
}
