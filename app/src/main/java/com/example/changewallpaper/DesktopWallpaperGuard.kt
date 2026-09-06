package com.example.changewallpaper

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import java.util.concurrent.TimeUnit

/** An unconfirmed desktop is never permission to change wallpaper. */
object DesktopWallpaperGuard {
    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun getLauncherPackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        @Suppress("DEPRECATION")
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    /**
     * Usage events are a best-effort signal, not a synchronous top-window API.
     * Track resumed activities rather than lastTimeUsed (which can include background use).
     * On tablets multiple apps can be resumed; ambiguity must block the wallpaper change.
     */
    @Suppress("DEPRECATION")
    fun getForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        // Ignore earlier boots and fail closed if the current activity predates our bounded window.
        val lookback = minOf(SystemClock.elapsedRealtime(), TimeUnit.HOURS.toMillis(24))
        val events = usage.queryEvents(now - lookback, now) ?: return null
        val resumed = mutableMapOf<String, String>()
        val event = UsageEvents.Event()
        var lastForeground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            // Activity instance IDs are not exposed by the public Android SDK.
            val activityKey = "$packageName:${event.className}"
            when (event.eventType) {
                // MOVE_TO_FOREGROUND/BACKGROUND have the same values as RESUMED/PAUSED on API 29+.
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    resumed[activityKey] = packageName
                    lastForeground = packageName
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    resumed.remove(activityKey)
                    if (lastForeground == packageName && packageName !in resumed.values) lastForeground = null
                }
                UsageEvents.Event.DEVICE_SHUTDOWN, UsageEvents.Event.DEVICE_STARTUP -> {
                    resumed.clear()
                    lastForeground = null
                }
            }
        }
        // Don't fall back to an older launcher event after a game or another app has paused.
        val foreground = lastForeground ?: return null
        return foreground.takeIf { resumed.values.toSet() == setOf(foreground) }
    }

    fun blockedReason(context: Context): String? {
        if (!hasUsageAccess(context)) return "已跳过：请在设置中开启使用情况访问权限，仅在桌面时更换壁纸"
        return try {
            val power = context.getSystemService(PowerManager::class.java)
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            val user = context.getSystemService(UserManager::class.java)
            when {
                power?.isInteractive != true || keyguard?.isKeyguardLocked != false || user?.isUserUnlocked != true ->
                    "已跳过：屏幕关闭或设备锁定"
                else -> {
                    val launchers = getLauncherPackages(context)
                    val foreground = getForegroundPackage(context)
                    when {
                        launchers.isEmpty() || foreground == null -> "已跳过：无法确认当前处于桌面"
                        foreground !in launchers -> "已跳过：当前不是桌面，请回到桌面后再更换"
                        else -> null
                    }
                }
            }
        } catch (_: Exception) {
            "已跳过：无法读取桌面状态"
        }
    }

    fun requireDesktop(context: Context) {
        blockedReason(context)?.let { throw WallpaperChangeDeferredException(it) }
    }
}

/** A normal skipped run: never exclude its image, record a failure, or retry aggressively. */
internal class WallpaperChangeDeferredException(message: String) : Exception(message)
