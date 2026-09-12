package com.example.changewallpaper

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GalleryImage(model: Any?, description: String?, modifier: Modifier = Modifier) {
    var retry by remember(model) { mutableIntStateOf(0) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        key(model, retry) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(true).build(),
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton({ retry++ }) { Text("重试", style = MaterialTheme.typography.labelSmall) } } }
            )
        }
    }
}

private data class PreviewBitmap(val bitmap: Bitmap? = null, val error: String? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WallpaperPreview(
    name: String,
    model: String,
    settings: AppSettings,
    workStatus: WorkStatus,
    onDismiss: () -> Unit,
    onApply: (WallpaperTarget, CropMode) -> Unit,
    downloadInProgress: Boolean = false,
    downloadMessage: String = "",
    onDownload: (() -> Unit)? = null,
    excluded: Boolean? = null,
    onToggleExcluded: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var target by rememberSaveable(model) { mutableStateOf(settings.target) }
    var crop by rememberSaveable(model) { mutableStateOf(settings.cropMode) }
    var retry by remember(model) { mutableIntStateOf(0) }
    val configuration = LocalConfiguration.current
    val targetSize = remember(context, configuration.screenWidthDp, configuration.screenHeightDp) {
        WallpaperRenderer.resolveTargetSize(context)
    }
    // Use the same rendering dimensions and algorithm as the wallpaper worker.
    val source by produceState(PreviewBitmap(), model, retry, targetSize) {
        value = PreviewBitmap()
        value = withContext(Dispatchers.IO) {
            try {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(model).allowHardware(false)
                        .size(targetSize.width, targetSize.height).build()
                )
                val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                if (bitmap == null) PreviewBitmap(error = "图片加载失败，请重试") else PreviewBitmap(bitmap)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                PreviewBitmap(error = "图片加载失败，请检查网络或相册权限")
            }
        }
    }
    val rendered by produceState(PreviewBitmap(), source, crop, targetSize) {
        value = PreviewBitmap()
        val bitmap = source.bitmap
        value = if (bitmap == null) PreviewBitmap(error = source.error) else withContext(Dispatchers.Default) {
            try {
                PreviewBitmap(WallpaperRenderer.render(bitmap, targetSize.width, targetSize.height, crop))
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                PreviewBitmap(error = "无法生成预览，请重试")
            }
        }
    }
    var applicationRequested by rememberSaveable(model) { mutableStateOf(false) }
    val imageContent: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            val bitmap = rendered.bitmap
            when {
                bitmap != null -> Image(bitmap.asImageBitmap(), "${cropModeLabel(crop)}效果预览",
                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                rendered.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rendered.error.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    TextButton({ retry++ }) { Text("重新加载") }
                }
                else -> CircularProgressIndicator()
            }
        }
    }
    val options: @Composable () -> Unit = {
        Text("按屏幕比例预览 · ${cropModeLabel(crop)}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text(
            if (settings.desktopProtectionEnabled) "桌面保护已开启；请返回桌面或使用桌面快捷方式更换。"
            else "桌面保护未开启；可直接从应用内更换。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("应用到", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperTarget.entries.forEach { item ->
                FilterChip(target == item, { target = item }, enabled = !workStatus.isRunning,
                    label = { Text(if (item == WallpaperTarget.BOTH) "两者" else targetLabel(item)) })
            }
        }
        Text("显示方式", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CropMode.entries.forEach { item ->
                FilterChip(crop == item, { crop = item }, enabled = !workStatus.isRunning,
                    label = { Text(cropModeLabel(item)) })
            }
        }
        Text(cropModeDescription(crop), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (excluded != null && onToggleExcluded != null) {
            TextButton(onToggleExcluded) { Text(if (excluded) "恢复参与自动轮播" else "从自动轮播中排除") }
        }
    }
    val applyAction: @Composable () -> Unit = {
        if (workStatus.isRunning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            Text(workStatus.message.ifBlank { "正在提交任务" }, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else if (applicationRequested && workStatus.message.isNotBlank()) {
            Text(workStatus.message, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Button({ applicationRequested = true; onApply(target, crop) },
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            enabled = rendered.bitmap != null && !workStatus.isRunning) {
            Text(if (workStatus.isRunning) "正在更换…" else "设为${if (target == WallpaperTarget.BOTH) "主屏幕和锁屏" else targetLabel(target)}壁纸")
        }
        if (onDownload != null) {
            OutlinedButton(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                enabled = !downloadInProgress
            ) {
                if (downloadInProgress) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (downloadInProgress) "正在下载…" else "下载原图到相册")
            }
            if (downloadMessage.isNotBlank()) {
                Text(
                    downloadMessage,
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    val optionsScroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
                val wide = maxWidth >= 520.dp && maxWidth > maxHeight
                val controlsWidth = (maxWidth * 0.43f).coerceIn(240.dp, 380.dp)
                val portraitOptionsHeight = (maxHeight * 0.42f).coerceAtMost(270.dp)
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text("壁纸预览", style = MaterialTheme.typography.titleLarge)
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onDismiss) { Text("关闭") }
                    }
                    if (wide) {
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            imageContent(Modifier.weight(1f).fillMaxHeight())
                            Column(Modifier.width(controlsWidth).fillMaxHeight()) {
                                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(optionsScroll)) { options() }
                                applyAction()
                            }
                        }
                    } else {
                        imageContent(Modifier.fillMaxWidth().weight(1f))
                        Column(Modifier.fillMaxWidth().heightIn(max = portraitOptionsHeight).verticalScroll(optionsScroll)) { options() }
                        applyAction()
                    }
                }
            }
        }
    }
}

/** Keep the configured page size while using extra columns in a wider window. */
internal fun adaptiveGalleryColumns(width: Dp, preferredColumns: Int): Int {
    val preferred = preferredColumns.coerceIn(2, 4)
    if (width < 540.dp) return preferred
    return ((width.value - 32f + 8f) / 128f).toInt().coerceIn(preferred, 8)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentPicker(selected: AccentStyle, onSelected: (AccentStyle) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentStyle.entries.forEach { style ->
            val color = when (style) {
                AccentStyle.FOREST -> Color(0xFF41694E)
                AccentStyle.OCEAN -> Color(0xFF3D668C)
                AccentStyle.SUNSET -> Color(0xFFAD6651)
                AccentStyle.DYNAMIC -> MaterialTheme.colorScheme.primary
            }
            Column(Modifier.width(64.dp).selectable(selected == style, role = Role.RadioButton, onClick = { onSelected(style) }).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(40.dp).border(if (selected == style) 2.dp else 0.dp,
                    if (selected == style) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape).padding(5.dp).clip(CircleShape).background(color),
                    contentAlignment = Alignment.Center) {
                    if (selected == style) Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                }
                Spacer(Modifier.height(6.dp))
                Text(when (style) { AccentStyle.FOREST -> "森林"; AccentStyle.OCEAN -> "海洋"; AccentStyle.SUNSET -> "落日"; AccentStyle.DYNAMIC -> "系统" },
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun HourPickerDialog(title: String, initialHour: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(initialHour) }
    val listHeight = (LocalConfiguration.current.screenHeightDp.dp - 220.dp).coerceIn(64.dp, 260.dp)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (initialHour - 2).coerceAtLeast(0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("选择生效整点", style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = listHeight), state = listState) {
                    items(24) { hour ->
                        Row(Modifier.fillMaxWidth().selectable(selected == hour, role = Role.RadioButton,
                            onClick = { selected = hour }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected == hour, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text("%02d:00".format(hour))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(selected) }) { Text("确定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}
