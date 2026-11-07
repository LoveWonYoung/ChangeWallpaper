package com.example.changewallpaper

import android.app.PendingIntent
import android.app.StatusBarManager
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

class WallpaperTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_change_wallpaper)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setTileSubtitle(this)
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickActionActivity::class.java)
            .setAction(QuickActionActivity.ACTION_CHANGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                201,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            startLegacyActivityAndCollapse(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setTileSubtitle(tile: Tile) {
        tile.subtitle = getString(R.string.tile_tap_to_change)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startLegacyActivityAndCollapse(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    companion object {
        fun requestAdd(context: Context, onResult: (Int) -> Unit): Boolean {
            if (Build.VERSION.SDK_INT < 33) return false
            val manager = context.getSystemService(StatusBarManager::class.java) ?: return false
            manager.requestAddTileService(
                ComponentName(context, WallpaperTileService::class.java),
                context.getString(R.string.tile_change_wallpaper),
                Icon.createWithResource(context, R.mipmap.ic_launcher),
                context.mainExecutor,
                onResult
            )
            return true
        }
    }
}
