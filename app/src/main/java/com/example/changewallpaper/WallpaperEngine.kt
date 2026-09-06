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
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
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
    private const val MAX_CANVAS_WIDTH = 1_440
    private const val MAX_CANVAS_HEIGHT = 3_200
    private const val MAX_DECODE_PIXELS = 12_000_000L

    suspend fun apply(
        context: Context,
        image: WallpaperImage,
        cropMode: CropMode,
        target: WallpaperTarget
    ) = withContext(Dispatchers.IO) {
        DesktopWallpaperGuard.requireDesktop(context)
        val manager = WallpaperManager.getInstance(context)
        val targetSize = resolveTargetSize(context)
        val decoded = decodeSampled(context, image.uri.toUri(), targetSize.width, targetSize.height)
        try {
            val rendered = render(decoded, targetSize.width, targetSize.height, cropMode)
            try {
                applyRendered(context, manager, rendered, target)
            } finally {
                if (rendered !== decoded) rendered.recycle()
            }
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    private fun applyRendered(context: Context, manager: WallpaperManager, bitmap: Bitmap, target: WallpaperTarget) {
        val cropHint = Rect(0, 0, bitmap.width, bitmap.height)
        DesktopWallpaperGuard.requireDesktop(context)
        if (target == WallpaperTarget.BOTH) {
            manager.setBitmap(bitmap, cropHint, true, WallpaperManager.FLAG_SYSTEM)
            DesktopWallpaperGuard.requireDesktop(context)
            runCatching {
                manager.setBitmap(bitmap, cropHint, true, WallpaperManager.FLAG_LOCK)
            }
        } else {
            manager.setBitmap(bitmap, cropHint, true, target.toFlags())
        }
    }

    @Suppress("DEPRECATION")
    internal fun resolveTargetSize(context: Context): TargetSize {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val (rawWidth, rawHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
        val width = rawWidth.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val height = rawHeight.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        val scale = min(
            1f,
            min(
                MAX_CANVAS_WIDTH.toFloat() / width,
                MAX_CANVAS_HEIGHT.toFloat() / height
            )
        )
        return TargetSize(
            width = max(1, (width * scale).roundToInt()),
            height = max(1, (height * scale).roundToInt())
        )
    }

    private fun decodeSampled(context: Context, uri: Uri, width: Int, height: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openImageStream(context, uri).use { stream ->
            checkNotNull(stream) { "无法读取图片" }
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "不是可解码的图片" }
        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, width, height)
        val bitmap = openImageStream(context, uri).use { stream ->
            checkNotNull(stream) { "无法读取图片" }
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: error("图片解码失败")

        val orientation = openImageStream(context, uri).use { stream ->
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

    private fun openImageStream(context: Context, uri: Uri): InputStream? =
        if (uri.scheme == "file") File(requireNotNull(uri.path)).inputStream()
        else context.contentResolver.openInputStream(uri)

    internal fun calculateSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        var sample = 1
        while (
            sourceWidth / (sample * 2) >= targetWidth &&
            sourceHeight / (sample * 2) >= targetHeight
        ) {
            sample *= 2
        }
        while (
            sourceWidth.toLong() / sample * (sourceHeight.toLong() / sample) > MAX_DECODE_PIXELS
        ) {
            sample *= 2
        }
        return sample
    }

    internal fun render(source: Bitmap, width: Int, height: Int, mode: CropMode): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shouldFill = when (mode) {
            CropMode.FILL -> true
            CropMode.FIT, CropMode.BLUR -> false
            CropMode.SMART -> {
                val sourceRatio = source.width.toFloat() / source.height
                val targetRatio = width.toFloat() / height
                max(sourceRatio, targetRatio) / min(sourceRatio, targetRatio) <= 1.35f
            }
        }
        drawSoftBackground(
            canvas = canvas,
            source = source,
            width = width,
            height = height,
            darkness = if (mode == CropMode.BLUR) 55 else 20,
            paint = paint
        )
        drawScaled(canvas, source, width, height, fill = shouldFill, paint = paint)
        return output
    }

    private fun drawSoftBackground(
        canvas: Canvas,
        source: Bitmap,
        width: Int,
        height: Int,
        darkness: Int,
        paint: Paint
    ) {
        canvas.drawColor(averageOpaqueColor(source), PorterDuff.Mode.SRC)
        val tinyWidth = max(12, min(56, source.width / 10))
        val tinyHeight = max(12, min(56, source.height / 10))
        val tiny = Bitmap.createScaledBitmap(source, tinyWidth, tinyHeight, true)
        drawScaled(canvas, tiny, width, height, fill = true, paint = paint)
        if (tiny !== source) tiny.recycle()
        if (darkness > 0) canvas.drawColor(Color.argb(darkness, 0, 0, 0))
    }

    private fun averageOpaqueColor(bitmap: Bitmap): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        val stepX = max(1, bitmap.width / 12)
        val stepY = max(1, bitmap.height / 12)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= 64) {
                    red += Color.red(pixel)
                    green += Color.green(pixel)
                    blue += Color.blue(pixel)
                    count++
                }
            }
        }
        return if (count == 0L) Color.rgb(40, 40, 40) else Color.rgb(
            (red / count).toInt(),
            (green / count).toInt(),
            (blue / count).toInt()
        )
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
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight), paint)
    }

    private fun WallpaperTarget.toFlags(): Int = when (this) {
        WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
        WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
        WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }

    internal data class TargetSize(val width: Int, val height: Int)
}
