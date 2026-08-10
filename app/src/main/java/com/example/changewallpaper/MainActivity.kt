package com.example.changewallpaper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.changewallpaper.ui.theme.ChangeWallpaperTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var refreshVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChangeWallpaperTheme {
                WallpaperApp(
                    refreshVersion = refreshVersion,
                    onRefresh = { refreshVersion++ }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshVersion++
    }
}

private enum class IntervalUnit(val label: String, val minutes: Long) {
    MINUTES("分钟", 1),
    HOURS("小时", 60),
    DAYS("天", 1_440)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallpaperApp(refreshVersion: Int, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { WallpaperPreferences(context) }
    val settings = remember(refreshVersion) { preferences.load() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var intervalValue by remember(settings.intervalMinutes) {
        mutableStateOf(displayInterval(settings.intervalMinutes).first.toString())
    }
    var intervalUnit by remember(settings.intervalMinutes) {
        mutableStateOf(displayInterval(settings.intervalMinutes).second)
    }
    var target by remember(settings.target) { mutableStateOf(settings.target) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val folderName = DocumentFile.fromTreeUri(context, uri)?.name ?: "已选文件夹"
                preferences.saveFolder(uri.toString(), folderName)
                onRefresh()
                showMessage("已选择“$folderName”")
            } catch (_: SecurityException) {
                showMessage("无法保存文件夹访问权限，请重新选择")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(isEnabled = settings.isEnabled)

            SectionCard(title = "1  选择壁纸文件夹") {
                Text(
                    text = if (settings.folderUri == null) "尚未选择文件夹" else settings.folderName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "支持 JPG、PNG、WebP、HEIC 等图片，按文件名顺序循环切换。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { folderLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (settings.folderUri == null) "选择文件夹" else "更换文件夹")
                }
            }

            SectionCard(title = "2  设置切换频率") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = intervalValue,
                        onValueChange = { value ->
                            if (value.all(Char::isDigit) && value.length <= 5) intervalValue = value
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("间隔") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IntervalUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = intervalUnit == unit,
                                onClick = { intervalUnit = unit },
                                label = { Text(unit.label) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Android 后台周期任务的最短可靠间隔为 15 分钟，实际执行时间可能因省电策略略有延迟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(title = "3  应用到") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetOption("主屏幕", WallpaperTarget.HOME, target) { target = it }
                    TargetOption("锁定屏幕", WallpaperTarget.LOCK, target) { target = it }
                    TargetOption("主屏幕和锁定屏幕", WallpaperTarget.BOTH, target) { target = it }
                }
            }

            SectionCard(title = "自动更换") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.isEnabled) "正在运行" else "当前已停止",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (settings.isEnabled) {
                                "每 ${formatInterval(settings.intervalMinutes)}自动切换"
                            } else {
                                "开启后，即使退出应用也会继续工作"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.isEnabled,
                        onCheckedChange = { enable ->
                            if (!enable) {
                                WallpaperScheduler.stop(context)
                                preferences.setEnabled(false)
                                onRefresh()
                                showMessage("已停止自动更换")
                            } else {
                                val minutes = intervalValue.toLongOrNull()?.times(intervalUnit.minutes)
                                when {
                                    settings.folderUri == null -> showMessage("请先选择壁纸文件夹")
                                    minutes == null || minutes <= 0 -> showMessage("请输入有效的切换间隔")
                                    minutes < 15 -> showMessage("切换间隔不能少于 15 分钟")
                                    else -> {
                                        preferences.saveSchedule(minutes, target, true)
                                        WallpaperScheduler.start(context, minutes)
                                        onRefresh()
                                        showMessage("自动更换已开启")
                                    }
                                }
                            }
                        }
                    )
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (settings.folderUri == null) {
                            showMessage("请先选择壁纸文件夹")
                        } else {
                            preferences.saveSchedule(settings.intervalMinutes, target, settings.isEnabled)
                            WallpaperScheduler.changeNow(context)
                            showMessage("已提交更换任务，稍后即可看到新壁纸")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("立即更换一次")
                }
            }

            if (settings.lastWallpaperName.isNotBlank() || settings.lastError.isNotBlank()) {
                LastRunCard(settings)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(isEnabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "景",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "自动壁纸",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isEnabled) "让每次点亮屏幕都有新鲜感" else "从一个文件夹循环更换壁纸",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun TargetOption(
    label: String,
    value: WallpaperTarget,
    selected: WallpaperTarget,
    onSelected: (WallpaperTarget) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelected(value) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LastRunCard(settings: WallpaperSettings) {
    val hasError = settings.lastError.isNotBlank()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (hasError) "上次执行遇到问题" else "上次已更换",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasError) settings.lastError else settings.lastWallpaperName,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!hasError && settings.lastChangedAt > 0) {
                Text(
                    text = formatTime(settings.lastChangedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun displayInterval(minutes: Long): Pair<Long, IntervalUnit> = when {
    minutes >= IntervalUnit.DAYS.minutes && minutes % IntervalUnit.DAYS.minutes == 0L ->
        minutes / IntervalUnit.DAYS.minutes to IntervalUnit.DAYS
    minutes >= IntervalUnit.HOURS.minutes && minutes % IntervalUnit.HOURS.minutes == 0L ->
        minutes / IntervalUnit.HOURS.minutes to IntervalUnit.HOURS
    else -> minutes to IntervalUnit.MINUTES
}

private fun formatInterval(minutes: Long): String = when {
    minutes % 1_440L == 0L -> "${minutes / 1_440L} 天"
    minutes % 60L == 0L -> "${minutes / 60L} 小时"
    else -> "$minutes 分钟"
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
