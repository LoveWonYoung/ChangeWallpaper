package com.example.changewallpaper

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadCurrent(
        context: Context,
        mode: NetworkMode,
        apiBaseUrl: String = BASE_URL
    ): DownloadedWallpaper =
        download(
            context = context,
            url = endpointUrl(mode, apiBaseUrl),
            displayName = when (mode) {
                NetworkMode.CURRENT -> "同步壁纸"
                NetworkMode.NEXT -> "独立轮换壁纸"
                NetworkMode.RANDOM -> "随机壁纸"
            }
        )

    suspend fun downloadGalleryImage(
        context: Context,
        fileName: String,
        apiBaseUrl: String = BASE_URL
    ): DownloadedWallpaper = download(context, imageUrl(fileName, apiBaseUrl), fileName)

    suspend fun copyGalleryImageTo(
        fileName: String,
        apiBaseUrl: String = BASE_URL,
        output: OutputStream
    ): String = withContext(Dispatchers.IO) {
        client.newCall(noCacheRequest(imageUrl(fileName, apiBaseUrl))).execute().use { response ->
            if (!response.isSuccessful) throw httpException(response.code)
            val contentType = response.header("Content-Type").orEmpty().substringBefore(';').trim()
            if (!contentType.startsWith("image/", ignoreCase = true)) {
                throw IOException("服务端返回的不是图片")
            }
            val body = response.body ?: throw IOException("服务端返回了空图片")
            body.byteStream().use { input -> input.copyTo(output) }
            output.flush()
            contentType.lowercase()
        }
    }

    suspend fun fetchAlbums(apiBaseUrl: String = BASE_URL): NetworkAlbumsState = withContext(Dispatchers.IO) {
        val baseUrl = apiBaseUrl.toHttpUrl()
        val request = noCacheRequest(
            baseUrl.newBuilder().addPathSegment("albums").build().toString()
        )
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpException(response.code)
            val body = response.body?.string() ?: throw IOException("服务端返回了空响应")
            val json = JSONObject(body)
            val total = json.optInt("wallpaper_count", -1)
            if (total < 0) throw IOException("服务端返回的相册统计无效")
            val items = json.optJSONArray("albums") ?: JSONArray()
            val albums = buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val cover = item.optString("cover")
                    val count = item.optInt("count", -1)
                    if (cover.isBlank() || count < 0) continue
                    add(
                        NetworkAlbum(
                            name = item.optString("name"),
                            coverFileName = cover,
                            count = count,
                            coverUrl = thumbnailUrl(cover, apiBaseUrl)
                        )
                    )
                }
            }
            NetworkAlbumsState(albums = albums, totalCount = total)
        }
    }

    suspend fun fetchGalleryPage(
        offset: Int,
        limit: Int,
        folder: String? = null,
        apiBaseUrl: String = BASE_URL
    ): NetworkGalleryPage = withContext(Dispatchers.IO) {
        require(offset >= 0) { "offset must not be negative" }
        require(limit in 1..100) { "limit must be between 1 and 100" }
        val urlBuilder = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("gallery")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
        if (folder != null) urlBuilder.addQueryParameter("folder", folder)
        val url = urlBuilder.build()
            .toString()
        val request = noCacheRequest(url)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw httpException(response.code)
            val body = response.body?.string() ?: throw IOException("服务端返回了空响应")
            val json = JSONObject(body)
            val total = json.optInt("total", -1)
            val actualOffset = json.optInt("offset", -1)
            val actualLimit = json.optInt("limit", -1)
            if (total < 0 || actualOffset < 0 || actualLimit !in 1..100) {
                throw IOException("服务端返回的分页信息无效")
            }
            val names = json.optJSONArray("wallpapers") ?: throw IOException("服务端未返回壁纸列表")
            val fileNames = buildList {
                for (index in 0 until names.length()) {
                    names.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
            NetworkGalleryPage(
                total = total,
                offset = actualOffset,
                limit = actualLimit,
                hasMore = json.optBoolean("has_more", false),
                fileNames = fileNames
            )
        }
    }

    fun galleryItem(fileName: String, apiBaseUrl: String = BASE_URL): NetworkWallpaper = NetworkWallpaper(
        fileName = fileName,
        thumbnailUrl = thumbnailUrl(fileName, apiBaseUrl),
        imageUrl = imageUrl(fileName, apiBaseUrl)
    )

    fun endpointUrl(mode: NetworkMode, apiBaseUrl: String = BASE_URL): String =
        apiBaseUrl.toHttpUrl().newBuilder().addPathSegment(mode.endpoint).build().toString()

    fun thumbnailUrl(fileName: String, apiBaseUrl: String = BASE_URL): String =
        galleryFileUrl("thumb", fileName, apiBaseUrl)

    fun imageUrl(fileName: String, apiBaseUrl: String = BASE_URL): String =
        galleryFileUrl("image", fileName, apiBaseUrl)

    fun normalizeBaseUrl(value: String): String? {
        val url = value.trim().toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        return url.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .removeSuffix("/")
    }

    private fun galleryFileUrl(endpoint: String, fileName: String, apiBaseUrl: String): String {
        val builder = apiBaseUrl.toHttpUrl().newBuilder().addPathSegment(endpoint)
        fileName.split('/').forEach { segment ->
            builder.addPathSegment(segment)
        }
        return builder.build().toString()
    }

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
