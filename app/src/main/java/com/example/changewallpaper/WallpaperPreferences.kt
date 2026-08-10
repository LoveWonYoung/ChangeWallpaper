package com.example.changewallpaper

import android.content.Context
import androidx.core.content.edit

enum class WallpaperTarget(val storedValue: String) {
    HOME("home"),
    LOCK("lock"),
    BOTH("both");

    companion object {
        fun fromStoredValue(value: String?): WallpaperTarget =
            entries.firstOrNull { it.storedValue == value } ?: BOTH
    }
}

data class WallpaperSettings(
    val folderUri: String? = null,
    val folderName: String = "",
    val intervalMinutes: Long = 60,
    val target: WallpaperTarget = WallpaperTarget.BOTH,
    val isEnabled: Boolean = false,
    val currentIndex: Int = 0,
    val lastWallpaperName: String = "",
    val lastChangedAt: Long = 0,
    val lastError: String = ""
)

class WallpaperPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): WallpaperSettings = WallpaperSettings(
        folderUri = preferences.getString(KEY_FOLDER_URI, null),
        folderName = preferences.getString(KEY_FOLDER_NAME, "").orEmpty(),
        intervalMinutes = preferences.getLong(KEY_INTERVAL_MINUTES, 60),
        target = WallpaperTarget.fromStoredValue(preferences.getString(KEY_TARGET, null)),
        isEnabled = preferences.getBoolean(KEY_ENABLED, false),
        currentIndex = preferences.getInt(KEY_CURRENT_INDEX, 0),
        lastWallpaperName = preferences.getString(KEY_LAST_NAME, "").orEmpty(),
        lastChangedAt = preferences.getLong(KEY_LAST_CHANGED_AT, 0),
        lastError = preferences.getString(KEY_LAST_ERROR, "").orEmpty()
    )

    fun saveFolder(uri: String, name: String) {
        preferences.edit {
            putString(KEY_FOLDER_URI, uri)
            putString(KEY_FOLDER_NAME, name)
            putInt(KEY_CURRENT_INDEX, 0)
            putString(KEY_LAST_ERROR, "")
        }
    }

    fun saveSchedule(intervalMinutes: Long, target: WallpaperTarget, enabled: Boolean) {
        preferences.edit {
            putLong(KEY_INTERVAL_MINUTES, intervalMinutes)
            putString(KEY_TARGET, target.storedValue)
            putBoolean(KEY_ENABLED, enabled)
            putString(KEY_LAST_ERROR, "")
        }
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    fun recordSuccess(nextIndex: Int, wallpaperName: String) {
        preferences.edit {
            putInt(KEY_CURRENT_INDEX, nextIndex)
            putString(KEY_LAST_NAME, wallpaperName)
            putLong(KEY_LAST_CHANGED_AT, System.currentTimeMillis())
            putString(KEY_LAST_ERROR, "")
        }
    }

    fun recordError(message: String) {
        preferences.edit { putString(KEY_LAST_ERROR, message) }
    }

    private companion object {
        const val PREFS_NAME = "wallpaper_settings"
        const val KEY_FOLDER_URI = "folder_uri"
        const val KEY_FOLDER_NAME = "folder_name"
        const val KEY_INTERVAL_MINUTES = "interval_minutes"
        const val KEY_TARGET = "target"
        const val KEY_ENABLED = "enabled"
        const val KEY_CURRENT_INDEX = "current_index"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_LAST_CHANGED_AT = "last_changed_at"
        const val KEY_LAST_ERROR = "last_error"
    }
}
