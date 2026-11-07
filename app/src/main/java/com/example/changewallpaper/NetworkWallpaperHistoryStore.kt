package com.example.changewallpaper

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Properties

/** Immutable downloads, shared by history and the latest/previous wallpaper pointers. */
internal class NetworkWallpaperHistoryStore(filesDirectory: File) {
    private val directory = File(filesDirectory, "network-wallpaper-history")
    private val pointersFile = File(directory, "pointers.properties")

    data class Snapshot(val file: File, val sourceUrl: String, val name: String) {
        val historyUri: String get() = file.toURI().toString()
    }

    fun save(input: InputStream, sourceUrl: String, name: String): Snapshot = synchronized(lock) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建壁纸历史目录")
        val temporary = File.createTempFile("download-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(input, digest).use { stream ->
                temporary.outputStream().use { output -> stream.copyTo(output) }
            }
            if (temporary.length() == 0L) throw IOException("下载的图片为空")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val file = File(directory, "$hash.image")
            if (!file.isFile || file.length() == 0L) {
                if (!temporary.renameTo(file)) throw IOException("无法保存壁纸历史图片")
            }
            val snapshot = Snapshot(file, sourceUrl, name)
            val pointers = readPointers()
            val latest = readSnapshot(pointers, "latest")
            writeSnapshot(pointers, "previous", latest)
            writeSnapshot(pointers, "latest", snapshot)
            writePointers(pointers)
            snapshot
        } finally {
            temporary.delete()
        }
    }

    fun previous(): Snapshot? = synchronized(lock) {
        readSnapshot(readPointers(), "previous")
    }

    /** Keep every retained history image, plus the two downloads used by quick actions. */
    fun prune(historyUris: List<String>) = synchronized(lock) {
        val pointers = readPointers()
        val retained = historyUris.mapNotNull { uri ->
            runCatching {
                val parsed = URI(uri)
                if (parsed.scheme != "file") return@runCatching null
                File(parsed).takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }?.name
            }.getOrNull()
        }.toMutableSet()
        listOf("latest", "previous").forEach { prefix ->
            readSnapshot(pointers, prefix)?.let { retained += it.file.name }
        }
        directory.listFiles()?.forEach { file ->
            if (file.extension == "tmp" || (file.extension == "image" && file.name !in retained)) file.delete()
        }
    }

    private fun readPointers(): Properties = Properties().apply {
        if (pointersFile.isFile) pointersFile.inputStream().use { load(it) }
    }

    private fun readSnapshot(pointers: Properties, prefix: String): Snapshot? {
        val name = pointers.getProperty("$prefix.file") ?: return null
        if (!name.matches(Regex("[0-9a-f]{64}\\.image"))) return null
        val file = File(directory, name)
        if (!file.isFile || file.length() == 0L) return null
        return Snapshot(file, pointers.getProperty("$prefix.source", ""), pointers.getProperty("$prefix.name", "网络壁纸"))
    }

    private fun writeSnapshot(pointers: Properties, prefix: String, snapshot: Snapshot?) {
        listOf("file", "source", "name").forEach { pointers.remove("$prefix.$it") }
        if (snapshot != null) {
            pointers.setProperty("$prefix.file", snapshot.file.name)
            pointers.setProperty("$prefix.source", snapshot.sourceUrl)
            pointers.setProperty("$prefix.name", snapshot.name)
        }
    }

    private fun writePointers(pointers: Properties) {
        val temporary = File.createTempFile("pointers-", ".tmp", directory)
        try {
            temporary.outputStream().use { pointers.store(it, null) }
            if (!temporary.renameTo(pointersFile)) throw IOException("无法保存壁纸历史索引")
        } finally {
            temporary.delete()
        }
    }

    companion object {
        // Client downloads and history cleanup can run on different coroutine threads.
        private val lock = Any()
    }
}
