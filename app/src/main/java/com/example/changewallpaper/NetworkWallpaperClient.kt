package com.example.changewallpaper

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class DownloadedWallpaper(
    val image: WallpaperImage,
    val historyUrl: String
)

class WallpaperHttpException(
    val statusCode: Int,
    message: String
) : IOException(message) {
    val retryable: Boolean get() = statusCode >= 500
}

object NetworkWallpaperClient {
    const val BASE_URL = "https://wallpaper.wonyoung.top"
    const val NETWORK_ALBUM_ID = "network"

    private val baseUrl = BASE_URL.toHttpUrl()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadCurrent(context: Context, mode: NetworkMode): DownloadedWallpaper =
        download(
            context = context,
            url = endpointUrl(mode),
            displayName = when (mode) {
                NetworkMode.CURRENT -> "同步壁纸"
                NetworkMode.NEXT -> "独立轮换壁纸"
                NetworkMode.RANDOM -> "随机壁纸"
            }
        )

    suspend fun downloadGalleryImage(context: Context, fileName: String): DownloadedWallpaper =
        download(context, imageUrl(fileName), fileName)

    suspend fun fetchGallery(): List<NetworkWallpaper> = withContext(Dispatchers.IO) {
        val request = noCacheRequest(infoUrl())
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpException(response.code)
            val body = response.body?.string() ?: throw IOException("服务端返回了空响应")
            val names = JSONObject(body).optJSONArray("wallpapers")
            buildList {
                if (names == null) return@buildList
                for (index in 0 until names.length()) {
                    val name = names.optString(index).takeIf { it.isNotBlank() } ?: continue
                    add(NetworkWallpaper(name, thumbnailUrl(name), imageUrl(name)))
                }
            }
        }
    }

    fun endpointUrl(mode: NetworkMode): String =
        baseUrl.newBuilder().addPathSegment(mode.endpoint).build().toString()

    fun thumbnailUrl(fileName: String): String =
        baseUrl.newBuilder().addPathSegment("thumb").addPathSegment(fileName).build().toString()

    fun imageUrl(fileName: String): String =
        baseUrl.newBuilder().addPathSegment("image").addPathSegment(fileName).build().toString()

    private fun infoUrl(): String = baseUrl.newBuilder().addPathSegment("info").build().toString()

    private suspend fun download(
        context: Context,
        url: String,
        displayName: String
    ): DownloadedWallpaper = withContext(Dispatchers.IO) {
        client.newCall(noCacheRequest(url)).execute().use { response ->
            if (!response.isSuccessful) throw httpException(response.code)
            val contentType = response.header("Content-Type").orEmpty()
            if (!contentType.startsWith("image/", ignoreCase = true)) {
                throw IOException("服务端返回的不是图片")
            }
            val body = response.body ?: throw IOException("服务端返回了空图片")
            val destination = File(context.cacheDir, "wallpaper-latest")
            val temporary = File(context.cacheDir, "wallpaper-latest.tmp")
            body.byteStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            if (destination.length() == 0L) throw IOException("下载的图片为空")
            DownloadedWallpaper(
                image = WallpaperImage(NETWORK_ALBUM_ID, destination.toUri().toString(), displayName),
                historyUrl = url
            )
        }
    }

    private fun noCacheRequest(url: String): Request = Request.Builder()
        .url(url)
        .cacheControl(CacheControl.FORCE_NETWORK)
        .header("Cache-Control", "no-cache")
        .build()

    private fun httpException(statusCode: Int): WallpaperHttpException {
        val message = when (statusCode) {
            404 -> "服务端没有找到壁纸"
            in 500..599 -> "壁纸服务暂时不可用（HTTP $statusCode）"
            else -> "壁纸请求失败（HTTP $statusCode）"
        }
        return WallpaperHttpException(statusCode, message)
    }
}
