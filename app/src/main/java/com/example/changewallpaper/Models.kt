package com.example.changewallpaper

enum class WallpaperSource(val storedValue: String) {
    NETWORK("network"), LOCAL("local");

    companion object {
        fun from(value: String?): WallpaperSource = entries.firstOrNull { it.storedValue == value } ?: NETWORK
    }
}

enum class NetworkMode(val storedValue: String, val endpoint: String) {
    CURRENT("current", "current"), NEXT("next", "next"), RANDOM("random", "random");

    companion object {
        fun from(value: String?): NetworkMode = entries.firstOrNull { it.storedValue == value } ?: CURRENT
    }
}

enum class WallpaperTarget(val storedValue: String) {
    HOME("home"), LOCK("lock"), BOTH("both");

    companion object {
        fun from(value: String?): WallpaperTarget = entries.firstOrNull { it.storedValue == value } ?: BOTH
    }
}

enum class RotationMode(val storedValue: String) {
    SEQUENTIAL("sequential"), RANDOM("random"), SHUFFLE("shuffle");

    companion object {
        fun from(value: String?): RotationMode = entries.firstOrNull { it.storedValue == value } ?: SEQUENTIAL
    }
}

enum class CropMode(val storedValue: String) {
    SMART("smart"), FILL("fill"), FIT("fit"), BLUR("blur");

    companion object {
        fun from(value: String?): CropMode = entries.firstOrNull { it.storedValue == value } ?: SMART
    }
}

enum class AppThemeMode(val storedValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun from(value: String?): AppThemeMode = entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

enum class AccentStyle(val storedValue: String) {
    FOREST("forest"), OCEAN("ocean"), SUNSET("sunset"), DYNAMIC("dynamic");

    companion object {
        fun from(value: String?): AccentStyle = entries.firstOrNull { it.storedValue == value } ?: FOREST
    }
}

data class WallpaperAlbum(
    val id: String,
    val name: String,
    val treeUri: String,
    val includeSubfolders: Boolean = true
)

data class WallpaperImage(
    val albumId: String,
    val uri: String,
    val name: String
)

data class NetworkWallpaper(
    val fileName: String,
    val thumbnailUrl: String,
    val imageUrl: String
)

data class NetworkAlbum(
    val name: String,
    val coverFileName: String,
    val count: Int,
    val coverUrl: String
) {
    val displayName: String
        get() = name.ifBlank { "未分类" }
}

data class NetworkAlbumsState(
    val isLoading: Boolean = false,
    val albums: List<NetworkAlbum> = emptyList(),
    val totalCount: Int = 0,
    val error: String = ""
)

data class NetworkGalleryPage(
    val total: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val fileNames: List<String>
)

data class NetworkGalleryState(
    val isLoading: Boolean = false,
    val wallpapers: List<NetworkWallpaper> = emptyList(),
    val totalCount: Int = 0,
    val offset: Int = 0,
    val pageSize: Int = 24,
    val hasMore: Boolean = false,
    val error: String = ""
) {
    val pageCount: Int
        get() = if (totalCount == 0) 0 else (totalCount + pageSize - 1) / pageSize

    val pageIndex: Int
        get() = if (pageSize == 0) 0 else offset / pageSize

    val hasPreviousPage: Boolean
        get() = offset > 0

    val hasNextPage: Boolean
        get() = hasMore
}

data class HistoryEntry(
    val timestamp: Long,
    val imageName: String,
    val imageUri: String,
    val target: WallpaperTarget,
    val success: Boolean,
    val message: String = ""
)

data class AppSettings(
    val source: WallpaperSource = WallpaperSource.NETWORK,
    val networkMode: NetworkMode = NetworkMode.CURRENT,
    val albums: List<WallpaperAlbum> = emptyList(),
    val homeAlbumId: String? = null,
    val lockAlbumId: String? = null,
    val intervalMinutes: Long = 5,
    val target: WallpaperTarget = WallpaperTarget.BOTH,
    val rotationMode: RotationMode = RotationMode.SEQUENTIAL,
    val cropMode: CropMode = CropMode.SMART,
    val isEnabled: Boolean = false,
    val indexes: Map<String, Int> = emptyMap(),
    val excludedUris: Set<String> = emptySet(),
    val recentUris: List<String> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val activeHoursEnabled: Boolean = false,
    val activeStartHour: Int = 8,
    val activeEndHour: Int = 23,
    val notificationsEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val accentStyle: AccentStyle = AccentStyle.FOREST,
    val lastError: String = ""
) {
    fun albumFor(target: WallpaperTarget): WallpaperAlbum? {
        val id = when (target) {
            WallpaperTarget.HOME -> homeAlbumId ?: albums.firstOrNull()?.id
            WallpaperTarget.LOCK -> lockAlbumId ?: homeAlbumId ?: albums.firstOrNull()?.id
            WallpaperTarget.BOTH -> homeAlbumId ?: albums.firstOrNull()?.id
        }
        return albums.firstOrNull { it.id == id }
    }
}

enum class RunCommand { NEXT, PREVIOUS, RANDOM }

data class WorkStatus(
    val isRunning: Boolean = false,
    val message: String = ""
)
