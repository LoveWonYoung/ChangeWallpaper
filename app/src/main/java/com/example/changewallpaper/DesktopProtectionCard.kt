package com.example.changewallpaper

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
internal fun DesktopProtectionCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var granted by remember { mutableStateOf(DesktopWallpaperGuard.hasUsageAccess(context)) }
    var settingsError by remember { mutableStateOf("") }
    DisposableEffect(context, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = DesktopWallpaperGuard.hasUsageAccess(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (granted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (granted) "仅在桌面更换 · 已授权" else "桌面保护 · 需要授权", style = MaterialTheme.typography.titleMedium)
            Text(
                if (granted) "游戏、其他应用、锁屏或无法确认桌面时均跳过更换，自动任务在下个检查周期再尝试。"
                else "请开启“使用情况访问权限”，用于在本机判断当前是否为桌面。未授权时不会更换壁纸。",
                style = MaterialTheme.typography.bodySmall
            )
            Text("应用内手动更换同样受此限制。请返回桌面等待自动更换，或使用桌面快捷方式。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton({
                settingsError = ""
                val opened = runCatching {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        data = "package:${context.packageName}".toUri()
                    })
                }.isSuccess || runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }.isSuccess
                if (!opened) settingsError = "请在系统设置中搜索“使用情况访问权限”，并允许自动壁纸。"
            }) { Text(if (granted) "管理使用情况权限" else "去授权") }
            if (settingsError.isNotBlank()) Text(settingsError, style = MaterialTheme.typography.bodySmall)
        }
    }
}
