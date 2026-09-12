package com.example.changewallpaper

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class WallpaperUiState(
    val settings: AppSettings = AppSettings(),
    val imagesByAlbum: Map<String, List<WallpaperImage>> = emptyMap(),
    val networkAlbums: NetworkAlbumsState = NetworkAlbumsState(),
    val selectedNetworkAlbum: NetworkAlbum? = null,
    val networkGallery: NetworkGalleryState = NetworkGalleryState(),
    val downloadingFileNames: Set<String> = emptySet(),
    val downloadMessages: Map<String, String> = emptyMap(),
    val isScanning: Boolean = false,
    val workStatus: WorkStatus = WorkStatus(),
    val userMessage: String = ""
)

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SettingsStore.get(application)
    private val workManager = WorkManager.getInstance(application)
    private val _settings = MutableStateFlow(AppSettings())
    private val _images = MutableStateFlow<Map<String, List<WallpaperImage>>>(emptyMap())
    private val _networkAlbums = MutableStateFlow(NetworkAlbumsState())
    private val _selectedNetworkAlbum = MutableStateFlow<NetworkAlbum?>(null)
    private val _networkGallery = MutableStateFlow(NetworkGalleryState())
    private val _downloadingFileNames = MutableStateFlow<Set<String>>(emptySet())
    private val _downloadMessages = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _scanning = MutableStateFlow(false)
    private val _workStatus = MutableStateFlow(WorkStatus())
    private val _message = MutableStateFlow("")
    private val _uiState = MutableStateFlow(WallpaperUiState())
    private var settingsLoaded = false
    val uiState: StateFlow<WallpaperUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.ensureMigrated()
            store.settingsFlow.collect { settings ->
                val firstLoad = !settingsLoaded
                settingsLoaded = true
                val albumsChanged = settings.albums != _settings.value.albums
                _settings.value = settings
                publish()
                if (albumsChanged) refreshImages()
                if (firstLoad && settings.isEnabled) {
                    WallpaperScheduler.start(getApplication(), settings.intervalMinutes, settings.source)
                    if (settings.source == WallpaperSource.NETWORK) {
                        WallpaperScheduler.changeNow(getApplication(), source = settings.source)
                    }
                }
            }
        }
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(WallpaperScheduler.IMMEDIATE_WORK_NAME)
                .catch { emit(emptyList()) }
                .collect { infos ->
                    val latest = infos.maxByOrNull { it.runAttemptCount }
                    _workStatus.value = when (latest?.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> WorkStatus(true, "等待执行")
                        WorkInfo.State.RUNNING -> WorkStatus(true, "正在处理图片")
                        WorkInfo.State.SUCCEEDED -> WorkStatus(false, latest.outputData.getString(WallpaperWorker.KEY_MESSAGE).orEmpty())
                        WorkInfo.State.FAILED -> WorkStatus(false, latest.outputData.getString(WallpaperWorker.KEY_MESSAGE) ?: "更换失败")
                        else -> WorkStatus()
                    }
                    publish()
                }
        }
    }

    fun addAlbum(uri: Uri) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val name = DocumentFile.fromTreeUri(getApplication(), uri)?.name ?: "壁纸相册"
                store.update { current ->
                    val existing = current.albums.firstOrNull { it.treeUri == uri.toString() }
                    val album = existing ?: WallpaperAlbum(UUID.randomUUID().toString(), name, uri.toString())
                    val albums = if (existing == null) current.albums + album else current.albums
                    current.copy(
                        albums = albums,
                        homeAlbumId = current.homeAlbumId ?: album.id,
                        lockAlbumId = current.lockAlbumId ?: album.id,
                        lastError = ""
                    )
                }
                message("已添加相册“$name”")
            } catch (_: SecurityException) {
                message("无法保存文件夹访问权限")
            }
        }
    }

    fun removeAlbum(album: WallpaperAlbum) = update { current ->
        val albums = current.albums.filterNot { it.id == album.id }
        current.copy(
            albums = albums,
            homeAlbumId = current.homeAlbumId.takeUnless { it == album.id } ?: albums.firstOrNull()?.id,
            lockAlbumId = current.lockAlbumId.takeUnless { it == album.id } ?: albums.firstOrNull()?.id,
            indexes = current.indexes - album.id,
            excludedUris = current.excludedUris.filterNot { uri ->
                _images.value[album.id].orEmpty().any { it.uri == uri }
            }.toSet()
        )
    }

    fun setHomeAlbum(id: String) = update { it.copy(homeAlbumId = id) }
    fun setLockAlbum(id: String) = update { it.copy(lockAlbumId = id) }
    fun setIncludeSubfolders(album: WallpaperAlbum, enabled: Boolean) = update { current ->
        current.copy(albums = current.albums.map { if (it.id == album.id) it.copy(includeSubfolders = enabled) else it })
    }
    fun setTarget(value: WallpaperTarget) = update { it.copy(target = value) }
    fun setSource(value: WallpaperSource) = update(reschedule = true) { it.copy(source = value, lastError = "") }
    fun setNetworkMode(value: NetworkMode) = update { it.copy(networkMode = value) }
    fun setApiBaseUrl(value: String) {
        val normalized = NetworkWallpaperClient.normalizeBaseUrl(value)
        if (normalized == null) {
            message("请输入有效的 HTTP 或 HTTPS 接口网址")
            return
        }
        viewModelScope.launch {
            val updated = store.update { it.copy(apiBaseUrl = normalized, lastError = "") }
            _settings.value = updated
            _selectedNetworkAlbum.value = null
            _networkGallery.value = NetworkGalleryState()
            _networkAlbums.value = NetworkAlbumsState()
            publish()
            message("接口网址已保存")
            refreshNetworkAlbums()
        }
    }
    fun setRotationMode(value: RotationMode) = update { it.copy(rotationMode = value, recentUris = emptyList()) }
    fun setCropMode(value: CropMode) = update { it.copy(cropMode = value) }
    fun setInterval(minutes: Long) = update(reschedule = true) { it.copy(intervalMinutes = minutes.coerceAtLeast(5)) }
    fun setActiveHours(enabled: Boolean, start: Int, end: Int) = update {
        it.copy(activeHoursEnabled = enabled, activeStartHour = start.coerceIn(0, 23), activeEndHour = end.coerceIn(0, 23))
    }
    fun setDesktopProtection(enabled: Boolean) = update { it.copy(desktopProtectionEnabled = enabled) }
    fun setNotifications(enabled: Boolean) = update { it.copy(notificationsEnabled = enabled) }
    fun setTheme(mode: AppThemeMode) = update { it.copy(themeMode = mode) }
    fun setAccent(style: AccentStyle) = update { it.copy(accentStyle = style) }
    fun setGalleryColumns(value: Int) = updateGalleryLayout {
        it.copy(galleryColumns = value.coerceIn(2, 4))
    }

    fun setGalleryRows(value: Int) = updateGalleryLayout {
        it.copy(galleryRows = value.coerceIn(3, 8))
    }

    private fun updateGalleryLayout(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = store.update(transform)
            _networkGallery.value = NetworkGalleryState(pageSize = updated.galleryPageSize)
            publish()
            if (_selectedNetworkAlbum.value != null) loadNetworkGalleryPage(offset = 0)
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = _settings.value
            if (enabled && current.source == WallpaperSource.LOCAL && current.albums.isEmpty()) {
                message("请先添加壁纸相册")
                return@launch
            }
            store.update { it.copy(isEnabled = enabled, lastError = "") }
            if (enabled) {
                WallpaperScheduler.start(
                    getApplication(),
                    current.intervalMinutes,
                    current.source
                )
                if (current.source == WallpaperSource.NETWORK) {
                    WallpaperScheduler.changeNow(getApplication(), source = current.source)
                }
            } else WallpaperScheduler.stop(getApplication())
            message(if (enabled) "自动更换已开启" else "自动更换已停止")
        }
    }

    fun toggleExcluded(image: WallpaperImage) = update { current ->
        val excluded = current.excludedUris.toMutableSet()
        if (!excluded.add(image.uri)) excluded.remove(image.uri)
        current.copy(excludedUris = excluded)
    }

    fun setExcluded(image: WallpaperImage, excluded: Boolean) = update { current ->
        current.copy(excludedUris = if (excluded) current.excludedUris + image.uri else current.excludedUris - image.uri)
    }

    fun changeNow(command: RunCommand = RunCommand.NEXT) {
        if (_workStatus.value.isRunning) return
        val settings = _settings.value
        if (settings.source == WallpaperSource.LOCAL && settings.albums.isEmpty()) {
            message("请先添加壁纸相册")
            return
        }
        _workStatus.value = WorkStatus(true, "正在提交任务")
        publish()
        WallpaperScheduler.changeNow(
            getApplication(),
            command,
            settings.source
        )
    }

    fun setNetworkWallpaper(
        fileName: String,
        target: WallpaperTarget = _settings.value.target,
        cropMode: CropMode = _settings.value.cropMode
    ) {
        if (_workStatus.value.isRunning) return
        _workStatus.value = WorkStatus(true, "正在下载网络图片")
        publish()
        WallpaperScheduler.changeNow(
            context = getApplication(),
            source = WallpaperSource.NETWORK,
            networkFileName = fileName,
            target = target,
            cropMode = cropMode
        )
    }

    fun setLocalWallpaper(image: WallpaperImage, target: WallpaperTarget, cropMode: CropMode) {
        if (_workStatus.value.isRunning) return
        _workStatus.value = WorkStatus(true, "正在应用所选图片")
        publish()
        WallpaperScheduler.changeNow(
            context = getApplication(),
            source = WallpaperSource.LOCAL,
            localImage = image,
            target = target,
            cropMode = cropMode
        )
    }

    fun downloadNetworkWallpaper(fileName: String) {
        if (fileName in _downloadingFileNames.value) return
        val apiBaseUrl = _settings.value.apiBaseUrl
        _downloadingFileNames.value += fileName
        _downloadMessages.value -= fileName
        publish()
        viewModelScope.launch {
            runCatching {
                WallpaperFileSaver.saveNetworkWallpaper(
                    getApplication(),
                    fileName,
                    apiBaseUrl
                )
            }.onSuccess { location ->
                val result = "壁纸已保存到$location"
                _downloadMessages.value += fileName to result
                message(result)
            }.onFailure { exception ->
                val result = "下载失败：${exception.message ?: "无法保存壁纸"}"
                _downloadMessages.value += fileName to result
                message(result)
            }
            _downloadingFileNames.value -= fileName
            publish()
        }
    }

    fun onStoragePermissionDenied(fileName: String) {
        val result = "需要存储权限才能下载壁纸"
        _downloadMessages.value += fileName to result
        message(result)
    }

    fun clearHistory() = update { it.copy(history = emptyList()) }

    fun refreshImages() {
        viewModelScope.launch {
            _scanning.value = true
            publish()
            val result = withContext(Dispatchers.IO) {
                _settings.value.albums.associate { album ->
                    album.id to runCatching { WallpaperScanner.scan(getApplication(), album) }.getOrDefault(emptyList())
                }
            }
            _images.value = result
            _scanning.value = false
            publish()
        }
    }

    fun refreshNetworkAlbums() {
        if (_networkAlbums.value.isLoading) return
        val apiBaseUrl = _settings.value.apiBaseUrl
        _networkAlbums.value = _networkAlbums.value.copy(isLoading = true, error = "")
        publish()
        viewModelScope.launch {
            runCatching { NetworkWallpaperClient.fetchAlbums(apiBaseUrl) }
                .onSuccess { result ->
                    _networkAlbums.value = result
                    val selectedName = _selectedNetworkAlbum.value?.name
                    if (selectedName != null) {
                        _selectedNetworkAlbum.value = result.albums.firstOrNull { it.name == selectedName }
                        if (_selectedNetworkAlbum.value == null) {
                            _networkGallery.value = NetworkGalleryState()
                        }
                    }
                }
                .onFailure { exception ->
                    _networkAlbums.value = _networkAlbums.value.copy(
                        isLoading = false,
                        error = exception.message ?: "无法加载网络相册"
                    )
                }
            publish()
        }
    }

    fun openNetworkAlbum(album: NetworkAlbum) {
        _selectedNetworkAlbum.value = album
        _networkGallery.value = NetworkGalleryState(pageSize = _settings.value.galleryPageSize)
        publish()
        loadNetworkGalleryPage(offset = 0)
    }

    fun closeNetworkAlbum() {
        _selectedNetworkAlbum.value = null
        _networkGallery.value = NetworkGalleryState(pageSize = _settings.value.galleryPageSize)
        publish()
    }

    fun refreshNetworkGallery() {
        if (_selectedNetworkAlbum.value == null) refreshNetworkAlbums()
        else loadNetworkGalleryPage(offset = 0)
    }

    fun previousNetworkGalleryPage() {
        val state = _networkGallery.value
        if (!state.hasPreviousPage) return
        loadNetworkGalleryPage((state.offset - state.pageSize).coerceAtLeast(0))
    }

    fun nextNetworkGalleryPage() {
        val state = _networkGallery.value
        if (!state.hasNextPage) return
        loadNetworkGalleryPage(state.offset + state.wallpapers.size)
    }

    private fun loadNetworkGalleryPage(offset: Int) {
        val album = _selectedNetworkAlbum.value ?: return
        if (_networkGallery.value.isLoading) return
        val pageSize = _networkGallery.value.pageSize
        val apiBaseUrl = _settings.value.apiBaseUrl
        _networkGallery.value = _networkGallery.value.copy(isLoading = true, error = "")
        publish()
        viewModelScope.launch {
            runCatching { NetworkWallpaperClient.fetchGalleryPage(offset, pageSize, album.name, apiBaseUrl) }
                .onSuccess { page ->
                    if (_selectedNetworkAlbum.value?.name != album.name ||
                        _networkGallery.value.pageSize != pageSize
                    ) return@onSuccess
                    _networkGallery.value = NetworkGalleryState(
                        wallpapers = page.fileNames.map { fileName ->
                            NetworkWallpaperClient.galleryItem(fileName, apiBaseUrl)
                        },
                        totalCount = page.total,
                        offset = page.offset,
                        pageSize = page.limit,
                        hasMore = page.hasMore
                    )
                }
                .onFailure { exception ->
                    if (_selectedNetworkAlbum.value?.name != album.name ||
                        _networkGallery.value.pageSize != pageSize
                    ) return@onFailure
                    _networkGallery.value = _networkGallery.value.copy(
                        isLoading = false,
                        error = exception.message ?: "无法加载网络图库"
                    )
                }
            publish()
        }
    }

    fun exportSettings(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = store.exportJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt").use { stream ->
                        checkNotNull(stream).writer().use { it.write(json) }
                    }
                }
            }.onSuccess { message("设置已导出") }
                .onFailure { message("导出失败：${it.message}") }
        }
    }

    fun importSettings(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri).use { stream ->
                        checkNotNull(stream).reader().use { it.readText() }
                    }
                }
                store.importJson(json)
                WallpaperScheduler.stop(getApplication())
            }.onSuccess {
                message("设置已导入，请确认相册访问权限后重新开启")
                refreshImages()
            }.onFailure { message("导入失败：${it.message}") }
        }
    }

    fun consumeMessage() {
        _message.value = ""
        publish()
    }

    private fun update(reschedule: Boolean = false, transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = store.update(transform)
            if (reschedule && updated.isEnabled) {
                WallpaperScheduler.start(
                    getApplication(),
                    updated.intervalMinutes,
                    updated.source
                )
            }
        }
    }

    private fun message(value: String) {
        _message.value = value
        publish()
    }

    private fun publish() {
        _uiState.value = WallpaperUiState(
            settings = _settings.value,
            imagesByAlbum = _images.value,
            networkAlbums = _networkAlbums.value,
            selectedNetworkAlbum = _selectedNetworkAlbum.value,
            networkGallery = _networkGallery.value,
            downloadingFileNames = _downloadingFileNames.value,
            downloadMessages = _downloadMessages.value,
            isScanning = _scanning.value,
            workStatus = _workStatus.value,
            userMessage = _message.value
        )
    }
}
