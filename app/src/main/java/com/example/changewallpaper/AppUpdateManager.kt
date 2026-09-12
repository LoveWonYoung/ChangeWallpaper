package com.example.changewallpaper

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class AppUpdateInfo(
    val app: String,
    val platform: String,
    val channel: String,
    val version: String,
    val versionCode: Long,
    val url: String,
    val sha256: String,
    val size: Long,
    val notes: String
)

data class AppUpdateState(
    val availableUpdate: AppUpdateInfo? = null,
    val showDialog: Boolean = false,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val downloadTotalBytes: Long = 0,
    val downloadedApk: File? = null,
    val installRequest: Long = 0,
    val message: String = ""
) {
    val downloadProgress: Float?
        get() = downloadTotalBytes.takeIf { it > 0 }?.let {
            (downloadedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

object AppUpdateClient {
    const val APP_ID = "changewallpaper"
    const val PLATFORM = "android"
    const val CHANNEL = "stable"
    const val BASE_URL = "https://update.wonyoung.top"
    const val LATEST_URL = "$BASE_URL/$APP_ID/$PLATFORM/$CHANNEL/latest.json"

    private const val MAX_MANIFEST_BYTES = 1024L * 1024L
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.MINUTES)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    suspend fun checkForUpdate(currentVersionCode: Long): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .header("Cache-Control", "no-cache, no-store")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("检查更新失败：HTTP ${response.code}")
            val body = response.body ?: throw IOException("更新信息为空")
            if (body.contentLength() > MAX_MANIFEST_BYTES) throw IOException("更新信息过大")
            val raw = body.string()
            if (raw.toByteArray().size > MAX_MANIFEST_BYTES) throw IOException("更新信息过大")
            val info = parseManifest(raw)
            if (info.versionCode > currentVersionCode) info else null
        }
    }

    internal fun parseManifest(raw: String): AppUpdateInfo {
        val json = JSONObject(raw)
        val info = AppUpdateInfo(
            app = json.getString("app"),
            platform = json.getString("platform"),
            channel = json.getString("channel"),
            version = json.getString("version"),
            versionCode = json.getLong("versionCode"),
            url = json.getString("url"),
            sha256 = json.getString("sha256").lowercase(),
            size = json.optLong("size", 0L),
            notes = json.optString("notes")
        )
        validateManifest(info)
        return info
    }

    internal fun validateManifest(info: AppUpdateInfo) {
        if (info.app != APP_ID || info.platform != PLATFORM || info.channel != CHANNEL) {
            throw IOException("更新信息与当前应用不匹配")
        }
        if (info.version.isBlank() || info.versionCode <= 0 || info.size < 0) {
            throw IOException("更新版本信息无效")
        }
        val downloadUrl = info.url.toHttpUrlOrNull()
        if (downloadUrl == null || !downloadUrl.isHttps) throw IOException("更新下载地址无效")
        if (!sha256Pattern.matches(info.sha256)) throw IOException("更新校验值无效")
    }

    suspend fun downloadAndVerify(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        if (!updateDir.isDirectory) throw IOException("无法创建更新缓存目录")
        updateDir.listFiles()?.forEach { it.delete() }
        val partial = File(updateDir, "changewallpaper-${info.versionCode}.apk.part")
        val destination = File(updateDir, "changewallpaper-${info.versionCode}.apk")
        val request = Request.Builder().url(info.url).cacheControl(CacheControl.FORCE_NETWORK).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载更新失败：HTTP ${response.code}")
                val body = response.body ?: throw IOException("更新安装包为空")
                val responseSize = body.contentLength()
                if (info.size > 0 && responseSize >= 0 && responseSize != info.size) {
                    throw IOException("更新安装包大小不匹配")
                }
                val total = info.size.takeIf { it > 0 } ?: responseSize.coerceAtLeast(0)
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                var lastReportedPercent = -1L
                partial.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            copied += count
                            val percent = if (total > 0) copied * 100 / total else copied / (256 * 1024)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(copied, total)
                            }
                        }
                    }
                }
                if (info.size > 0 && copied != info.size) throw IOException("更新安装包大小不匹配")
                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actualSha256.equals(info.sha256, ignoreCase = true)) {
                    throw IOException("更新安装包校验失败")
                }
                validateApk(context, partial, info.versionCode)
                if (!partial.renameTo(destination)) throw IOException("无法保存更新安装包")
                onProgress(copied, total)
            }
            destination
        } catch (exception: Exception) {
            partial.delete()
            destination.delete()
            throw exception
        }
    }

    private fun validateApk(context: Context, apk: File, expectedVersionCode: Long) {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: throw IOException("下载的文件不是有效 APK")
        if (packageInfo.packageName != context.packageName ||
            PackageInfoCompat.getLongVersionCode(packageInfo) != expectedVersionCode
        ) {
            throw IOException("APK 包名或版本与更新信息不匹配")
        }
    }
}

enum class InstallLaunchResult { INSTALLER_OPENED, SETTINGS_OPENED, FAILED }

object AppUpdateInstaller {
    fun launch(context: Context, apk: File): InstallLaunchResult = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            )
            InstallLaunchResult.SETTINGS_OPENED
        } else {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            InstallLaunchResult.INSTALLER_OPENED
        }
    } catch (_: Exception) {
        InstallLaunchResult.FAILED
    }
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()
    private var automaticCheckStarted = false

    fun checkAutomatically() {
        if (automaticCheckStarted) return
        automaticCheckStarted = true
        viewModelScope.launch {
            delay(3_000)
            check(manual = false)
        }
    }

    fun checkManually() {
        viewModelScope.launch { check(manual = true) }
    }

    private suspend fun check(manual: Boolean) {
        if (_state.value.isChecking || _state.value.isDownloading) return
        _state.update { it.copy(isChecking = true, message = if (manual) "正在检查更新…" else it.message) }
        runCatching { AppUpdateClient.checkForUpdate(BuildConfig.VERSION_CODE.toLong()) }
            .onSuccess { info ->
                if (info == null) {
                    _state.update {
                        it.copy(
                            availableUpdate = null,
                            showDialog = false,
                            isChecking = false,
                            message = if (manual) "当前已是最新版本" else it.message
                        )
                    }
                } else {
                    val shouldPrompt = manual || info.versionCode > preferences.getLong(LAST_PROMPTED_VERSION, 0)
                    if (shouldPrompt) {
                        preferences.edit { putLong(LAST_PROMPTED_VERSION, info.versionCode) }
                    }
                    _state.update {
                        it.copy(
                            availableUpdate = info,
                            showDialog = shouldPrompt,
                            isChecking = false,
                            message = "发现新版本 ${info.version}"
                        )
                    }
                }
            }
            .onFailure {
                _state.update {
                    it.copy(
                        isChecking = false,
                        message = if (manual) "检查更新失败，请稍后重试" else it.message
                    )
                }
            }
    }

    fun showAvailableUpdate() {
        if (_state.value.availableUpdate != null) _state.update { it.copy(showDialog = true) }
    }

    fun dismissDialog() {
        if (!_state.value.isDownloading) _state.update { it.copy(showDialog = false) }
    }

    fun downloadAndInstall() {
        val snapshot = _state.value
        snapshot.downloadedApk?.let {
            _state.update { state -> state.copy(installRequest = state.installRequest + 1, message = "正在打开安装界面…") }
            return
        }
        val info = snapshot.availableUpdate ?: return
        if (snapshot.isDownloading) return
        viewModelScope.launch {
            _state.update {
                it.copy(isDownloading = true, downloadedBytes = 0, downloadTotalBytes = info.size, message = "正在下载更新…")
            }
            runCatching {
                AppUpdateClient.downloadAndVerify(getApplication(), info) { downloaded, total ->
                    _state.update { it.copy(downloadedBytes = downloaded, downloadTotalBytes = total) }
                }
            }.onSuccess { apk ->
                _state.update {
                    it.copy(
                        isDownloading = false,
                        downloadedApk = apk,
                        installRequest = it.installRequest + 1,
                        message = "下载完成，校验通过"
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isDownloading = false,
                        downloadedApk = null,
                        message = error.message ?: "更新下载失败"
                    )
                }
            }
        }
    }

    fun onInstallLaunchResult(result: InstallLaunchResult) {
        _state.update {
            it.copy(
                message = when (result) {
                    InstallLaunchResult.INSTALLER_OPENED -> "请在系统界面确认安装"
                    InstallLaunchResult.SETTINGS_OPENED -> "允许安装未知应用后，请返回并再次点击安装"
                    InstallLaunchResult.FAILED -> "无法打开系统安装界面"
                }
            )
        }
    }

    companion object {
        private const val LAST_PROMPTED_VERSION = "last_prompted_version_code"
    }
}
