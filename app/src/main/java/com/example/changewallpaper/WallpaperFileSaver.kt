package com.example.changewallpaper

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object WallpaperFileSaver {
    private const val ALBUM_NAME = "ChangeWallpaper"

    suspend fun saveNetworkWallpaper(
        context: Context,
        fileName: String,
        apiBaseUrl: String
    ): String = withContext(Dispatchers.IO) {
        val displayName = safeFileName(fileName)
        val savedName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, fileName, apiBaseUrl, displayName)
        } else {
            saveToLegacyPictures(context, fileName, apiBaseUrl, displayName)
        }
        "图片/$ALBUM_NAME/$savedName"
    }

    internal fun safeFileName(fileName: String): String {
        val leaf = fileName.substringAfterLast('/').trim()
        val sanitized = leaf
            .map { character ->
                if (character.code < 32 || character in INVALID_FILE_NAME_CHARACTERS) '_' else character
            }
            .joinToString("")
            .trim(' ', '.')
            .ifBlank { "wallpaper-${System.currentTimeMillis()}" }
        return if (sanitized.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS) {
            sanitized
        } else {
            "$sanitized.jpg"
        }
    }

    private suspend fun saveWithMediaStore(
        context: Context,
        sourceFileName: String,
        apiBaseUrl: String,
        displayName: String
    ): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeTypeFor(displayName))
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法在系统图片库中创建文件")
        try {
            val actualMimeType = resolver.openOutputStream(uri, "w")?.use { output ->
                NetworkWallpaperClient.copyGalleryImageTo(sourceFileName, apiBaseUrl, output)
            } ?: throw IOException("无法写入系统图片库")
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.MIME_TYPE, actualMimeType)
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
            return displayName
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun saveToLegacyPictures(
        context: Context,
        sourceFileName: String,
        apiBaseUrl: String,
        displayName: String
    ): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("需要存储权限才能下载壁纸")
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM_NAME
        )
        if (!directory.exists() && !directory.mkdirs()) throw IOException("无法创建壁纸保存目录")
        val destination = uniqueFile(directory, displayName)
        try {
            val mimeType = destination.outputStream().buffered().use { output ->
                NetworkWallpaperClient.copyGalleryImageTo(sourceFileName, apiBaseUrl, output)
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destination.absolutePath),
                arrayOf(mimeType),
                null
            )
            return destination.name
        } catch (exception: Exception) {
            destination.delete()
            throw exception
        }
    }

    private fun uniqueFile(directory: File, displayName: String): File {
        val initial = File(directory, displayName)
        if (!initial.exists()) return initial
        val extension = displayName.substringAfterLast('.', "")
        val baseName = displayName.removeSuffix(if (extension.isBlank()) "" else ".$extension")
        var suffix = 2
        while (true) {
            val candidate = File(directory, "$baseName ($suffix)${if (extension.isBlank()) "" else ".$extension"}")
            if (!candidate.exists()) return candidate
            suffix++
        }
    }

    private fun mimeTypeFor(displayName: String): String = when (displayName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        else -> "image/jpeg"
    }

    private val INVALID_FILE_NAME_CHARACTERS = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
}
