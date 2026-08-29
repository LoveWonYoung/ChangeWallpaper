package com.example.changewallpaper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.io.IOException
import java.util.concurrent.TimeUnit

class WallpaperWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = workerMutex.withLock {
        val store = SettingsStore.get(applicationContext)
        var settings = store.read()
        val forced = inputData.getBoolean(KEY_FORCED, false)
        if (!forced && !settings.isEnabled) {
            return@withLock Result.success(output("自动更换已关闭"))
        }
        if (!forced && settings.activeHoursEnabled && !isWithinActiveHours(settings)) {
            return@withLock Result.success(output("当前不在生效时间段"))
        }
        val networkFileName = inputData.getString(KEY_NETWORK_FILE)
        if (networkFileName != null || settings.source == WallpaperSource.NETWORK) {
            return@withLock changeNetworkWallpaper(store, settings, networkFileName)
        }
        if (settings.albums.isEmpty()) {
            store.update { it.copy(lastError = "尚未添加壁纸相册") }
            return@withLock Result.failure(output("尚未添加壁纸相册"))
        }

        val command = runCatching {
            RunCommand.valueOf(inputData.getString(KEY_COMMAND) ?: RunCommand.NEXT.name)
        }.getOrDefault(RunCommand.NEXT)
        val targets = when (settings.target) {
            WallpaperTarget.HOME -> listOf(WallpaperTarget.HOME)
            WallpaperTarget.LOCK -> listOf(WallpaperTarget.LOCK)
            WallpaperTarget.BOTH -> {
                if (settings.albumFor(WallpaperTarget.HOME)?.id == settings.albumFor(WallpaperTarget.LOCK)?.id) {
                    listOf(WallpaperTarget.BOTH)
                } else listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)
            }
        }

        var lastSuccess: HistoryEntry? = null
        var failureMessage = ""
        for (target in targets) {
            val album = settings.albumFor(target)
            if (album == null) {
                failureMessage = "没有为${target.label()}选择相册"
                continue
            }
            val images = try {
                WallpaperScanner.scan(applicationContext, album)
            } catch (_: SecurityException) {
                failureMessage = "相册“${album.name}”访问权限已失效"
                emptyList()
            } catch (exception: Exception) {
                failureMessage = exception.message ?: "无法扫描相册“${album.name}”"
                emptyList()
            }
            if (images.isEmpty()) {
                if (failureMessage.isBlank()) failureMessage = "相册“${album.name}”内没有可用图片"
                continue
            }

            val attempted = mutableSetOf<String>()
            var applied = false
            while (attempted.size < images.size) {
                val choice = SelectionLogic.select(
                    images = images,
                    settings = settings.copy(excludedUris = settings.excludedUris + attempted),
                    albumId = album.id,
                    command = command
                ) ?: break
                attempted += choice.image.uri
                try {
                    WallpaperRenderer.apply(applicationContext, choice.image, settings.cropMode, target)
                    val entry = HistoryEntry(
                        System.currentTimeMillis(),
                        choice.image.name,
                        choice.image.uri,
                        target,
                        true
                    )
                    settings = store.update { current ->
                        current.copy(
                            indexes = current.indexes + (album.id to choice.nextIndex),
                            recentUris = choice.recentUris,
                            history = (listOf(entry) + current.history).take(MAX_HISTORY),
                            lastError = ""
                        )
                    }
                    lastSuccess = entry
                    applied = true
                    break
                } catch (exception: Exception) {
                    failureMessage = "已跳过 ${choice.image.name}：${exception.message ?: "图片不可用"}"
                    val failedEntry = HistoryEntry(
                        System.currentTimeMillis(),
                        choice.image.name,
                        choice.image.uri,
                        target,
                        false,
                        failureMessage
                    )
                    settings = store.update { current ->
                        val remainingCount = (images.count { image ->
                            image.uri !in current.excludedUris && image.uri != choice.image.uri
                        }).coerceAtLeast(1)
                        current.copy(
                            indexes = current.indexes + (album.id to (choice.selectedIndex % remainingCount)),
                            excludedUris = current.excludedUris + choice.image.uri,
                            history = (listOf(failedEntry) + current.history).take(MAX_HISTORY),
                            lastError = failureMessage
                        )
                    }
                }
            }
            if (!applied && failureMessage.isBlank()) failureMessage = "所有图片都已排除或无法使用"
        }

        if (lastSuccess != null) {
            if (settings.notificationsEnabled) showNotification(lastSuccess!!)
            Result.success(output("已更换为 ${lastSuccess!!.imageName}"))
        } else {
            store.update { it.copy(lastError = failureMessage.ifBlank { "更换壁纸失败" }) }
            Result.failure(output(failureMessage.ifBlank { "更换壁纸失败" }))
        }
    }

    private suspend fun changeNetworkWallpaper(
        store: SettingsStore,
        settings: AppSettings,
        fileName: String?
    ): Result {
        if (settings.wifiOnly && !hasUnmeteredNetwork()) {
            return Result.retry()
        }
        val command = runCatching {
            RunCommand.valueOf(inputData.getString(KEY_COMMAND) ?: RunCommand.NEXT.name)
        }.getOrDefault(RunCommand.NEXT)
        val mode = if (command == RunCommand.RANDOM) NetworkMode.RANDOM else settings.networkMode
        return try {
            val downloaded = if (fileName == null) {
                NetworkWallpaperClient.downloadCurrent(applicationContext, mode)
            } else {
                NetworkWallpaperClient.downloadGalleryImage(applicationContext, fileName)
            }
            WallpaperRenderer.apply(applicationContext, downloaded.image, settings.cropMode, settings.target)
            val entry = HistoryEntry(
                timestamp = System.currentTimeMillis(),
                imageName = downloaded.image.name,
                imageUri = downloaded.historyUrl,
                target = settings.target,
                success = true
            )
            store.update { current ->
                current.copy(
                    history = (listOf(entry) + current.history).take(MAX_HISTORY),
                    lastError = ""
                )
            }
            if (settings.notificationsEnabled) showNotification(entry)
            Result.success(output("已从网络更换为 ${entry.imageName}"))
        } catch (exception: WallpaperHttpException) {
            recordNetworkFailure(store, exception.message ?: "网络壁纸请求失败")
            if (exception.retryable) Result.retry()
            else Result.failure(output(exception.message ?: "网络壁纸请求失败"))
        } catch (exception: IOException) {
            recordNetworkFailure(store, "网络连接失败，稍后重试")
            Result.retry()
        } catch (exception: Exception) {
            val message = exception.message ?: "网络图片无法使用"
            recordNetworkFailure(store, message)
            if (runAttemptCount < 1) Result.retry() else Result.failure(output(message))
        }
    }

    private suspend fun recordNetworkFailure(store: SettingsStore, message: String) {
        store.update { it.copy(lastError = message) }
    }

    private fun hasUnmeteredNetwork(): Boolean {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
        return manager.activeNetwork != null && !manager.isActiveNetworkMetered
    }

    private fun isWithinActiveHours(settings: AppSettings): Boolean {
        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = settings.activeStartHour
        val end = settings.activeEndHour
        if (start == end) return true
        return if (start < end) now in start until end else now >= start || now < end
    }

    private fun showNotification(entry: HistoryEntry) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "壁纸更换结果", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("壁纸已更换")
            .setContentText(entry.imageName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    private fun output(message: String) = Data.Builder().putString(KEY_MESSAGE, message).build()

    private fun WallpaperTarget.label(): String = when (this) {
        WallpaperTarget.HOME -> "主屏幕"
        WallpaperTarget.LOCK -> "锁定屏幕"
        WallpaperTarget.BOTH -> "主屏幕和锁屏"
    }

    companion object {
        const val KEY_COMMAND = "command"
        const val KEY_FORCED = "forced"
        const val KEY_MESSAGE = "message"
        const val KEY_NETWORK_FILE = "network_file"
        private const val MAX_HISTORY = 50
        private const val CHANNEL_ID = "wallpaper_changes"
        private const val NOTIFICATION_ID = 7001
        private val workerMutex = Mutex()
    }
}

object WallpaperScheduler {
    const val PERIODIC_WORK_NAME = "automatic_wallpaper_change"
    const val IMMEDIATE_WORK_NAME = "immediate_wallpaper_change"

    fun start(
        context: Context,
        intervalMinutes: Long,
        source: WallpaperSource,
        wifiOnly: Boolean
    ) {
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(
            intervalMinutes.coerceAtLeast(15),
            TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints(source == WallpaperSource.NETWORK, wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    fun changeNow(
        context: Context,
        command: RunCommand = RunCommand.NEXT,
        source: WallpaperSource = WallpaperSource.LOCAL,
        wifiOnly: Boolean = false,
        networkFileName: String? = null
    ) {
        val input = Data.Builder()
            .putString(WallpaperWorker.KEY_COMMAND, command.name)
            .putBoolean(WallpaperWorker.KEY_FORCED, true)
            .apply { networkFileName?.let { putString(WallpaperWorker.KEY_NETWORK_FILE, it) } }
            .build()
        val needsNetwork = source == WallpaperSource.NETWORK || networkFileName != null
        val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .setInputData(input)
            .setConstraints(networkConstraints(needsNetwork, wifiOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun networkConstraints(needsNetwork: Boolean, wifiOnly: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                when {
                    !needsNetwork -> NetworkType.NOT_REQUIRED
                    wifiOnly -> NetworkType.UNMETERED
                    else -> NetworkType.CONNECTED
                }
            )
            .build()
}
