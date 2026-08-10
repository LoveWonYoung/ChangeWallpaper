package com.example.changewallpaper

import android.app.WallpaperManager
import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val preferences = WallpaperPreferences(applicationContext)
        val settings = preferences.load()
        val folderUri = settings.folderUri
            ?: return failure(preferences, "尚未选择壁纸文件夹")

        return try {
            val folder = DocumentFile.fromTreeUri(applicationContext, folderUri.toUri())
                ?: return failure(preferences, "无法打开所选文件夹，请重新选择")
            val images = folder.listFiles()
                .filter { it.isFile && isImage(it) }
                .sortedBy { it.name?.lowercase().orEmpty() }

            if (images.isEmpty()) {
                return failure(preferences, "所选文件夹内没有可用图片")
            }

            val index = Math.floorMod(settings.currentIndex, images.size)
            val image = images[index]
            applicationContext.contentResolver.openInputStream(image.uri).use { stream ->
                checkNotNull(stream) { "无法读取图片 ${image.name.orEmpty()}" }
                WallpaperManager.getInstance(applicationContext).setStream(
                    stream,
                    null,
                    true,
                    settings.target.toWallpaperFlags()
                )
            }
            preferences.recordSuccess(
                nextIndex = (index + 1) % images.size,
                wallpaperName = image.name ?: "未命名图片"
            )
            Result.success()
        } catch (securityException: SecurityException) {
            failure(preferences, "文件夹访问权限已失效，请重新选择")
        } catch (exception: Exception) {
            val message = exception.message?.takeIf { it.isNotBlank() } ?: "更换壁纸失败"
            preferences.recordError(message)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun failure(preferences: WallpaperPreferences, message: String): Result {
        preferences.recordError(message)
        return Result.failure()
    }

    private fun isImage(file: DocumentFile): Boolean {
        if (file.type?.startsWith("image/") == true) return true
        return file.name?.substringAfterLast('.', "")?.lowercase() in IMAGE_EXTENSIONS
    }

    private fun WallpaperTarget.toWallpaperFlags(): Int = when (this) {
        WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
        WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
        WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
    }
}

object WallpaperScheduler {
    private const val PERIODIC_WORK_NAME = "automatic_wallpaper_change"

    fun start(context: Context, intervalMinutes: Long) {
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    fun changeNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueue(OneTimeWorkRequestBuilder<WallpaperWorker>().build())
    }
}
