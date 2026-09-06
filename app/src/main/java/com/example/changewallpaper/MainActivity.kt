package com.example.changewallpaper

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.changewallpaper.ui.theme.ChangeWallpaperTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: WallpaperViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ChangeWallpaperTheme(
                themeMode = uiState.settings.themeMode,
                accentStyle = uiState.settings.accentStyle
            ) {
                WallpaperApp(uiState, viewModel)
            }
        }
    }
}

private enum class AppPage(val label: String, val icon: Int) {
    HOME("主页", R.drawable.ic_home),
    GALLERY("图库", R.drawable.ic_gallery),
    HISTORY("历史", R.drawable.ic_history),
    SETTINGS("设置", R.drawable.ic_settings)
}

private enum class IntervalUnit(val label: String, val minutes: Long) {
    MINUTES("分钟", 1), HOURS("小时", 60), DAYS("天", 1_440)
}

@Composable
private fun WallpaperApp(uiState: WallpaperUiState, viewModel: WallpaperViewModel) {
    var page by rememberSaveable { mutableStateOf(AppPage.HOME) }
    var gallerySource by rememberSaveable { mutableStateOf(uiState.settings.source) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(uiState.userMessage) {
        if (uiState.userMessage.isNotBlank()) {
            snackbar.showSnackbar(uiState.userMessage)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(uiState.workStatus.message) {
        if (!uiState.workStatus.isRunning && uiState.workStatus.message.isNotBlank()) {
            snackbar.showSnackbar(uiState.workStatus.message)
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(viewModel::addAlbum)
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.setNotifications(granted) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { it?.let(viewModel::exportSettings) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { it?.let(viewModel::importSettings) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        Row(Modifier.fillMaxSize().then(
            if (wide) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier
        )) {
            if (wide) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                    containerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
                ) {
                    AppPage.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = page == destination,
                            onClick = { page = destination },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide) Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        NavigationBar(
                            modifier = Modifier.clip(RoundedCornerShape(28.dp)),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            AppPage.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = page == destination,
                                    onClick = { page = destination },
                                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                                    label = { Text(destination.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                        .padding(padding)
                ) {
                    val pages = rememberSaveableStateHolder()
                    pages.SaveableStateProvider(page) {
                        when (page) {
                            AppPage.HOME -> HomePage(uiState, viewModel) {
                                gallerySource = uiState.settings.source
                                page = AppPage.GALLERY
                            }
                            AppPage.GALLERY -> Column(Modifier.fillMaxSize()) {
                                if (wide) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("图库", style = MaterialTheme.typography.titleLarge)
                                        ChoiceRow(WallpaperSource.entries, gallerySource,
                                            { if (it == WallpaperSource.NETWORK) "网络图库" else "本地相册" }
                                        ) { gallerySource = it }
                                    }
                                } else {
                                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                        PageTitle("图库", "挑选一张，让屏幕焕然一新")
                                        Spacer(Modifier.height(10.dp))
                                        ChoiceRow(WallpaperSource.entries, gallerySource,
                                            { if (it == WallpaperSource.NETWORK) "网络图库" else "本地相册" }
                                        ) { gallerySource = it }
                                    }
                                }
                                Box(Modifier.weight(1f)) {
                                    val galleries = rememberSaveableStateHolder()
                                    galleries.SaveableStateProvider(gallerySource) {
                                        if (gallerySource == WallpaperSource.NETWORK) NetworkAlbumsPage(uiState, viewModel)
                                        else AlbumsPage(uiState, viewModel) { folderLauncher.launch(null) }
                                    }
                                }
                            }
                            AppPage.HISTORY -> HistoryPage(uiState.settings, viewModel)
                            AppPage.SETTINGS -> SettingsPage(
                                settings = uiState.settings,
                                viewModel = viewModel,
                                onNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else viewModel.setNotifications(true)
                                },
                                onExport = { exportLauncher.launch("wallpaper-settings.json") },
                                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                                onBatterySettings = {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                                        .onFailure { scope.launch { snackbar.showSnackbar("无法打开电池优化设置") } }
                                },
                                onPinShortcut = {
                                    val requested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        QuickActionActivity.requestPinnedShortcut(context)
                                    } else false
                                    scope.launch {
                                        snackbar.showSnackbar(if (requested) "请在系统弹窗中确认添加" else "当前桌面不支持固定快捷方式")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomePage(
    uiState: WallpaperUiState,
    viewModel: WallpaperViewModel,
    openAlbums: () -> Unit
) {
    val settings = uiState.settings
    val busy = uiState.workStatus.isRunning
    var expanded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= 600.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (twoColumns) 2 else 1),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AppHeader(settings) }
            item {
                SectionCard("换一张壁纸") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("自动更换", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (settings.isEnabled) "已开启 · 每 ${formatInterval(settings.intervalMinutes)}"
                                else "已暂停 · 随时可以手动更换",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(settings.isEnabled, viewModel::setEnabled)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button({ viewModel.changeNow() }, Modifier.fillMaxWidth(), enabled = !busy) {
                        Text(if (busy) "正在更换…" else "换一张")
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (settings.source == WallpaperSource.LOCAL) {
                            OutlinedButton({ viewModel.changeNow(RunCommand.PREVIOUS) }, Modifier, enabled = !busy) { Text("上一张") }
                        }
                        OutlinedButton({ viewModel.changeNow(RunCommand.RANDOM) }, Modifier, enabled = !busy) { Text("随机一张") }
                        TextButton(openAlbums, Modifier) { Text("去图库挑选") }
                    }
                    if (busy) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(uiState.workStatus.message, style = MaterialTheme.typography.bodySmall)
                    }
                    if (settings.lastError.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(settings.lastError, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton({ viewModel.changeNow() }, enabled = !busy) { Text("重试") }
                                    TextButton(openAlbums) { Text(if (settings.source == WallpaperSource.LOCAL) "检查相册" else "查看图库") }
                                }
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { DesktopProtectionCard() }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionCard("轮播设置") {
                    Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${if (settings.source == WallpaperSource.NETWORK) "网络图库" else "本地相册"} · ${formatInterval(settings.intervalMinutes)}")
                            Text("${targetLabel(settings.target)} · ${cropModeLabel(settings.cropMode)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton({ expanded = !expanded }) { Text(if (expanded) "收起" else "调整") }
                    }
                    if (expanded) {
                        Spacer(Modifier.height(14.dp))
                        Text("壁纸来源", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(WallpaperSource.entries, settings.source,
                            { if (it == WallpaperSource.NETWORK) "网络图库" else "本地相册" }, viewModel::setSource)
                        Spacer(Modifier.height(12.dp))
                        Text("轮播方式", style = MaterialTheme.typography.labelLarge)
                        if (settings.source == WallpaperSource.NETWORK) {
                            ChoiceRow(NetworkMode.entries, settings.networkMode, ::networkModeLabel, viewModel::setNetworkMode)
                            Text(networkModeDescription(settings.networkMode), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            ChoiceRow(RotationMode.entries, settings.rotationMode, ::rotationLabel, viewModel::setRotationMode)
                        }
                        Spacer(Modifier.height(16.dp))
                        IntervalOptions(settings.intervalMinutes, viewModel::setInterval)
                        Spacer(Modifier.height(16.dp))
                        Text("应用范围", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(WallpaperTarget.entries, settings.target, ::targetLabel, viewModel::setTarget)
                        Spacer(Modifier.height(12.dp))
                        Text("图片显示", style = MaterialTheme.typography.labelLarge)
                        ChoiceRow(CropMode.entries, settings.cropMode, ::cropModeLabel, viewModel::setCropMode)
                        Text(cropModeDescription(settings.cropMode), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (settings.source == WallpaperSource.LOCAL && settings.albums.isEmpty()) {
                item {
                    SectionCard("从喜欢的照片开始") {
                        Text("选择一个图片文件夹，就能用自己的照片自动更换壁纸。", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(openAlbums, Modifier.fillMaxWidth()) { Text("添加本地相册") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsPage(uiState: WallpaperUiState, viewModel: WallpaperViewModel, addAlbum: () -> Unit) {
    var selectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = uiState.imagesByAlbum.values.asSequence().flatten().firstOrNull { it.uri == selectedUri }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = adaptiveGalleryColumns(maxWidth, uiState.settings.galleryColumns)
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${uiState.settings.albums.size} 个本地相册", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(viewModel::refreshImages, enabled = !uiState.isScanning) { Text("重新扫描") }
                    Button(addAlbum) { Text("添加文件夹") }
                }
                if (uiState.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            items(uiState.settings.albums, key = { it.id }) { album ->
                AlbumCard(album, uiState.imagesByAlbum[album.id].orEmpty(), uiState.settings, viewModel, columns) { selectedUri = it.uri }
            }
            if (uiState.settings.albums.isEmpty()) item { EmptyState("收藏你的日常风景", "添加图片文件夹后，点击照片即可预览和设为壁纸。") }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    selected?.let { image ->
        WallpaperPreview(
            name = image.name, model = image.uri, settings = uiState.settings, workStatus = uiState.workStatus,
            onDismiss = { selectedUri = null },
            onApply = { target, crop -> viewModel.setLocalWallpaper(image, target, crop) },
            excluded = image.uri in uiState.settings.excludedUris,
            onToggleExcluded = {
                val wasExcluded = image.uri in uiState.settings.excludedUris
                viewModel.setExcluded(image, !wasExcluded)
                selectedUri = null
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    if (snackbar.showSnackbar(if (wasExcluded) "已恢复参与轮播" else "已从轮播中排除", "撤销") == SnackbarResult.ActionPerformed) {
                        viewModel.setExcluded(image, wasExcluded)
                    }
                }
            }
        )
    }
}

@Composable
private fun NetworkAlbumsPage(uiState: WallpaperUiState, viewModel: WallpaperViewModel) {
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = uiState.networkGallery.wallpapers.firstOrNull { it.fileName == selectedFileName }
    val listState = rememberLazyListState()
    val album = uiState.selectedNetworkAlbum
    val gallery = uiState.networkGallery
    val albums = uiState.networkAlbums
    BackHandler(enabled = album != null) { viewModel.closeNetworkAlbum() }
    LaunchedEffect(Unit) { if (albums.albums.isEmpty()) viewModel.refreshNetworkAlbums() }
    val positionKey = "${album?.name}:${gallery.offset}"
    var previousPositionKey by rememberSaveable { mutableStateOf(positionKey) }
    LaunchedEffect(positionKey) {
        if (previousPositionKey != positionKey) {
            listState.scrollToItem(0)
            previousPositionKey = positionKey
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = adaptiveGalleryColumns(maxWidth, uiState.settings.galleryColumns)
        LazyColumn(
            modifier = Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (album != null) TextButton(viewModel::closeNetworkAlbum) { Text("返回") }
                    Column(Modifier.weight(1f)) {
                        Text(album?.displayName ?: "全部相册", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (album == null) "${albums.totalCount} 张壁纸" else "${gallery.totalCount} 张壁纸", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ if (album == null) viewModel.refreshNetworkAlbums() else viewModel.refreshNetworkGallery() },
                        enabled = !(if (album == null) albums.isLoading else gallery.isLoading)) { Text("刷新") }
                }
            }
            val loading = if (album == null) albums.isLoading else gallery.isLoading
            val error = if (album == null) albums.error else gallery.error
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error.isNotBlank()) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton({ if (album == null) viewModel.refreshNetworkAlbums() else viewModel.refreshNetworkGallery() }, enabled = !loading) { Text("重新加载") }
                    }
                }
            }
            if (album == null) {
                items(albums.albums.chunked(columns)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            Column(Modifier.weight(1f).clickable { viewModel.openNetworkAlbum(item) }) {
                                GalleryImage(item.coverUrl, item.displayName,
                                    Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(14.dp)))
                                Text(item.displayName, Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge)
                                Text("${item.count} 张", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (!loading && error.isBlank() && albums.albums.isEmpty()) item { EmptyState("图库还没有图片", "刷新试试，或切换到本地相册添加照片。") }
            } else {
                items(gallery.wallpapers.chunked(columns)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            GalleryImage(item.thumbnailUrl, item.fileName,
                                Modifier.weight(1f).aspectRatio(0.62f).clip(RoundedCornerShape(14.dp)).clickable { selectedFileName = item.fileName })
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (gallery.totalCount > 0) item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(viewModel::previousNetworkGalleryPage, Modifier.weight(1f), enabled = !loading && gallery.hasPreviousPage) { Text("上一页") }
                        Text("${gallery.pageIndex + 1} / ${gallery.pageCount}", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(viewModel::nextNetworkGalleryPage, Modifier.weight(1f), enabled = !loading && gallery.hasNextPage) { Text("下一页") }
                    }
                }
                if (!loading && error.isBlank() && gallery.wallpapers.isEmpty()) item { EmptyState("相册还是空的", "返回挑选其他相册。") }
            }
        }
    }
    selected?.let { wallpaper ->
        WallpaperPreview(wallpaper.fileName.substringAfterLast('/'), wallpaper.imageUrl, uiState.settings, uiState.workStatus,
            onDismiss = { selectedFileName = null },
            onApply = { target, crop -> viewModel.setNetworkWallpaper(wallpaper.fileName, target, crop) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumCard(
    album: WallpaperAlbum,
    images: List<WallpaperImage>,
    settings: AppSettings,
    viewModel: WallpaperViewModel,
    columns: Int,
    onOpen: (WallpaperImage) -> Unit
) {
    var manage by rememberSaveable(album.id) { mutableStateOf(false) }
    var page by rememberSaveable(album.id, settings.galleryPageSize) { mutableStateOf(0) }
    val pageCount = ((images.size + settings.galleryPageSize - 1) / settings.galleryPageSize).coerceAtLeast(1)
    val safePage = page.coerceIn(0, pageCount - 1)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${images.size} 张 · ${images.count { it.uri !in settings.excludedUris }} 张参与轮播", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton({ manage = !manage }) { Text(if (manage) "完成" else "管理") }
        }
        if (manage) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.albumFor(WallpaperTarget.HOME)?.id == album.id,
                    { viewModel.setHomeAlbum(album.id) }, label = { Text("主屏幕使用") })
                FilterChip(settings.albumFor(WallpaperTarget.LOCK)?.id == album.id,
                    { viewModel.setLockAlbum(album.id) }, label = { Text("锁屏使用") })
            }
            Text("主屏幕：${settings.albumFor(WallpaperTarget.HOME)?.name.orEmpty()} · 锁屏：${settings.albumFor(WallpaperTarget.LOCK)?.name.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            SettingSwitch("扫描子文件夹", album.includeSubfolders) { viewModel.setIncludeSubfolders(album, it) }
            TextButton({ viewModel.removeAlbum(album) }) { Text("移除相册", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(10.dp))
        images.drop(safePage * settings.galleryPageSize).take(settings.galleryPageSize).chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { image ->
                    val excluded = image.uri in settings.excludedUris
                    Box(Modifier.weight(1f).aspectRatio(0.62f).clip(RoundedCornerShape(14.dp)).clickable { onOpen(image) }) {
                        GalleryImage(image.uri, image.name, Modifier.fillMaxSize().alpha(if (excluded) 0.5f else 1f))
                        if (excluded) Surface(Modifier.align(Alignment.BottomCenter).padding(6.dp), color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)) { Text("已排除", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (images.isEmpty()) Text("暂无图片，请检查文件夹或重新扫描。", style = MaterialTheme.typography.bodySmall)
        if (pageCount > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton({ page = safePage - 1 }, enabled = safePage > 0) { Text("上一页") }
            Text("${safePage + 1} / $pageCount", style = MaterialTheme.typography.labelLarge)
            TextButton({ page = safePage + 1 }, enabled = safePage < pageCount - 1) { Text("下一页") }
        }
    }
}

@Composable
private fun HistoryPage(settings: AppSettings, viewModel: WallpaperViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PageTitle("更换历史", "最多保留最近 50 条成功和失败记录")
            if (settings.history.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(viewModel::clearHistory, Modifier.fillMaxWidth()) { Text("清空历史") }
            }
        }
        items(settings.history) { entry ->
            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (entry.success) MaterialTheme.colorScheme.outlineVariant
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (entry.success) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    GalleryImage(entry.imageUri, entry.imageName, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.imageName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${targetLabel(entry.target)} · ${formatTime(entry.timestamp)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!entry.success) Text(entry.message, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (entry.success) "成功" else "跳过")
                }
            }
        }
        if (settings.history.isEmpty()) item { EmptyState("暂无记录", "更换壁纸后会在这里显示") }
    }
}

@Composable
private fun SettingsPage(
    settings: AppSettings,
    viewModel: WallpaperViewModel,
    onNotificationPermission: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBatterySettings: () -> Unit,
    onPinShortcut: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (maxWidth >= 640.dp) 2 else 1),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { PageTitle("设置", "让壁纸更贴合你的使用习惯") }
            item { DesktopProtectionCard() }
            item { ActiveHoursCard(settings, viewModel) }
            item {
                SectionCard("图库布局") {
                    Text("每行列数", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(listOf(2, 3, 4), settings.galleryColumns, { "$it 列" }, viewModel::setGalleryColumns)
                    Spacer(Modifier.height(12.dp))
                    Text("每页行数", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow((3..8).toList(), settings.galleryRows, { "$it 行" }, viewModel::setGalleryRows)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "每页 ${settings.galleryPageSize} 张图片；宽屏会自动增加显示列数。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SectionCard("通知与后台") {
                    SettingSwitch("更换成功后通知", settings.notificationsEnabled) { enabled ->
                        if (enabled) onNotificationPermission() else viewModel.setNotifications(false)
                    }
                    Text("部分品牌手机会限制后台任务，可在系统中允许本应用后台运行。", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onBatterySettings, Modifier.fillMaxWidth()) { Text("打开电池优化设置") }
                }
            }
            item {
                SectionCard("外观") {
                    Text("显示模式", style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(AppThemeMode.entries, settings.themeMode, {
                        when (it) { AppThemeMode.SYSTEM -> "跟随系统"; AppThemeMode.LIGHT -> "浅色"; AppThemeMode.DARK -> "深色" }
                    }, viewModel::setTheme)
                    Spacer(Modifier.height(12.dp))
                    Text("主题颜色", style = MaterialTheme.typography.labelLarge)
                    AccentPicker(settings.accentStyle, viewModel::setAccent)
                }
            }
            item {
                SectionCard("一键快捷方式") {
                    Text("长按应用图标可直接选择“换壁纸”或“随机换”。也可以在桌面添加一个独立的一键更换图标。")
                    Spacer(Modifier.height(12.dp))
                    Button(onPinShortcut, Modifier.fillMaxWidth()) { Text("添加到桌面") }
                }
            }
            item { ApiBaseUrlCard(settings.apiBaseUrl, viewModel::setApiBaseUrl) }
            item {
                SectionCard("备份与恢复") {
                    Text("导出轮播、主题、排除列表和相册配置。导入后可能需要重新授权文件夹。")
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onExport, Modifier.weight(1f)) { Text("导出设置") }
                        OutlinedButton(onImport, Modifier.weight(1f)) { Text("导入设置") }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("自动壁纸 2.0 · Android 7.0+", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ApiBaseUrlCard(currentUrl: String, onSave: (String) -> Unit) {
    var value by rememberSaveable(currentUrl) { mutableStateOf(currentUrl) }
    val isValid = NetworkWallpaperClient.normalizeBaseUrl(value) != null
    SectionCard("接口网址") {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API 地址") },
            placeholder = { Text(NetworkWallpaperClient.BASE_URL) },
            supportingText = {
                Text(if (value.isBlank() || isValid) "请输入 HTTPS 地址" else "网址格式无效")
            },
            isError = value.isNotBlank() && !isValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSave(value) },
            modifier = Modifier.fillMaxWidth(),
            enabled = isValid && NetworkWallpaperClient.normalizeBaseUrl(value) != currentUrl
        ) { Text("保存接口网址") }
    }
}

@Composable
private fun IntervalOptions(currentMinutes: Long, onSave: (Long) -> Unit) {
    val presets = listOf(5L, 30L, 60L)
    var custom by rememberSaveable(currentMinutes) { mutableStateOf(currentMinutes !in presets) }
    val initial = displayInterval(currentMinutes)
    var value by rememberSaveable(currentMinutes) { mutableStateOf(initial.first.toString()) }
    var unit by rememberSaveable(currentMinutes) { mutableStateOf(initial.second) }
    Text("切换间隔", style = MaterialTheme.typography.labelLarge)
    ChoiceRow(presets + 0L, if (custom) 0L else currentMinutes,
        { if (it == 0L) "自定义" else formatInterval(it) }) {
        custom = it == 0L
        if (it != 0L) onSave(it)
    }
    if (custom) {
        val minutes = (value.toLongOrNull() ?: 0) * unit.minutes
        OutlinedTextField(value, { if (it.all(Char::isDigit) && it.length <= 5) value = it }, Modifier.fillMaxWidth(),
            label = { Text("间隔数值") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            isError = value.isNotBlank() && minutes < 5,
            supportingText = { Text("最短 5 分钟") })
        ChoiceRow(IntervalUnit.entries, unit, { it.label }) { unit = it }
        TextButton({ onSave(minutes) }, enabled = minutes >= 5 && minutes != currentMinutes) { Text("保存自定义间隔") }
    }
    Text("实际更换时间可能受系统省电影响。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ActiveHoursCard(settings: AppSettings, viewModel: WallpaperViewModel) {
    var editingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    editingStart?.let { isStart ->
        HourPickerDialog(
            title = if (isStart) "开始时间" else "结束时间",
            initialHour = if (isStart) settings.activeStartHour else settings.activeEndHour,
            onDismiss = { editingStart = null },
            onConfirm = { hour ->
                viewModel.setActiveHours(settings.activeHoursEnabled, if (isStart) hour else settings.activeStartHour,
                    if (isStart) settings.activeEndHour else hour)
                editingStart = null
            }
        )
    }
    SectionCard("每日生效时间") {
        SettingSwitch("仅在指定时间自动更换", settings.activeHoursEnabled) {
            viewModel.setActiveHours(it, settings.activeStartHour, settings.activeEndHour)
        }
        if (settings.activeHoursEnabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true, false).forEach { isStart ->
                    val hour = if (isStart) settings.activeStartHour else settings.activeEndHour
                    OutlinedButton({ editingStart = isStart }, Modifier.weight(1f)) {
                        Text("${if (isStart) "开始" else "结束"}  %02d:00".format(hour))
                    }
                }
            }
            Text(when {
                settings.activeStartHour == settings.activeEndHour -> "开始和结束相同，全天生效。"
                settings.activeStartHour > settings.activeEndHour -> "时间段跨越午夜，次日结束。"
                else -> "在此时间段内自动更换，手动更换不受限制。"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text("全天可自动更换", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(values: List<T>, selected: T?, label: (T) -> String, onSelected: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected == value, { onSelected(value) }, label = { Text(label(value)) }) }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChecked).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun AppHeader(settings: AppSettings) {
    val latest = settings.history.firstOrNull { it.success }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
            )
    ) {
        latest?.let { entry ->
            AsyncImage(
                model = entry.imageUri.toUri(),
                contentDescription = entry.imageName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = if (latest == null) 0.04f else 0.30f),
                            Color.Transparent,
                            Color.Black.copy(alpha = if (latest == null) 0.20f else 0.78f)
                        )
                    )
                )
        )
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.Black.copy(alpha = 0.24f))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (settings.isEnabled) Color(0xFFB8F5C8) else Color.White.copy(alpha = 0.62f))
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (settings.isEnabled) "自动轮播中" else "等待开启",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (latest == null) "你的自然画廊" else "你的壁纸",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(5.dp))
            Text(
                if (latest == null) "让喜欢的风景常在" else "上次使用的壁纸",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(7.dp))
            Text(
                latest?.let { "${formatTime(it.timestamp)} · ${targetLabel(it.target)}" }
                    ?: if (settings.isEnabled) "每一次点亮，都遇见一幅新风景。" else "开启轮播，让喜欢的图片自然出现。",
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("◇", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 24.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun rotationLabel(mode: RotationMode) = when (mode) {
    RotationMode.SEQUENTIAL -> "顺序"
    RotationMode.RANDOM -> "随机"
    RotationMode.SHUFFLE -> "随机不重复"
}

private fun networkModeLabel(mode: NetworkMode) = when (mode) {
    NetworkMode.CURRENT -> "同步（推荐）"
    NetworkMode.NEXT -> "独立轮换"
    NetworkMode.RANDOM -> "随机"
}

private fun networkModeDescription(mode: NetworkMode) = when (mode) {
    NetworkMode.CURRENT -> "所有设备在同一 15 分钟窗口使用同一张图片"
    NetworkMode.NEXT -> "使用服务端共享队列，适合只有一台设备的场景"
    NetworkMode.RANDOM -> "每次从服务端随机获取一张图片"
}

internal fun targetLabel(target: WallpaperTarget) = when (target) {
    WallpaperTarget.HOME -> "主屏幕"
    WallpaperTarget.LOCK -> "锁定屏幕"
    WallpaperTarget.BOTH -> "主屏幕和锁屏"
}

internal fun cropModeLabel(mode: CropMode) = when (mode) {
    CropMode.SMART -> "智能"
    CropMode.FILL -> "居中裁剪"
    CropMode.FIT -> "完整显示"
    CropMode.BLUR -> "柔焦背景"
}

internal fun cropModeDescription(mode: CropMode) = when (mode) {
    CropMode.SMART -> "根据图片与屏幕方向自动选择裁剪或柔焦补边"
    CropMode.FILL -> "填满整个屏幕，超出部分从中心裁剪，不产生黑边"
    CropMode.FIT -> "保留完整图片，空余区域使用图片边缘柔焦填充"
    CropMode.BLUR -> "完整显示主体，并使用更明显的柔焦背景填满屏幕"
}

private fun displayInterval(minutes: Long): Pair<Long, IntervalUnit> = when {
    minutes >= 1_440 && minutes % 1_440 == 0L -> minutes / 1_440 to IntervalUnit.DAYS
    minutes >= 60 && minutes % 60 == 0L -> minutes / 60 to IntervalUnit.HOURS
    else -> minutes to IntervalUnit.MINUTES
}

private fun formatInterval(minutes: Long): String = when {
    minutes % 1_440 == 0L -> "${minutes / 1_440} 天"
    minutes % 60 == 0L -> "${minutes / 60} 小时"
    else -> "$minutes 分钟"
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
