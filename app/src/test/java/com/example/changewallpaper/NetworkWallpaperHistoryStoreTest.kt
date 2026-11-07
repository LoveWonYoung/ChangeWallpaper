package com.example.changewallpaper

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class NetworkWallpaperHistoryStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun store() = NetworkWallpaperHistoryStore(temporaryFolder.root)

    private fun save(contents: String, url: String = "https://example.com/random") =
        store().save(ByteArrayInputStream(contents.toByteArray()), url, contents)

    @Test
    fun `same dynamic endpoint produces distinct immutable history images`() {
        val first = save("first image")
        val second = save("second image")

        assertNotEquals(first.historyUri, second.historyUri)
        assertEquals("first image", first.file.readText())
        assertEquals("second image", second.file.readText())
        assertTrue(first.file.name.matches(Regex("[0-9a-f]{64}\\.image")))
        assertEquals(first.historyUri, store().previous()?.historyUri)
    }

    @Test
    fun `identical bytes across gallery and automatic downloads share identity`() {
        val gallery = save("same image", "https://example.com/image/IU/a.jpg")
        val automatic = save("same image")

        assertEquals(gallery.historyUri, automatic.historyUri)
        assertEquals(1, gallery.file.parentFile!!.listFiles()!!.count { it.extension == "image" })
    }

    @Test
    fun `previous metadata survives a new store instance and later downloads`() {
        save("one")
        val second = save("two", "https://example.com/random?folder=IU")
        save("three")

        val previous = store().previous()!!
        assertEquals(second.historyUri, previous.historyUri)
        assertEquals("two", previous.name)
        assertEquals("https://example.com/random?folder=IU", previous.sourceUrl)
        assertEquals("two", previous.file.readText())
    }

    @Test
    fun `pruning retains history and both quick action pointers`() {
        val images = (1..53).map { save("image $it") }
        // Keep 50 historical downloads, deliberately excluding the current pointer pair.
        store().prune(images.take(50).map { it.historyUri })

        assertTrue(images.take(50).all { it.file.isFile })
        assertFalse(images[50].file.exists())
        assertTrue(images[51].file.isFile)
        assertTrue(images[52].file.isFile)
        assertEquals(images[51].historyUri, store().previous()?.historyUri)
    }

    @Test
    fun `clearing history keeps only latest and previous downloads`() {
        val images = (1..4).map { save("image $it") }
        store().prune(emptyList())

        assertFalse(images[0].file.exists())
        assertFalse(images[1].file.exists())
        assertTrue(images[2].file.isFile)
        assertTrue(images[3].file.isFile)
    }

    @Test
    fun `empty or interrupted downloads leave previous pointer intact and remove temporary files`() {
        val first = save("one")
        save("two")
        assertThrows(IOException::class.java) { save("") }
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("connection lost")
        }
        assertThrows(IOException::class.java) { store().save(broken, "https://example.com/next", "broken") }

        assertEquals(first.historyUri, store().previous()?.historyUri)
        assertFalse(first.file.parentFile!!.listFiles()!!.any { it.extension == "tmp" })
    }

    @Test
    fun `pruning never deletes files outside the history directory`() {
        val unrelated = File(temporaryFolder.root, "unrelated.image").apply { writeText("keep") }
        save("one")
        store().prune(listOf(unrelated.toURI().toString(), "https://example.com/image/a.jpg", "invalid URI"))

        assertEquals("keep", unrelated.readText())
    }

    @Test
    fun `pruning removes partial files left by an interrupted process`() {
        val snapshot = save("one")
        val partial = File(snapshot.file.parentFile, "download-interrupted.tmp").apply { writeText("partial") }
        store().prune(listOf(snapshot.historyUri))

        assertFalse(partial.exists())
        assertTrue(snapshot.file.isFile)
    }
}
