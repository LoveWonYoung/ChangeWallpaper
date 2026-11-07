package com.example.changewallpaper

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WallpaperActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = SettingsStore.get(appContext)
                when (action) {
                    ACTION_PREVIOUS, ACTION_NEXT, ACTION_RANDOM -> {
                        val settings = store.read()
                        val command = when (action) {
                            ACTION_PREVIOUS -> RunCommand.PREVIOUS
                            ACTION_RANDOM -> RunCommand.RANDOM
                            else -> RunCommand.NEXT
                        }
                        WallpaperScheduler.changeNow(
                            context = appContext,
                            command = command,
                            source = settings.source,
                            initialDelaySeconds = 2
                        )
                        toast(appContext, "已提交壁纸更换")
                    }
                    ACTION_PAUSE_ONE_DAY -> {
                        val until = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
                        val updated = store.update {
                            if (it.isEnabled) it.copy(pausedUntilMillis = until, lastError = "") else it
                        }
                        appContext.getSystemService(NotificationManager::class.java)
                            ?.cancel(WallpaperWorker.NOTIFICATION_ID)
                        WallpaperWidgetProvider.updateAll(appContext)
                        toast(appContext, if (updated.isEnabled) "自动更换已暂停一天" else "自动更换尚未开启")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_PREVIOUS = "com.example.changewallpaper.action.PREVIOUS"
        const val ACTION_NEXT = "com.example.changewallpaper.action.NEXT"
        const val ACTION_RANDOM = "com.example.changewallpaper.action.RANDOM"
        const val ACTION_PAUSE_ONE_DAY = "com.example.changewallpaper.action.PAUSE_ONE_DAY"
    }
}
