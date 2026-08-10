@file:Suppress("UseKtx")

package com.example.changewallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

object WallpaperScanner {
    suspend fun scan(context: Context, album: WallpaperAlbum): List<WallpaperImage> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, album.treeUri.toUri())
                ?: throw SecurityException("无法访问相册 ${album.name}")
            buildList { collect(root, album, this) }
                .sortedBy { it.name.lowercase() }
        }

    private fun collect(
        folder: DocumentFile,
        album: WallpaperAlbum,
        output: MutableList<WallpaperImage>
    ) {
        folder.listFiles().forEach { file ->
            when {
                file.isDirectory && album.includeSubfolders -> collect(file, album, output)
                file.isFile && isImage(file) -> output += WallpaperImage(
                    albumId = album.id,
                    uri = file.uri.toString(),
                    name = file.name ?: "未命名图片"
                )
            }
        }
    }

    private fun isImage(file: DocumentFile): Boolean {
        if (file.type?.startsWith("image/") == true) return true
        return file.name?.substringAfterLast('.', "")?.lowercase() in IMAGE_EXTENSIONS
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")
}

data class SelectionResult(
    val image: WallpaperImage,
    val selectedIndex: Int,
    val nextIndex: Int,
    val recentUris: List<String>
)

object SelectionLogic {
    fun select(
        images: List<WallpaperImage>,
        settings: AppSettings,
        albumId: String,
        command: RunCommand,
        randomIndex: (Int) -> Int = { Random.nextInt(it) }
    ): SelectionResult? {
        val available = images.filterNot { it.uri in settings.excludedUris }
        if (available.isEmpty()) return null
        val storedIndex = Math.floorMod(settings.indexes[albumId] ?: 0, available.size)
        val effectiveMode = if (command == RunCommand.RANDOM) RotationMode.RANDOM else settings.rotationMode

        val selectedIndex = when {
            command == RunCommand.PREVIOUS -> Math.floorMod(storedIndex - 2, available.size)
            effectiveMode == RotationMode.SEQUENTIAL -> storedIndex
            effectiveMode == RotationMode.RANDOM -> {
                if (available.size == 1) 0 else {
                    val lastUri = settings.recentUris.lastOrNull()
                    val candidates = available.indices.filter { available[it].uri != lastUri }
                    candidates[randomIndex(candidates.size).coerceIn(candidates.indices)]
                }
            }
            else -> {
                val unseen = available.indices.filter { available[it].uri !in settings.recentUris }
                val candidates = unseen.ifEmpty { available.indices.toList() }
                candidates[randomIndex(candidates.size).coerceIn(candidates.indices)]
            }
        }
        val selected = available[selectedIndex]
        val recent = when (effectiveMode) {
            RotationMode.SHUFFLE -> {
                val prior = if (available.all { it.uri in settings.recentUris }) emptyList() else settings.recentUris
                (prior + selected.uri).takeLast(available.size)
            }
            else -> listOf(selected.uri)
        }
        return SelectionResult(selected, selectedIndex, (selectedIndex + 1) % available.size, recent)
    }
}

object WallpaperRenderer {
    suspend fun apply(
        context: Context,
        image: WallpaperImage,
        cropMode: CropMode,
        target: WallpaperTarget
    ) = withContext(Dispatchers.IO) {
        val manager = WallpaperManager.getInstance(context)
        val metrics = context.resources.displayMetrics
        val targetWidth = manager.desiredMinimumWidth.takeIf { it > 0 } ?: metrics.widthPixels
        val targetHeight = manager.desiredMinimumHeight.takeIf { it > 0 } ?: metrics.heightPixels
        val safeWidth = targetWidth.coerceAtMost(2_560)
        val safeHeight = targetHeight.coerceAtMost(4_096)
        val decoded = decodeSampled(context, image.uri.toUri(), safeWidth, safeHeight)
        try {
            val rendered = render(decoded, safeWidth, safeHeight, cropMode)
            try {
                manager.setBitmap(rendered, null, true, target.toFlags())
            } finally {
                if (rendered !== decoded) rendered.recycle()
            }
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, width: Int, height: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { stream ->
            checkNotNull(stream) { "无法读取图片" }
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "不是可解码的图片" }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) {
            sample *= 2
        }
        val bitmap = context.contentResolver.openInputStream(uri).use { stream ->
            checkNotNull(stream) { "无法读取图片" }
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: error("图片解码失败")

        val orientation = context.contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) ExifInterface.ORIENTATION_NORMAL
            else runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postScale(-1f, 1f); postRotate(270f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postScale(-1f, 1f); postRotate(90f) }
            }
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun render(source: Bitmap, width: Int, height: Int, mode: CropMode): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (mode == CropMode.BLUR) {
            val tinyWidth = max(16, min(64, source.width / 8))
            val tinyHeight = max(16, min(64, source.height / 8))
            val tiny = Bitmap.createScaledBitmap(source, tinyWidth, tinyHeight, true)
            drawScaled(canvas, tiny, width, height, fill = true, paint = paint)
            tiny.recycle()
            canvas.drawColor(Color.argb(55, 0, 0, 0))
            drawScaled(canvas, source, width, height, fill = false, paint = paint)
        } else {
            canvas.drawColor(Color.BLACK)
            drawScaled(canvas, source, width, height, fill = mode == CropMode.FILL, paint = paint)
        }
        return output
    }

    private fun drawScaled(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        fill: Boolean,
        paint: Paint
    ) {
        val scale = if (fill) {
            max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        } else {
            min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        }
        val drawWidth = (bitmap.width * scale).roundToInt()
        val drawHeight = (bitmap.height * scale).roundToInt()
        val left = (width - drawWidth) / 2
        val top = (height - drawHeight) / 2
        canvas.drawBitmap(bitmap, null, android.graphics.Rect(left, top, left + drawWidth, top + drawHeight), paint)
    }

    private fun WallpaperTarget.toFlags(): Int = when (this) {
        WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
        WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
        WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }
}
