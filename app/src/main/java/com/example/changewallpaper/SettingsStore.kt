package com.example.changewallpaper

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

private val Context.wallpaperDataStore by preferencesDataStore(name = "wallpaper_data")

class SettingsStore private constructor(private val context: Context) {
    private val settingsKey = stringPreferencesKey("settings_json")

    val settingsFlow: Flow<AppSettings> = context.wallpaperDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw exception
        }
        .map { preferences -> decode(preferences[settingsKey]) }

    suspend fun ensureMigrated() {
        context.wallpaperDataStore.edit { data ->
            if (data[settingsKey] != null) return@edit
            val legacy = context.getSharedPreferences("wallpaper_settings", Context.MODE_PRIVATE)
            val uri = legacy.getString("folder_uri", null)
            val name = legacy.getString("folder_name", "").orEmpty()
            val album = uri?.let {
                WallpaperAlbum(UUID.randomUUID().toString(), name.ifBlank { "壁纸相册" }, it)
            }
            val migrated = AppSettings(
                source = if (album == null) WallpaperSource.NETWORK else WallpaperSource.LOCAL,
                albums = listOfNotNull(album),
                homeAlbumId = album?.id,
                lockAlbumId = album?.id,
                intervalMinutes = 5,
                target = WallpaperTarget.from(legacy.getString("target", null)),
                isEnabled = legacy.getBoolean("enabled", false),
                indexes = album?.let { mapOf(it.id to legacy.getInt("current_index", 0)) }.orEmpty(),
                lastError = legacy.getString("last_error", "").orEmpty()
            )
            data[settingsKey] = encode(migrated)
        }
    }

    suspend fun read(): AppSettings {
        ensureMigrated()
        return settingsFlow.first()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        ensureMigrated()
        var result = AppSettings()
        context.wallpaperDataStore.edit { data ->
            result = transform(decode(data[settingsKey]))
            data[settingsKey] = encode(result)
        }
        return result
    }

    suspend fun importJson(json: String): AppSettings {
        val imported = decode(json, throwOnInvalid = true)
        require(imported.albums.all { it.treeUri.startsWith("content://") }) { "备份中包含无效文件夹" }
        return update { imported.copy(isEnabled = false, lastError = "") }
    }

    suspend fun exportJson(): String = encode(read())

    private fun encode(settings: AppSettings): String = JSONObject().apply {
        put("version", 6)
        put("source", settings.source.storedValue)
        put("apiBaseUrl", settings.apiBaseUrl)
        put("networkMode", settings.networkMode.storedValue)
        put("galleryColumns", settings.galleryColumns)
        put("galleryRows", settings.galleryRows)
        put("albums", JSONArray().apply {
            settings.albums.forEach { album ->
                put(JSONObject().apply {
                    put("id", album.id)
                    put("name", album.name)
                    put("treeUri", album.treeUri)
                    put("includeSubfolders", album.includeSubfolders)
                })
            }
        })
        put("homeAlbumId", settings.homeAlbumId)
        put("lockAlbumId", settings.lockAlbumId)
        put("intervalMinutes", settings.intervalMinutes)
        put("target", settings.target.storedValue)
        put("rotationMode", settings.rotationMode.storedValue)
        put("cropMode", settings.cropMode.storedValue)
        put("isEnabled", settings.isEnabled)
        put("indexes", JSONObject().apply { settings.indexes.forEach { (key, value) -> put(key, value) } })
        put("excludedUris", JSONArray(settings.excludedUris.toList()))
        put("recentUris", JSONArray(settings.recentUris))
        put("history", JSONArray().apply {
            settings.history.take(MAX_HISTORY).forEach { entry ->
                put(JSONObject().apply {
                    put("timestamp", entry.timestamp)
                    put("imageName", entry.imageName)
                    put("imageUri", entry.imageUri)
                    put("target", entry.target.storedValue)
                    put("success", entry.success)
                    put("message", entry.message)
                })
            }
        })
        put("activeHoursEnabled", settings.activeHoursEnabled)
        put("activeStartHour", settings.activeStartHour)
        put("activeEndHour", settings.activeEndHour)
        put("notificationsEnabled", settings.notificationsEnabled)
        put("themeMode", settings.themeMode.storedValue)
        put("accentStyle", settings.accentStyle.storedValue)
        put("lastError", settings.lastError)
    }.toString()

    private fun decode(raw: String?, throwOnInvalid: Boolean = false): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        return try {
            val json = JSONObject(raw)
            val albums = json.optJSONArray("albums").toObjectList { item ->
                WallpaperAlbum(
                    id = item.getString("id"),
                    name = item.optString("name", "壁纸相册"),
                    treeUri = item.getString("treeUri"),
                    includeSubfolders = item.optBoolean("includeSubfolders", true)
                )
            }
            val indexesJson = json.optJSONObject("indexes") ?: JSONObject()
            val indexes = indexesJson.keys().asSequence().associateWith { indexesJson.optInt(it, 0) }
            val interval = if (json.optInt("version", 0) < 4) {
                5L
            } else {
                json.optLong("intervalMinutes", 5).coerceAtLeast(5)
            }
            AppSettings(
                source = if (json.has("source")) {
                    WallpaperSource.from(json.optString("source"))
                } else if (albums.isNotEmpty()) {
                    WallpaperSource.LOCAL
                } else {
                    WallpaperSource.NETWORK
                },
                apiBaseUrl = NetworkWallpaperClient.normalizeBaseUrl(
                    json.optString("apiBaseUrl", NetworkWallpaperClient.BASE_URL)
                ) ?: NetworkWallpaperClient.BASE_URL,
                networkMode = NetworkMode.from(json.optString("networkMode")),
                galleryColumns = json.optInt("galleryColumns", 3).coerceIn(2, 4),
                galleryRows = json.optInt("galleryRows", 6).coerceIn(3, 8),
                albums = albums,
                homeAlbumId = json.optNullableString("homeAlbumId"),
                lockAlbumId = json.optNullableString("lockAlbumId"),
                intervalMinutes = interval,
                target = WallpaperTarget.from(json.optString("target")),
                rotationMode = RotationMode.from(json.optString("rotationMode")),
                cropMode = CropMode.from(json.optString("cropMode")),
                isEnabled = json.optBoolean("isEnabled", false),
                indexes = indexes,
                excludedUris = json.optJSONArray("excludedUris").toStringSet(),
                recentUris = json.optJSONArray("recentUris").toStringList(),
                history = json.optJSONArray("history").toObjectList { item ->
                    HistoryEntry(
                        timestamp = item.optLong("timestamp"),
                        imageName = item.optString("imageName"),
                        imageUri = item.optString("imageUri"),
                        target = WallpaperTarget.from(item.optString("target")),
                        success = item.optBoolean("success"),
                        message = item.optString("message")
                    )
                },
                activeHoursEnabled = json.optBoolean("activeHoursEnabled", false),
                activeStartHour = json.optInt("activeStartHour", 8).coerceIn(0, 23),
                activeEndHour = json.optInt("activeEndHour", 23).coerceIn(0, 23),
                notificationsEnabled = json.optBoolean("notificationsEnabled", false),
                themeMode = AppThemeMode.from(json.optString("themeMode")),
                accentStyle = AccentStyle.from(json.optString("accentStyle")),
                lastError = json.optString("lastError")
            )
        } catch (exception: Exception) {
            if (throwOnInvalid) throw IllegalArgumentException("无法读取备份文件", exception)
            AppSettings(lastError = "设置文件损坏，已恢复默认设置")
        }
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        if (this@toStringList == null) return@buildList
        for (index in 0 until length()) add(optString(index))
    }

    private fun JSONArray?.toStringSet(): Set<String> = toStringList().toSet()

    private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> = buildList {
        if (this@toObjectList == null) return@buildList
        for (index in 0 until length()) optJSONObject(index)?.let { add(transform(it)) }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        private const val MAX_HISTORY = 50

        fun get(context: Context): SettingsStore = SettingsStore(context.applicationContext)
    }
}
