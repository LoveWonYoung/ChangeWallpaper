package com.example.changewallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkGalleryStateTest {
    @Test
    fun `7820 wallpapers are split into bounded pages`() {
        val firstPage = NetworkGalleryState(totalCount = 7_820, hasMore = true)
        val lastPage = firstPage.copy(offset = 7_800, hasMore = false)

        assertEquals(326, firstPage.pageCount)
        assertFalse(firstPage.hasPreviousPage)
        assertTrue(firstPage.hasNextPage)
        assertTrue(lastPage.hasPreviousPage)
        assertFalse(lastPage.hasNextPage)
    }

    @Test
    fun `empty gallery has no pages`() {
        val state = NetworkGalleryState()

        assertEquals(0, state.pageCount)
        assertFalse(state.hasPreviousPage)
        assertFalse(state.hasNextPage)
    }

    @Test
    fun `gallery layout controls page size`() {
        val compact = AppSettings(galleryColumns = 3, galleryRows = 4)
        val maximum = AppSettings(galleryColumns = 4, galleryRows = 8)

        assertEquals(12, compact.galleryPageSize)
        assertEquals(32, maximum.galleryPageSize)
    }
}
