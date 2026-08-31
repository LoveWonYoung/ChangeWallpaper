package com.example.changewallpaper

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

private enum class AppPage(val label: String, val glyph: String) {
    HOME("主页", "⌂"), ALBUMS("图库", "▦"), HISTORY("历史", "◷"), SETTINGS("设置", "⚙")
}

private enum class IntervalUnit(val label: String, val minutes: Long) {
    MINUTES("分钟", 1), HOURS("小时", 60), DAYS("天", 1_440)
}

@Composable
private fun WallpaperApp(uiState: WallpaperUiState, viewModel: WallpaperViewModel) {
    var page by remember { mutableStateOf(AppPage.HOME) }
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

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Box(
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
                            icon = { Text(destination.glyph, fontSize = 20.sp) },
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
            when (page) {
                AppPage.HOME -> HomePage(uiState, viewModel) { page = AppPage.ALBUMS }
                AppPage.ALBUMS -> AlbumsPage(uiState, viewModel) { folderLauncher.launch(null) }
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

@Composable
private fun HomePage(
    uiState: WallpaperUiState,
    viewModel: WallpaperViewModel,
    openAlbums: () -> Unit
) {
    val settings = uiState.settings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AppHeader(settings) }
        if (uiState.workStatus.isRunning) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(uiState.workStatus.message, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionCard("壁纸来源") {
                ChoiceRow(
                    values = WallpaperSource.entries,
                    selected = settings.source,
                    label = { if (it == WallpaperSource.NETWORK) "网络图库" else "本地相册" },
                    onSelected = viewModel::setSource
                )
                Text(
                    if (settings.source == WallpaperSource.NETWORK) {
                        "从 wallpaper.wonyoung.top 获取图片"
                    } else {
                        "使用已授权的本地图片文件夹"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SectionCard("自动更换") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (settings.isEnabled) "正在运行" else "当前已停止",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                settings.source == WallpaperSource.NETWORK ->
                                    "每 ${formatInterval(settings.intervalMinutes)} · ${networkModeLabel(settings.networkMode)}"
                                settings.albums.isEmpty() -> "添加一个相册开始使用"
                                else -> "每 ${formatInterval(settings.intervalMinutes)} · ${rotationLabel(settings.rotationMode)}"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(settings.isEnabled, viewModel::setEnabled)
                }
                Spacer(Modifier.height(16.dp))
                if (settings.source == WallpaperSource.NETWORK) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ viewModel.changeNow() }, Modifier.weight(1f)) { Text("立即更换") }
                        OutlinedButton({ viewModel.changeNow(RunCommand.RANDOM) }, Modifier.weight(1f)) { Text("随机一张") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ viewModel.changeNow(RunCommand.PREVIOUS) }, Modifier.weight(1f)) { Text("上一张") }
                        Button({ viewModel.changeNow(RunCommand.NEXT) }, Modifier.weight(1f)) { Text("下一张") }
                        OutlinedButton({ viewModel.changeNow(RunCommand.RANDOM) }, Modifier.weight(1f)) { Text("随机") }
                    }
                }
                settings.history.firstOrNull { it.success }?.let { latest ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "上次成功：${formatTime(latest.timestamp)} · ${latest.imageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item {
            SectionCard(if (settings.source == WallpaperSource.NETWORK) "网络模式" else "轮播方式") {
                if (settings.source == WallpaperSource.NETWORK) {
                    ChoiceRow(
                        values = NetworkMode.entries,
                        selected = settings.networkMode,
                        label = ::networkModeLabel,
                        onSelected = viewModel::setNetworkMode
                    )
                    Text(
                        networkModeDescription(settings.networkMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Wi-Fi 和移动数据均可下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ChoiceRow(
                        values = RotationMode.entries,
                        selected = settings.rotationMode,
                        label = ::rotationLabel,
                        onSelected = viewModel::setRotationMode
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("图片显示", style = MaterialTheme.typography.labelLarge)
                ChoiceRow(
                    values = CropMode.entries,
                    selected = settings.cropMode,
                    label = { cropModeLabel(it) },
                    onSelected = viewModel::setCropMode
                )
                Text(
                    text = cropModeDescription(settings.cropMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { IntervalCard(settings.intervalMinutes, viewModel::setInterval) }
        item {
            SectionCard("应用范围") {
                ChoiceRow(
                    values = WallpaperTarget.entries,
                    selected = settings.target,
                    label = ::targetLabel,
                    onSelected = viewModel::setTarget
                )
            }
        }
        item {
            SectionCard(if (settings.source == WallpaperSource.NETWORK) "网络图库" else "壁纸相册") {
                if (settings.source == WallpaperSource.NETWORK) {
                    val count = uiState.networkAlbums.totalCount
                    Text(if (count == 0) "可浏览服务端提供的全部壁纸" else "服务端图库 · $count 张")
                } else if (settings.albums.isEmpty()) {
                    Text("还没有相册。选择一个包含图片的文件夹即可开始。")
                } else {
                    settings.albums.forEach { album ->
                        val count = uiState.imagesByAlbum[album.id]?.size ?: 0
                        Text("${album.name} · $count 张", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(openAlbums, Modifier.fillMaxWidth()) {
                    Text(if (settings.source == WallpaperSource.NETWORK) "浏览网络图库" else "管理相册与预览")
                }
            }
        }
        if (settings.lastError.isNotBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(settings.lastError, Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AlbumsPage(uiState: WallpaperUiState, viewModel: WallpaperViewModel, addAlbum: () -> Unit) {
    val settings = uiState.settings
    var selectedNetworkWallpaper by remember { mutableStateOf<NetworkWallpaper?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = uiState.selectedNetworkAlbum != null) {
        viewModel.closeNetworkAlbum()
    }
    LaunchedEffect(Unit) { viewModel.refreshNetworkAlbums() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PageTitle("图库", "浏览网络图库，或管理本地图片文件夹")
        }
        item {
            SectionCard("网络图库") {
                val selectedAlbum = uiState.selectedNetworkAlbum
                if (selectedAlbum == null) {
                    NetworkAlbumsContent(
                        state = uiState.networkAlbums,
                        onRefresh = viewModel::refreshNetworkAlbums,
                        onOpen = { album ->
                            viewModel.openNetworkAlbum(album)
                            scope.launch { listState.animateScrollToItem(1) }
                        }
                    )
                } else {
                    NetworkAlbumContent(
                        album = selectedAlbum,
                        gallery = uiState.networkGallery,
                        onBack = viewModel::closeNetworkAlbum,
                        onRefresh = viewModel::refreshNetworkGallery,
                        onPreviousPage = {
                            viewModel.previousNetworkGalleryPage()
                            scope.launch { listState.animateScrollToItem(1) }
                        },
                        onNextPage = {
                            viewModel.nextNetworkGalleryPage()
                            scope.launch { listState.animateScrollToItem(1) }
                        },
                        onOpenImage = { selectedNetworkWallpaper = it }
                    )
                }
            }
        }
        item {
            PageTitle("本地相册", "可添加多个文件夹，并分别指定主屏幕和锁屏来源")
            Spacer(Modifier.height(14.dp))
            Button(addAlbum, Modifier.fillMaxWidth()) { Text("添加图片文件夹") }
            if (uiState.isScanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        items(settings.albums, key = { it.id }) { album ->
            val images = uiState.imagesByAlbum[album.id].orEmpty()
            AlbumCard(album, images, settings, viewModel)
        }
        if (settings.albums.isEmpty()) {
            item { EmptyState("还没有相册", "点击上方按钮选择一个图片文件夹") }
        }
    }
    selectedNetworkWallpaper?.let { wallpaper ->
        NetworkWallpaperPreview(
            wallpaper = wallpaper,
            target = settings.target,
            onDismiss = { selectedNetworkWallpaper = null },
            onApply = {
                viewModel.setNetworkWallpaper(wallpaper.fileName)
                selectedNetworkWallpaper = null
            }
        )
    }
}

@Composable
private fun NetworkAlbumsContent(
    state: NetworkAlbumsState,
    onRefresh: () -> Unit,
    onOpen: (NetworkAlbum) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${state.albums.size} 个相册 · ${state.totalCount} 张图片",
            Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onRefresh, enabled = !state.isLoading) { Text("刷新") }
    }
    if (state.isLoading) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (state.error.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(state.error, color = MaterialTheme.colorScheme.error)
    }
    state.albums.chunked(2).forEach { rowAlbums ->
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowAlbums.forEach { album ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(190.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpen(album) }
                ) {
                    AsyncImage(
                        model = album.coverUrl,
                        contentDescription = album.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.76f))
                                )
                            )
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        Text(
                            album.displayName,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("${album.count} 张", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            repeat(2 - rowAlbums.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (!state.isLoading && state.albums.isEmpty() && state.error.isBlank()) {
        Spacer(Modifier.height(8.dp))
        Text("服务端还没有相册")
    }
}

@Composable
private fun NetworkAlbumContent(
    album: NetworkAlbum,
    gallery: NetworkGalleryState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenImage: (NetworkWallpaper) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onBack) { Text("返回相册") }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(album.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                "${if (gallery.totalCount > 0) gallery.totalCount else album.count} 张图片",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onRefresh, enabled = !gallery.isLoading) { Text("刷新") }
    }
    if (gallery.isLoading) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (gallery.error.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(gallery.error, color = MaterialTheme.colorScheme.error)
    }
    gallery.wallpapers.chunked(2).forEach { rowImages ->
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowImages.forEach { image ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(190.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenImage(image) }
                ) {
                    AsyncImage(
                        model = image.thumbnailUrl,
                        contentDescription = image.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )
                    Text(
                        image.fileName.substringAfterLast('/'),
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            repeat(2 - rowImages.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (gallery.totalCount > 0) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPreviousPage,
                modifier = Modifier.weight(1f),
                enabled = !gallery.isLoading && gallery.hasPreviousPage
            ) { Text("上一页") }
            Text("${gallery.pageIndex + 1} / ${gallery.pageCount}", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = onNextPage,
                modifier = Modifier.weight(1f),
                enabled = !gallery.isLoading && gallery.hasNextPage
            ) { Text("下一页") }
        }
    }
    if (!gallery.isLoading && gallery.wallpapers.isEmpty() && gallery.error.isBlank()) {
        Spacer(Modifier.height(8.dp))
        Text("这个相册还没有图片")
    }
}

@Composable
private fun NetworkWallpaperPreview(
    wallpaper: NetworkWallpaper,
    target: WallpaperTarget,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(wallpaper.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = wallpaper.imageUrl,
                    contentDescription = wallpaper.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Spacer(Modifier.height(12.dp))
                Text("将应用到：${targetLabel(target)}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onDismiss, Modifier.weight(1f)) { Text("关闭") }
                    Button(onApply, Modifier.weight(1f)) { Text("设为壁纸") }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: WallpaperAlbum,
    images: List<WallpaperImage>,
    settings: AppSettings,
    viewModel: WallpaperViewModel
) {
    SectionCard(album.name) {
        Text("${images.size} 张图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(settings.homeAlbumId == album.id, { viewModel.setHomeAlbum(album.id) }, label = { Text("主屏幕使用") })
            FilterChip(settings.lockAlbumId == album.id, { viewModel.setLockAlbum(album.id) }, label = { Text("锁屏使用") })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("扫描子文件夹", Modifier.weight(1f))
            Switch(album.includeSubfolders, { viewModel.setIncludeSubfolders(album, it) })
        }
        if (images.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            images.take(30).chunked(2).forEach { rowImages ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowImages.forEach { image ->
                        val excluded = image.uri in settings.excludedUris
                        Box(
                            Modifier
                                .weight(1f)
                                .height(170.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { viewModel.toggleExcluded(image) }
                                .alpha(if (excluded) 0.35f else 1f)
                        ) {
                            AsyncImage(
                                model = image.uri.toUri(),
                                contentDescription = image.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                        )
                                    )
                            )
                            Text(
                                if (excluded) "已排除" else image.name,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    repeat(2 - rowImages.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (images.size > 30) Text("仅预览前 30 张，轮播会使用全部图片")
            Text("点击图片可排除或恢复", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton({ viewModel.removeAlbum(album) }, Modifier.fillMaxWidth()) { Text("移除相册") }
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
                    AsyncImage(
                        model = entry.imageUri.toUri(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                    )
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { PageTitle("设置", "时间段、通知、外观、快捷方式和备份") }
        item { ActiveHoursCard(settings, viewModel) }
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
                ChoiceRow(AccentStyle.entries, settings.accentStyle, {
                    when (it) { AccentStyle.FOREST -> "森林"; AccentStyle.OCEAN -> "海洋"; AccentStyle.SUNSET -> "落日"; AccentStyle.DYNAMIC -> "系统" }
                }, viewModel::setAccent)
            }
        }
        item {
            SectionCard("一键快捷方式") {
                Text("长按应用图标可直接选择“换壁纸”或“随机换”。也可以在桌面添加一个独立的一键更换图标。")
                Spacer(Modifier.height(12.dp))
                Button(onPinShortcut, Modifier.fillMaxWidth()) { Text("添加到桌面") }
            }
        }
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
        item {
            Text("自动壁纸 2.0 · Android 7.0+", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun IntervalCard(currentMinutes: Long, onSave: (Long) -> Unit) {
    val initial = displayInterval(currentMinutes)
    var value by remember(currentMinutes) { mutableStateOf(initial.first.toString()) }
    var unit by remember(currentMinutes) { mutableStateOf(initial.second) }
    SectionCard("切换间隔") {
        OutlinedTextField(
            value,
            { if (it.all(Char::isDigit) && it.length <= 5) value = it },
            Modifier.fillMaxWidth(),
            label = { Text("间隔数值") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        ChoiceRow(IntervalUnit.entries, unit, { it.label }) { unit = it }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { value.toLongOrNull()?.times(unit.minutes)?.takeIf { it >= 5 }?.let(onSave) },
            modifier = Modifier.fillMaxWidth(),
            enabled = (value.toLongOrNull()?.times(unit.minutes) ?: 0) >= 5
        ) { Text("保存间隔") }
        Text("最短 5 分钟；保存后正在运行的任务会自动更新。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActiveHoursCard(settings: AppSettings, viewModel: WallpaperViewModel) {
    var start by remember(settings.activeStartHour) { mutableStateOf(settings.activeStartHour.toString()) }
    var end by remember(settings.activeEndHour) { mutableStateOf(settings.activeEndHour.toString()) }
    SectionCard("每日生效时间") {
        SettingSwitch("限制自动更换时间段", settings.activeHoursEnabled) {
            viewModel.setActiveHours(it, start.toIntOrNull() ?: 8, end.toIntOrNull() ?: 23)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(start, { if (it.all(Char::isDigit) && it.length <= 2) start = it }, Modifier.weight(1f), label = { Text("开始小时") })
            OutlinedTextField(end, { if (it.all(Char::isDigit) && it.length <= 2) end = it }, Modifier.weight(1f), label = { Text("结束小时") })
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            { viewModel.setActiveHours(settings.activeHoursEnabled, start.toIntOrNull() ?: 8, end.toIntOrNull() ?: 23) },
            Modifier.fillMaxWidth()
        ) { Text("保存时间段") }
    }
}

@Composable
private fun <T> ChoiceRow(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected == value, { onSelected(value) }, label = { Text(label(value)) }) }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 6.dp, height = 18.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
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
            .height(310.dp)
            .clip(RoundedCornerShape(32.dp))
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
                if (latest == null) "你的自然画廊" else "最近的风景",
                color = Color.White.copy(alpha = 0.76f),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(5.dp))
            Text(
                latest?.imageName ?: "让屏幕随风景流动",
                style = MaterialTheme.typography.headlineLarge,
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

private fun targetLabel(target: WallpaperTarget) = when (target) {
    WallpaperTarget.HOME -> "主屏幕"
    WallpaperTarget.LOCK -> "锁定屏幕"
    WallpaperTarget.BOTH -> "主屏幕和锁屏"
}

private fun cropModeLabel(mode: CropMode) = when (mode) {
    CropMode.SMART -> "智能"
    CropMode.FILL -> "居中裁剪"
    CropMode.FIT -> "完整显示"
    CropMode.BLUR -> "柔焦背景"
}

private fun cropModeDescription(mode: CropMode) = when (mode) {
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
