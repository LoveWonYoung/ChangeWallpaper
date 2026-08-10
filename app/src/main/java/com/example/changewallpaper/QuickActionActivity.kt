package com.example.changewallpaper

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class QuickActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = when (intent?.action) {
            ACTION_RANDOM -> RunCommand.RANDOM
            else -> RunCommand.NEXT
        }
        WallpaperScheduler.changeNow(this, command)
        Toast.makeText(this, "正在更换壁纸…", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val ACTION_CHANGE = "com.example.changewallpaper.CHANGE_NOW"
        const val ACTION_RANDOM = "com.example.changewallpaper.RANDOM_NOW"

        fun requestPinnedShortcut(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = context.getSystemService(ShortcutManager::class.java)
            if (!manager.isRequestPinShortcutSupported) return false
            val shortcut = ShortcutInfo.Builder(context, "pinned_change_wallpaper")
                .setShortLabel("一键换壁纸")
                .setLongLabel("立即更换下一张壁纸")
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(Intent(context, QuickActionActivity::class.java).setAction(ACTION_CHANGE))
                .build()
            val callback = PendingIntent.getBroadcast(
                context,
                0,
                Intent("com.example.changewallpaper.SHORTCUT_PINNED").setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            ).intentSender
            return manager.requestPinShortcut(shortcut, callback)
        }
    }
}
